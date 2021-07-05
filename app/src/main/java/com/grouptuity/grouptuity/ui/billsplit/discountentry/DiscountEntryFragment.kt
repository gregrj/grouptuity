package com.grouptuity.grouptuity.ui.billsplit.discountentry

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.transition.Transition
import androidx.transition.TransitionValues
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.databinding.*
import com.grouptuity.grouptuity.ui.custom.transitions.*
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import com.grouptuity.grouptuity.ui.custom.views.TabLayoutMediator
import com.grouptuity.grouptuity.ui.custom.views.slideUpFAB
import java.text.NumberFormat

// TODO handle inset changes
// TODO update item prices during selection on discount properties


class DiscountEntryFragment: Fragment(), Revealable by RevealableImpl() {
    private val args: DiscountEntryFragmentArgs by navArgs()
    private var binding by setNullOnDestroy<FragDiscountEntryBinding>()
    private lateinit var discountEntryViewModel: DiscountEntryViewModel
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var toolbarInTertiaryState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        discountEntryViewModel = ViewModelProvider(requireActivity()).get(DiscountEntryViewModel::class.java).also {
            it.initializeForDiscount(args.editedDiscount)
        }
        binding = FragDiscountEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Intercept user interactions while while fragment transitions and animations are running
        binding.rootLayout.attachLock(discountEntryViewModel.isInputLocked)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (discountEntryViewModel.handleOnBackPressed() != null)
                    closeFragment()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        //Reset state and setup transitions
        postponeEnterTransition()
        discountEntryViewModel.startTransitionEvent.observe(viewLifecycleOwner) {
            it.consume()?.also { requireView().doOnPreDraw { startPostponedEnterTransition() } }
        }
        if(args.editedDiscount == null) {
            if(args.originParams == null) {
                // New discount entry starting from tax & tip fragment
                setupEnterTransitionNewFromTaxTip()
            } else {
                // New discount entry starting from discount fragment
                binding.innerCoveredFragment.setImageBitmap(coveredFragmentBitmap)
                binding.innerCoveredFragment.visibility = View.VISIBLE

                enterTransition = CircularRevealTransition(
                    binding.fadeView,
                    binding.revealedLayout,
                    args.originParams as CircularRevealTransition.OriginParams,
                    resources.getInteger(R.integer.frag_transition_duration).toLong(), true)
                    .addListener(object: Transition.TransitionListener{
                        override fun onTransitionStart(transition: Transition) { discountEntryViewModel.notifyTransitionStarted() }
                        override fun onTransitionPause(transition: Transition) { }
                        override fun onTransitionResume(transition: Transition) { }
                        override fun onTransitionCancel(transition: Transition) { }
                        override fun onTransitionEnd(transition: Transition) { discountEntryViewModel.notifyTransitionFinished() }
                    })
            }
        } else {
            // Editing existing discount
            binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)
            binding.fadeView.visibility = View.GONE
            binding.container.transitionName = "container" + (discountEntryViewModel.loadedDiscount.value?.id ?: "")

            sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.revealedLayout.id, true)
                .setOnTransitionProgressCallback { _: Transition, sceneRoot: ViewGroup, _: View, animator: ValueAnimator ->
                    sceneRoot.findViewById<FrameLayout>(R.id.revealed_layout)?.apply {
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
                        this.alpha = progress
                    }
                }
                .setOnTransitionStartCallback { _, _, _, _ -> discountEntryViewModel.notifyTransitionStarted() }
                .setOnTransitionEndCallback { _, _, _, _ -> discountEntryViewModel.notifyTransitionFinished() }
        }


        setupToolbar()

        setupMainTabs()

        setupCostCalculator()
        setupPriceCalculator()

        setupFAB()

        binding.pricePlaceholder.text = NumberFormat.getCurrencyInstance().apply { this.minimumFractionDigits = 0 }.format(0)

        discountEntryViewModel.showUnsavedValidEditsAlertEvent.observe(viewLifecycleOwner) { it.consume()?.apply { showUnsavedValidEditsAlertEvent() } }
        discountEntryViewModel.showUnsavedInvalidEditsAlertEvent.observe(viewLifecycleOwner) { it.consume()?.apply { showUnsavedInvalidEditsAlertEvent() } }
        discountEntryViewModel.showIncompleteReimbursementAlertEvent.observe(viewLifecycleOwner) { it.consume()?.apply { showReimbursementNoSelectionsAlert() } }
    }

    override fun onResume() {
        super.onResume()
        binding.fadeView.visibility = View.GONE

        // Reset UI input/output locks leftover from aborted transitions/animations
        discountEntryViewModel.unFreezeOutput()
        discountEntryViewModel.unLockInput()
    }

    private fun trySavingDiscount() {
        when(discountEntryViewModel.saveDiscount()) {
            DiscountEntryViewModel.INVALID_PRICE -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_invalid_price, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.MISSING_ITEMS -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_missing_items, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.MISSING_RECIPIENTS -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_missing_recipients, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.INVALID_COST -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_invalid_cost, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.MISSING_PURCHASERS -> Snackbar.make(binding.coordinatorLayout, R.string.discountentry_alert_missing_purchasers, Snackbar.LENGTH_SHORT).show()
            DiscountEntryViewModel.DISCOUNT_SAVED -> closeFragment()
        }
    }

    private fun closeFragment() {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

        // Prevent live updates to UI during return transition
        discountEntryViewModel.freezeOutput()

        when {
            discountEntryViewModel.loadedDiscount.value == null -> {
                // New discount was not completed
                if (args.originParams == null) {
                    // Discount entry was started from TaxTipFragment
                    setupReturnTransitionToTaxTip(requireView(), false)
                } else {
                    // Discount entry was started from DiscountFragment so close by shrinking into the FAB
                    returnTransition = CircularRevealTransition(
                        binding.fadeView,
                        binding.revealedLayout,
                        (args.originParams as CircularRevealTransition.OriginParams).withInsetsOn(binding.fab),
                        resources.getInteger(R.integer.frag_transition_duration).toLong(),
                        false)
                }
            }
            args.editedDiscount != null -> {
                // Completed or canceled editing of existing discount so return to DiscountsFragment
                setupReturnTransitionToDiscounts(requireView())
            }
            args.originParams == null -> {
                // New discount is also the only one so return directly to the TaxTipFragment
                setupReturnTransitionToTaxTip(requireView(), true)
            }
            else -> {
                // Created new discount so return by collapsing into DiscountsFragment FAB
                returnTransition = CircularRevealTransition(
                    binding.fadeView,
                    binding.revealedLayout,
                    (args.originParams as CircularRevealTransition.OriginParams).withInsetsOn(binding.fab),
                    resources.getInteger(R.integer.frag_transition_duration).toLong(),
                    false)
            }
        }

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    private fun showUnsavedValidEditsAlertEvent() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.discountentry_alert_abort_unsaved_valid_edits_title))
            .setMessage(resources.getString(R.string.discountentry_alert_abort_unsaved_valid_edits_message))
            .setCancelable(true)
            .setNeutralButton(resources.getString(R.string.cancel)) { _, _ -> }
            .setNegativeButton(resources.getString(R.string.discard)) { _, _ -> closeFragment() }
            .setPositiveButton(resources.getString(R.string.save)) { _, _ -> trySavingDiscount() }
            .show()
    }

    private fun showUnsavedInvalidEditsAlertEvent() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.discountentry_alert_abort_unsaved_invalid_edits_title))
            .setMessage(resources.getString(R.string.discountentry_alert_abort_unsaved_invalid_edits_message))
            .setCancelable(true)
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->  }
            .setPositiveButton(resources.getString(R.string.discard)) { _, _ -> closeFragment() }
            .show()
    }

    private fun showReimbursementNoSelectionsAlert() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.discountentry_alert_abort_reimbursement_no_selections_title))
            .setMessage(resources.getString(R.string.discountentry_alert_abort_reimbursement_no_selections_message))
            .setCancelable(false)
            .setNegativeButton(resources.getString(R.string.keep)) { _, _ ->
                binding.tabLayout.apply {
                    // Set tab position to ensure consistency of TabLayout position with ViewPager
                    selectTab(getTabAt(1))
                }
            }
            .setPositiveButton(resources.getString(R.string.discard)) { _, _ -> discountEntryViewModel.cancelCost() }
            .show()
    }

    private fun showReimbursementZeroValueAlert() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.discountentry_alert_abort_reimbursement_zero_value_title))
            .setMessage(resources.getString(R.string.discountentry_alert_abort_reimbursement_zero_value_message))
            .setCancelable(false)
            .setNegativeButton(resources.getString(R.string.edit)) { _, _ -> discountEntryViewModel.editCost() }
            .setPositiveButton(resources.getString(R.string.discard)) { _, _ -> discountEntryViewModel.cancelCost() }
            .show()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { discountEntryViewModel.handleOnBackPressed() }

        discountEntryViewModel.toolbarTitle.observe(viewLifecycleOwner) { binding.toolbar.title = it }
        discountEntryViewModel.toolbarInTertiaryState.observe(viewLifecycleOwner) { tertiaryBackground ->
            if(tertiaryBackground != toolbarInTertiaryState) {
                val secondaryColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSecondary, it, true) }.data
                val secondaryDarkColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSecondaryVariant, it, true) }.data
                val tertiaryColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorTertiary, it, true) }.data
                val tertiaryDarkColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorTertiaryVariant, it, true) }.data

                if(toolbarInTertiaryState) {
                    ValueAnimator.ofObject(ArgbEvaluator(), tertiaryColor, secondaryColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator ->
                            binding.toolbar.setBackgroundColor(animator.animatedValue as Int)
                            binding.tabLayout.setBackgroundColor(animator.animatedValue as Int)
                        }
                    }.start()

                    ValueAnimator.ofObject(ArgbEvaluator(), tertiaryDarkColor, secondaryDarkColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator -> binding.statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()
                } else {
                    ValueAnimator.ofObject(ArgbEvaluator(), secondaryColor, tertiaryColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator ->
                            binding.toolbar.setBackgroundColor(animator.animatedValue as Int)
                            binding.tabLayout.setBackgroundColor(animator.animatedValue as Int)
                        }
                    }.start()

                    ValueAnimator.ofObject(ArgbEvaluator(), secondaryDarkColor, tertiaryDarkColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator -> binding.statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()
                }

                toolbarInTertiaryState = tertiaryBackground
            }
        }
    }

    private fun setupMainTabs() {
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.overScrollMode = View.OVER_SCROLL_NEVER
        binding.viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when(state) {
                    ViewPager2.SCROLL_STATE_IDLE -> { discountEntryViewModel.topViewPagerInputLocked.value = false }
                    else -> { discountEntryViewModel.topViewPagerInputLocked.value = true }
                }
            }
        })
        binding.viewPager.adapter = object: FragmentStateAdapter(this) {
            var fragments = mutableListOf(PropertiesFragment(), Fragment())

            init {
                discountEntryViewModel.loadReimbursementFragmentEvent.observe(viewLifecycleOwner) {
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
                    if(discountEntryViewModel.isHandlingReimbursements.value == true &&
                        discountEntryViewModel.reimburseeSelections.value.isNullOrEmpty()) {
                        showReimbursementNoSelectionsAlert()
                        false
                    } else {
                        discountEntryViewModel.switchToDiscountProperties()
                        true
                    }
                }
                1 -> {
                    discountEntryViewModel.switchToReimbursements()
                    true
                }
                else -> { false /* Only two tabs */ }
            }
        }
        tabLayoutMediator.attach()

        discountEntryViewModel.isHandlingReimbursements.observe(viewLifecycleOwner, {
            binding.viewPager.currentItem = if(it) 1 else 0 })

        discountEntryViewModel.tabsVisible.observe(viewLifecycleOwner, {
            binding.tabLayout.visibility = if(it) View.VISIBLE else View.GONE
        })
    }

    private fun setupCostCalculator() {
        val costBottomSheetBehavior = BottomSheetBehavior.from(binding.costNumberPad.container)
        costBottomSheetBehavior.isDraggable = false
        costBottomSheetBehavior.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback(){
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                /* HACK: Disable input lock when sheet is nearly settled. State change updates have
                         an apparent delay that blocks resumption of user input for too long after
                         the sheet has visually settled. */
                if(slideOffset <= 0.05f) {
                    discountEntryViewModel.costNumberPadInputLocked.value = false
                } else if(slideOffset >= 0.95) {
                    discountEntryViewModel.costNumberPadInputLocked.value = false
                }
            }
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> { discountEntryViewModel.costNumberPadInputLocked.value = false }
                    BottomSheetBehavior.STATE_COLLAPSED -> { discountEntryViewModel.costNumberPadInputLocked.value = false }
                    else -> { discountEntryViewModel.costNumberPadInputLocked.value = true }
                }
            }
        })

        binding.costNumberPad.basisToggleButtons.visibility = View.GONE

        discountEntryViewModel.costNumberPadVisible.observe(viewLifecycleOwner, {
            BottomSheetBehavior.from(binding.costNumberPad.container).state = if(it) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
        })
        discountEntryViewModel.costZeroButtonEnabled.observe(viewLifecycleOwner) { binding.costNumberPad.button0.isEnabled = it }
        discountEntryViewModel.costNonZeroButtonsEnabled.observe(viewLifecycleOwner) {
            binding.costNumberPad.button1.isEnabled = it
            binding.costNumberPad.button2.isEnabled = it
            binding.costNumberPad.button3.isEnabled = it
            binding.costNumberPad.button4.isEnabled = it
            binding.costNumberPad.button5.isEnabled = it
            binding.costNumberPad.button6.isEnabled = it
            binding.costNumberPad.button7.isEnabled = it
            binding.costNumberPad.button8.isEnabled = it
            binding.costNumberPad.button9.isEnabled = it
        }
        discountEntryViewModel.costDecimalButtonEnabled.observe(viewLifecycleOwner) { binding.costNumberPad.buttonDecimal.isEnabled = it }
        discountEntryViewModel.costAcceptButtonEnabled.observe(viewLifecycleOwner) { binding.costNumberPad.buttonAccept.isEnabled = it }

        discountEntryViewModel.costAcceptEvents.observe(viewLifecycleOwner) {
            it.consume()?.apply {
                if (!this.contains(Regex("[1-9]"))) {
                    showReimbursementZeroValueAlert()
                }
            }
        }

        binding.costNumberPad.buttonDecimal.setOnClickListener { discountEntryViewModel.addDecimalToCost() }
        binding.costNumberPad.button0.setOnClickListener { discountEntryViewModel.addDigitToCost('0') }
        binding.costNumberPad.button1.setOnClickListener { discountEntryViewModel.addDigitToCost('1') }
        binding.costNumberPad.button2.setOnClickListener { discountEntryViewModel.addDigitToCost('2') }
        binding.costNumberPad.button3.setOnClickListener { discountEntryViewModel.addDigitToCost('3') }
        binding.costNumberPad.button4.setOnClickListener { discountEntryViewModel.addDigitToCost('4') }
        binding.costNumberPad.button5.setOnClickListener { discountEntryViewModel.addDigitToCost('5') }
        binding.costNumberPad.button6.setOnClickListener { discountEntryViewModel.addDigitToCost('6') }
        binding.costNumberPad.button7.setOnClickListener { discountEntryViewModel.addDigitToCost('7') }
        binding.costNumberPad.button8.setOnClickListener { discountEntryViewModel.addDigitToCost('8') }
        binding.costNumberPad.button9.setOnClickListener { discountEntryViewModel.addDigitToCost('9') }
        binding.costNumberPad.buttonAccept.setOnClickListener { discountEntryViewModel.acceptCost() }
    }

    private fun setupPriceCalculator() {
        val priceBottomSheetBehavior = BottomSheetBehavior.from(binding.priceNumberPad.container)
        priceBottomSheetBehavior.isDraggable = false
        priceBottomSheetBehavior.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback(){
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                /* HACK: Disable input lock when sheet is nearly settled. State change updates have
                         an apparent delay that blocks resumption of user input for too long after
                         the sheet has visually settled. */
                if(slideOffset <= 0.05f) {
                    discountEntryViewModel.priceNumberPadInputLocked.value = false
                } else if(slideOffset >= 0.95) {
                    discountEntryViewModel.priceNumberPadInputLocked.value = false
                }
            }
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> { discountEntryViewModel.priceNumberPadInputLocked.value = false }
                    BottomSheetBehavior.STATE_COLLAPSED -> { discountEntryViewModel.priceNumberPadInputLocked.value = false }
                    else -> { discountEntryViewModel.priceNumberPadInputLocked.value = true }
                }
            }
        })

        discountEntryViewModel.priceNumberPadVisible.observe(viewLifecycleOwner, {
            BottomSheetBehavior.from(binding.priceNumberPad.container).state = if(it) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
        })
        discountEntryViewModel.isPriceInPercent.observe(viewLifecycleOwner) {
            binding.priceNumberPad.buttonPercent.isEnabled = !it
            binding.priceNumberPad.buttonCurrency.isEnabled = it
        }
        discountEntryViewModel.priceZeroButtonEnabled.observe(viewLifecycleOwner) { binding.priceNumberPad.button0.isEnabled = it }
        discountEntryViewModel.priceNonZeroButtonsEnabled.observe(viewLifecycleOwner) {
            binding.priceNumberPad.button1.isEnabled = it
            binding.priceNumberPad.button2.isEnabled = it
            binding.priceNumberPad.button3.isEnabled = it
            binding.priceNumberPad.button4.isEnabled = it
            binding.priceNumberPad.button5.isEnabled = it
            binding.priceNumberPad.button6.isEnabled = it
            binding.priceNumberPad.button7.isEnabled = it
            binding.priceNumberPad.button8.isEnabled = it
            binding.priceNumberPad.button9.isEnabled = it
        }
        discountEntryViewModel.priceDecimalButtonEnabled.observe(viewLifecycleOwner) { binding.priceNumberPad.buttonDecimal.isEnabled = it }
        discountEntryViewModel.priceAcceptButtonEnabled.observe(viewLifecycleOwner) { binding.priceNumberPad.buttonAccept.isEnabled = it }

        binding.priceNumberPad.buttonCurrency.setOnClickListener { discountEntryViewModel.switchPriceToCurrency() }
        binding.priceNumberPad.buttonPercent.setOnClickListener { discountEntryViewModel.switchPriceToPercent() }
        binding.priceNumberPad.buttonDecimal.setOnClickListener { discountEntryViewModel.addDecimalToPrice() }
        binding.priceNumberPad.button0.setOnClickListener { discountEntryViewModel.addDigitToPrice('0') }
        binding.priceNumberPad.button1.setOnClickListener { discountEntryViewModel.addDigitToPrice('1') }
        binding.priceNumberPad.button2.setOnClickListener { discountEntryViewModel.addDigitToPrice('2') }
        binding.priceNumberPad.button3.setOnClickListener { discountEntryViewModel.addDigitToPrice('3') }
        binding.priceNumberPad.button4.setOnClickListener { discountEntryViewModel.addDigitToPrice('4') }
        binding.priceNumberPad.button5.setOnClickListener { discountEntryViewModel.addDigitToPrice('5') }
        binding.priceNumberPad.button6.setOnClickListener { discountEntryViewModel.addDigitToPrice('6') }
        binding.priceNumberPad.button7.setOnClickListener { discountEntryViewModel.addDigitToPrice('7') }
        binding.priceNumberPad.button8.setOnClickListener { discountEntryViewModel.addDigitToPrice('8') }
        binding.priceNumberPad.button9.setOnClickListener { discountEntryViewModel.addDigitToPrice('9') }
        binding.priceNumberPad.buttonAccept.setOnClickListener { discountEntryViewModel.acceptPrice() }
    }

    private fun setupFAB() {
        var allowReshowFABOnHidden: Boolean
        discountEntryViewModel.fabIcon.observe(viewLifecycleOwner, {
            if(it == null) {
                allowReshowFABOnHidden = false
                binding.fab.hide(object: FloatingActionButton.OnVisibilityChangedListener() {
                    override fun onHidden(fab: FloatingActionButton) {
                        slideUpFAB(binding.fab)
                    }
                })
            } else {
                allowReshowFABOnHidden = true
                if(binding.fab.isOrWillBeShown) {
                    binding.fab.hide(object: FloatingActionButton.OnVisibilityChangedListener() {
                        override fun onHidden(fab: FloatingActionButton) {
                            binding.fab.setImageResource(it)
                            slideUpFAB(binding.fab)

                            if(allowReshowFABOnHidden)
                                binding.fab.show()
                        }
                    })
                } else {
                    binding.fab.setImageResource(it)
                    slideUpFAB(binding.fab)
                    binding.fab.show()
                }
            }
        })

        // If selections change and FAB should be showing, ensure FAB is not out of view
        discountEntryViewModel.dinerSelections.observe(viewLifecycleOwner, { if(it.isNotEmpty()) slideUpFAB(binding.fab) })
        discountEntryViewModel.itemSelections.observe(viewLifecycleOwner, { if(it.isNotEmpty()) slideUpFAB(binding.fab) })
        discountEntryViewModel.reimburseeSelections.observe(viewLifecycleOwner, { if(it.isNotEmpty()) slideUpFAB(binding.fab) })

        binding.fab.setOnClickListener {
            when(discountEntryViewModel.fabIcon.value) {
                R.drawable.ic_arrow_back -> discountEntryViewModel.switchToDiscountProperties()
                R.drawable.ic_arrow_forward -> trySavingDiscount()
            }
        }
    }

    private fun setupEnterTransitionNewFromTaxTip() {
        val propBottomInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_bottom_inset"
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_top_inset"
        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_corner_radius"

        binding.addDiscountButton.transitionName = "discountButtonTransitionName"
        binding.addDiscountButton.visibility = View.VISIBLE
        binding.editDiscountsButton.transitionName = null
        binding.editDiscountsButton.visibility = View.GONE
        binding.fadeView.visibility = View.GONE

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.revealedLayout.id, true).addElement(
            binding.addDiscountButton.transitionName,
            object: CardViewExpandTransition.Element{
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
        ).setOnTransitionStartCallback { _, _, _, _ -> discountEntryViewModel.notifyTransitionStarted() }
            .setOnTransitionEndCallback { _, _, _, _ -> discountEntryViewModel.notifyTransitionFinished() }

        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)
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
        ).setOnTransitionStartCallback { _, _, _, _ -> discountEntryViewModel.notifyTransitionStarted() }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(view)
        }
    }

    private fun setupReturnTransitionToDiscounts(view: View) {
        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)
        binding.fadeView.visibility = View.GONE
        binding.container.transitionName = "container" + (discountEntryViewModel.loadedDiscount.value?.id ?: "")

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