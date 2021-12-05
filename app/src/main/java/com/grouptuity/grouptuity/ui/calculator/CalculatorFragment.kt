package com.grouptuity.grouptuity.ui.calculator

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.Transition
import androidx.transition.TransitionValues
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.CalculationType
import com.grouptuity.grouptuity.databinding.FragCalculatorBinding
import com.grouptuity.grouptuity.ui.custom.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy

// TODO handle inset changes

const val CALCULATOR_RETURN_KEY = "calculatorReturnKey"


class CalculatorFragment: Fragment() {
    private val args: CalculatorFragmentArgs by navArgs()
    private var binding by setNullOnDestroy<FragCalculatorBinding>()
    private lateinit var calculatorViewModel: CalculatorViewModel
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        calculatorViewModel = ViewModelProvider(requireActivity())[CalculatorViewModel::class.java].also {
            it.initialize(args.calculationType)
        }
        binding = FragCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Intercept user interactions while while fragment transitions and animations are running
        binding.rootLayout.attachLock(calculatorViewModel.isInputLocked)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!calculatorViewModel.isInputLocked.value)
                    closeFragment()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // Setup transitions
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        setupEnterTransition()
        binding.fadeOutEditText.setText(args.previousValue)

        // Setup toolbar
        binding.toolbar.apply {
            title = args.title

            // Set toolbar back navigation icon and its color
            setNavigationIcon(R.drawable.ic_arrow_back_light)

            // Close fragment using default onBackPressed behavior unless animation in progress
            setNavigationOnClickListener {
                if (!calculatorViewModel.isInputLocked.value)
                    closeFragment()
            }
        }

        setupNumberPad()

        calculatorViewModel.acceptEvents.observe(viewLifecycleOwner) {
            it.consume()?.apply {
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    CALCULATOR_RETURN_KEY, Pair(args.calculationType, this.first)
                )
                binding.numberPad.displayTextview.text = this.second
                binding.fadeOutEditText.setText(this.third)
                closeFragment()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.fadeOutEditText.visibility = View.GONE

        // Reset UI input/output locks leftover from aborted transitions/animations
        calculatorViewModel.unFreezeOutput()
        calculatorViewModel.unLockInput()
    }

    private fun closeFragment() {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

        // Prevent live updates to UI during return transition
        calculatorViewModel.freezeOutput()

        setupReturnTransition()

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    private fun setupNumberPad() {
        calculatorViewModel.formattedValue.observe(viewLifecycleOwner, {
            binding.numberPad.displayTextview.text = it
        })

        calculatorViewModel.backspaceButtonVisible.observe(viewLifecycleOwner) {
            binding.numberPad.buttonBackspace.visibility = if(it) View.VISIBLE else View.GONE
        }
        binding.numberPad.buttonBackspace.setOnClickListener { calculatorViewModel.removeDigitFromPrice() }
        binding.numberPad.buttonBackspace.setOnLongClickListener {
            calculatorViewModel.resetPrice()
            true
        }

        calculatorViewModel.zeroButtonEnabled.observe(viewLifecycleOwner) {
            binding.numberPad.button0.isEnabled = it
        }
        calculatorViewModel.nonZeroButtonsEnabled.observe(viewLifecycleOwner) {
            binding.numberPad.button1.isEnabled = it
            binding.numberPad.button2.isEnabled = it
            binding.numberPad.button3.isEnabled = it
            binding.numberPad.button4.isEnabled = it
            binding.numberPad.button5.isEnabled = it
            binding.numberPad.button6.isEnabled = it
            binding.numberPad.button7.isEnabled = it
            binding.numberPad.button8.isEnabled = it
            binding.numberPad.button9.isEnabled = it
        }
        calculatorViewModel.decimalButtonEnabled.observe(viewLifecycleOwner) {
            binding.numberPad.buttonDecimal.isEnabled = it
        }
        calculatorViewModel.acceptButtonEnabled.observe(viewLifecycleOwner) {
            if(it) {
                binding.numberPad.buttonAccept.show()
            } else {
                binding.numberPad.buttonAccept.hide()
            }
        }

        binding.numberPad.buttonDecimal.setOnClickListener { calculatorViewModel.addDecimalToPrice() }
        binding.numberPad.button0.setOnClickListener { calculatorViewModel.addDigitToPrice('0') }
        binding.numberPad.button1.setOnClickListener { calculatorViewModel.addDigitToPrice('1') }
        binding.numberPad.button2.setOnClickListener { calculatorViewModel.addDigitToPrice('2') }
        binding.numberPad.button3.setOnClickListener { calculatorViewModel.addDigitToPrice('3') }
        binding.numberPad.button4.setOnClickListener { calculatorViewModel.addDigitToPrice('4') }
        binding.numberPad.button5.setOnClickListener { calculatorViewModel.addDigitToPrice('5') }
        binding.numberPad.button6.setOnClickListener { calculatorViewModel.addDigitToPrice('6') }
        binding.numberPad.button7.setOnClickListener { calculatorViewModel.addDigitToPrice('7') }
        binding.numberPad.button8.setOnClickListener { calculatorViewModel.addDigitToPrice('8') }
        binding.numberPad.button9.setOnClickListener { calculatorViewModel.addDigitToPrice('9') }
        binding.numberPad.buttonAccept.setOnClickListener { calculatorViewModel.tryAcceptValue() }
    }

    private fun setupEnterTransition() {
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:top_inset"

        binding.container.transitionName = when(args.calculationType) {
            CalculationType.SUBTOTAL -> "subtotalContainerTransitionName"
            CalculationType.OVERALL_DISCOUNT_PERCENT -> "discountPercentContainerTransitionName"
            CalculationType.OVERALL_DISCOUNT_AMOUNT -> "discountAmountContainerTransitionName"
            CalculationType.AFTER_DISCOUNT -> "afterDiscountContainerTransitionName"
            CalculationType.TAX_PERCENT -> "taxPercentContainerTransitionName"
            CalculationType.TAX_AMOUNT -> "taxAmountContainerTransitionName"
            CalculationType.AFTER_TAX -> "afterTaxContainerTransitionName"
            CalculationType.TIP_PERCENT -> "tipPercentContainerTransitionName"
            CalculationType.TIP_AMOUNT -> "tipAmountContainerTransitionName"
            CalculationType.TOTAL -> "totalContainerTransitionName"
            else -> ""
        }

        binding.fadeOutEditText.transitionName = when(args.calculationType) {
            CalculationType.SUBTOTAL -> "subtotalEditTextTransitionName"
            CalculationType.OVERALL_DISCOUNT_PERCENT -> "discountPercentEditTextTransitionName"
            CalculationType.OVERALL_DISCOUNT_AMOUNT -> "discountAmountEditTextTransitionName"
            CalculationType.AFTER_DISCOUNT -> {
                binding.fadeOutEditText.setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                "afterDiscountEditTextTransitionName"
            }
            CalculationType.TAX_PERCENT -> "taxPercentEditTextTransitionName"
            CalculationType.TAX_AMOUNT -> "taxAmountEditTextTransitionName"
            CalculationType.AFTER_TAX -> {
                binding.fadeOutEditText.setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                "afterTaxEditTextTransitionName"
            }
            CalculationType.TIP_PERCENT -> "tipPercentEditTextTransitionName"
            CalculationType.TIP_AMOUNT -> "tipAmountEditTextTransitionName"
            CalculationType.TOTAL -> {
                binding.fadeOutEditText.setTypeface(binding.fadeOutEditText.typeface, Typeface.BOLD)
                "totalEditTextTransitionName"
            }
            else -> ""
        }

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, true).addElement(
            binding.fadeOutEditText.transitionName,
            object: CardViewExpandTransition.Element{
                override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[propTopInset] = 0
                }

                override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[propTopInset] = 1
                }

                override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.doOnStart {
                        binding.coordinatorLayout.alpha = 0f
                        binding.fadeOutEditText.alpha = 1f
                    }

                    animator.addUpdateListener {
                        val twentyTo80Progress = (1.666667f*AccelerateDecelerateInterpolator()
                            .getInterpolation(it.animatedFraction) - 0.33333f).coerceIn(0f, 1f)
                        binding.fadeOutEditText.alpha = 1f - twentyTo80Progress
                        binding.coordinatorLayout.alpha = twentyTo80Progress
                    }

                    return animator
                }
            }
        ).setOnTransitionStartCallback { _, _, _, _ -> calculatorViewModel.notifyTransitionStarted() }
            .setOnTransitionEndCallback { _, _, _, _ -> calculatorViewModel.notifyTransitionFinished() }

        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
    }

    private fun setupReturnTransition() {
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:top_inset"

        binding.fadeOutEditText.visibility = View.VISIBLE
        when(args.calculationType) {
            CalculationType.TAX_PERCENT -> {
                binding.container.transitionName = "taxPercentContainerTransitionName"
                binding.fadeOutEditText.transitionName = "taxPercentEditTextTransitionName"
            }
            CalculationType.TAX_AMOUNT -> {
                binding.container.transitionName = "taxAmountContainerTransitionName"
                binding.fadeOutEditText.transitionName = "taxAmountEditTextTransitionName"
            }
            CalculationType.TIP_PERCENT -> {
                binding.container.transitionName = "tipPercentContainerTransitionName"
                binding.fadeOutEditText.transitionName = "tipPercentEditTextTransitionName"
            }
            CalculationType.TIP_AMOUNT -> {
                binding.container.transitionName = "tipAmountContainerTransitionName"
                binding.fadeOutEditText.transitionName = "tipAmountEditTextTransitionName"
            }
            else -> { /* Other CalculationTypes should not be received */ }
        }

        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, false)
            .addElement(
            binding.fadeOutEditText.transitionName,
            object: CardViewExpandTransition.Element{
                override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[propTopInset] = 0
                }

                override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[propTopInset] = 1
                }

                override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.doOnStart {
                        binding.coordinatorLayout.alpha = 1f
                        binding.fadeOutEditText.alpha = 0f
                    }

                    animator.addUpdateListener {
                        val twentyTo60Progress = (2.5f*AccelerateDecelerateInterpolator()
                            .getInterpolation(it.animatedFraction) - 0.5f).coerceIn(0f, 1f)

                        binding.fadeOutEditText.alpha = twentyTo60Progress
                        binding.coordinatorLayout.alpha = 1f - twentyTo60Progress
                    }

                    return animator
                }
            }
        ).setOnTransitionStartCallback { _, _, _, _ -> calculatorViewModel.notifyTransitionStarted() }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(requireView())
        }
    }
}