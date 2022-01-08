package com.grouptuity.grouptuity.ui.billsplit

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.CalculationType
import com.grouptuity.grouptuity.databinding.FragTaxTipBinding
import com.grouptuity.grouptuity.ui.calculator.CALCULATOR_RETURN_KEY
import com.grouptuity.grouptuity.ui.util.views.setNullOnDestroy
import java.math.BigDecimal
import java.text.NumberFormat


class TaxTipFragment: Fragment() {
    private var binding by setNullOnDestroy<FragTaxTipBinding>()
    private lateinit var taxTipViewModel: TaxTipViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        taxTipViewModel = ViewModelProvider(requireActivity()).get(TaxTipViewModel::class.java)
        binding = FragTaxTipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Pair<CalculationType, BigDecimal>>(
            CALCULATOR_RETURN_KEY)?.observe(viewLifecycleOwner) { pair ->
                when(pair.first) {
                    CalculationType.TAX_PERCENT -> { taxTipViewModel.setTaxPercent(pair.second) }
                    CalculationType.TAX_AMOUNT -> { taxTipViewModel.setTaxAmount(pair.second) }
                    CalculationType.TIP_PERCENT -> { taxTipViewModel.setTipPercent(pair.second) }
                    CalculationType.TIP_AMOUNT -> { taxTipViewModel.setTipAmount(pair.second) }
                    else -> { /* Other CalculationTypes should not be received */ }
                }
        }

        val percentFormatter = NumberFormat.getPercentInstance()
        percentFormatter.minimumFractionDigits = 0
        percentFormatter.maximumFractionDigits = 3

        taxTipViewModel.subtotal.observe(viewLifecycleOwner) { binding.subtotal.text = it }
        taxTipViewModel.discountAmount.observe(viewLifecycleOwner) { binding.discountAmount.text = it }
        taxTipViewModel.subtotalWithDiscounts.observe(viewLifecycleOwner) { binding.afterDiscountAmount.text = it }
        taxTipViewModel.taxPercent.observe(viewLifecycleOwner) { binding.taxPercent.setText(it) }
        taxTipViewModel.taxAmount.observe(viewLifecycleOwner) { binding.taxAmount.setText(it) }
        taxTipViewModel.subtotalWithDiscountsAndTax.observe(viewLifecycleOwner) { binding.afterTaxAmount.text = it }
        taxTipViewModel.tipPercent.observe(viewLifecycleOwner) { binding.tipPercent.setText(it) }
        taxTipViewModel.tipAmount.observe(viewLifecycleOwner) { binding.tipAmount.setText(it) }
        taxTipViewModel.total.observe(viewLifecycleOwner) { binding.total.text = it }

        val cardMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics).toInt()
        binding.nestedScrollView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.constraintLayout.minHeight = binding.nestedScrollView.height - cardMargin
        }

        binding.taxPercent.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

            findNavController().navigate(BillSplitFragmentDirections.editTaxTip(
                title = resources.getString(
                    R.string.calculator_toolbar_title_tax_pct,
                    binding.taxPercent.text.toString()),
                previousValue = binding.taxPercent.text.toString(),
                calculationType = CalculationType.TAX_PERCENT),
                FragmentNavigatorExtras(
                    binding.editTaxPercentContainer to binding.editTaxPercentContainer.transitionName,
                    binding.taxPercent to binding.taxPercent.transitionName
                )
            )
        }

        binding.taxAmount.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

            findNavController().navigate(BillSplitFragmentDirections.editTaxTip(
                title = resources.getString(
                    R.string.calculator_toolbar_title_tax_amt,
                    binding.taxAmount.text.toString()),
                previousValue = binding.taxAmount.text.toString(),
                calculationType = CalculationType.TAX_AMOUNT),
                FragmentNavigatorExtras(
                    binding.editTaxAmountContainer to binding.editTaxAmountContainer.transitionName,
                    binding.taxAmount to binding.taxAmount.transitionName
                )
            )
        }

        binding.tipPercent.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

            findNavController().navigate(BillSplitFragmentDirections.editTaxTip(
                title = resources.getString(
                    R.string.calculator_toolbar_title_tip_pct,
                    binding.tipPercent.text.toString()),
                previousValue = binding.tipPercent.text.toString(),
                calculationType = CalculationType.TIP_PERCENT),
                FragmentNavigatorExtras(
                    binding.editTipPercentContainer to binding.editTipPercentContainer.transitionName,
                    binding.tipPercent to binding.tipPercent.transitionName
                )
            )
        }

        binding.tipAmount.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

            findNavController().navigate(BillSplitFragmentDirections.editTaxTip(
                title = resources.getString(
                    R.string.calculator_toolbar_title_tip_amt,
                    binding.tipAmount.text.toString()),
                previousValue = binding.tipAmount.text.toString(),
                calculationType = CalculationType.TIP_AMOUNT),
                FragmentNavigatorExtras(
                    binding.editTipAmountContainer to binding.editTipAmountContainer.transitionName,
                    binding.tipAmount to binding.tipAmount.transitionName
                )
            )
        }

        binding.addDiscountButton.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

            findNavController().navigate(BillSplitFragmentDirections.createFirstDiscount(editedDiscountId = null, originParams = null),
                FragmentNavigatorExtras(
                    binding.addDiscountsContainer to binding.addDiscountsContainer.transitionName,
                    binding.addDiscountButton to binding.addDiscountButton.transitionName
                )
            )
        }

        binding.editDiscountsButton.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

            findNavController().navigate(
                BillSplitFragmentDirections.manageDiscounts(null),
                FragmentNavigatorExtras(
                    binding.editDiscountsContainer to binding.editDiscountsContainer.transitionName,
                    binding.editDiscountsButton to binding.editDiscountsButton.transitionName
                )
            )
        }

        taxTipViewModel.hasNoDiscounts.observe(viewLifecycleOwner) {
            if(it) {
                binding.addDiscountButton.transitionName = "discountButtonTransitionName"
                binding.addDiscountsContainer.transitionName = "discountsContainerTransitionName"
                binding.editDiscountsButton.transitionName = null
                binding.editDiscountsContainer.transitionName = null

                binding.discountTitle.visibility = View.VISIBLE
                binding.discountAmount.visibility = View.GONE

                binding.addDiscountButton.visibility = View.VISIBLE
                binding.editDiscountsButton.visibility = View.GONE
            } else {
                binding.addDiscountButton.transitionName = null
                binding.addDiscountsContainer.transitionName = null
                binding.editDiscountsButton.transitionName = "discountButtonTransitionName"
                binding.editDiscountsContainer.transitionName = "discountsContainerTransitionName"

                binding.discountTitle.visibility = View.INVISIBLE
                binding.discountAmount.visibility = View.VISIBLE

                binding.addDiscountButton.visibility = View.GONE
                binding.editDiscountsButton.visibility = View.VISIBLE
            }
        }
    }
}
