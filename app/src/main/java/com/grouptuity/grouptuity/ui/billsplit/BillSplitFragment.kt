package com.grouptuity.grouptuity.ui.billsplit

import android.os.Bundle
import android.view.*
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
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import com.grouptuity.grouptuity.ui.custom.transitions.CircularRevealTransition


// TODO prevent double tap on fab causing navigation error

class BillSplitFragment: Fragment() {
    private var binding by setNullOnDestroy<FragBillSplitBinding>()
    private lateinit var billSplitViewModel: BillSplitViewModel
    private var fabPageIndex = 0

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
                if(position != fabPageIndex && binding.fab.isOrWillBeShown) {
                    // Old FAB is showing so first hide and once hidden, show with new image
                    binding.fab.hide(object: FloatingActionButton.OnVisibilityChangedListener() {
                        override fun onHidden(fab: FloatingActionButton) {
                            super.onHidden(fab)
                            fabPageIndex = position
                            when (position) {
                                0 -> {
                                    fab.setImageResource(R.drawable.ic_add_person)
                                    fab.show()
                                }
                                1 -> {
                                    fab.setImageResource(R.drawable.ic_add_item)
                                    fab.show()
                                }
                                2 -> { /* no FAB on the Tax&Tip fragment */ }
                                3 -> { /* no FAB on the Payment fragment */ }
                            }
                        }
                    })
                } else {
                    fabPageIndex = position
                    when (position) {
                        0 -> {  binding.fab.setImageResource(R.drawable.ic_add_person)
                            binding.fab.show() }
                        1 -> {  binding.fab.setImageResource(R.drawable.ic_add_item)
                            binding.fab.show() }
                        2 -> {  binding.fab.hide() }
                        3 -> {  binding.fab.hide() }
                    }
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

        binding.fab.setOnClickListener {
            when(binding.viewPager.currentItem) {
                0 -> {  // Show address book for contact selection
                    findNavController().navigate(BillSplitFragmentDirections.actionBillSplitFragmentToAddressBookFragment(CircularRevealTransition.OriginParams(binding.fab)))
                }
                1 -> {  // Show fragment for item entry
                    findNavController().navigate(BillSplitFragmentDirections.actionBillSplitToItemEntry(editedItem = null, CircularRevealTransition.OriginParams(binding.fab)))
                }
            }
        }
    }
}