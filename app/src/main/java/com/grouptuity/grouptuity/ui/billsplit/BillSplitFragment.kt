package com.grouptuity.grouptuity.ui.billsplit

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.transition.Transition
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayoutMediator
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import com.grouptuity.grouptuity.databinding.FragBillSplitBinding
import com.grouptuity.grouptuity.databinding.FragDinersListitemBinding
import com.grouptuity.grouptuity.databinding.FragItemsListitemBinding
import com.grouptuity.grouptuity.ui.custom.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.custom.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.custom.transitions.progressWindow
import com.grouptuity.grouptuity.ui.custom.views.ContactIcon
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import java.text.NumberFormat

// TODO prevent double tap on fab causing navigation error

class BillSplitFragment: Fragment() {
    private var binding by setNullOnDestroy<FragBillSplitBinding>()
    private lateinit var billSplitViewModel: BillSplitViewModel
    private var fabActiveDrawableId: Int? = R.drawable.ic_add_person
    private var toolbarInSecondaryState = false
    private var tabLayoutHeight: Int = 0
        set(value) { if (value > 0) field = value }
    private var newDinerIdForTransition: String? = null
    private var newItemIdForTransition: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        billSplitViewModel = ViewModelProvider(this)[BillSplitViewModel::class.java]

        binding = FragBillSplitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.also { args ->
            val newDiner = args.getParcelable<Diner>("new_diner")
            if (newDiner != null) {
                postponeEnterTransition()
                newDinerIdForTransition = newDiner.id
            } else {
                val newItem = args.getParcelable<Item>("new_item")
                if (newItem != null) {
                    postponeEnterTransition()
                    newItemIdForTransition = newItem.id
                }
            }
            args.clear()
        }

        setupToolbarMenu()

