package com.grouptuity.grouptuity.ui.billsplit.debtentry

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.transition.Transition
import androidx.transition.TransitionValues
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.databinding.FragDebtentryBinding
import com.grouptuity.grouptuity.databinding.FragDebtentryListdinerBinding
import com.grouptuity.grouptuity.ui.util.UIFragment
import com.grouptuity.grouptuity.ui.util.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.util.views.setupCalculatorDisplay
import com.grouptuity.grouptuity.ui.util.views.setupCollapsibleNumberPad
import com.grouptuity.grouptuity.ui.util.views.slideUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO ripple effect on long press clear
// TODO disable interactions when transitions are running
// TODO finish item name editor -> issues with back press and keyboard dismiss; block calculator inputs
// TODO check for substantial edits and show alert on dismiss

// TODO Warn if launching diner not part of debt

class DebtEntryFragment: UIFragment<FragDebtentryBinding, DebtEntryViewModel, Diner?, Boolean>() {
    private val args: DebtEntryFragmentArgs by navArgs()
    private lateinit var recyclerAdapter: DebtEntryDinerSelectionRecyclerViewAdapter
    private var toolbarInTertiaryState = false

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragDebtentryBinding.inflate(inflater, container, false)

    override fun createViewModel(): DebtEntryViewModel =
        ViewModelProvider(requireActivity())[DebtEntryViewModel::class.java]

