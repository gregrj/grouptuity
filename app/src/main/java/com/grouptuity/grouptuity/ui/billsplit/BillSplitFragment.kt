package com.grouptuity.grouptuity.ui.billsplit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayoutMediator
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.databinding.FragBillSplitBinding
import com.grouptuity.grouptuity.ui.custom.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.custom.views.hideExtendedFAB
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import com.grouptuity.grouptuity.ui.custom.views.showExtendedFAB


// TODO prevent double tap on fab causing navigation error

class BillSplitFragment: Fragment() {
    private var binding by setNullOnDestroy<FragBillSplitBinding>()
    private lateinit var billSplitViewModel: BillSplitViewModel
    private var fabPageIndexActual = 0
    private var fabPageIndexTarget = 0
    private var fabAnimatorsLive = false
    private var payFABHideAnimation: Animation? = null
    private var payFABShowAnimation: Animation? = null

    companion object {
        const val FRAG_POSITION_DINERS = 0
        const val FRAG_POSITION_ITEMS = 1
        const val FRAG_POSITION_TAX_TIP = 2
        const val FRAG_POSITION_PAYMENTS = 3
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        billSplitViewModel = ViewModelProvider(this).get(BillSplitViewModel::class.java)

        binding = FragBillSplitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
        binding.toolbar.setNavigationOnClickListener { (activity as MainActivity).openNavViewDrawer() }

        binding.viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun getItemCount() = 4

            override fun createFragment(position: Int) = when (position) {
                FRAG_POSITION_DINERS -> DinersFragment.newInstance()
                FRAG_POSITION_ITEMS -> ItemsFragment.newInstance()
                FRAG_POSITION_TAX_TIP -> TaxTipFragment()
                FRAG_POSITION_PAYMENTS -> PaymentsFragment.newInstance()
                else -> { Fragment() /* Not reachable */ }
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                fabPageIndexTarget = position

                if (fabPageIndexActual != fabPageIndexTarget && binding.fab.isOrWillBeShown) {

                    if (fabPageIndexTarget != 3) {
                        payFABShowAnimation?.apply {
                            if (!this.hasEnded()) {
                                payFABShowAnimation?.cancel()
                                payFABShowAnimation = null
                            }
                        }

                        if (payFABHideAnimation == null || payFABHideAnimation!!.hasEnded()) {
                            payFABHideAnimation = hideExtendedFAB(binding.paymentFab)
                        }
                    }

                    hideFAB()
                } else {
                    showFAB()
                }
            }
        })

        // Retain all pages for smoother transitions. This also corrects an undesired behavior where
        // the appbar collapses when selecting tabs that were not created at start
        binding.viewPager.offscreenPageLimit = 4

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.billsplit_tab_diners)
                1 -> getString(R.string.billsplit_tab_items)
                2 -> getString(R.string.billsplit_tab_taxtip)
                else -> getString(R.string.billsplit_tab_payment)
            }
        }.attach()

        // Overlay badge on diners tab showing how many diners are on the bill
        billSplitViewModel.dinerCount.observe(viewLifecycleOwner) {
            binding.tabLayout.getTabAt(FRAG_POSITION_DINERS)?.apply {
                if(it > 0) {
                    orCreateBadge.number = it
                } else {
                    removeBadge()
                }
            }
        }

        // Overlay badge on items tab showing how many items are on the bill
        billSplitViewModel.itemCount.observe(viewLifecycleOwner) {
            binding.tabLayout.getTabAt(FRAG_POSITION_ITEMS)?.apply {
                if(it > 0) {
                    orCreateBadge.number = it
                } else {
                    removeBadge()
                }
            }
        }

        // Override payments FAB visibility if no payments need to be processed
        billSplitViewModel.hasPaymentsToProcess.observe(viewLifecycleOwner) {
            if (fabPageIndexActual == 3) {
                showFAB()
            }
        }

        binding.fab.setOnClickListener {
            when(binding.viewPager.currentItem) {
                0 -> {  // Show address book for contact selection
                    findNavController().navigate(BillSplitFragmentDirections.openAddressbook(CircularRevealTransition.OriginParams(binding.fab)))
                }
                1 -> {  // Show fragment for item entry
                    findNavController().navigate(BillSplitFragmentDirections.createNewItem(editedItem = null, CircularRevealTransition.OriginParams(binding.fab)))
                }
            }
        }

        binding.paymentFab.setOnClickListener { billSplitViewModel.requestProcessPayments() }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        fabPageIndexTarget = binding.viewPager.currentItem
        fabPageIndexActual = fabPageIndexTarget
        when (fabPageIndexActual) {
            0 -> {
                binding.fab.setImageResource(R.drawable.ic_add_person)
                binding.fab.show()
                binding.paymentFab.visibility = View.INVISIBLE
            }
            1 -> {
                binding.fab.setImageResource(R.drawable.ic_add_item)
                binding.fab.show()
                binding.paymentFab.visibility = View.INVISIBLE
            }
            2 -> {
                binding.fab.hide()
                binding.paymentFab.visibility = View.INVISIBLE
            }
            3 -> {
                binding.fab.hide()

                if (billSplitViewModel.hasPaymentsToProcess.value == true) {
                    binding.paymentFab.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun hideFAB() {
        binding.fab.hide(object: FloatingActionButton.OnVisibilityChangedListener() {
            override fun onHidden(fab: FloatingActionButton) {
                super.onHidden(fab)
                showFAB()
            }
        })
    }

    private fun showFAB() {
        fabPageIndexActual = fabPageIndexTarget

        if (fabPageIndexActual == 3) {
            binding.fab.hide()

            if (billSplitViewModel.hasPaymentsToProcess.value == true) {
                payFABHideAnimation?.apply {
                    if (!this.hasEnded()) {
                        payFABHideAnimation?.cancel()
                        payFABHideAnimation = null
                    }
                }

                if (payFABShowAnimation == null || payFABShowAnimation!!.hasEnded()) {
                    payFABShowAnimation = showExtendedFAB(binding.paymentFab)
                }
            } else {
                payFABShowAnimation?.apply {
                    if (!this.hasEnded()) {
                        payFABShowAnimation?.cancel()
                        payFABShowAnimation = null
                    }
                }

                if (payFABHideAnimation == null || payFABHideAnimation!!.hasEnded()) {
                    payFABHideAnimation = hideExtendedFAB(binding.paymentFab)
                }
            }
        } else {
            payFABShowAnimation?.apply {
                if (!this.hasEnded()) {
                    payFABShowAnimation?.cancel()
                    payFABShowAnimation = null
                }
            }

            if (payFABHideAnimation == null || payFABHideAnimation!!.hasEnded()) {
                payFABHideAnimation = hideExtendedFAB(binding.paymentFab)
            }

            when (fabPageIndexActual) {
                0 -> {
                    binding.fab.setImageResource(R.drawable.ic_add_person)
                    binding.fab.show()
                }
                1 -> {
                    binding.fab.setImageResource(R.drawable.ic_add_item)
                    binding.fab.show()
                }
                2 -> {
                    binding.fab.hide()
                }
            }
        }
    }
}