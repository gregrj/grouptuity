package com.grouptuity.grouptuity.ui.billsplit.contactentry

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.transition.Transition
import androidx.transition.TransitionValues
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.Diner
import com.grouptuity.grouptuity.data.entities.PaymentMethod
import com.grouptuity.grouptuity.databinding.FragContactEntryBinding
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeScannerActivity
import com.grouptuity.grouptuity.ui.util.UIFragment
import com.grouptuity.grouptuity.ui.util.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.util.transitions.progressWindow
import com.grouptuity.grouptuity.ui.util.views.clearFocusAndHideKeyboard
import com.grouptuity.grouptuity.ui.util.views.focusAndShowKeyboard


// TODO voice name entry

class ContactEntryFragment: UIFragment<FragContactEntryBinding, ContactEntryViewModel, Unit?, Diner?>() {
    private lateinit var qrCodeScannerLauncher: ActivityResultLauncher<Intent>

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragContactEntryBinding.inflate(inflater, container, false)

    override fun createViewModel() = ViewModelProvider(requireActivity())[ContactEntryViewModel::class.java]

    override fun getInitialInput(): Unit? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()

        setupEnterTransition()

        qrCodeScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val paymentMethod = result.data?.extras?.get(requireContext().getString(R.string.intent_key_qrcode_payment_method))
                val address = result.data?.extras?.get(requireContext().getString(R.string.intent_key_qrcode_payment_method_address)) as String

