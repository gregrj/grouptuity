package com.grouptuity.grouptuity.ui.billsplit.itementry

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.AppViewModel
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.databinding.FragItementryBinding
import com.grouptuity.grouptuity.databinding.ListDinerBinding
import com.grouptuity.grouptuity.ui.billsplit.contactentry.ContactEntryFragmentDirections
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import com.grouptuity.grouptuity.ui.custom.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.custom.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.custom.views.slideUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO ripple effect on long press clear
// TODO disable interactions when transitions are running
// TODO finish item name editor -> issues with back press and keyboard dismiss; block calculator inputs
// TODO check for substantial edits and show alert on dismiss



class ItemEntryFragment: Fragment() {
    private var binding by setNullOnDestroy<FragItementryBinding>()
    private val args: ItemEntryFragmentArgs by navArgs()
    private lateinit var appViewModel: AppViewModel
    private lateinit var itemEntryViewModel: ItemEntryViewModel
    private lateinit var recyclerAdapter: ItemEntryDinerSelectionRecyclerViewAdapter
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var toolbarInTertiaryState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        appViewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        itemEntryViewModel = ViewModelProvider(requireActivity())[ItemEntryViewModel::class.java]
        itemEntryViewModel.initializeForItem(args.editedItem)
        binding = FragItementryBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Intercept user interactions while fragment transitions are running
        binding.rootLayout.attachLock(itemEntryViewModel.isInputLocked)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val addItemToBill = itemEntryViewModel.handleOnBackPressed()
                if (addItemToBill != null) {
                    closeFragment(addItemToBill)
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        /* Need a way to discriminate between user dismissal of keyboard and system dismissal from starting voice search
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            // Close the SearchView if no item name exists when keyboard is dismissed except if
            // a voice search is running
            if(itemEntryViewModel.editingName.value && !insets.isVisible(WindowInsetsCompat.Type.ime())) {
                closeSearchView()
            }
            insets
        }
        */

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.calculator.container)
        bottomSheetBehavior.isDraggable = false
        bottomSheetBehavior.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {  }

