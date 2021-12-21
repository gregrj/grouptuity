package com.grouptuity.grouptuity.ui.calculator

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
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
import com.grouptuity.grouptuity.ui.util.UIFragment
import com.grouptuity.grouptuity.ui.util.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.util.views.setupFixedNumberPad
import com.grouptuity.grouptuity.ui.util.views.setupToolbarSecondaryTertiaryAnimation


const val CALCULATOR_RETURN_KEY = "calculatorReturnKey"


class CalculatorFragment: UIFragment<
        FragCalculatorBinding,
        CalculatorViewModel,
        CalculationType,
        Pair<Double, String>?>() {

    private val args: CalculatorFragmentArgs by navArgs()

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragCalculatorBinding.inflate(inflater, container, false)

    override fun createViewModel() = ViewModelProvider(requireActivity())[CalculatorViewModel::class.java]

    override fun getInitialInput() = args.calculationType

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup transitions
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        setupEnterTransition()
        binding.fadeOutEditText.setText(args.previousValue)

        setupToolbar()

        setupFixedNumberPad(
            viewLifecycleOwner,
            viewModel.calculator,
            binding.numberPad)
    }

    override fun onResume() {
        super.onResume()
        binding.fadeOutEditText.visibility = View.GONE
    }

    override fun onFinish(output: Pair<Double, String>?) {
        setupReturnTransition()

        if (output != null) {
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                CALCULATOR_RETURN_KEY, Pair(args.calculationType, output.first)
            )

            binding.fadeOutEditText.setText(output.second)
        }

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            title = args.title

            // Set toolbar back navigation icon and its color
            setNavigationIcon(R.drawable.ic_arrow_back_light)

            // Close fragment using default onBackPressed behavior unless animation in progress
            setNavigationOnClickListener {
                if (!viewModel.isInputLocked.value)
                    viewModel.handleOnBackPressed()
            }

            setupToolbarSecondaryTertiaryAnimation(
                requireContext(),
                viewLifecycleOwner,
                viewModel.toolBarInTertiaryState,
                this,
                binding.statusBarBackgroundView)
        }
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
        ).setOnTransitionStartCallback { _, _, _, _ -> viewModel.notifyTransitionStarted() }
            .setOnTransitionEndCallback { _, _, _, _ -> viewModel.notifyTransitionFinished() }

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
        ).setOnTransitionStartCallback { _, _, _, _ -> viewModel.notifyTransitionStarted() }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(requireView())
        }
    }
}