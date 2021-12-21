package com.grouptuity.grouptuity.ui.basictipcalc

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.CalculationType
import com.grouptuity.grouptuity.databinding.FragBasicTipCalcBinding
import com.grouptuity.grouptuity.ui.calculator.CALCULATOR_RETURN_KEY
import com.grouptuity.grouptuity.ui.util.UIFragment
import java.text.NumberFormat


class BasicTipCalcFragment: UIFragment<FragBasicTipCalcBinding, BasicTipCalcViewModel, Unit?, Unit?>() {

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragBasicTipCalcBinding.inflate(inflater, container, false)

    override fun createViewModel() = ViewModelProvider(requireActivity())[BasicTipCalcViewModel::class.java]

    override fun getInitialInput(): Unit? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Pair<CalculationType, Double>>(
            CALCULATOR_RETURN_KEY)?.observe(viewLifecycleOwner) { pair ->
            when(pair.first) {
                CalculationType.SUBTOTAL -> { viewModel.setSubtotal(pair.second) }
                CalculationType.OVERALL_DISCOUNT_PERCENT -> { viewModel.setDiscountPercent(pair.second) }
                CalculationType.OVERALL_DISCOUNT_AMOUNT -> { viewModel.setDiscountAmount(pair.second) }
                CalculationType.AFTER_DISCOUNT -> { viewModel.setAfterDiscount(pair.second) }
                CalculationType.TAX_PERCENT -> { viewModel.setTaxPercent(pair.second) }
                CalculationType.TAX_AMOUNT -> { viewModel.setTaxAmount(pair.second) }
                CalculationType.AFTER_TAX -> { viewModel.setAfterTax(pair.second) }
                CalculationType.TIP_PERCENT -> { viewModel.setTipPercent(pair.second) }
                CalculationType.TIP_AMOUNT -> { viewModel.setTipAmount(pair.second) }
                CalculationType.TOTAL -> { viewModel.setTotal(pair.second) }
                else -> { /* Other CalculationTypes should not be received */ }
            }
        }

        binding.toolbar.apply {
            setNavigationIcon(R.drawable.ic_arrow_back_light)

            // Close fragment using default onBackPressed behavior
            setNavigationOnClickListener { requireActivity().onBackPressed() }

            inflateMenu(R.menu.toolbar_calculator)
            setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    R.id.tax_is_tipped -> {
                        viewModel.toggleTipTaxed()
                        true
                    }
                    R.id.discount_reduces_tip -> {
                        viewModel.toggleDiscountsReduceTip()
                        true
                    }
                    R.id.reset -> {
                        viewModel.reset()
                        true
                    }
                    else -> { false }
                }
            }
        }

        viewModel.taxTipped.observe(viewLifecycleOwner, {
            binding.toolbar.menu.findItem(R.id.tax_is_tipped).isChecked = it
        })
        viewModel.discountReducesTip.observe(viewLifecycleOwner, {
            binding.toolbar.menu.findItem(R.id.discount_reduces_tip).isChecked = it
        })

        // Listener for sizing the CardView
        val cardMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics).toInt()
        binding.nestedScrollView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.constraintLayout.minHeight = binding.nestedScrollView.height - cardMargin
        }

        val percentFormatter = NumberFormat.getPercentInstance()
        percentFormatter.minimumFractionDigits = 0
        percentFormatter.maximumFractionDigits = 3

        viewModel.subtotalString.observe(viewLifecycleOwner, { binding.subtotal.setText(it) })
        viewModel.discountPercentString.observe(viewLifecycleOwner, { binding.discountPercent.setText(it) })
        viewModel.discountAmountString.observe(viewLifecycleOwner, { binding.discountAmount.setText(it) })
        viewModel.afterDiscountString.observe(viewLifecycleOwner, { binding.afterDiscountAmount.setText(it) })
        viewModel.taxPercentString.observe(viewLifecycleOwner, { binding.taxPercent.setText(it) })
        viewModel.taxAmountString.observe(viewLifecycleOwner, { binding.taxAmount.setText(it) })
        viewModel.afterTaxString.observe(viewLifecycleOwner, { binding.afterTaxAmount.setText(it) })
        viewModel.tipPercentString.observe(viewLifecycleOwner, { binding.tipPercent.setText(it) })
        viewModel.tipAmountString.observe(viewLifecycleOwner, { binding.tipAmount.setText(it) })
        viewModel.totalString.observe(viewLifecycleOwner, { binding.total.setText(it) })

        viewModel.warningMessageEvent.observe(viewLifecycleOwner, {
            it.consume()?.apply {
                Snackbar.make(
                    binding.nestedScrollView,
                    this.value,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        })

        binding.subtotal.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_subtotal,
                binding.subtotal.text.toString(),
                CalculationType.SUBTOTAL,
                binding.editSubtotalContainer,
                binding.subtotal
            )
        }

        binding.discountPercent.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_discount_pct,
                binding.discountPercent.text.toString(),
                CalculationType.OVERALL_DISCOUNT_PERCENT,
                binding.editDiscountPercentContainer,
                binding.discountPercent
            )
        }

        binding.discountAmount.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_discount_amt,
                binding.discountAmount.text.toString(),
                CalculationType.OVERALL_DISCOUNT_AMOUNT,
                binding.editDiscountAmountContainer,
                binding.discountAmount
            )
        }

        binding.afterDiscountAmount.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_after_discounts,
                binding.afterDiscountAmount.text.toString(),
                CalculationType.AFTER_DISCOUNT,
                binding.editAfterDiscountContainer,
                binding.afterDiscountAmount
            )
        }

        binding.taxPercent.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_tax_pct,
                binding.taxPercent.text.toString(),
                CalculationType.TAX_PERCENT,
                binding.editTaxPercentContainer,
                binding.taxPercent
            )
        }

        binding.taxAmount.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_tax_amt,
                binding.taxAmount.text.toString(),
                CalculationType.TAX_AMOUNT,
                binding.editTaxAmountContainer,
                binding.taxAmount
            )
        }

        binding.afterTaxAmount.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_after_tax,
                binding.afterTaxAmount.text.toString(),
                CalculationType.AFTER_TAX,
                binding.editAfterTaxContainer,
                binding.afterTaxAmount
            )
        }

        binding.tipPercent.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_tip_pct,
                binding.tipPercent.text.toString(),
                CalculationType.TIP_PERCENT,
                binding.editTipPercentContainer,
                binding.tipPercent
            )
        }

        binding.tipAmount.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_tip_amt,
                binding.tipAmount.text.toString(),
                CalculationType.TIP_AMOUNT,
                binding.editTipAmountContainer,
                binding.tipAmount
            )
        }

        binding.total.setOnClickListener {
            navigateToCalculator(
                R.string.calculator_toolbar_title_total,
                binding.total.text.toString(),
                CalculationType.TOTAL,
                binding.editTotalContainer,
                binding.total
            )
        }
    }

    private fun navigateToCalculator(titleStringId: Int,
                                     previousValue: String,
                                     calculationType: CalculationType,
                                     cardView: MaterialCardView,
                                     editText: EditText) {
        // Exit transition is needed to prevent next fragment from appearing immediately
        exitTransition = Hold().apply {
            duration = 0L
            addTarget(requireView())
        }

        (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

        findNavController().navigate(
            BasicTipCalcFragmentDirections.editSimpleCalcTaxTip(
                title = resources.getString(titleStringId, previousValue),
                previousValue = previousValue,
                calculationType = calculationType),
            FragmentNavigatorExtras(
                cardView to cardView.transitionName,
                editText to editText.transitionName
            )
        )
    }
}