            @SuppressLint("ClickableViewAccessibility")
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when(newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.priceTextview.setOnClickListener { itemEntryViewModel.openCalculator() }
                        binding.priceTextview.setOnTouchListener(null)
                    }
                    else -> {
                        binding.priceTextview.setOnClickListener(null)
                        binding.priceTextview.setOnTouchListener { _, _ -> true }
                    }
                }
            }
        })

        //Reset state and setup transitions
        if(args.editedItem == null) {
            // New item
            binding.innerCoveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            binding.priceTextview.setOnClickListener(null)
            binding.priceTextview.setOnTouchListener { _, _ -> true }

            enterTransition = CircularRevealTransition(
                binding.fadeView,
                binding.revealedLayout,
                args.originParams!!,
                resources.getInteger(R.integer.frag_transition_duration).toLong(),
                true).addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { itemEntryViewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { itemEntryViewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })
        } else {
            // Editing existing item
            binding.fadeView.visibility = View.GONE

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            binding.priceTextview.setOnClickListener { itemEntryViewModel.openCalculator() }

            setupEnterTransition()
        }

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        setupToolbar()

        setupCalculator()

        setupDinerList()

        binding.fab.setOnClickListener {
            itemEntryViewModel.selections.value?.apply {
                if(this.isNotEmpty()) {
                    closeFragment(true)
                } //TODO move logic
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.fadeView.visibility = View.GONE

        // Reset UI input/output locks leftover from aborted transitions/animations
        itemEntryViewModel.unFreezeOutput()
    }

    private fun closeFragment(addItemToBill: Boolean) {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

        // Freeze UI in place as the fragment closes
        itemEntryViewModel.freezeOutput()

        if (args.editedItem == null) {
            // Working with new item
            if (addItemToBill) {
                itemEntryViewModel.addNewItemToBill()?.also { newItem ->

                    binding.itemEntryContainer.transitionName = "new_item" + newItem.id

                    // Exit transition is needed to prevent next fragment from appearing immediately
                    exitTransition = Hold().apply {
                        duration = 0L
                        addTarget(requireView())
                    }

                    // Close fragment by popping up to the BillSplitFragment
                    findNavController().navigate(
                        ItemEntryFragmentDirections.itemEntryToBillSplit(newItem = newItem),
                        FragmentNavigatorExtras(
                            binding.itemEntryContainer to binding.itemEntryContainer.transitionName
                        )
                    )
                }
            } else {
                // Closing animation shrinking fragment into the FAB of the previous fragment.
                // Transition is defined here to incorporate dynamic changes to window insets.
                returnTransition = CircularRevealTransition(
                    binding.fadeView,
                    binding.revealedLayout,
                    args.originParams!!.withInsetsOn(binding.fab),
                    resources.getInteger(R.integer.frag_transition_duration).toLong(),
                    false)

                // Close fragment using default onBackPressed behavior
                requireActivity().onBackPressed()
            }
        } else {
            // Working with existing item
            if (addItemToBill) {
                itemEntryViewModel.saveItemEdits()
            }

            setupExitTransition()

            // Close fragment using default onBackPressed behavior
            requireActivity().onBackPressed()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.toolbar_itementry)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { itemEntryViewModel.handleOnBackPressed() }

        itemEntryViewModel.toolBarState.observe(viewLifecycleOwner) { toolBarState ->
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

        val searchView = binding.toolbar.menu.findItem(R.id.edit_item_name).actionView as SearchView
        searchView.setSearchableInfo((requireActivity().getSystemService(Context.SEARCH_SERVICE) as SearchManager).getSearchableInfo(requireActivity().componentName))
        searchView.queryHint = resources.getString(R.string.itementry_toolbar_enter_name)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.isSubmitButtonEnabled = true

        searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)?.apply { this.setImageResource(R.drawable.ic_edit) }
        searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_go_btn)?.apply { this.setImageResource(R.drawable.ic_done) }

        searchView.setOnSearchClickListener {
            searchView.setQuery(if(itemEntryViewModel.hasItemNameInput) { itemEntryViewModel.itemName.value } else { "" }, false)
            itemEntryViewModel.startNameEdit()
        }

        searchView.setOnCloseListener {
            itemEntryViewModel.stopNameEdit()
            false
        }

        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextChange(string: String?): Boolean { return true }
            override fun onQueryTextSubmit(string: String?): Boolean {
                if(string.isNullOrBlank()){
                    // function does not get called if string is blank
                } else {
                    itemEntryViewModel.acceptItemNameInput(string.trim())
                    closeSearchView()
                    requireActivity().invalidateOptionsMenu()
                }
                return true
            }
        })

        var activeAnimation: ValueAnimator? = null
        itemEntryViewModel.editNameShimVisible.observe(viewLifecycleOwner) {
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
        appViewModel.voiceInput.observe(viewLifecycleOwner, {
            it.consume()?.apply {
                // Update displayed text, but do not submit. Event will cascade to a separate
                // QueryTextListener, which is responsible for running the search.
                itemEntryViewModel.startNameEdit()
                searchView.isIconified = false
                searchView.setQuery(this, false)
            }
        })
    }

    private fun setupCalculator() {
        // Layout sets correct calculator height without needing placeholder for the price TextView
        binding.calculator.pricePlaceholder.visibility = View.GONE

        // Only currency mode will be used so no need to show basis toggle buttons
        binding.calculator.basisToggleButtons.visibility = View.GONE

        // Observer for expanding and collapsing the number pad
        itemEntryViewModel.numberPadVisible.observe(viewLifecycleOwner, { visible ->
            BottomSheetBehavior.from(binding.calculator.container).state =
                if (visible)
                    BottomSheetBehavior.STATE_EXPANDED
                else
                    BottomSheetBehavior.STATE_COLLAPSED
        })

        itemEntryViewModel.formattedPrice.observe(viewLifecycleOwner, { price: String -> binding.priceTextview.text = price })

        // Observers and listeners for the number pad buttons
        itemEntryViewModel.priceZeroButtonEnabled.observe(viewLifecycleOwner) { binding.calculator.button0.isEnabled = it }
        itemEntryViewModel.priceDecimalButtonEnabled.observe(viewLifecycleOwner) { binding.calculator.buttonDecimal.isEnabled = it }
        itemEntryViewModel.priceAcceptButtonEnabled.observe(viewLifecycleOwner) {
            if(it) {
                binding.calculator.buttonAccept.show()
            } else {
                binding.calculator.buttonAccept.hide()
            }
        }
        binding.calculator.buttonDecimal.setOnClickListener { itemEntryViewModel.addDecimalToPrice() }
        binding.calculator.button0.setOnClickListener { itemEntryViewModel.addDigitToPrice('0') }
        binding.calculator.button1.setOnClickListener { itemEntryViewModel.addDigitToPrice('1') }
        binding.calculator.button2.setOnClickListener { itemEntryViewModel.addDigitToPrice('2') }
        binding.calculator.button3.setOnClickListener { itemEntryViewModel.addDigitToPrice('3') }
        binding.calculator.button4.setOnClickListener { itemEntryViewModel.addDigitToPrice('4') }
        binding.calculator.button5.setOnClickListener { itemEntryViewModel.addDigitToPrice('5') }
        binding.calculator.button6.setOnClickListener { itemEntryViewModel.addDigitToPrice('6') }
        binding.calculator.button7.setOnClickListener { itemEntryViewModel.addDigitToPrice('7') }
        binding.calculator.button8.setOnClickListener { itemEntryViewModel.addDigitToPrice('8') }
        binding.calculator.button9.setOnClickListener { itemEntryViewModel.addDigitToPrice('9') }
        binding.calculator.buttonAccept.setOnClickListener { itemEntryViewModel.acceptPrice() }

        // Observers and listeners for the backspace/clear button
        itemEntryViewModel.priceBackspaceButtonVisible.observe(viewLifecycleOwner) { binding.buttonBackspace.visibility = if(it) View.VISIBLE else View.GONE }
        binding.buttonBackspace.setOnClickListener { itemEntryViewModel.removeDigitFromPrice() }
        binding.buttonBackspace.setOnLongClickListener {
            itemEntryViewModel.resetPrice()
            true
        }

        itemEntryViewModel.priceEditButtonVisible.observe(viewLifecycleOwner) { binding.buttonEdit.visibility = if(it) View.VISIBLE else View.GONE }
    }

    private fun setupDinerList() {
        recyclerAdapter = ItemEntryDinerSelectionRecyclerViewAdapter(
            requireContext(),
            object: RecyclerViewListener {
                override fun onClick(view: View) {
                    itemEntryViewModel.toggleDinerSelection(view.tag as Diner)
                    binding.fab.slideUp()  // Slide up FAB into view if it was hidden by scrolling
                }
                override fun onLongClick(view: View): Boolean { return false }
            }
        )

        binding.dinerList.apply {
            adapter = recyclerAdapter

            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

            itemAnimator = object: DefaultItemAnimator() {
                val backgroundColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
                val backgroundColorVariant = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

                override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean { return true }

                override fun animateChange(oldHolder: RecyclerView.ViewHolder?, newHolder: RecyclerView.ViewHolder?, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
                    oldHolder?.apply {
                        if (oldHolder === newHolder) {
                            val diner = (this as ItemEntryDinerSelectionRecyclerViewAdapter.ViewHolder).itemView.tag as Diner

                            if (itemEntryViewModel.isDinerSelected(diner)) {
                                oldHolder.itemView.setBackgroundColor(backgroundColorVariant)
                            } else {
                                oldHolder.itemView.setBackgroundColor(backgroundColor)
                            }
                            dispatchAnimationFinished(newHolder)
                        }
                    }
                    return false
                }
            }
        }

        binding.clearSelections.setOnClickListener { itemEntryViewModel.clearDinerSelections() }

        binding.selectAll.setOnClickListener {
            itemEntryViewModel.selectAllDiners()
            binding.fab.slideUp()  // Slide up FAB into view if it was hidden by scrolling
        }

        itemEntryViewModel.dinerData.observe(viewLifecycleOwner, { data -> lifecycleScope.launch { recyclerAdapter.updateDataSet(dinerData = data) } })

        itemEntryViewModel.selections.observe(viewLifecycleOwner, { selections ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(selections = selections) }

            if(selections.isNullOrEmpty()) {
                binding.fab.hide()
            } else {
                binding.fab.show()
            }
        })

        itemEntryViewModel.selectAllButtonDisabled.observe(viewLifecycleOwner) { binding.selectAll.isEnabled = !it }
    }

    private fun closeSearchView() {
        val searchView = binding.toolbar.menu.findItem(R.id.edit_item_name).actionView as SearchView
        searchView.setQuery("", false)
        searchView.isIconified = true
        itemEntryViewModel.stopNameEdit()
    }

    private fun setupEnterTransition() {
        binding.itemEntryContainer.transitionName = "container" + args.editedItem!!.id

        val PROP_PRICE_HEIGHT = "com.grouptuity.grouptuity:CardViewExpandTransition:price_height"
        val PROP_PRICE_WIDTH = "com.grouptuity.grouptuity:CardViewExpandTransition:price_width"
        val PROP_IMAGE_MARGIN = "com.grouptuity.grouptuity:CardViewExpandTransition:price_margin"

        sharedElementEnterTransition = CardViewExpandTransition(binding.itemEntryContainer.transitionName, binding.revealedLayout.id, true)
            .setOnTransitionProgressCallback { _: Transition, sceneRoot: ViewGroup, _: View, animator: ValueAnimator ->
                sceneRoot.findViewById<FrameLayout>(R.id.revealed_layout)?.apply {
                    val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
                    this.alpha = progress
                }
            }
            .addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { itemEntryViewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { itemEntryViewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })

        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

        postponeEnterTransition()
    }

    private fun setupExitTransition() {
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

        binding.itemEntryContainer.transitionName = "container" + args.editedItem!!.id

        val PROP_PRICE_HEIGHT = "com.grouptuity.grouptuity:CardViewExpandTransition:price_height"
        val PROP_PRICE_WIDTH = "com.grouptuity.grouptuity:CardViewExpandTransition:price_width"
        val PROP_IMAGE_MARGIN = "com.grouptuity.grouptuity:CardViewExpandTransition:price_margin"

        sharedElementReturnTransition = CardViewExpandTransition(binding.itemEntryContainer.transitionName, binding.revealedLayout.id, false)
            .setOnTransitionProgressCallback { _: Transition, _: ViewGroup, _: View, animator: ValueAnimator ->
                binding.revealedLayout.apply {
                    val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
                    this.alpha = 1f - progress
                }
            }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(requireView())
        }
    }
}