                when (paymentMethod) {
                    PaymentMethod.PAYBACK_LATER -> { binding.emailInput.setText(address) }
                    PaymentMethod.VENMO -> { binding.venmoInput.setText(address) }
                    PaymentMethod.CASH_APP -> { binding.cashAppInput.setText(address) }
                    PaymentMethod.ALGO -> { binding.algorandInput.setText(address) }
                    else -> { }
                }
            } else {
                Log.e("false", result.toString())
                // TODO show snackbar
            }
        }

        setupFocusListeners()

        setupTextChangedListeners()

        binding.fab.setOnClickListener {
            // Collect entries from all input fields and create the new diner
            viewModel.addContactToBill(
                binding.nameInput.text.toString(),
                binding.emailInput.text.toString(),
                binding.venmoInput.text.toString(),
                binding.cashAppInput.text.toString(),
                binding.algorandInput.text.toString())
        }

        viewModel.enableCreateContact.observe(viewLifecycleOwner) {
            if (it) {
                binding.fab.show()

                // Enable entry in other fields since a valid contact name has been provided
                enableField(binding.emailIcon, binding.emailInputLayout, binding.emailInput)
                enableFieldWithQRScan(binding.venmoIcon, binding.venmoInputLayout, binding.venmoInput, PaymentMethod.VENMO)
                enableFieldWithQRScan(binding.cashAppIcon, binding.cashAppInputLayout, binding.cashAppInput, PaymentMethod.CASH_APP)
                enableFieldWithQRScan(binding.algorandIcon, binding.algorandInputLayout, binding.algorandInput, PaymentMethod.ALGO)
            } else {
                binding.fab.hide()

                // Disable entry in other fields until a valid contact name is provided
                disableField(binding.emailIcon, binding.emailInputLayout, binding.emailInput)
                disableField(binding.venmoIcon, binding.venmoInputLayout, binding.venmoInput)
                disableField(binding.cashAppIcon, binding.cashAppInputLayout, binding.cashAppInput)
                disableField(binding.algorandIcon, binding.algorandInputLayout, binding.algorandInput)
            }
        }

        viewModel.toolbarButtonState.observe(viewLifecycleOwner) {
            when (it) {
                ContactEntryViewModel.FINISH -> {
                    binding.toolbar.menu.setGroupVisible(R.id.group_cancel_entry, false)
                    binding.toolbar.menu.setGroupVisible(R.id.group_finish_entry, true)
                }
                ContactEntryViewModel.CANCEL -> {
                    binding.toolbar.menu.setGroupVisible(R.id.group_cancel_entry, true)
                    binding.toolbar.menu.setGroupVisible(R.id.group_finish_entry, false)
                }
                else -> {
                    binding.toolbar.menu.setGroupVisible(R.id.group_cancel_entry, false)
                    binding.toolbar.menu.setGroupVisible(R.id.group_finish_entry, false)
                }
            }
        }

        binding.nameInput.focusAndShowKeyboard()
    }

    override fun onResume() {
        super.onResume()
        binding.newContactButton.visibility = View.GONE
    }

    override fun onFinish(newDiner: Diner?) {
        if (newDiner == null) {
            setupExitTransition()

            // Close fragment using default onBackPressed behavior
            requireActivity().onBackPressed()
        } else {
            // Exit transition is needed to prevent next fragment from appearing immediately
            exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireView())
            }

            binding.container.transitionName = "new_diner" + newDiner.id

            // Close fragment by popping up to the BillSplitFragment
            findNavController().navigate(
                ContactEntryFragmentDirections.contactEntryToBillSplit(newDinerId = newDiner.id),
                FragmentNavigatorExtras(
                    binding.container to binding.container.transitionName
                )
            )
        }
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.toolbar_contactentry)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { viewModel.handleOnBackPressed() }
        binding.toolbar.title = getString(R.string.contact_entry_toolbar_title_new_diner)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.finish_field_entry -> {
                    binding.nameInput.clearFocusAndHideKeyboard()
                    binding.emailInput.clearFocusAndHideKeyboard()
                    binding.venmoInput.clearFocusAndHideKeyboard()
                    binding.cashAppInput.clearFocusAndHideKeyboard()
                    binding.algorandInput.clearFocusAndHideKeyboard()
                    true
                }
                R.id.cancel_field_entry -> {
                    binding.nameInput.clearFocusAndHideKeyboard()
                    binding.emailInput.clearFocusAndHideKeyboard()
                    binding.venmoInput.clearFocusAndHideKeyboard()
                    binding.cashAppInput.clearFocusAndHideKeyboard()
                    binding.algorandInput.clearFocusAndHideKeyboard()
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    private fun setupFocusListeners() {
        binding.nameInput.setOnFocusChangeListener { _, focused ->
            if (focused) {
                viewModel.activateField(ContactEntryViewModel.NAME)
                if (binding.nameInput.text.isNotEmpty()) {
                    setEndIconToClearText(binding.nameInputLayout, binding.nameInput)
                }
            }
            else {
                viewModel.deactivateField(ContactEntryViewModel.NAME)
                removeEndIcon(binding.nameInputLayout)
            }
        }

        binding.emailInput.setOnFocusChangeListener { _, focused ->
            if (focused) {
                viewModel.activateField(ContactEntryViewModel.EMAIL)
                if (binding.emailInput.text.isNotEmpty()) {
                    setEndIconToClearText(binding.emailInputLayout, binding.emailInput)
                }
            }
            else {
                viewModel.deactivateField(ContactEntryViewModel.EMAIL)
                removeEndIcon(binding.emailInputLayout)
            }
        }

        binding.venmoInput.setOnFocusChangeListener { _, focused ->
            if (focused) {
                viewModel.activateField(ContactEntryViewModel.VENMO)
                if (binding.venmoInput.text.isNotEmpty()) {
                    setEndIconToClearText(binding.venmoInputLayout, binding.venmoInput)
                }
            }
            else {
                viewModel.deactivateField(ContactEntryViewModel.VENMO)
                if (binding.venmoInput.text.isEmpty()) {
                    setEndIconToScanQRCode(binding.venmoInputLayout, PaymentMethod.VENMO)
                } else {
                    removeEndIcon(binding.venmoInputLayout)
                }
            }
        }

        binding.cashAppInput.setOnFocusChangeListener { _, focused ->
            if (focused) {
                viewModel.activateField(ContactEntryViewModel.CASH_APP)
                if (binding.cashAppInput.text.isNotEmpty()) {
                    setEndIconToClearText(binding.cashAppInputLayout, binding.cashAppInput)
                }
            }
            else {
                viewModel.deactivateField(ContactEntryViewModel.CASH_APP)
                if (binding.cashAppInput.text.isEmpty()) {
                    setEndIconToScanQRCode(binding.cashAppInputLayout, PaymentMethod.CASH_APP)
                } else {
                    removeEndIcon(binding.cashAppInputLayout)
                }
            }
        }

        binding.algorandInput.setOnFocusChangeListener { _, focused ->
            if (focused) {
                viewModel.activateField(ContactEntryViewModel.ALGORAND)
                if (binding.algorandInput.text.isNotEmpty()) {
                    setEndIconToClearText(binding.algorandInputLayout, binding.algorandInput)
                }
            }
            else {
                viewModel.deactivateField(ContactEntryViewModel.ALGORAND)
                if (binding.algorandInput.text.isEmpty()) {
                    setEndIconToScanQRCode(binding.algorandInputLayout, PaymentMethod.ALGO)
                } else {
                    removeEndIcon(binding.algorandInputLayout)
                }
            }
        }
    }

    private fun setupTextChangedListeners() {
        binding.nameInput.addTextChangedListener { editable ->
            viewModel.setNameInput(editable.toString())

            if (editable?.isNotEmpty() == true) {
                setEndIconToClearText(binding.nameInputLayout, binding.nameInput)
            } else {
                removeEndIcon(binding.nameInputLayout)
            }
        }

        binding.emailInput.addTextChangedListener { editable ->
            viewModel.setEmailInput(editable.toString())

            if (editable?.isNotEmpty() == true) {
                setEndIconToClearText(binding.emailInputLayout, binding.emailInput)
            } else {
                removeEndIcon(binding.emailInputLayout)
            }
        }

        binding.venmoInput.addTextChangedListener { editable ->
            viewModel.setVenmoInput(editable.toString())

            if (editable?.isNotEmpty() == true) {
                setEndIconToClearText(binding.venmoInputLayout, binding.venmoInput)
            } else {
                setEndIconToScanQRCode(binding.venmoInputLayout, PaymentMethod.VENMO)
            }
        }

        binding.cashAppInput.addTextChangedListener { editable ->
            viewModel.setCashAppInput(editable.toString())

            if (editable?.isNotEmpty() == true) {
                setEndIconToClearText(binding.cashAppInputLayout, binding.cashAppInput)
            } else {
                setEndIconToScanQRCode(binding.cashAppInputLayout, PaymentMethod.CASH_APP)
            }
        }

        binding.algorandInput.addTextChangedListener { editable ->
            viewModel.setAlgorandInput(editable.toString())

            if (editable?.isNotEmpty() == true) {
                setEndIconToClearText(binding.algorandInputLayout, binding.algorandInput)
            } else {
                setEndIconToScanQRCode(binding.algorandInputLayout, PaymentMethod.ALGO)
            }
        }
    }

    private fun removeEndIcon(textInputLayout: TextInputLayout) {
        // Ripple from clearing text gets stuck unless the icon removal is delayed
        Handler(Looper.getMainLooper()).postDelayed({
            textInputLayout.endIconDrawable = null
            textInputLayout.setEndIconOnClickListener(null)
        }, 100)
    }

    private fun setEndIconToClearText(textInputLayout: TextInputLayout, textInput: AppCompatAutoCompleteTextView) {
        textInputLayout.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_cancel)
        textInputLayout.endIconContentDescription = requireContext().getString(R.string.dinerdetails_biographics_clear_address)

        textInputLayout.setEndIconOnClickListener {
            textInput.text = null
        }
    }

    private fun setEndIconToScanQRCode(textInputLayout: TextInputLayout, method: PaymentMethod) {
        textInputLayout.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_qr_code_24dp)
        textInputLayout.endIconContentDescription = requireContext().getString(R.string.dinerdetails_biographics_scan_qr_code)

        textInputLayout.setEndIconOnClickListener {
            val intent = Intent(requireContext(), QRCodeScannerActivity::class.java)
            intent.putExtra(getString(R.string.intent_key_qrcode_payment_method), method)
            intent.putExtra(getString(R.string.intent_key_qrcode_diner_name), viewModel.name.value)
            qrCodeScannerLauncher.launch(intent)
        }
    }

    private fun enableField(icon: ImageView, textInputLayout: TextInputLayout, textInput: AppCompatAutoCompleteTextView) {
        icon.alpha = 1.0f

        textInput.setTextColor(TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnSurface, it, true) }.data)

        textInputLayout.isEnabled = true
    }

    private fun enableFieldWithQRScan(icon: ImageView, textInputLayout: TextInputLayout, textInput: AppCompatAutoCompleteTextView, method: PaymentMethod) {
        icon.alpha = 1.0f

        textInput.setTextColor(TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnSurface, it, true) }.data)

        textInputLayout.isEnabled = true
        if (textInput.text.isNullOrEmpty()) {
            setEndIconToScanQRCode(textInputLayout, method)
        }
    }

    private fun disableField(icon: ImageView, textInputLayout: TextInputLayout, textInput: AppCompatAutoCompleteTextView) {
        icon.alpha = 0.25f

        textInput.setTextColor(TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnSurfaceLowEmphasis, it, true) }.data)

        textInputLayout.isEnabled = false
        textInputLayout.endIconDrawable = null
        textInputLayout.setEndIconOnClickListener(null)
    }

    private fun setupEnterTransition() {
        postponeEnterTransition()
        requireView().doOnPreDraw { startPostponedEnterTransition() }

        binding.newContactButton.visibility = View.VISIBLE
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
        binding.nestedScrollView.isNestedScrollingEnabled = false

        binding.container.transitionName = "new_contact_container_transition_name"

        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:button_corner_radius"

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, true)
            .addElement(binding.newContactButton.transitionName,
                object: CardViewExpandTransition.Element {
                    override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                        val fabView = (transitionValues.view as ExtendedFloatingActionButton)
                        transitionValues.values[propCornerRadius] =
                            fabView.shapeAppearanceModel.topLeftCornerSize.getCornerSize(RectF(
                                fabView.left.toFloat(),
                                fabView.top.toFloat(),
                                fabView.right.toFloat(),
                                fabView.bottom.toFloat()))
                    }

                    override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                        transitionValues.values[propCornerRadius] = 0f
                    }

                    override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                        val surfaceColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSurface, it, true) }.data

                        val animator = ValueAnimator.ofFloat(0f, 1f)
                        animator.doOnStart {
                            // CardView needs to be transparent initially so edges around the FAB
                            // are not visible
                            binding.container.setCardBackgroundColor(Color.TRANSPARENT)

                            // Content will be hidden initially and fade in later
                            binding.coordinatorLayout.alpha = 0f
                        }
                        animator.addUpdateListener {
                            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                            // Adjust shape of button to match background container during first 20%
                            val zeroTo20Progress = (5f*progress).coerceIn(0f, 1f)
                            val cornerRadius = (startValues.values[propCornerRadius] as Float)*(1f - zeroTo20Progress)
                            binding.newContactButton.shapeAppearanceModel =
                                binding.newContactButton.shapeAppearanceModel
                                    .toBuilder()
                                    .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                                    .build()

                            // Restore card view background color before fading out the ExtendedFAB.
                            // This presents the covered fragment from bleeding through.
                            if (progress >= 0.2f) {
                                binding.container.setCardBackgroundColor(surfaceColor)
                            }

                            // Fade out ExtendedFAB and fade in content for
                            val twentyTo80Progress = (1.666667f*progress - 0.33333f).coerceIn(0f, 1f)
                            binding.newContactButton.alpha = 1f - twentyTo80Progress
                            binding.coordinatorLayout.alpha = twentyTo80Progress
                        }
                        animator.doOnEnd {
                            binding.newContactButton.visibility = View.GONE
                        }

                        return animator
                    }
                }
            )
            .addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })
    }

    private fun setupExitTransition() {
        binding.newContactButton.visibility = View.VISIBLE
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
        binding.nestedScrollView.isNestedScrollingEnabled = false

        binding.container.transitionName = "new_contact_container_transition_name"

        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:button_corner_radius"

        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, false)
            .addElement(binding.newContactButton.transitionName,
                object: CardViewExpandTransition.Element {
                    override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                        transitionValues.values[propCornerRadius] = 0f
                    }

                    override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                        val fabView = (transitionValues.view as ExtendedFloatingActionButton)
                        transitionValues.values[propCornerRadius] =
                            fabView.shapeAppearanceModel.topLeftCornerSize.getCornerSize(RectF(
                                fabView.left.toFloat(),
                                fabView.top.toFloat(),
                                fabView.right.toFloat(),
                                fabView.bottom.toFloat()))
                    }

                    override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                        val animator = ValueAnimator.ofFloat(0f, 1f)
                        animator.doOnStart {
                            // Ensure ExtendedFAB is being rendered, but initially starts as
                            // transparent
                            binding.newContactButton.alpha = 0f
                            binding.newContactButton.visibility = View.VISIBLE

                            // fade_view in AddressBook is visible by default so visibility needs
                            // to be gone at the start of the transition
                            sceneRoot.findViewById<View>(R.id.fade_view)?.apply {
                                visibility = View.GONE
                            }
                        }
                        animator.addUpdateListener {
                            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                            // Fade out content and fade in ExtendedFAB
                            val twentyTo80Progress = progressWindow(progress, 0.2f, 0.8f)
                            binding.newContactButton.alpha = twentyTo80Progress
                            binding.coordinatorLayout.alpha = 1f - twentyTo80Progress

                            // Make CardView background transparent before contracting the corners
                            // of the ExtendedFAB
                            if (progress >= 0.8f) {
                                binding.container.setCardBackgroundColor(Color.TRANSPARENT)
                            }

                            // Apply rounded corners to the ExtendedFAB during the last 20%
                            val eightyTo100Progress = progressWindow(progress, 0.8f, 1f)
                            val cornerRadius = (endValues.values[propCornerRadius] as Float)*(eightyTo100Progress)
                            binding.newContactButton.shapeAppearanceModel =
                                binding.newContactButton.shapeAppearanceModel
                                    .toBuilder()
                                    .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                                    .build()
                        }

                        return animator
                    }
                }
            )
    }
}