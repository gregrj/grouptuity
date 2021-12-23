package com.grouptuity.grouptuity.ui.billsplit

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Payment
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.databinding.FragPaymentsBinding
import com.grouptuity.grouptuity.databinding.FragPaymentsListitemBinding
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.CANDIDATE_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.DEFAULT_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.INELIGIBLE_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.PROCESSING_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.SELECTING_METHOD_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.SHOWING_INSTRUCTIONS_STATE
import com.grouptuity.grouptuity.ui.billsplit.payments.*
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeScannerActivity
import com.grouptuity.grouptuity.ui.util.BaseUIFragment
import com.grouptuity.grouptuity.ui.util.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.util.views.focusAndShowKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// TODO block input from buttons under select payer instructions
// TODO styling for ineligible surrogate

class PaymentsFragment: BaseUIFragment<FragPaymentsBinding, PaymentsViewModel>() {
    companion object {
        @JvmStatic
        fun newInstance() = PaymentsFragment()
    }

    // Activity result launchers for QR code scanning, processing payments, and installing PtP apps
    private lateinit var qrCodeScannerLauncher: ActivityResultLauncher<Intent>
    private lateinit var emailLauncher: ActivityResultLauncher<Intent>
    private lateinit var venmoLauncher: ActivityResultLauncher<Intent>
    private lateinit var venmoInstallerLauncher: ActivityResultLauncher<Intent>
    private lateinit var cashAppLauncher: ActivityResultLauncher<Intent>
    private lateinit var cashAppInstallerLauncher: ActivityResultLauncher<Intent>
    private lateinit var algorandLauncher: ActivityResultLauncher<Intent>

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragPaymentsBinding.inflate(inflater, container, false)

    override fun createViewModel() = ViewModelProvider(requireActivity())[PaymentsViewModel::class.java]

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerAdapter = PaymentRecyclerViewAdapter()

        binding.list.apply {
            adapter = recyclerAdapter

            //TODO start transition getting called on all 4 tab fragments?
//            requireParentFragment().apply {
//                postponeEnterTransition()
//                viewTreeObserver.addOnPreDrawListener {
//                    startPostponedEnterTransition()
//                    true
//                }
//            }

            // Add a spacer to the last item in the list to ensure it is not cut off when the toolbar
            // and floating action button are visible
            addItemDecoration(
                RecyclerViewBottomOffset(
                    resources.getDimension(R.dimen.recyclerview_bottom_offset).toInt()
                )
            )

            itemAnimator = object : DefaultItemAnimator() {
                override fun canReuseUpdatedViewHolder(
                    viewHolder: RecyclerView.ViewHolder,
                    payloads: MutableList<Any>
                ): Boolean {
                    return true
                }
            }
        }

        viewModel.paymentsData.observe(viewLifecycleOwner) { paymentsData ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(paymentsData) }

            binding.noPaymentsHint.visibility =
                if (paymentsData.first.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.showSetAddressDialogEvent.observe(viewLifecycleOwner) {
            it.consume()?.apply {
                val (subject, diner, method) = this.value
                showSetAddressDialog(subject, diner, method)
            }
        }

        registerActivityLaunchers()
    }