private class ItemEntryDinerSelectionRecyclerViewAdapter(val context: Context, val listener: RecyclerViewListener): RecyclerView.Adapter<ItemEntryDinerSelectionRecyclerViewAdapter.ViewHolder>() {
    private var mDinerData = emptyList<Pair<Diner, String>>()
    private var mSelections = emptySet<Diner>()

    private val backgroundColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
    private val backgroundColorVariant = TypedValue().also { context.theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

    inner class ViewHolder(val viewBinding: ListDinerBinding): RecyclerView.ViewHolder(viewBinding.root) {

        init {
            itemView.setOnClickListener(listener)
            itemView.setOnLongClickListener(listener)
        }
    }

    override fun getItemCount() = mDinerData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ListDinerBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (newDiner, dinerSubtotal) = mDinerData[position]

        holder.apply {
            itemView.tag = newDiner // store updated data

            val isSelected = mSelections.contains(newDiner)

            viewBinding.contactIcon.setContact(newDiner.asContact(), isSelected)

            viewBinding.name.text = newDiner.name

            itemView.setBackgroundColor(if(isSelected) backgroundColorVariant else backgroundColor)

            if(newDiner.itemIds.isEmpty()) {
                viewBinding.message.text = context.resources.getString(R.string.itementry_zero_items)
            } else {
                viewBinding.message.text = context.resources.getQuantityString(
                    R.plurals.itementry_num_items_with_subtotal,
                    newDiner.itemIds.size,
                    newDiner.itemIds.size,
                    dinerSubtotal)
            }
        }
    }

    suspend fun updateDataSet(dinerData: List<Pair<Diner, String>>?=null, selections: Set<Diner>?=null) {
        val newDinerData = dinerData ?: mDinerData
        val newSelections = selections ?: mSelections

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mDinerData.size

            override fun getNewListSize() = newDinerData.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newDinerData[newPosition].first.id == mDinerData[oldPosition].first.id

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val (newDiner, newDinerSubtotal) = newDinerData[newPosition]
                val (oldDiner, oldDinerSubtotal) = mDinerData[oldPosition]

                return newDiner.id == oldDiner.id &&
                        newSelections.contains(newDiner) == mSelections.contains(oldDiner) &&
                        newDinerSubtotal == oldDinerSubtotal
            }
        })

        val adapter = this
        withContext(Dispatchers.Main) {
            mDinerData = newDinerData
            mSelections = newSelections
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}