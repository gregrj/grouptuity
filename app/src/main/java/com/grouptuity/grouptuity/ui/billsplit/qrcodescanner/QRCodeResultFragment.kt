package com.grouptuity.grouptuity.ui.billsplit.qrcodescanner

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.grouptuity.grouptuity.databinding.QrCodeResultSheetBinding
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy


class QRCodeResultFragment: BottomSheetDialogFragment() {
    private var binding by setNullOnDestroy<QrCodeResultSheetBinding>()

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View {
        binding = QrCodeResultSheetBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getParcelable<QRCodeDisplayResults>(ARG_DISPLAY_RESULTS)?.apply {
            applyDisplayResults(this)
        }

        binding.cancelButton.setOnClickListener { dismiss() }
        binding.retryCancelButton.setOnClickListener { dismiss() }
        binding.abortLoadButton.setOnClickListener { dismiss() }

        binding.acceptButton.setOnClickListener { acceptCallbackFunction?.invoke() }
    }

    override fun onDismiss(dialogInterface: DialogInterface) {
        super.onDismiss(dialogInterface)
        val callback = dismissalCallbackFunction
        dismissalCallbackFunction = null
        callback?.invoke()
    }

    fun applyDisplayResults(displayResults: QRCodeDisplayResults) {
        binding.title.text = displayResults.title
        binding.message.text = displayResults.message
        binding.result.text = displayResults.result

        if (displayResults.status == QRCodeParser.Status.INVALID_URL) {
            binding.retryButton.setOnClickListener { dismiss() }
        } else {
            binding.retryButton.setOnClickListener { retryCallbackFunction?.invoke() }
        }

        when(displayResults.status) {
            QRCodeParser.Status.INVALID_URL -> {
                binding.progress.visibility = View.GONE
                binding.abortLoadButton.visibility = View.GONE
                binding.retryCancelButton.visibility = View.GONE
                binding.retryButton.visibility = View.VISIBLE
                binding.cancelButton.visibility = View.GONE
                binding.acceptButton.visibility = View.GONE
            }
            QRCodeParser.Status.REQUESTED -> {
                binding.progress.visibility = View.VISIBLE
                binding.abortLoadButton.visibility = View.VISIBLE
                binding.retryCancelButton.visibility = View.GONE
                binding.retryButton.visibility = View.GONE
                binding.cancelButton.visibility = View.GONE
                binding.acceptButton.visibility = View.GONE
            }
            QRCodeParser.Status.VALID_ADDRESS -> {
                binding.progress.visibility = View.GONE
                binding.abortLoadButton.visibility = View.GONE
                binding.retryCancelButton.visibility = View.GONE
                binding.retryButton.visibility = View.GONE
                binding.cancelButton.visibility = View.VISIBLE
                binding.acceptButton.visibility = View.VISIBLE
            }
            else -> {
                binding.progress.visibility = View.GONE
                binding.abortLoadButton.visibility = View.GONE
                binding.retryCancelButton.visibility = View.VISIBLE
                binding.retryButton.visibility = View.VISIBLE
                binding.cancelButton.visibility = View.GONE
                binding.acceptButton.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val TAG = "QRCodeResultFragment"
        private const val ARG_DISPLAY_RESULTS = "arg_display_results"
        private var acceptCallbackFunction: (() -> Unit)? = null
        private var retryCallbackFunction: (() -> Unit)? = null
        private var dismissalCallbackFunction: (() -> Unit)? = null

        fun show(fragmentManager: FragmentManager,
                 displayResults: QRCodeDisplayResults,
                 acceptCallback: () -> Unit,
                 retryCallback: () -> Unit,
                 dismissCallback: () -> Unit) {

            acceptCallbackFunction = acceptCallback
            retryCallbackFunction = retryCallback
            dismissalCallbackFunction = dismissCallback

            if (fragmentManager.findFragmentByTag(TAG) == null) {
                val resultFragment = QRCodeResultFragment()
                resultFragment.arguments = Bundle().apply {
                    putParcelable(ARG_DISPLAY_RESULTS, displayResults)
                }
                resultFragment.show(fragmentManager, TAG)
            } else {
                (fragmentManager.findFragmentByTag(TAG) as QRCodeResultFragment?)?.applyDisplayResults(displayResults)
            }
        }

        fun dismiss(fragmentManager: FragmentManager) {
            (fragmentManager.findFragmentByTag(TAG) as QRCodeResultFragment?)?.dismiss()
        }
    }
}