    private fun registerActivityLaunchers() {
        qrCodeScannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val subject = result.data?.getIntExtra(getString(R.string.intent_key_qrcode_subject), -1)
                    val address = result.data?.getStringExtra(getString(R.string.intent_key_qrcode_payment_method_address))

                    if (subject != null && address != null) {
                        when (subject) {
                            PaymentsViewModel.SELECTING_PAYER_ALIAS -> { viewModel.setPayerAddress(address) }
                            PaymentsViewModel.SELECTING_PAYEE_ALIAS -> { viewModel.setPayeeAddress(address) }
                            PaymentsViewModel.SELECTING_SURROGATE_ALIAS -> { viewModel.setSurrogateAddress(address) }
                        }
                        // TODO
                    } else {
                        // TODO
                    }
                } else {
                    Log.e("false qr", result.toString())
                    // TODO
                }
            }

        emailLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Log.e("ok email", result.toString())
                } else {
                    Log.e("false email", result.toString())
                }
            }

        venmoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            viewModel.paymentInProcessing?.apply {
                val (valid, message) = parseVenmoResponse(requireContext(), result, this)

                if (valid) {
                    viewModel.commitPayment()
                }

                Snackbar.make(binding.list, message, Snackbar.LENGTH_LONG).show()
            }
        }

        venmoInstallerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.paymentInProcessing?.also { sendVenmoRequest(this, venmoLauncher, null, it) }
        }

        cashAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.e("ok cash app", result.toString())
            } else {
                Log.e("false cash app", result.toString())
            }
            viewModel.paymentInProcessing = null
        }

        cashAppInstallerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.paymentInProcessing?.also { sendVenmoRequest(this, cashAppLauncher, null, it) }
        }

        algorandLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.e("ok algo", result.toString())
            } else {
                Log.e("false algo", result.toString())
            }
            viewModel.paymentInProcessing = null
        }
    }

    fun onClickViewPaymentDetails(payment: Payment) {

    }

    fun onClickProcessPayment(payment: Payment) {
        when (payment.method) {
            PaymentMethod.PAYBACK_LATER -> { sendPaybackLaterEmail(requireActivity(), emailLauncher, payment) }
            PaymentMethod.VENMO -> {
                viewModel.paymentInProcessing = payment
                sendVenmoRequest(this, venmoLauncher, venmoInstallerLauncher, payment)
            }
            PaymentMethod.CASH_APP -> {
                viewModel.paymentInProcessing = payment
                sendCashAppRequest(this, cashAppLauncher, cashAppInstallerLauncher, payment)
            }
            PaymentMethod.ALGO -> {
                viewModel.paymentInProcessing = payment
                startAlgorandTransaction(this, algorandLauncher, payment)
            }
            else -> {

            }
        }
    }

    fun onClickActivePaymentMethodIcon(payment: Payment) {
        viewModel.setActivePayment(payment)
    }

    fun onClickCloseButton() {
        viewModel.setActivePayment(null)
    }

    fun onClickPaymentMethod(method: PaymentMethod) {
        viewModel.setPaymentMethod(method)
    }

    fun onClickShowPtPMethods() {
        showSelectPtPMethodDialog()
    }

    fun onClickSurrogate(diner: Diner) {
        viewModel.setSurrogate(diner)
    }

    private fun showSelectPtPMethodDialog() {
        val ptpPaymentMethods = listOf(PaymentMethod.ALGO, PaymentMethod.CASH_APP, PaymentMethod.VENMO)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(requireContext().getString(R.string.payments_ptp_selection_prompt))
            .setAdapter(object: ArrayAdapter<PaymentMethod>(
                requireContext(),
                android.R.layout.select_dialog_item,
                ptpPaymentMethods) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
                        super.getView(position, convertView, parent).also {
                            it.findViewById<TextView>(android.R.id.text1).apply {
                                compoundDrawablePadding = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_SP,
                                    14F,
                                    context.resources.displayMetrics
                                ).toInt()
                                setCompoundDrawablesWithIntrinsicBounds(ptpPaymentMethods[position].paymentIconId, 0, 0, 0)
                                text = requireContext().getString(ptpPaymentMethods[position].displayNameStringId)
                            }
                        }
            }) { _, position -> onClickPaymentMethod(ptpPaymentMethods[position]) }
            .show()
    }

    private fun showSetAddressDialog(subject: Int, diner: Diner, method: PaymentMethod) {

        val items = mutableListOf<Triple<String, Int, () -> Unit>>()

        if (method.addressCodeScannable && requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            items.add(
                Triple(
                    requireContext().getString(R.string.payments_scan_code),
                    R.drawable.ic_qr_code_scanner_36dp
                ) {
                    val intent = Intent(requireContext(), QRCodeScannerActivity::class.java)
                    intent.putExtra(getString(R.string.intent_key_qrcode_subject), subject)
                    intent.putExtra(getString(R.string.intent_key_qrcode_payment_method), method)
                    intent.putExtra(getString(R.string.intent_key_qrcode_diner_name), diner.name)
                    qrCodeScannerLauncher.launch(intent)
                })
        }

        if (method.addressCanBeEmail) {
            diner.emailAddresses.forEach { email ->
                items.add(Triple(email, R.drawable.ic_email_surface) {
                    when (subject) {
                        PaymentsViewModel.SELECTING_PAYER_ALIAS -> viewModel.setPayerAddress(
                            email
                        )
                        PaymentsViewModel.SELECTING_PAYEE_ALIAS -> viewModel.setPayeeAddress(
                            email
                        )
                        PaymentsViewModel.SELECTING_SURROGATE_ALIAS -> viewModel.setSurrogateAddress(
                            email
                        )
                    }
                })
            }
        }

        items.add(Triple(requireContext().getString(R.string.payments_enter_manually), R.drawable.ic_edit_24dp_surface) {
            val editTextDialog = MaterialAlertDialogBuilder(ContextThemeWrapper(requireContext(), R.style.AlertDialog))
                .setIcon(method.paymentIconId)
                .setTitle(
                    requireContext().getString(
                        method.addressSelectionStringId,
                        diner.name
                    )
                )
                .setView(R.layout.dialog_edit_text_ptp)
                .setNegativeButton(resources.getString(R.string.cancel)) { _, _ -> }
                .setPositiveButton(resources.getString(R.string.save)) { dialog, _ ->
                    (dialog as? AlertDialog)?.findViewById<EditText>(R.id.edit_text)?.text?.toString()
                        ?.apply {
                            when (subject) {
                                PaymentsViewModel.SELECTING_PAYER_ALIAS -> viewModel.setPayerAddress(
                                    this
                                )
                                PaymentsViewModel.SELECTING_PAYEE_ALIAS -> viewModel.setPayeeAddress(
                                    this
                                )
                                PaymentsViewModel.SELECTING_SURROGATE_ALIAS -> viewModel.setSurrogateAddress(
                                    this
                                )
                            }
                        }
                }.create()
            editTextDialog.show()

            // Positive button is only enabled when the EditText is not empty
            editTextDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            editTextDialog.findViewById<EditText>(R.id.edit_text)?.also { editText ->
                editText.requestFocus()
                editText.addTextChangedListener {
                    editTextDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                        !it.isNullOrBlank()
                }
                editText.focusAndShowKeyboard()
            }
        })

        val adapter = object : ArrayAdapter<Triple<String, Int, () -> Unit>>(
            requireContext(),
            android.R.layout.select_dialog_item,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
                super.getView(position, convertView, parent).also {
                    it.findViewById<TextView>(android.R.id.text1).apply {
                        compoundDrawablePadding = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_SP,
                            14F,
                            context.resources.displayMetrics
                        ).toInt()
                        setCompoundDrawablesWithIntrinsicBounds(items[position].second, 0, 0, 0)

                        if (items[position].second == R.drawable.ic_email_surface) {
                            setAutoSizeTextTypeUniformWithConfiguration(16, 18, 2, TypedValue.COMPLEX_UNIT_SP)
                        }

                        text = items[position].first
                    }
                }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setIcon(method.paymentIconId)
            .setTitle(requireContext().getString(method.addressSelectionStringId, diner.name))
            .setAdapter(adapter) { _, position -> items[position].third() }
            .show()
    }

    private inner class PaymentRecyclerViewAdapter:
        RecyclerView.Adapter<PaymentRecyclerViewAdapter.ViewHolder>() {
        var mPaymentDataSet = emptyList<PaymentData>()
        var mActivePaymentIdAndState = PaymentsViewModel.INITIAL_STABLE_ID_AND_STATE
        var animationStartFlag = false
        var listAnimationStartTime = 0L

        private val activePaymentMethodIconRippleColor = ColorStateList.valueOf(
            TypedValue().also {
                requireContext().theme.resolveAttribute(
                    R.attr.colorOnBackgroundLowEmphasis,
                    it,
                    true
                )
            }.data
        )
        private val activePaymentMethodIconStrokeColor = ColorStateList.valueOf(
            TypedValue().also {
                requireContext().theme.resolveAttribute(
                    R.attr.colorOnBackgroundLowEmphasis,
                    it,
                    true
                )
            }.data
        )
        private val iconColorOnBackgroundTint = ColorStateList.valueOf(
            TypedValue().also {
                requireContext().theme.resolveAttribute(
                    R.attr.colorOnBackgroundMediumEmphasis,
                    it,
                    true
                )
            }.data
        )

        // TODO move hardcoded values to xml and also use in layout
        private val surfaceBackground =
            TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSurface, it, true) }.data
        private val tertiaryBackground = TypedValue().also {
            requireContext().theme.resolveAttribute(
                R.attr.colorTertiary,
                it,
                true
            )
        }.data
        private val payInstructionsVerticalPx = (TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14F,
            requireContext().resources.displayMetrics
        ) +
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8F,
                    requireContext().resources.displayMetrics
                )).toInt()
        private val paymentMethodFlipButtonHorizontalPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56F,
            requireContext().resources.displayMetrics
        ).toInt()
        private val enlargedPayerTextSizeSpFrac = 1.22f
        private val animationDuration =
            requireContext().resources.getInteger(R.integer.card_flip_time_full).toLong()

        init {
            setHasStableIds(true)
        }

        inner class ViewHolder(val viewBinding: FragPaymentsListitemBinding) :
            RecyclerView.ViewHolder(viewBinding.root) {
            var viewHolderPaymentStableId: Long? = null
            var viewHolderState: Int = DEFAULT_STATE
            var viewHolderAnimationStartTime = 0L

            lateinit var backgroundAnimator: ObjectAnimator
            lateinit var fadeOutExpandingLayoutAnimator: ObjectAnimator
            lateinit var fadeOutFlipIconAnimator: ValueAnimator
            lateinit var fadeOutPaymentDetailsAnimator: ValueAnimator
            lateinit var fadeInSelectionInstructionAnimator: ObjectAnimator
            lateinit var fadeOutContactAnimator: ValueAnimator
            lateinit var enlargeContactAnimator: ValueAnimator

            fun setEnlargedPlaceholders() {
                viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = 1
                }
                viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    width = 1
                }
                viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    width = 1
                }

                viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    marginStart = 2 * viewBinding.payerReference.marginStart
                }
                viewBinding.payer.scaleX = enlargedPayerTextSizeSpFrac
                viewBinding.payer.scaleY = enlargedPayerTextSizeSpFrac
            }

            fun setShrunkenPlaceholders() {
                itemView.doOnPreDraw {
                    viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = viewBinding.amount.width
                    }
                }

                viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = payInstructionsVerticalPx
                }

                viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    width = viewBinding.amount.width
                }

                viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    width = paymentMethodFlipButtonHorizontalPx
                }

                viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    marginStart = viewBinding.payerReference.marginStart
                }
                viewBinding.payer.scaleX = 1f
                viewBinding.payer.scaleY = 1f
            }

            fun applyState(newState: Int) {
                backgroundAnimator.cancel()
                fadeOutExpandingLayoutAnimator.cancel()
                fadeOutFlipIconAnimator.cancel()
                fadeOutPaymentDetailsAnimator.cancel()
                fadeInSelectionInstructionAnimator.cancel()
                fadeOutContactAnimator.cancel()
                enlargeContactAnimator.cancel()

                when (newState) {
                    DEFAULT_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                        viewBinding.paymentMethodFlipButton.setDeselected()
                        viewBinding.paymentMethodFlipButton.alpha = 1f
                        viewBinding.payInstructions.alpha = 1f
                        viewBinding.amount.alpha = 1f
                        viewBinding.selectionInstruction.alpha = 0f
                        viewBinding.payerContactIcon.alpha = 1f
                        viewBinding.payer.alpha = 1f

                        setShrunkenPlaceholders()
                    }
                    SELECTING_METHOD_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        viewBinding.backgroundView.setBackgroundColor(tertiaryBackground)
                        viewBinding.paymentMethodsExpandingLayout.setExpanded()
                        viewBinding.paymentMethodsExpandingLayout.alpha = 1f
                        viewBinding.paymentMethodFlipButton.setSelected()
                        viewBinding.paymentMethodFlipButton.alpha = 1f
                        viewBinding.payInstructions.alpha = 0f
                        viewBinding.amount.alpha = 0f
                        viewBinding.selectionInstruction.alpha = 0f
                        viewBinding.payerContactIcon.alpha = 0f
                        viewBinding.payer.alpha = 0f

                        setShrunkenPlaceholders()
                    }
                    SHOWING_INSTRUCTIONS_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        viewBinding.backgroundView.setBackgroundColor(tertiaryBackground)
                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                        viewBinding.paymentMethodFlipButton.setSelected()
                        viewBinding.paymentMethodFlipButton.alpha = 1f
                        viewBinding.payInstructions.alpha = 0f
                        viewBinding.amount.alpha = 0f
                        viewBinding.selectionInstruction.alpha = 1f
                        viewBinding.payerContactIcon.alpha = 0f
                        viewBinding.payer.alpha = 0f

                        setShrunkenPlaceholders()
                    }
                    CANDIDATE_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.VISIBLE
                        viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                        viewBinding.paymentMethodFlipButton.setDeselected()
                        viewBinding.paymentMethodFlipButton.alpha = 0f
                        viewBinding.payInstructions.alpha = 0f
                        viewBinding.amount.alpha = 0f
                        viewBinding.selectionInstruction.alpha = 0f
                        viewBinding.payerContactIcon.alpha = 1f
                        viewBinding.payer.alpha = 1f

                        setEnlargedPlaceholders()
                    }
                    INELIGIBLE_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                        viewBinding.paymentMethodFlipButton.setDeselected()
                        viewBinding.paymentMethodFlipButton.alpha = 0f
                        viewBinding.payInstructions.alpha = 0f
                        viewBinding.amount.alpha = 0f
                        viewBinding.selectionInstruction.alpha = 0f
                        viewBinding.payerContactIcon.alpha = 1f
                        viewBinding.payer.alpha = 1f

                        setEnlargedPlaceholders()
                    }
                    PROCESSING_STATE -> {
                        viewBinding.paymentCardView.isClickable = true
                        viewBinding.surrogateSelection.visibility = View.GONE
                        viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                        viewBinding.paymentMethodFlipButton.setDeselected()
                        viewBinding.paymentMethodFlipButton.alpha = 1f
                        viewBinding.payInstructions.alpha = 1f
                        viewBinding.amount.alpha = 1f
                        viewBinding.selectionInstruction.alpha = 0f
                        viewBinding.payerContactIcon.alpha = 1f
                        viewBinding.payer.alpha = 1f

                        setShrunkenPlaceholders()
                    }
                }
            }

            fun animateStateChange(oldState: Int, newState: Int, initialProgress: Float) {
                backgroundAnimator.cancel()
                fadeOutExpandingLayoutAnimator.cancel()
                fadeOutFlipIconAnimator.cancel()
                fadeOutPaymentDetailsAnimator.cancel()
                fadeInSelectionInstructionAnimator.cancel()
                fadeOutContactAnimator.cancel()
                enlargeContactAnimator.cancel()

                when (newState) {
                    DEFAULT_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        when (oldState) {
                            SELECTING_METHOD_STATE -> {
                                backgroundAnimator.setCurrentFraction(1f - initialProgress)
                                backgroundAnimator.reverse()

                                viewBinding.paymentMethodsExpandingLayout.alpha = 1f
                                viewBinding.paymentMethodsExpandingLayout.collapse(initialProgress)

                                viewBinding.paymentMethodFlipButton.alpha = 1f
                                viewBinding.paymentMethodFlipButton.animateDeselection(
                                    initialProgress
                                )

                                fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutPaymentDetailsAnimator.reverse()

                                fadeOutContactAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutContactAnimator.reverse()

                                setShrunkenPlaceholders()

                                viewBinding.selectionInstruction.alpha = 0f
                            }
                            SHOWING_INSTRUCTIONS_STATE -> {
                                backgroundAnimator.setCurrentFraction(1f - initialProgress)
                                backgroundAnimator.reverse()

                                viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                                viewBinding.paymentMethodFlipButton.alpha = 1f
                                viewBinding.paymentMethodFlipButton.animateDeselection(
                                    initialProgress
                                )

                                fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutPaymentDetailsAnimator.reverse()

                                fadeOutContactAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutContactAnimator.reverse()

                                setShrunkenPlaceholders()

                                fadeInSelectionInstructionAnimator.setCurrentFraction(1f - initialProgress)
                                fadeInSelectionInstructionAnimator.reverse()
                            }
                            CANDIDATE_STATE -> {
                                viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                                viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                                viewBinding.paymentMethodFlipButton.setDeselected()
                                fadeOutFlipIconAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutFlipIconAnimator.reverse()

                                fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutPaymentDetailsAnimator.reverse()

                                viewBinding.payerContactIcon.alpha = 1f
                                viewBinding.payer.alpha = 1f

                                enlargeContactAnimator.setCurrentFraction(1f - initialProgress)
                                enlargeContactAnimator.reverse()

                                viewBinding.selectionInstruction.alpha = 0f
                            }
                            INELIGIBLE_STATE -> {
                                viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                                viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                                viewBinding.paymentMethodFlipButton.setDeselected()
                                fadeOutFlipIconAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutFlipIconAnimator.reverse()

                                fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                                fadeOutPaymentDetailsAnimator.reverse()

                                viewBinding.payerContactIcon.alpha = 1f
                                viewBinding.payer.alpha = 1f

                                enlargeContactAnimator.setCurrentFraction(1f - initialProgress)
                                enlargeContactAnimator.reverse()

                                viewBinding.selectionInstruction.alpha = 0f
                            }
                            PROCESSING_STATE -> {
                                applyState(DEFAULT_STATE)
                            }
                        }
                    }
                    SELECTING_METHOD_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        backgroundAnimator.setCurrentFraction(initialProgress)
                        backgroundAnimator.start()

                        viewBinding.paymentMethodsExpandingLayout.alpha = 1f
                        viewBinding.paymentMethodsExpandingLayout.expand(initialProgress)

                        viewBinding.paymentMethodFlipButton.alpha = 1f
                        viewBinding.paymentMethodFlipButton.animateSelection(initialProgress)

                        fadeOutPaymentDetailsAnimator.setCurrentFraction(initialProgress)
                        fadeOutPaymentDetailsAnimator.start()

                        fadeOutContactAnimator.setCurrentFraction(initialProgress)
                        fadeOutContactAnimator.start()

                        setShrunkenPlaceholders()

                        viewBinding.selectionInstruction.alpha = 0f
                    }
                    SHOWING_INSTRUCTIONS_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        viewBinding.backgroundView.setBackgroundColor(tertiaryBackground)

                        viewBinding.paymentMethodsExpandingLayout.setExpanded()
                        fadeOutExpandingLayoutAnimator.setCurrentFraction(initialProgress)
                        fadeOutExpandingLayoutAnimator.start()

                        viewBinding.paymentMethodFlipButton.alpha = 1f
                        viewBinding.paymentMethodFlipButton.setSelected()

                        viewBinding.payInstructions.alpha = 0f
                        viewBinding.amount.alpha = 0f

                        viewBinding.payerContactIcon.alpha = 0f
                        viewBinding.payer.alpha = 0f

                        setShrunkenPlaceholders()

                        fadeInSelectionInstructionAnimator.setCurrentFraction(initialProgress)
                        fadeInSelectionInstructionAnimator.start()
                    }
                    CANDIDATE_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.VISIBLE
                        viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                        viewBinding.paymentMethodFlipButton.setDeselected()
                        fadeOutFlipIconAnimator.setCurrentFraction(initialProgress)
                        fadeOutFlipIconAnimator.start()

                        fadeOutPaymentDetailsAnimator.setCurrentFraction(initialProgress)
                        fadeOutPaymentDetailsAnimator.start()

                        viewBinding.payerContactIcon.alpha = 1f
                        viewBinding.payer.alpha = 1f

                        enlargeContactAnimator.setCurrentFraction(initialProgress)
                        enlargeContactAnimator.start()

                        viewBinding.selectionInstruction.alpha = 0f
                    }
                    INELIGIBLE_STATE -> {
                        viewBinding.paymentCardView.isClickable = false
                        viewBinding.surrogateSelection.visibility = View.GONE
                        viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                        viewBinding.paymentMethodFlipButton.setDeselected()
                        fadeOutFlipIconAnimator.setCurrentFraction(initialProgress)
                        fadeOutFlipIconAnimator.start()

                        fadeOutPaymentDetailsAnimator.setCurrentFraction(initialProgress)
                        fadeOutPaymentDetailsAnimator.start()

                        viewBinding.payerContactIcon.alpha = 1f
                        viewBinding.payer.alpha = 1f

                        enlargeContactAnimator.setCurrentFraction(initialProgress)
                        enlargeContactAnimator.start()

                        viewBinding.selectionInstruction.alpha = 0f
                    }
                    PROCESSING_STATE -> {
                        applyState(PROCESSING_STATE)
                    }
                }
            }
        }

        override fun getItemCount() = mPaymentDataSet.size

        override fun getItemId(position: Int) = mPaymentDataSet[position].stableId ?: NO_ID

        @SuppressLint("Recycle")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val viewHolder = ViewHolder(
                FragPaymentsListitemBinding.inflate(
                    LayoutInflater.from(context),
                    parent,
                    false
                )
            )

            viewHolder.viewBinding.apply {
                paymentCardView.setOnClickListener {
                    if (viewHolder.viewHolderState == PROCESSING_STATE) {
                        onClickProcessPayment((viewHolder.itemView.tag as PaymentData).payment)
                    } else if(viewHolder.viewHolderState == DEFAULT_STATE) {
                        onClickViewPaymentDetails((viewHolder.itemView.tag as PaymentData).payment)
                    }
                }

                surrogateSelection.setOnClickListener {
                    if (viewHolder.viewHolderState == CANDIDATE_STATE) {
                        onClickSurrogate((viewHolder.itemView.tag as PaymentData).payment.payer)
                    }
                }

                activePaymentMethodIcon.setOnClickListener {
                    if (viewHolder.viewHolderState == DEFAULT_STATE) {
                        onClickActivePaymentMethodIcon((viewHolder.itemView.tag as PaymentData).payment)
                    }
                }

                closePaymentMethodListButton.setOnClickListener {
                    if (viewHolder.viewHolderState == SELECTING_METHOD_STATE ||
                        viewHolder.viewHolderState == SHOWING_INSTRUCTIONS_STATE
                    ) {
                        onClickCloseButton()
                    }
                }

                paymentMethodMorePtpButton.setOnClickListener { onClickShowPtPMethods() }
                paymentMethodCashButton.setOnClickListener { onClickPaymentMethod(PaymentMethod.CASH) }
                paymentMethodCreditCardButton.setOnClickListener { onClickPaymentMethod(PaymentMethod.CREDIT_CARD_INDIVIDUAL) }
                paymentMethodCreditCardSplitButton.setOnClickListener { onClickPaymentMethod(PaymentMethod.CREDIT_CARD_SPLIT) }
                paymentMethodPaybackLaterButton.setOnClickListener { onClickPaymentMethod(PaymentMethod.PAYBACK_LATER) }
                paymentMethodVenmoButton.setOnClickListener { onClickPaymentMethod(PaymentMethod.VENMO) }
                paymentMethodCashAppButton.setOnClickListener { onClickPaymentMethod(PaymentMethod.CASH_APP) }
                paymentMethodAlgorandButton.setOnClickListener { onClickPaymentMethod(PaymentMethod.ALGO) }

                viewHolder.backgroundAnimator = ObjectAnimator.ofArgb(
                    backgroundView,
                    "backgroundColor",
                    surfaceBackground,
                    tertiaryBackground
                ).also { it.duration = animationDuration }

                viewHolder.fadeOutExpandingLayoutAnimator = ObjectAnimator.ofFloat(
                    paymentMethodsExpandingLayout,
                    "alpha",
                    1f,
                    0f
                ).also { it.duration = animationDuration }

                viewHolder.fadeOutFlipIconAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                    it.duration = animationDuration
                    it.addUpdateListener { animator ->
                        viewHolder.viewBinding.paymentMethodFlipButton.alpha =
                            (1f - 5f * animator.animatedFraction).coerceIn(0f, 1f)
                    }
                }

                viewHolder.fadeOutPaymentDetailsAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                    it.duration = animationDuration
                    it.addUpdateListener { animator ->
                        val progress = (1f - 5f * animator.animatedFraction).coerceIn(0f, 1f)
                        viewHolder.viewBinding.payInstructions.alpha = progress
                        viewHolder.viewBinding.amount.alpha = progress
                    }
                }

                viewHolder.fadeInSelectionInstructionAnimator = ObjectAnimator.ofFloat(
                    selectionInstruction,
                    "alpha",
                    0f,
                    1f
                ).also { it.duration = animationDuration }

                viewHolder.fadeOutContactAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                    it.duration = animationDuration
                    it.addUpdateListener { animator ->
                        val progress = (1f - 5f * animator.animatedFraction).coerceIn(0f, 1f)
                        viewHolder.viewBinding.payerContactIcon.alpha = progress
                        viewHolder.viewBinding.payer.alpha = progress
                    }
                }

                viewHolder.enlargeContactAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                    it.duration = animationDuration
                    it.addUpdateListener { animator ->
                        val progress = animator.animatedValue as Float

                        viewHolder.viewBinding.apply {
                            paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                width =
                                    ((paymentMethodFlipButton.width + paymentMethodFlipButton.marginStart)
                                            * (progress)).toInt().coerceAtLeast(1)
                            }

                            payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                height =
                                    ((payInstructions.height + payInstructions.marginBottom) * (progress)).toInt()
                                        .coerceAtLeast(1)
                            }

                            amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                width = (amount.width * (progress)).toInt().coerceAtLeast(1)
                            }

                            payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                marginStart = (payerReference.marginStart * (2f - progress)).toInt()
                            }

                            payer.scaleX = (1f - progress) * (enlargedPayerTextSizeSpFrac - 1f) + 1f
                            payer.scaleY = (1f - progress) * (enlargedPayerTextSizeSpFrac - 1f) + 1f
                        }
                    }
                }
            }

            viewHolder.setShrunkenPlaceholders()

            return viewHolder
        }

        private fun hidePtPButtons(viewBinding: FragPaymentsListitemBinding) {
            viewBinding.paymentMethodVenmoButton.visibility = View.GONE
            viewBinding.venmoSpace.visibility = View.GONE
            viewBinding.paymentMethodVenmoButton.visibility = View.GONE
            viewBinding.cashAppSpace.visibility = View.GONE
            viewBinding.paymentMethodCashAppButton.visibility = View.GONE
            viewBinding.algorandSpace.visibility = View.GONE
            viewBinding.paymentMethodAlgorandButton.visibility = View.GONE
        }

        private fun showPtPButtons(viewBinding: FragPaymentsListitemBinding) {
            viewBinding.paymentMethodVenmoButton.visibility = View.VISIBLE
            viewBinding.venmoSpace.visibility = View.VISIBLE
            viewBinding.paymentMethodVenmoButton.visibility = View.VISIBLE
            viewBinding.cashAppSpace.visibility = View.VISIBLE
            viewBinding.paymentMethodCashAppButton.visibility = View.VISIBLE
            viewBinding.algorandSpace.visibility = View.VISIBLE
            viewBinding.paymentMethodAlgorandButton.visibility = View.VISIBLE
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.apply {
                val newPaymentData = mPaymentDataSet[position]
                itemView.tag = newPaymentData

                viewBinding.paymentMethodsExpandingLayout.setExpandedSize(
                    viewBinding.paymentCardView.width
                            - viewBinding.paymentMethodFlipButton.width
                            - viewBinding.amount.marginEnd
                            - viewBinding.payInstructions.marginStart
                )

                viewBinding.payer.text = newPaymentData.payerString
                viewBinding.payInstructions.text = newPaymentData.payInstructionsString
                viewBinding.amount.text = newPaymentData.amountString
                viewBinding.amountPlaceholder.text = newPaymentData.amountString

                viewBinding.activePaymentMethodIcon.icon =
                    ContextCompat.getDrawable(requireContext(), newPaymentData.payment.method.paymentIconId)
                viewBinding.activePaymentMethodIcon.iconTint =
                    if (newPaymentData.payment.method.isIconColorless) iconColorOnBackgroundTint else null

                viewBinding.payerContactIcon.setContact(newPaymentData.iconContact, false)

                if (newPaymentData.isPaymentIconClickable) {
                    viewBinding.activePaymentMethodIcon.isClickable = true
                    viewBinding.activePaymentMethodIcon.rippleColor =
                        activePaymentMethodIconRippleColor
                    viewBinding.activePaymentMethodIcon.strokeColor =
                        activePaymentMethodIconStrokeColor
                    viewBinding.closePaymentMethodListButton.isClickable = true

                    if (newPaymentData.payment.payee.isRestaurant()) {
                        viewBinding.creditCardSpace.visibility = View.VISIBLE
                        viewBinding.paymentMethodCreditCardButton.visibility = View.VISIBLE
                        viewBinding.creditCardSplitSpace.visibility = View.VISIBLE
                        viewBinding.paymentMethodCreditCardSplitButton.visibility = View.VISIBLE

                        hidePtPButtons(viewBinding)

                        if (newPaymentData.allowSurrogatePaymentMethods) {
                            viewBinding.paybackLaterSpace.visibility = View.VISIBLE
                            viewBinding.paymentMethodPaybackLaterButton.visibility = View.VISIBLE
                            viewBinding.morePtpSpace.visibility = View.VISIBLE
                            viewBinding.paymentMethodMorePtpButton.visibility = View.VISIBLE
                        } else {
                            viewBinding.paybackLaterSpace.visibility = View.GONE
                            viewBinding.paymentMethodPaybackLaterButton.visibility = View.GONE
                            viewBinding.morePtpSpace.visibility = View.GONE
                            viewBinding.paymentMethodMorePtpButton.visibility = View.GONE
                        }
                    } else {
                        viewBinding.creditCardSpace.visibility = View.GONE
                        viewBinding.paymentMethodCreditCardButton.visibility = View.GONE
                        viewBinding.creditCardSplitSpace.visibility = View.GONE
                        viewBinding.paymentMethodCreditCardSplitButton.visibility = View.GONE
                        viewBinding.morePtpSpace.visibility = View.GONE
                        viewBinding.paymentMethodMorePtpButton.visibility = View.GONE

                        showPtPButtons(viewBinding)
                    }
                } else {
                    viewBinding.activePaymentMethodIcon.isClickable = false
                    viewBinding.activePaymentMethodIcon.rippleColor = null
                    viewBinding.activePaymentMethodIcon.strokeColor =
                        ColorStateList.valueOf(Color.TRANSPARENT)
                    viewBinding.closePaymentMethodListButton.isClickable = false
                }

                val timeNow = System.currentTimeMillis()
                if (animationStartFlag) {
                    // Record start time for new list animation
                    animationStartFlag = false
                    listAnimationStartTime = timeNow
                }

                if (viewHolderPaymentStableId == newPaymentData.stableId) {
                    if (viewHolderState == newPaymentData.displayState) {
                        // ViewHolder is bound to the same payment and state is unchanged
                    } else {
                        // ViewHolder is bound to the same payment, but state has changed
                        val viewHolderElapsedTime = timeNow - viewHolderAnimationStartTime

                        viewHolderAnimationStartTime =
                            if (viewHolderElapsedTime >= animationDuration) {
                                listAnimationStartTime
                            } else {
                                listAnimationStartTime - animationDuration + viewHolderElapsedTime
                            }

                        val animationProgress = ((timeNow - viewHolderAnimationStartTime)
                            .toFloat() / animationDuration).coerceIn(0f, 1f)

                        if (animationProgress == 1f) {
                            applyState(newPaymentData.displayState)
                        } else {
                            animateStateChange(
                                viewHolderState,
                                newPaymentData.displayState,
                                animationProgress
                            )
                        }
                        viewHolderState = newPaymentData.displayState
                    }
                } else {
                    /* ViewHolder is showing a different payment */
                    viewHolderPaymentStableId = newPaymentData.stableId

                    if (newPaymentData.displayState == DEFAULT_STATE) {
                        viewHolderAnimationStartTime = 0L
                        applyState(DEFAULT_STATE)
                    } else {
                        val animationProgress =
                            ((System.currentTimeMillis() - listAnimationStartTime)
                                .toFloat() / animationDuration).coerceIn(0f, 1f)
                        viewHolderAnimationStartTime = listAnimationStartTime

                        if (animationProgress == 1f) {
                            applyState(newPaymentData.displayState)
                        } else {
                            when (newPaymentData.displayState) {
                                SELECTING_METHOD_STATE -> {
                                    animateStateChange(
                                        DEFAULT_STATE,
                                        SELECTING_METHOD_STATE,
                                        animationProgress
                                    )
                                }
                                SHOWING_INSTRUCTIONS_STATE -> {
                                    animateStateChange(
                                        SELECTING_METHOD_STATE,
                                        SHOWING_INSTRUCTIONS_STATE,
                                        animationProgress
                                    )
                                }
                                CANDIDATE_STATE -> {
                                    animateStateChange(
                                        DEFAULT_STATE,
                                        CANDIDATE_STATE,
                                        animationProgress
                                    )
                                }
                                INELIGIBLE_STATE -> {
                                    animateStateChange(
                                        DEFAULT_STATE,
                                        INELIGIBLE_STATE,
                                        animationProgress
                                    )
                                }
                                PROCESSING_STATE -> {
                                    animateStateChange(
                                        DEFAULT_STATE,
                                        PROCESSING_STATE,
                                        animationProgress
                                    )
                                }
                            }
                        }
                    }

                    viewHolderState = newPaymentData.displayState
                }
            }
        }

        suspend fun updateDataSet(paymentsData: Pair<List<PaymentData>, Pair<Long?, Int>>) {
            val adapter = this

            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = mPaymentDataSet.size

                override fun getNewListSize() = paymentsData.first.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val oldPaymentId = mPaymentDataSet[oldPosition].stableId
                    return oldPaymentId != null && oldPaymentId == paymentsData.first[newPosition].stableId
                }

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val oldPaymentData = mPaymentDataSet[oldPosition]
                    val newPaymentData = paymentsData.first[newPosition]

                    return newPaymentData.displayState == oldPaymentData.displayState &&
                            newPaymentData.payment.method == oldPaymentData.payment.method &&
                            newPaymentData.payerString == oldPaymentData.payerString &&
                            newPaymentData.payInstructionsString == oldPaymentData.payInstructionsString &&
                            newPaymentData.amountString == oldPaymentData.amountString &&
                            newPaymentData.iconContact == oldPaymentData.iconContact &&
                            newPaymentData.isPaymentIconClickable == oldPaymentData.isPaymentIconClickable &&
                            newPaymentData.allowSurrogatePaymentMethods == oldPaymentData.allowSurrogatePaymentMethods
                }
            })

            withContext(Dispatchers.Main) {
                adapter.notifyItemChanged(mPaymentDataSet.size - 1) // clears BottomOffset from old last item

                mPaymentDataSet = paymentsData.first
                if (paymentsData.second != mActivePaymentIdAndState) {
                    // Active payment and/or state change that will trigger a new animation
                    mActivePaymentIdAndState = paymentsData.second
                    animationStartFlag = true
                }
                diffResult.dispatchUpdatesTo(adapter)
            }
        }
    }
}