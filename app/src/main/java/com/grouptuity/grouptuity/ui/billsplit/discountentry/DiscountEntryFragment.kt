package com.grouptuity.grouptuity.ui.billsplit.discountentry

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.transition.Transition
import androidx.transition.TransitionValues
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Discount
import com.grouptuity.grouptuity.databinding.*
import com.grouptuity.grouptuity.ui.util.UIFragment
import com.grouptuity.grouptuity.ui.util.transitions.*
import com.grouptuity.grouptuity.ui.util.views.*
import java.text.NumberFormat

// TODO handle screen orientation change (also on other fragments)


class DiscountEntryFragment: UIFragment<FragDiscountEntryBinding, DiscountEntryViewModel, Discount?, Discount?>() {
    private val args: DiscountEntryFragmentArgs by navArgs()
    private lateinit var tabLayoutMediator: TabLayoutMediator

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragDiscountEntryBinding.inflate(inflater, container, false)

    override fun createViewModel() = ViewModelProvider(requireActivity())[DiscountEntryViewModel::class.java]

    override fun getInitialInput(): Discount? = args.editedDiscount

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Reset state and setup transitions
        postponeEnterTransition()
        viewModel.startTransitionEvent.observe(viewLifecycleOwner) {
            it.consume()?.also { requireView().doOnPreDraw { startPostponedEnterTransition() } }
        }
        if(args.editedDiscount == null) {
            if(args.originParams == null) {
                // New discount entry starting from tax & tip fragment
                setupEnterTransitionNewFromTaxTip()
            } else {
                // New discount entry starting from discount fragment
                binding.innerCoveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
                binding.innerCoveredFragment.visibility = View.VISIBLE

                enterTransition = CircularRevealTransition(
                    binding.fadeView,
                    binding.revealedLayout,
                    args.originParams as CircularRevealTransition.OriginParams,
                    resources.getInteger(R.integer.frag_transition_duration).toLong(), true)
                    .addListener(object: Transition.TransitionListener{
                        override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                        override fun onTransitionPause(transition: Transition) { }
                        override fun onTransitionResume(transition: Transition) { }
                        override fun onTransitionCancel(transition: Transition) { }
                        override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                    })
            }
        } else {
            // Editing existing discount
            binding.fadeView.visibility = View.GONE
            binding.container.transitionName = "container" + (viewModel.loadedDiscount.value?.id ?: "")
            binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

            sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.revealedLayout.id, true)
                .setOnTransitionProgressCallback { _: Transition, sceneRoot: ViewGroup, _: View, animator: ValueAnimator ->
                    sceneRoot.findViewById<FrameLayout>(R.id.revealed_layout)?.apply {
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
                        this.alpha = progress
                    }
                }
                .setOnTransitionStartCallback { _, _, _, _ -> viewModel.notifyTransitionStarted() }
                .setOnTransitionEndCallback { _, _, _, _ -> viewModel.notifyTransitionFinished() }
        }

        setupToolbar()

        setupMainTabs()

        setupCollapsibleNumberPad(
            viewLifecycleOwner,
            viewModel.priceCalculator,
            binding.priceNumberPad,
            useValuePlaceholder = true,
            showBasisToggleButtons = false)

        setupCollapsibleNumberPad(viewLifecycleOwner,
            viewModel.costCalculator,
            binding.costNumberPad,
            useValuePlaceholder = true,
            showBasisToggleButtons = false)

        setupFAB()

        binding.pricePlaceholder.text = NumberFormat.getCurrencyInstance().apply { this.minimumFractionDigits = 0 }.format(0)

        viewModel.showUnsavedValidEditsAlertEvent.observe(viewLifecycleOwner) { it.consume()?.apply { showUnsavedValidEditsAlert() } }
        viewModel.showUnsavedInvalidEditsAlertEvent.observe(viewLifecycleOwner) { it.consume()?.apply { showUnsavedInvalidEditsAlert() } }
        viewModel.showIncompleteReimbursementAlertEvent.observe(viewLifecycleOwner) { it.consume()?.apply { showReimbursementNoSelectionsAlert() } }
    }

    override fun onResume() {
        super.onResume()
        binding.fadeView.visibility = View.GONE
    }

    private fun trySavingDiscount() {
        when(viewModel.saveDiscount()) {
            DiscountEntryViewModel.INVALID_PRICE -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_invalid_price, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.MISSING_ITEMS -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_missing_items, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.MISSING_RECIPIENTS -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_missing_recipients, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.INVALID_COST -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_invalid_cost, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.MISSING_PURCHASERS -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_missing_purchasers, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onFinish(output: Discount?) {
        if (args.editedDiscount == null) {
            // Creating a new discount
            if (args.originParams == null) {
                // Fragment was opened from TaxTipFragment so return to TaxTipFragment
                setupReturnTransitionToTaxTip(requireView(), output != null)
            } else {
                // Fragment was opened from DiscountsFragment so close by shrinking into the FAB
                returnTransition = CircularRevealTransition(
                    binding.fadeView,
                    binding.revealedLayout,
                    (args.originParams as CircularRevealTransition.OriginParams).withInsetsOn(binding.fab),
                    resources.getInteger(R.integer.frag_transition_duration).toLong(),
                    false)
            }
        } else {
            // Completed or canceled editing of existing discount so return to DiscountsFragment
            setupReturnTransitionToDiscounts(requireView())
        }

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    private fun showUnsavedValidEditsAlert() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.discountentry_alert_abort_unsaved_valid_edits_title))
            .setMessage(resources.getString(R.string.discountentry_alert_abort_unsaved_valid_edits_message))
            .setCancelable(true)
            .setNeutralButton(resources.getString(R.string.keep_editing)) { _, _ -> }
            .setNegativeButton(resources.getString(R.string.discard)) { _, _ -> viewModel.finishFragment(null) }
            .setPositiveButton(resources.getString(R.string.save)) { _, _ -> trySavingDiscount() }
            .show()
    }

    private fun showUnsavedInvalidEditsAlert() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.discountentry_alert_abort_unsaved_invalid_edits_title))
            .setMessage(resources.getString(R.string.discountentry_alert_abort_unsaved_invalid_edits_message))
            .setCancelable(true)
            .setNegativeButton(resources.getString(R.string.keep_editing)) { _, _ ->  }
            .setPositiveButton(resources.getString(R.string.discard)) { _, _ -> viewModel.finishFragment(null) }
            .show()
    }

    private fun showReimbursementNoSelectionsAlert() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.discountentry_alert_abort_reimbursement_no_selections_title))
            .setMessage(resources.getString(R.string.discountentry_alert_abort_reimbursement_no_selections_message))
            .setCancelable(false)
            .setNegativeButton(resources.getString(R.string.keep_editing)) { _, _ ->
                binding.tabLayout.apply {
                    // Set tab position to ensure consistency of TabLayout position with ViewPager
                    selectTab(getTabAt(1))
                }
            }
            .setPositiveButton(resources.getString(R.string.discard)) { _, _ -> viewModel.cancelCost() }
            .show()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { viewModel.handleOnBackPressed() }

        viewModel.toolbarTitle.observe(viewLifecycleOwner) { binding.toolbar.title = it }

        setupToolbarSecondaryTertiaryAnimation(
            requireContext(),
            viewLifecycleOwner,
            viewModel.uiInTertiaryState,
            binding.toolbar,
            binding.statusBarBackgroundView,
            extraView = binding.tabLayout)
    }

    private fun setupMainTabs() {
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.overScrollMode = View.OVER_SCROLL_NEVER
        binding.viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when(state) {
                    ViewPager2.SCROLL_STATE_IDLE -> { viewModel.topViewPagerInputLocked.value = false }
                    else -> { viewModel.topViewPagerInputLocked.value = true }
                }
            }
        })
        binding.viewPager.adapter = object: FragmentStateAdapter(this) {
            var fragments = mutableListOf(PropertiesFragment(), Fragment())

            init {
                viewModel.loadReimbursementFragmentEvent.observe(viewLifecycleOwner) {
                    it.consume()?.also {
                        if(fragments[1] !is ReimbursementFragment) {
                            fragments[1] = ReimbursementFragment()
                            notifyItemChanged(1)
                        }
                    }
                }
            }

            override fun getItemCount() = 2
            override fun getItemId(position: Int) = when(position) {
                0 -> 0L
                1 -> if(fragments[1] is ReimbursementFragment) 1L else 2L
                else -> RecyclerView.NO_ID
            }
            override fun containsItem(itemId: Long) = when(itemId) {
                0L -> true
                1L -> fragments[1] is ReimbursementFragment
                2L -> fragments[1] !is ReimbursementFragment
                else -> false
            }
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        tabLayoutMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager,
            object: TabLayoutMediator.TabConfigurationStrategy {
                override fun onConfigureTab(tab: TabLayout.Tab, position: Int) {
                    tab.text = when (position) {
                        0 -> getString(R.string.discountentry_tabs_discount)
                        else -> getString(R.string.discountentry_tabs_reimbursement)
                    }
                }
            }
        )
        tabLayoutMediator.setOnTabSelectedReviewer { tab ->
            when(tab.position) {
                0 -> {
                    if(viewModel.isHandlingReimbursements.value == true &&
                        viewModel.reimburseeSelections.value.isNullOrEmpty()) {
                        showReimbursementNoSelectionsAlert()
                        false
                    } else {
                        viewModel.switchToDiscountProperties()
                        true
                    }
                }
                1 -> {
                    viewModel.switchToReimbursements()
                    true
                }
                else -> { false /* Only two tabs */ }
            }
        }
        tabLayoutMediator.attach()

        viewModel.isHandlingReimbursements.observe(viewLifecycleOwner, {
            binding.viewPager.currentItem = if(it) 1 else 0 })

        viewModel.tabsVisible.observe(viewLifecycleOwner, {
            binding.tabLayout.visibility = if(it) View.VISIBLE else View.GONE
        })
    }

    private fun setupFAB() {
        var allowReshowFABOnHidden: Boolean
        viewModel.fabIcon.observe(viewLifecycleOwner, {
            if(it == null) {
                allowReshowFABOnHidden = false
                binding.fab.hide(object: FloatingActionButton.OnVisibilityChangedListener() {
                    override fun onHidden(fab: FloatingActionButton) {
                        binding.fab.slideUp()
                    }
                })
            } else {
                allowReshowFABOnHidden = true
                if(binding.fab.isOrWillBeShown) {
                    binding.fab.hide(object: FloatingActionButton.OnVisibilityChangedListener() {
                        override fun onHidden(fab: FloatingActionButton) {
                            binding.fab.setImageResource(it)
                            binding.fab.slideUp()

                            if(allowReshowFABOnHidden)
                                binding.fab.show()
                        }
                    })
                } else {
                    binding.fab.setImageResource(it)
                    binding.fab.slideUp()
                    binding.fab.show()
                }
            }
        })

        // If selections change and FAB should be showing, ensure FAB is not out of view
        viewModel.dinerSelections.observe(viewLifecycleOwner, { if(it.isNotEmpty()) binding.fab.slideUp() })
        viewModel.itemSelections.observe(viewLifecycleOwner, { if(it.isNotEmpty()) binding.fab.slideUp() })
        viewModel.reimburseeSelections.observe(viewLifecycleOwner, { if(it.isNotEmpty()) binding.fab.slideUp() })

        binding.fab.setOnClickListener {
            when(viewModel.fabIcon.value) {
                R.drawable.ic_arrow_back_light -> viewModel.switchToDiscountProperties()
                R.drawable.ic_arrow_forward -> trySavingDiscount()
            }
        }
    }

    private fun setupEnterTransitionNewFromTaxTip() {
        val propBottomInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_bottom_inset"
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_top_inset"
        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_corner_radius"

        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
        binding.addDiscountButton.transitionName = "discountButtonTransitionName"
        binding.addDiscountButton.visibility = View.VISIBLE
        binding.editDiscountsButton.transitionName = null
        binding.editDiscountsButton.visibility = View.GONE
        binding.fadeView.visibility = View.GONE

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.revealedLayout.id, true).addElement(
            binding.addDiscountButton.transitionName,
            object: CardViewExpandTransition.Element {
                override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                    val discountButton = transitionValues.view as com.google.android.material.button.MaterialButton
                    transitionValues.values[propTopInset] = discountButton.insetTop
                    transitionValues.values[propBottomInset] = discountButton.insetBottom
                    transitionValues.values[propCornerRadius] = resources.getDimension(R.dimen.material_card_corner_radius).toInt()
                }

                override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                    val discountButton = transitionValues.view as com.google.android.material.button.MaterialButton
                    transitionValues.values[propTopInset] = discountButton.insetTop
                    transitionValues.values[propBottomInset] = discountButton.insetBottom
                    transitionValues.values[propCornerRadius] = 0
                }

                override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.doOnStart {
                        // Button is smaller than the container initially so start with the card background hidden
                        binding.container.setCardBackgroundColor(Color.TRANSPARENT)

                        // Container content will be hidden initially and fade in later
                        binding.coordinatorLayout.alpha = 0f
                    }

                    val button = endValues.view as com.google.android.material.button.MaterialButton

                    val surfaceColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSurface, it, true) }.data

                    animator.addUpdateListener {
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                        // Adjust shape of button to match background container
                        val zeroTo20Progress = (5f*progress).coerceIn(0f, 1f)
                        button.insetTop = ((startValues.values[propTopInset] as Int) +
                                ((endValues.values[propTopInset] as Int) - (startValues.values[propTopInset] as Int)) * zeroTo20Progress).toInt()
                        button.insetBottom = ((startValues.values[propBottomInset] as Int) +
                                ((endValues.values[propBottomInset] as Int) - (startValues.values[propBottomInset] as Int)) * zeroTo20Progress).toInt()
                        button.cornerRadius = ((startValues.values[propCornerRadius] as Int) +
                                ((endValues.values[propCornerRadius] as Int) - (startValues.values[propCornerRadius] as Int)) * zeroTo20Progress).toInt()

                        // Restore card background once the button starts fading out
                        if(progress >= 0.2) {
                            binding.container.setCardBackgroundColor(surfaceColor)

                            val twentyTo80Progress = (1.666667f*progress - 0.33333f).coerceIn(0f, 1f)
                            button.alpha = 1f - twentyTo80Progress
                            binding.coordinatorLayout.alpha = twentyTo80Progress
                        }
                    }

                    return animator
                }
            }
        ).setOnTransitionStartCallback { _, _, _, _ -> viewModel.notifyTransitionStarted() }
            .setOnTransitionEndCallback { _, _, _, _ -> viewModel.notifyTransitionFinished() }
    }

    private fun setupReturnTransitionToTaxTip(view: View, withNewDiscount: Boolean) {
        val propBottomInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_bottom_inset"
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_top_inset"
        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_corner_radius"

        val button = if(withNewDiscount) {
            binding.addDiscountButton.transitionName = null
            binding.addDiscountButton.visibility = View.GONE
            binding.editDiscountsButton.transitionName = "discountButtonTransitionName"
            binding.editDiscountsButton.visibility = View.VISIBLE
            binding.editDiscountsButton
        } else {
            binding.addDiscountButton.transitionName = "discountButtonTransitionName"
            binding.addDiscountButton.visibility = View.VISIBLE
            binding.editDiscountsButton.transitionName = null
            binding.editDiscountsButton.visibility = View.GONE
            binding.addDiscountButton
        }

        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.revealedLayout.id, false).addElement(
            button.transitionName,
            object: CardViewExpandTransition.Element{
                override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                    val discountButton = transitionValues.view as com.google.android.material.button.MaterialButton
                    transitionValues.values[propTopInset] = discountButton.insetTop
                    transitionValues.values[propBottomInset] = discountButton.insetBottom
                    transitionValues.values[propCornerRadius] = 0
                }

                override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                    val discountButton = transitionValues.view as com.google.android.material.button.MaterialButton
                    transitionValues.values[propTopInset] = discountButton.insetTop
                    transitionValues.values[propBottomInset] = discountButton.insetBottom
                    transitionValues.values[propCornerRadius] = resources.getDimension(R.dimen.material_card_corner_radius).toInt()
                }

                override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.doOnStart {
                        val surfaceColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSurface, it, true) }.data
                        binding.container.setCardBackgroundColor(surfaceColor)
                        binding.coordinatorLayout.alpha = 1f
                        binding.fab.hide()
                    }

                    animator.addUpdateListener {
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                        // Fade out discount button after its shape matches background container
                        val twentyTo80Progress = (1.666667f*progress - 0.33333f).coerceIn(0f, 1f)
                        button.alpha = twentyTo80Progress
                        binding.coordinatorLayout.alpha = 1f - twentyTo80Progress

                        if(progress >= 0.8) {
                            binding.container.setCardBackgroundColor(Color.TRANSPARENT)
                            binding.coordinatorLayout.visibility = View.GONE
                        }

                        // Adjust shape of discount button to final state
                        val eightyTo100Progress = (5f*progress - 4f).coerceIn(0f, 1f)
                        button.insetTop = ((startValues.values[propTopInset] as Int) +
                                ((endValues.values[propTopInset] as Int) - (startValues.values[propTopInset] as Int)) * eightyTo100Progress).toInt()
                        button.insetBottom = ((startValues.values[propBottomInset] as Int) +
                                ((endValues.values[propBottomInset] as Int) - (startValues.values[propBottomInset] as Int)) * eightyTo100Progress).toInt()
                        button.cornerRadius = ((startValues.values[propCornerRadius] as Int) +
                                ((endValues.values[propCornerRadius] as Int) - (startValues.values[propCornerRadius] as Int)) * eightyTo100Progress).toInt()
                    }

                    return animator
                }
            }
        ).setOnTransitionStartCallback { _, _, _, _ -> viewModel.notifyTransitionStarted() }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(view)
        }
    }

    private fun setupReturnTransitionToDiscounts(view: View) {
        binding.fadeView.visibility = View.GONE
        binding.container.transitionName = "container" + (viewModel.loadedDiscount.value?.id ?: "")

        //TODO move to attemptClose  to handle inset changes
        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.revealedLayout.id, false)
            .setOnTransitionProgressCallback { _: Transition, _: ViewGroup, _: View, animator: ValueAnimator ->
                binding.revealedLayout.apply {
                    val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
                    this.alpha = 1f - progress
                }
            }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(view)
        }
    }
}