        binding.viewPager.also { viewPager ->
            viewPager.adapter = object: FragmentStateAdapter(this) {

                override fun getItemCount() = 4

                override fun createFragment(position: Int) = when (position) {
                    FRAG_DINERS -> DinersFragment.newInstance().also { frag ->
                        newDinerIdForTransition?.also { frag.setSharedElementDinerId(it) }
                    }
                    FRAG_ITEMS -> ItemsFragment.newInstance().also { frag ->
                        newItemIdForTransition?.also { frag.setSharedElementItemId(it) }
                    }
                    FRAG_TAX_TIP -> TaxTipFragment()
                    FRAG_PAYMENTS -> PaymentsFragment.newInstance()
                    else -> { Fragment() /* Not reachable */ }
                }
            }

            viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    billSplitViewModel.activeFragmentIndex.value = position
                }
            })

            // Retain all pages for smoother transitions. This also corrects an undesired behavior where
            // the appbar collapses when selecting tabs that were not created at start.
            viewPager.offscreenPageLimit = 4
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                FRAG_DINERS -> getString(R.string.billsplit_tab_diners)
                FRAG_ITEMS -> getString(R.string.billsplit_tab_items)
                FRAG_TAX_TIP -> getString(R.string.billsplit_tab_taxtip)
                FRAG_PAYMENTS -> getString(R.string.billsplit_tab_payment)
                else -> ""
            }
        }.attach()

        val primaryColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data
        val primaryDarkColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorPrimaryVariant, it, true) }.data
        val secondaryColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSecondary, it, true) }.data
        val secondaryDarkColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSecondaryVariant, it, true) }.data
        val animationDuration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
        billSplitViewModel.isProcessingPayments.observe(viewLifecycleOwner) {
            if (it) {
                binding.viewPager.isUserInputEnabled = false

                binding.toolbar.setNavigationIcon(R.drawable.ic_close)
                binding.toolbar.setNavigationOnClickListener { billSplitViewModel.stopProcessingPayments() }
                binding.toolbar.setTitle(R.string.payments_toolbar_title)

                if (it != toolbarInSecondaryState) {
                    ValueAnimator.ofObject(ArgbEvaluator(), primaryColor, secondaryColor).apply {
                        duration = animationDuration
                        addUpdateListener { animator -> binding.toolbar.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()

                    ValueAnimator.ofObject(ArgbEvaluator(), primaryDarkColor, secondaryDarkColor).apply {
                        duration = animationDuration
                        addUpdateListener { animator -> binding.statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()

                    val startHeight = binding.appbarLayout.height
                    tabLayoutHeight = binding.tabLayout.height
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = animationDuration
                        addUpdateListener { animator ->
                            binding.appbarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                height = (startHeight - (animator.animatedValue as Float) * tabLayoutHeight).toInt()
                            }
                        }
                        addListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator?) { }
                            override fun onAnimationEnd(animation: Animator?) {
                                binding.tabLayout.visibility = View.GONE
                                binding.appbarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                    height = CoordinatorLayout.LayoutParams.WRAP_CONTENT
                                }
                            }
                            override fun onAnimationCancel(animation: Animator?) { }
                            override fun onAnimationRepeat(animation: Animator?) { }
                        })
                    }.start()

                    toolbarInSecondaryState = it
                } else {
                    binding.toolbar.setBackgroundColor(secondaryColor)
                    binding.statusBarBackgroundView.setBackgroundColor(secondaryDarkColor)
                    binding.tabLayout.visibility = View.GONE
                    binding.appbarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                        height = CoordinatorLayout.LayoutParams.WRAP_CONTENT
                    }
                }
            } else {
                binding.viewPager.isUserInputEnabled = true

                binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
                binding.toolbar.setNavigationOnClickListener { (activity as MainActivity).openNavViewDrawer() }
                binding.toolbar.setTitle(R.string.app_name)

                if (it != toolbarInSecondaryState) {
                    ValueAnimator.ofObject(ArgbEvaluator(), secondaryColor, primaryColor).apply {
                        duration = animationDuration
                        addUpdateListener { animator -> binding.toolbar.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()

                    ValueAnimator.ofObject(ArgbEvaluator(), secondaryDarkColor, primaryDarkColor).apply {
                        duration = animationDuration
                        addUpdateListener { animator -> binding.statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()

                    val startHeight = binding.appbarLayout.height
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = animationDuration
                        addUpdateListener { animator ->
                            binding.appbarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                height = (startHeight + (animator.animatedValue as Float) * tabLayoutHeight).toInt()
                            }
                        }
                        addListener(object: Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator?) {
                                binding.tabLayout.visibility = View.VISIBLE
                            }
                            override fun onAnimationEnd(animation: Animator?) {
                                binding.appbarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                                    height = CoordinatorLayout.LayoutParams.WRAP_CONTENT
                                }
                            }
                            override fun onAnimationCancel(animation: Animator?) { }
                            override fun onAnimationRepeat(animation: Animator?) { }
                        })
                    }.start()

                    toolbarInSecondaryState = it
                } else {
                    binding.toolbar.setBackgroundColor(primaryColor)
                    binding.statusBarBackgroundView.setBackgroundColor(primaryDarkColor)
                    binding.tabLayout.visibility = View.VISIBLE
                    binding.appbarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                        height = CoordinatorLayout.LayoutParams.WRAP_CONTENT
                    }
                }
            }
        }

        // Overlay badge on diners tab showing how many diners are on the bill
        billSplitViewModel.dinerCount.observe(viewLifecycleOwner) {
            binding.tabLayout.getTabAt(FRAG_DINERS)?.apply {
                if(it > 0) {
                    orCreateBadge.number = it
                } else {
                    removeBadge()
                }
            }
        }

        // Overlay badge on items tab showing how many items are on the bill
        billSplitViewModel.itemCount.observe(viewLifecycleOwner) {
            binding.tabLayout.getTabAt(FRAG_ITEMS)?.apply {
                if(it > 0) {
                    orCreateBadge.number = it
                } else {
                    removeBadge()
                }
            }
        }

        billSplitViewModel.fabDrawableId.observe(viewLifecycleOwner) { drawableId ->
            Log.e("drawableId", ""+drawableId)

            when {
                (drawableId != fabActiveDrawableId) -> {
                    Log.e("mismatch", "s")
                    if (binding.fab.isOrWillBeShown) {
                        binding.fab.hide(object: FloatingActionButton.OnVisibilityChangedListener() {
                            override fun onHidden(fab: FloatingActionButton) {
                                super.onHidden(fab)
                                fabActiveDrawableId = billSplitViewModel.fabDrawableId.value?.also {
                                    binding.fab.setImageResource(it)
                                    binding.fab.show()
                                }
                            }
                        })
                    } else if (binding.fab.visibility != View.VISIBLE) {
                        fabActiveDrawableId = billSplitViewModel.fabDrawableId.value?.also {
                            binding.fab.setImageResource(it)
                            binding.fab.show()
                        }
                    }
                }
                (drawableId != null) -> {
                    Log.e("match", "non null")
                    // Set image resource without animation (this is used during return navigation)
                    binding.fab.setImageResource(drawableId)
                    binding.fab.visibility = View.VISIBLE
                }
                else -> {
                    Log.e("match", "null")
                    // Hide without animation (this is used during return navigation)
                    binding.fab.visibility = View.INVISIBLE
                }
            }
        }

        billSplitViewModel.showProcessPaymentsButton.observe(viewLifecycleOwner) {
            if (it) {
                binding.paymentFab.showWithAnimation()
            } else {
                binding.paymentFab.hideWithAnimation()
            }
        }

        binding.fab.setOnClickListener {
            when(binding.viewPager.currentItem) {
                FRAG_DINERS -> {
                    // Show address book for contact selection
                    (requireActivity() as MainActivity).storeViewAsBitmap(requireView())
                    findNavController().navigate(BillSplitFragmentDirections.openAddressbook(
                        CircularRevealTransition.OriginParams(binding.fab)))
                }
                FRAG_ITEMS -> {
                    // Show fragment for item entry
                    (requireActivity() as MainActivity).storeViewAsBitmap(requireView())
                    findNavController().navigate(BillSplitFragmentDirections.createNewItem(
                        editedItem = null,
                        CircularRevealTransition.OriginParams(binding.fab)))
                }
                FRAG_PAYMENTS -> {
                    // Send email receipt to diners
                    billSplitViewModel.requestSendEmailReceipt()
                }
            }
        }

        binding.paymentFab.setOnClickListener { billSplitViewModel.requestProcessPayments() }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        //Restore view pager state to the active fragment index
        binding.viewPager.setCurrentItem(billSplitViewModel.activeFragmentIndex.value, false)
    }

    override fun onResume() {
        super.onResume()

        // Disable all surrogate views used for simulated shared element return transitions
        binding.newDinerSharedElement.cardBackground.visibility = View.GONE
        binding.newItemSharedElement.cardBackground.visibility = View.GONE
        newDinerIdForTransition = null
        newItemIdForTransition = null
    }

    private fun setupToolbarMenu() {
        binding.toolbar.inflateMenu(R.menu.toolbar_billsplit)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.new_bill -> {
                    billSplitViewModel.createNewBill()
                    true
                }
                R.id.clear_diners -> {
                    //TODO
                    true
                }
                else -> { false }
            }
        }
    }

    fun startNewDinerReturnTransition(viewBinding: FragDinersListitemBinding, newDiner: Diner, dinerSubtotal: String) {
        binding.newDinerSharedElement.apply {

            contactIcon.setContact(newDiner.asContact(), false)
            name.text = newDiner.name

            if(newDiner.itemIds.isEmpty()) {
                message.text = resources.getString(R.string.diners_zero_items)
            } else {
                message.text = resources.getQuantityString(
                    R.plurals.diners_num_items_with_subtotal,
                    newDiner.itemIds.size,
                    newDiner.itemIds.size,
                    dinerSubtotal)
            }

            cardBackground.apply {
                transitionName = "new_diner" + newDiner.id

                (layoutParams as CoordinatorLayout.LayoutParams).also {
                    it.topMargin = viewBinding.cardBackground.top + binding.appbarLayout.height
                }

                visibility = View.VISIBLE
            }
        }

        sharedElementEnterTransition = CardViewExpandTransition(
            binding.newDinerSharedElement.cardBackground.transitionName,
            binding.newDinerSharedElement.cardContent.id,
            false)
            .setOnTransitionStartCallback { transition: Transition, _: ViewGroup, startView: View, _: View ->
                // Fade out content of the ContactEntryFragment
                startView.findViewById<CoordinatorLayout>(R.id.coordinator_layout)?.apply {
                    this.animate().setDuration(transition.duration).setUpdateListener { animator ->
                        this.alpha = 1f - progressWindow(
                            AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction),
                            0f,
                            0.4f)
                    }.start()
                }
            }

        startPostponedEnterTransition()
    }

    fun startNewItemReturnTransition(viewBinding: FragItemsListitemBinding, newItem: Item) {
        binding.newItemSharedElement.apply {

            name.text = newItem.name

            itemPrice.text = NumberFormat.getCurrencyInstance().format(newItem.price)

            dinerIcons.removeAllViews()

            when(newItem.diners.size) {
                0 -> {
                    dinerSummary.setText(R.string.items_no_diners_warning)
                    dinerSummary.setTextColor(
                        TypedValue().also {
                            requireContext().theme.resolveAttribute(R.attr.colorPrimary, it, true)
                        }.data
                    )
                }
                billSplitViewModel.dinerCount.value -> {
                    dinerSummary.setText(R.string.items_shared_by_everyone)
                }
                else -> {
                    dinerSummary.text = ""
                    newItem.diners.forEach { diner ->
                        val icon = ContactIcon(requireContext())
                        icon.setSelectable(false)

                        val dim = (24 * requireContext().resources.displayMetrics.density).toInt()
                        val params = LinearLayout.LayoutParams(dim, dim)
                        params.marginEnd = (2 * requireContext().resources.displayMetrics.density).toInt()
                        icon.layoutParams = params

                        icon.setContact(diner.asContact(), false)
                        dinerIcons.addView(icon)
                    }
                }
            }

            cardBackground.apply {
                transitionName = "new_item" + newItem.id

                (layoutParams as CoordinatorLayout.LayoutParams).also {
                    it.topMargin = viewBinding.cardBackground.top + binding.appbarLayout.height
                }

                visibility = View.VISIBLE
            }
        }

        sharedElementEnterTransition = CardViewExpandTransition(
            binding.newItemSharedElement.cardBackground.transitionName,
            binding.newItemSharedElement.cardContent.id,
            false)
            .setOnTransitionStartCallback { transition: Transition, _: ViewGroup, startView: View, _: View ->
                // Remove covered fragment from ItemEntryFragment at start of transition
                startView.findViewById<ImageView>(R.id.inner_covered_fragment)?.apply {
                    this.visibility = View.GONE
                }

                // Fade out content of the ItemEntryFragment
                startView.findViewById<CoordinatorLayout>(R.id.coordinator_layout)?.apply {
                    this.animate().setDuration(transition.duration).setUpdateListener { animator ->
                        this.alpha = 1f - progressWindow(
                            AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction),
                            0f,
                            0.4f)
                    }.start()
                }
            }

        startPostponedEnterTransition()
    }
}