    override fun getInitialInput(): Diner? = args.loadedDiner

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* Need a way to discriminate between user dismissal of keyboard and system dismissal from starting voice search
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            // Close the SearchView if no item name exists when keyboard is dismissed except if
            // a voice search is running
            if(debtEntryViewModel.editingName.value && !insets.isVisible(WindowInsetsCompat.Type.ime())) {
                closeSearchView()
            }
            insets
        }
        */

        setupEnterTransition()

        setupToolbar()

        setupCollapsibleNumberPad(
            viewLifecycleOwner,
            viewModel.calculator,
            binding.numberPad,
            useValuePlaceholder = false,
            showBasisToggleButtons = false)

        setupCalculatorDisplay(
            viewLifecycleOwner,
            viewModel.calculator,
            binding.priceTextview,
            binding.buttonEdit,
            binding.buttonBackspace)

        // Reset bottom sheet number pad
        BottomSheetBehavior.from(binding.numberPad.container).state = BottomSheetBehavior.STATE_EXPANDED

        setupDinerList()

        binding.fab.setOnClickListener {
            if(viewModel.areDebtInputsValid.value == true) {
                if (viewModel.isLaunchingDinerPresent) {
                    viewModel.addDebtToBill()
                } else {
                    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
                        .setTitle(resources.getString(R.string.debtentry_launching_diner_missing_alert, viewModel.launchingDiner!!.name))
                        .setCancelable(true)
                        .setNegativeButton(resources.getString(R.string.cancel)) { _, _ -> }
                        .setPositiveButton(resources.getString(R.string.proceed)) { _, _ -> viewModel.addDebtToBill() }
                        .show()
                }
            }
        }

        viewModel.areDebtInputsValid.observe(viewLifecycleOwner, {
            if(it) {
                binding.fab.show()
                binding.fab.slideUp()
            } else {
                binding.fab.hide()
            }
        })
    }

    override fun onFinish(output: Boolean) {
        setupExitTransition()

        // Flag to indicate to DinerDetailsFragment that debt was created
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            "DebtEntryNavBack",
            output
        )

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.toolbar_debtentry)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { viewModel.handleOnBackPressed() }

        viewModel.toolBarState.observe(viewLifecycleOwner) { toolBarState ->
            binding.toolbar.title = toolBarState.title

            if(toolBarState.navIconVisible) {
                binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
            } else {
                binding.toolbar.navigationIcon = null
            }

            binding.toolbar.menu.setGroupVisible(R.id.group_editor, toolBarState.nameEditVisible)

            if(toolBarState.tertiaryBackground != toolbarInTertiaryState) {
                val secondaryColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSecondary, it, true) }.data
                val secondaryDarkColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSecondaryVariant, it, true) }.data
                val tertiaryColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorTertiary, it, true) }.data
                val tertiaryDarkColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorTertiaryVariant, it, true) }.data

                if(toolbarInTertiaryState) {
                    ValueAnimator.ofObject(ArgbEvaluator(), tertiaryColor, secondaryColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator -> binding.toolbar.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()

                    ValueAnimator.ofObject(ArgbEvaluator(), tertiaryDarkColor, secondaryDarkColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator -> binding.statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()
                } else {
                    ValueAnimator.ofObject(ArgbEvaluator(), secondaryColor, tertiaryColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator -> binding.toolbar.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()

                    ValueAnimator.ofObject(ArgbEvaluator(), secondaryDarkColor, tertiaryDarkColor).apply {
                        duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                        addUpdateListener { animator -> binding.statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()
                }

                toolbarInTertiaryState = toolBarState.tertiaryBackground
            }
        }

        val searchView = binding.toolbar.menu.findItem(R.id.edit_debt_name).actionView as SearchView
        searchView.setSearchableInfo((requireActivity().getSystemService(Context.SEARCH_SERVICE) as SearchManager).getSearchableInfo(requireActivity().componentName))
        searchView.queryHint = resources.getString(R.string.debtentry_toolbar_enter_name)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.isSubmitButtonEnabled = true

        searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)?.apply { this.setImageResource(R.drawable.ic_edit) }
        searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_go_btn)?.apply { this.setImageResource(R.drawable.ic_done) }

        searchView.setOnSearchClickListener {
            searchView.setQuery(
                if(viewModel.hasDebtNameInput) { viewModel.debtName.value } else { "" },
                false
            )
            viewModel.startNameEdit()
        }

        searchView.setOnCloseListener {
            viewModel.stopNameEdit()
            false
        }

        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextChange(string: String?): Boolean { return true }
            override fun onQueryTextSubmit(string: String?): Boolean {
                if(string.isNullOrBlank()){
                    // function does not get called if string is blank
                } else {
                    viewModel.acceptDebtNameInput(string.trim())
                    closeSearchView()
                    requireActivity().invalidateOptionsMenu()
                }
                return true
            }
        })

        var activeAnimation: ValueAnimator? = null
        viewModel.editNameShimVisible.observe(viewLifecycleOwner) {
            if(it) {
                binding.editNameScrim.setOnTouchListener { _, _ -> true }
            } else {
                binding.editNameScrim.setOnTouchListener(null)
                closeSearchView() // Needed to iconify the SearchView if triggered by back press
            }

            activeAnimation?.cancel()
            activeAnimation = ValueAnimator.ofFloat(binding.editNameScrim.alpha, if(it) 0.33f else 0f).apply {
                duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
                addUpdateListener { animator ->
                    binding.editNameScrim.alpha = (animator.animatedValue as Float)
                }
            }
            activeAnimation?.start()
        }

        // For handling voice input
        viewModel.voiceInput.observe(viewLifecycleOwner, {
            it.consume()?.apply {
                // Update displayed text, but do not submit. Event will cascade to a separate
                // QueryTextListener, which is responsible for running the search.
                viewModel.startNameEdit()
                searchView.isIconified = false
                searchView.setQuery(this.value, false)
            }
        })
    }

    private fun setupDinerList() {
        recyclerAdapter = DebtEntryDinerSelectionRecyclerViewAdapter(
            requireContext(),
            object: DebtEntryDinerSelectionRecyclerViewAdapter.DebtEntryDinerSelectionListener {
                override fun toggleCreditor(diner: Diner) {
                    viewModel.toggleCreditorSelection(diner)
                }

                override fun toggleDebtor(diner: Diner) {
                    viewModel.toggleDebtorSelection(diner)
                }
            }
        )

        binding.dinerList.apply {
            adapter = recyclerAdapter

            itemAnimator = null

            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }

        viewModel.dinerData.observe(viewLifecycleOwner, { data ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(dinerData = data) }
        })

        binding.selectAllCreditors.setOnClickListener {
            if (binding.selectAllCreditors.isChecked) {
                viewModel.selectAllCreditors()
                binding.fab.slideUp()  // Slide up FAB into view if it was hidden by scrolling
            } else {
                viewModel.clearCreditorSelections()
            }
        }

        binding.selectAllDebtors.setOnClickListener {
            if (binding.selectAllDebtors.isChecked) {
                viewModel.selectAllDebtors()
                binding.fab.slideUp()  // Slide up FAB into view if it was hidden by scrolling
            } else {
                viewModel.clearDebtorSelections()
            }
        }
    }

    private fun closeSearchView() {
        val searchView = binding.toolbar.menu.findItem(R.id.edit_debt_name).actionView as SearchView
        searchView.setQuery("", false)
        searchView.isIconified = true
        viewModel.stopNameEdit()
    }

    private fun setupEnterTransition() {
        val propBottomInset = "com.grouptuity.grouptuity:CardViewExpandTransition:button_bottom_inset"
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:button_top_inset"
        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:button_corner_radius"

        binding.addDebtButton.visibility = View.VISIBLE
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

        sharedElementEnterTransition = CardViewExpandTransition(binding.debtEntryContainer.transitionName, binding.coordinatorLayout.id, true)
            .addElement(binding.addDebtButton.transitionName,
                object: CardViewExpandTransition.Element {
                     override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                        val button = transitionValues.view as com.google.android.material.button.MaterialButton
                        transitionValues.values[propTopInset] = button.insetTop
                        transitionValues.values[propBottomInset] = button.insetBottom
                        transitionValues.values[propCornerRadius] = resources.getDimension(R.dimen.material_card_corner_radius).toInt()
                    }

                    override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                        val button = transitionValues.view as com.google.android.material.button.MaterialButton
                        transitionValues.values[propTopInset] = button.insetTop
                        transitionValues.values[propBottomInset] = button.insetBottom
                        transitionValues.values[propCornerRadius] = 0
                    }

                    override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                        val button = endValues.view as com.google.android.material.button.MaterialButton

                        val surfaceColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSurface, it, true) }.data

                        val animator = ValueAnimator.ofFloat(0f, 1f)
                        animator.doOnStart {
                            // Button is smaller than the container initially so start with the card background hidden
                            binding.debtEntryContainer.setCardBackgroundColor(Color.TRANSPARENT)

                            // Container content will be hidden initially and fade in later
                            binding.coordinatorLayout.alpha = 0f
                        }
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
                                binding.debtEntryContainer.setCardBackgroundColor(surfaceColor)

                                val twentyTo80Progress = (1.666667f*progress - 0.33333f).coerceIn(0f, 1f)
                                button.alpha = 1f - twentyTo80Progress
                                binding.coordinatorLayout.alpha = twentyTo80Progress
                            }
                        }

                        return animator
                    }
                }
            )
            .addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })

        postponeEnterTransition()
        requireView().doOnPreDraw { startPostponedEnterTransition() }
    }

    private fun setupExitTransition() {
        val propBottomInset = "com.grouptuity.grouptuity:CardViewExpandTransition:button_bottom_inset"
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:button_top_inset"
        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:button_corner_radius"

        sharedElementReturnTransition = CardViewExpandTransition(binding.debtEntryContainer.transitionName, binding.coordinatorLayout.id, false).addElement(
            binding.addDebtButton.transitionName,
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
                        binding.debtEntryContainer.setCardBackgroundColor(surfaceColor)
                        binding.coordinatorLayout.alpha = 1f
                        binding.fab.hide()
                    }

                    animator.addUpdateListener {
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                        // Fade out discount button after its shape matches background container
                        val twentyTo80Progress = (1.666667f*progress - 0.33333f).coerceIn(0f, 1f)
                        binding.addDebtButton.alpha = twentyTo80Progress
                        binding.coordinatorLayout.alpha = 1f - twentyTo80Progress

                        if(progress >= 0.8) {
                            binding.debtEntryContainer.setCardBackgroundColor(Color.TRANSPARENT)
                            binding.coordinatorLayout.visibility = View.GONE
                        }

                        // Adjust shape of discount button to final state
                        val eightyTo100Progress = (5f*progress - 4f).coerceIn(0f, 1f)
                        binding.addDebtButton.insetTop = ((startValues.values[propTopInset] as Int) +
                                ((endValues.values[propTopInset] as Int) - (startValues.values[propTopInset] as Int)) * eightyTo100Progress).toInt()
                        binding.addDebtButton.insetBottom = ((startValues.values[propBottomInset] as Int) +
                                ((endValues.values[propBottomInset] as Int) - (startValues.values[propBottomInset] as Int)) * eightyTo100Progress).toInt()
                        binding.addDebtButton.cornerRadius = ((startValues.values[propCornerRadius] as Int) +
                                ((endValues.values[propCornerRadius] as Int) - (startValues.values[propCornerRadius] as Int)) * eightyTo100Progress).toInt()
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


private class DebtEntryDinerSelectionRecyclerViewAdapter(val context: Context, val listener: DebtEntryDinerSelectionListener):
        RecyclerView.Adapter<DebtEntryDinerSelectionRecyclerViewAdapter.ViewHolder>() {
    private var mDinerData = emptyList<DebtEntryViewModel.Companion.DinerDatapoint>()

    interface DebtEntryDinerSelectionListener {
        fun toggleCreditor(diner: Diner)
        fun toggleDebtor(diner: Diner)
    }

    inner class ViewHolder(val viewBinding: FragDebtentryListdinerBinding): RecyclerView.ViewHolder(viewBinding.root)

    override fun getItemCount() = mDinerData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(FragDebtentryListdinerBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.apply {
            val (diner, isDebtor, isCreditor, message) = mDinerData[position]
            viewBinding.contactIcon.setContact(diner.asContact(), false)
            viewBinding.name.text = diner.name
            viewBinding.debtorCheckbox.isChecked = isDebtor
            viewBinding.creditorCheckbox.isChecked = isCreditor

            if (message == null) {
                viewBinding.message.visibility = View.GONE
            } else {
                viewBinding.message.visibility = View.VISIBLE
                viewBinding.message.text = message
            }

            viewBinding.creditorCheckbox.setOnClickListener {
                Log.e("creditorCheckbox", "c")
                listener.toggleCreditor(diner) }
            viewBinding.debtorCheckbox.setOnClickListener { listener.toggleDebtor(diner) }
        }
    }

    suspend fun updateDataSet(dinerData: List<DebtEntryViewModel.Companion.DinerDatapoint>) {
        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mDinerData.size

            override fun getNewListSize() = dinerData.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                dinerData[newPosition].diner.lookupKey == mDinerData[oldPosition].diner.lookupKey

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val (newDiner, isNewContactDebtor, isNewContactCreditor, newMessage) = dinerData[newPosition]
                val (oldDiner, isOldContactDebtor, isOldContactCreditor, oldMessage) = mDinerData[oldPosition]

                return newDiner.name == oldDiner.name &&
                        newDiner.photoUri == oldDiner.photoUri &&
                        isNewContactCreditor == isOldContactCreditor &&
                        isNewContactDebtor == isOldContactDebtor &&
                        newMessage == oldMessage
            }
        })

        val adapter = this
        withContext(Dispatchers.Main) {
            mDinerData = dinerData
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}