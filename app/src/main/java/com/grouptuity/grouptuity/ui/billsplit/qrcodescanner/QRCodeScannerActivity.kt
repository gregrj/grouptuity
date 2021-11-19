package com.grouptuity.grouptuity.ui.billsplit.qrcodescanner

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.*
import android.util.TypedValue
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import com.android.volley.*
import com.android.volley.toolbox.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.databinding.ActivityCameraBinding
import java.util.concurrent.Executors
import kotlin.math.min



class QRCodeScannerActivity: AppCompatActivity() {
    private lateinit var viewModel: QRCodeScannerViewModel
    private lateinit var binding: ActivityCameraBinding
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var scanningReticle: ScanningReticle
    private var cameraReticleAnimator: CameraReticleAnimator? = null
    private var isInPortraitMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[QRCodeScannerViewModel::class.java].also {
            it.initialize(
                intent?.getSerializableExtra(getString(R.string.intent_key_qrcode_payment_method)) as? PaymentMethod
                    ?: PaymentMethod.CASH,
                intent?.getSerializableExtra(getString(R.string.intent_key_qrcode_diner_name)) as? String
                    ?: getString(R.string.qrcodescanner_unspecified_diner)
            )
        }

        isInPortraitMode = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        // Hack: Setting the navigation bar color with xml style had issues during testing. Setting
        // the color programmatically works.
        window.navigationBarColor = TypedValue().also { this.theme.resolveAttribute(R.attr.colorBackground, it, true) }.data

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        graphicOverlay = binding.overlay

        binding.closeButton.setOnClickListener { onBackPressed() }

        viewModel.cameraActive.observe(this) { if (it) { startCamera() } else { stopCamera() } }

        viewModel.chipInstructions.observe(this) {
            if (it == null) {
                binding.instructionsChip.visibility = View.GONE
            } else {
                binding.instructionsChip.text = it
                binding.instructionsChip.visibility = View.VISIBLE
            }
        }

        viewModel.displayResults.observe(this) {
            if (it != null) {
                QRCodeResultFragment.show(
                    supportFragmentManager,
                    it,
                    acceptCallback = {
                        finishActivityWithResult(wasPermissionDenied = false, hasCameraError = false, wasCanceled = false)
                    },
                    retryCallback = {
                        viewModel.retryLoad()
                    },
                    dismissCallback = {
                        // Update ViewModel if the fragment is directly dismissed
                        viewModel.clearBarcode()
                    }
                )
            } else {
                // Dismiss fragment if the barcode has been cleared in the ViewModel
                QRCodeResultFragment.dismiss(supportFragmentManager)
            }
        }

        permissionRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            if (it) {
                viewModel.allowCameraUse()
            } else {
                // Permission denied
                finishActivityWithResult(wasPermissionDenied = true, hasCameraError = false, wasCanceled = false)
            }
        }

        supportFragmentManager.setFragmentResultListener(getString(R.string.rationale_key_camera), this) { _, bundle ->
            if(bundle.getBoolean("resultKey")) {
                // User indicated to continue so try to acquire permission
                permissionRequestLauncher.launch(Manifest.permission.CAMERA)
            } else {
                // User dismissed dialog so finish activity
                finishActivityWithResult(wasPermissionDenied = true, hasCameraError = false, wasCanceled = false)
            }
        }
    }

    override fun onBackPressed() {
        if (viewModel.hasBarcode) {
            viewModel.clearBarcode()
        } else {
            finishActivityWithResult(wasPermissionDenied = false, hasCameraError = false, wasCanceled = true)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.clearBarcode()

        if (!viewModel.isPermissionGranted(Manifest.permission.CAMERA)) {
            viewModel.blockCameraUse()
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                RationaleDialogFragment(viewModel.getPaymentMethod()).show(supportFragmentManager, getString(R.string.rationale_key_camera))
            } else {
                permissionRequestLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            viewModel.allowCameraUse()
        }
    }

    private fun finishActivityWithResult(wasPermissionDenied: Boolean, hasCameraError: Boolean, wasCanceled: Boolean) {
        val data = Intent()
        data.putExtra(getString(R.string.intent_key_qrcode_payment_method), viewModel.getPaymentMethod())
        data.putExtra(getString(R.string.intent_key_qrcode_diner_name), viewModel.getDinerName())
        data.putExtra(getString(R.string.intent_key_qrcode_payment_method_address), viewModel.getVerifiedAddress())
        data.putExtra(getString(R.string.intent_key_qrcode_declined_camera_permission), wasPermissionDenied)
        data.putExtra(getString(R.string.intent_key_qrcode_camera_error), hasCameraError)
        data.putExtra(getString(R.string.intent_key_qrcode_canceled), wasCanceled)

        setResult(
            if (wasPermissionDenied || hasCameraError || wasCanceled)
                Activity.RESULT_CANCELED
            else
                Activity.RESULT_OK, data)

        finish()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
                scanningReticle = ScanningReticle()

                val imageAnalysis = ImageAnalysis.Builder().build().also {
                    it.setAnalyzer(Executors.newFixedThreadPool(1), BarcodeAnalyzer())
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis)

                // Setup button for toggling camera flash or hide if feature not present
                if (camera.cameraInfo.hasFlashUnit()) {
                    binding.flashButton.visibility = View.VISIBLE
                    binding.flashButton.setOnClickListener {
                        it.isSelected = !it.isSelected
                        camera.cameraControl.enableTorch(it.isSelected)
                    }
                } else {
                    binding.flashButton.visibility = View.GONE
                }

                // A delay is needed to prevent a white box on the GraphicOverlay from
                // momentarily displaying in the first frame.
                Handler(Looper.getMainLooper()).postDelayed({
                    graphicOverlay.clear()
                    cameraReticleAnimator?.start()
                    graphicOverlay.add(scanningReticle)
                    graphicOverlay.invalidate()
                }, 100)
            } catch(e: Exception) {
                finishActivityWithResult(wasPermissionDenied = false, hasCameraError = true, wasCanceled = false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraReticleAnimator?.cancel()
        ProcessCameraProvider.getInstance(this).get().unbindAll()
    }

    inner class BarcodeAnalyzer: ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            imageProxy.image?.apply {
                val imageWidth: Float
                val imageHeight: Float
                if (isInPortraitMode) {
                    imageWidth = height.toFloat()
                    imageHeight = width.toFloat()
                } else {
                    imageWidth = width.toFloat()
                    imageHeight = height.toFloat()
                }

                scanner.process(InputImage.fromMediaImage(this, imageProxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { results ->

                        val barcodeResult = results.firstOrNull { barcode ->
                            val boundingBox = barcode.boundingBox ?: return@firstOrNull false

                            // Barcode dimensions appear to be contracted ~10% on each side
                            // horizontally from the barcode displayed on the preview. Unclear if
                            // this is normal behavior or a bug, but it is not relevant for the
                            // barcode detection here.
                            val box = RectF(boundingBox.left / imageWidth * graphicOverlay.width,
                                boundingBox.top / imageHeight * graphicOverlay.height,
                                boundingBox.right / imageWidth * graphicOverlay.width,
                                boundingBox.bottom / imageHeight * graphicOverlay.height)

                            // Barcode should overlap with center of screen
                            box.contains(0.5f*graphicOverlay.width, 0.5f*graphicOverlay.height)
                        }

                        if(barcodeResult == null) {
                            cameraReticleAnimator?.start()
                        } else {
                            viewModel.setBarcode(barcodeResult)
                        }
                        graphicOverlay.invalidate()
                    }
            }

            imageProxy.close()
        }
    }

    inner class ScanningReticle: GraphicOverlay.Graphic(graphicOverlay) {
        private val fractionOfScreen = 0.7f
        private val cornerRadius = context.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_corner_radius).toFloat()
        private val ripplePaint: Paint
        private val rippleSizeOffset: Int
        private val rippleStrokeWidth: Int
        private val rippleAlpha: Int

        private val boxPaint: Paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.barcode_reticle_stroke)
            style = Paint.Style.STROKE
            strokeWidth = context.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_stroke_width).toFloat()
        }

        private val scrimPaint: Paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.barcode_reticle_background)
        }

        private val eraserPaint: Paint = Paint().apply {
            strokeWidth = boxPaint.strokeWidth
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        init {
            val resources = overlay.resources
            ripplePaint = Paint()
            ripplePaint.style = Paint.Style.STROKE
            ripplePaint.color = ContextCompat.getColor(context, R.color.camera_reticle_ripple)
            rippleSizeOffset = resources.getDimensionPixelOffset(R.dimen.barcode_reticle_ripple_size_offset)
            rippleStrokeWidth = resources.getDimensionPixelOffset(R.dimen.barcode_reticle_ripple_stroke_width)
            rippleAlpha = ripplePaint.alpha
        }

        private val boxRect: RectF = overlay.let {
            val overlayWidth = it.width.toFloat()
            val overlayHeight = it.height.toFloat()
            val halfLength = 0.5f * min(overlayWidth * fractionOfScreen, overlayHeight * fractionOfScreen)
            val cx = 0.5f * overlayWidth
            val cy = 0.5f * overlayHeight
            RectF(cx - halfLength, cy - halfLength, cx + halfLength, cy + halfLength)
        }

        override fun draw(canvas: Canvas) {
            // Draws the dark background scrim and leaves the box area clear.
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), scrimPaint)

            // As the stroke is always centered, so erase twice with FILL and STROKE respectively to clear
            // all area that the box rect would occupy.
            eraserPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, eraserPaint)

            eraserPaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, eraserPaint)

            // Draws the box.
            canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, boxPaint)

            // Draws the ripple to simulate the breathing animation effect.
            var offset = 0f
            cameraReticleAnimator?.apply {
                ripplePaint.alpha = (rippleAlpha * rippleAlphaScale).toInt()
                ripplePaint.strokeWidth = rippleStrokeWidth * rippleStrokeWidthScale
                offset = rippleSizeOffset * rippleSizeScale
            }
            val rippleRect = RectF(
                boxRect.left - offset,
                boxRect.top - offset,
                boxRect.right + offset,
                boxRect.bottom + offset)
            canvas.drawRoundRect(rippleRect, cornerRadius, cornerRadius, ripplePaint)
        }
    }
}


class RationaleDialogFragment(val method: PaymentMethod): DialogFragment() {
    init {
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.qrcodescanner_permission_rationale_title))
            .setMessage(resources.getString(
                R.string.qrcodescanner_camera_permission_rationale_message,
                requireContext().getString(method.addressNameStringId)))
            .setCancelable(false)
            .setPositiveButton(resources.getString(R.string.proceed)) { _, _ ->
                setFragmentResult(resources.getString(R.string.rationale_key_camera), bundleOf("resultKey" to true))
            }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                setFragmentResult(resources.getString(R.string.rationale_key_camera), bundleOf("resultKey" to false))
            }
            .create()
    }
}