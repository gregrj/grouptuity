package com.grouptuity.grouptuity.ui.billsplit.taxtip

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
import com.grouptuity.grouptuity.databinding.FragTaxTipBinding
import com.grouptuity.grouptuity.ui.billsplit.BillSplitFragmentDirections
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import java.text.NumberFormat


class TaxTipFragment: Fragment() {
    private var binding by setNullOnDestroy<FragTaxTipBinding>()
    private lateinit var taxTipViewModel: TaxTipViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragTaxTipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taxTipViewModel = ViewModelProvider(requireActivity()).get(TaxTipViewModel::class.java)

        val percentFormatter = NumberFormat.getPercentInstance()
        percentFormatter.minimumFractionDigits = 0
        percentFormatter.maximumFractionDigits = 4

        taxTipViewModel.subtotal.observe(viewLifecycleOwner, { value: Double -> binding.subtotal.text = NumberFormat.getCurrencyInstance().format(value) })

        taxTipViewModel.discountAmount.observe(viewLifecycleOwner, { value: Double -> binding.discountAmount.text = NumberFormat.getCurrencyInstance().format(value) })

        taxTipViewModel.subtotalWithDiscounts.observe(viewLifecycleOwner, { value: Double -> binding.afterDiscountAmount.text = NumberFormat.getCurrencyInstance().format(value) })

        taxTipViewModel.taxPercent.observe(viewLifecycleOwner, { value: Double -> binding.taxPercent.setText(percentFormatter.format(value / 100.0)) })

        taxTipViewModel.taxAmount.observe(viewLifecycleOwner, { value: Double -> binding.taxAmount.setText(NumberFormat.getCurrencyInstance().format(value)) })

        taxTipViewModel.subtotalWithDiscountsAndTax.observe(viewLifecycleOwner, { value: Double -> binding.afterTaxAmount.text = NumberFormat.getCurrencyInstance().format(value) })

        taxTipViewModel.tipPercent.observe(viewLifecycleOwner, { value: Double -> binding.tipPercent.setText(percentFormatter.format(value / 100.0)) })

        taxTipViewModel.tipAmount.observe(viewLifecycleOwner, { value: Double -> binding.tipAmount.setText(NumberFormat.getCurrencyInstance().format(value)) })

        taxTipViewModel.total.observe(viewLifecycleOwner, { value: Double -> binding.total.text = NumberFormat.getCurrencyInstance().format(value) })

        val cardMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics).toInt()
        binding.nestedScrollView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.constraintLayout.minHeight = binding.nestedScrollView.height - cardMargin
        }

        binding.addDiscountButton.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            findNavController().navigate(BillSplitFragmentDirections.createFirstDiscount(editedDiscount = null, originParams = null),
                FragmentNavigatorExtras(
                    binding.addDiscountsContainer to "discountsContainerTransitionName",
                    binding.addDiscountButton to "discountButtonTransitionName"
                )
            )
        }

        binding.editDiscountsButton.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            requireParentFragment().exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireParentFragment().requireView())
            }

            findNavController().navigate(
                BillSplitFragmentDirections.manageDiscounts(null),
                FragmentNavigatorExtras(
                    binding.editDiscountsContainer to "discountsContainerTransitionName",
                    binding.editDiscountsButton to "discountButtonTransitionName"
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
