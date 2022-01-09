package com.grouptuity.grouptuity.ui.billsplit.itementry

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.Diner
import com.grouptuity.grouptuity.data.entities.Item
import com.grouptuity.grouptuity.databinding.FragItementryBinding
import com.grouptuity.grouptuity.databinding.ListDinerBinding
import com.grouptuity.grouptuity.ui.util.UIFragment
import com.grouptuity.grouptuity.ui.util.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.util.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.util.views.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO ripple effect on long press clear
// TODO disable interactions when transitions are running
// TODO finish item name editor -> issues with back press and keyboard dismiss; block calculator inputs
// TODO check for substantial edits and show alert on dismiss


class ItemEntryFragment: UIFragment<FragItementryBinding, ItemEntryViewModel, String?, Item?>() {
    private val args: ItemEntryFragmentArgs by navArgs()
    private lateinit var recyclerAdapter: ItemEntryDinerSelectionRecyclerViewAdapter

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragItementryBinding.inflate(inflater, container, false)

    override fun createViewModel(): ItemEntryViewModel =
        ViewModelProvider(requireActivity())[ItemEntryViewModel::class.java]

    override fun getInitialInput(): String? = args.editedItemId

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        //Reset state and setup transitions
        if(args.editedItemId == null) {
            // New item
            binding.innerCoveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

            BottomSheetBehavior.from(binding.numberPad.container).state = BottomSheetBehavior.STATE_EXPANDED

            enterTransition = CircularRevealTransition(
                binding.fadeView,
                binding.revealedLayout,
                args.originParams!!,
                resources.getInteger(R.integer.frag_transition_duration).toLong(),
                true).addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })
        } else {
            // Editing existing item
            binding.fadeView.visibility = View.GONE

            BottomSheetBehavior.from(binding.numberPad.container).state = BottomSheetBehavior.STATE_COLLAPSED

            setupEnterTransition()
        }

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        setupToolbar()

        setupDinerList()

        binding.fab.setOnClickListener { viewModel.trySavingItem() }
    }

    override fun onResume() {
        super.onResume()
        binding.fadeView.visibility = View.GONE
    }

    override fun onFinish(output: Item?) {
        if (args.editedItemId == null) {
            if (output == null) {
                /* Close fragment without creating a new item. Animate fragment shrinking into the
                   FAB of the previous fragment. Transition is defined here to incorporate dynamic
                   changes to window insets. */
                returnTransition = CircularRevealTransition(
                    binding.fadeView,
                    binding.revealedLayout,
                    args.originParams!!.withInsetsOn(binding.fab),
                    resources.getInteger(R.integer.frag_transition_duration).toLong(),
                    false)

                // Close fragment using default onBackPressed behavior
                requireActivity().onBackPressed()
            } else {
                /* New item was created. Navigate forward to the BillSplitFragment so a collapse
                   animation can be run into the card view for the new item. */
                binding.itemEntryContainer.transitionName = "new_item" + output.id

                // Exit transition is needed to prevent next fragment from appearing immediately
                exitTransition = Hold().apply {
                    duration = 0L
                    addTarget(requireView())
                }

                findNavController().navigate(
                    ItemEntryFragmentDirections.itemEntryToBillSplit(newItemId = output.id),
                    FragmentNavigatorExtras(
                        binding.itemEntryContainer to binding.itemEntryContainer.transitionName
                    )
                )
            }
        } else {
            // Editing existing item. Same return animation regardless of whether edits were saved.
            setupExitTransition()

            // Close fragment using default onBackPressed behavior
            requireActivity().onBackPressed()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.toolbar_itementry)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { viewModel.handleOnBackPressed() }

        setupToolbarSecondaryTertiaryAnimation(
            requireContext(),
            viewLifecycleOwner,
            viewModel.toolBarInTertiaryState,
            binding.toolbar,
            binding.statusBarBackgroundView)

        viewModel.toolBarTitle.observe(viewLifecycleOwner) { binding.toolbar.title = it }

        viewModel.toolBarEditButtonVisible.observe(viewLifecycleOwner) {
            binding.toolbar.menu.setGroupVisible(R.id.group_editor, it)
        }

        viewModel.toolBarNavIconVisible.observe(viewLifecycleOwner) {
            if (it) {
                binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
            } else {
                binding.toolbar.navigationIcon = null
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
            searchView.setQuery(
                if(viewModel.hasItemNameInput) { viewModel.itemName.value } else { "" },
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
                    viewModel.acceptItemNameInput(string.trim())
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
        recyclerAdapter = ItemEntryDinerSelectionRecyclerViewAdapter(
            requireContext(),
            object: RecyclerViewListener {
                override fun onClick(view: View) {
                    viewModel.toggleDinerSelection(view.tag as Diner)
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

                            if (viewModel.isDinerSelected(diner)) {
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

        binding.clearSelections.setOnClickListener { viewModel.clearDinerSelections() }

        binding.selectAll.setOnClickListener {
            viewModel.selectAllDiners()
            binding.fab.slideUp()  // Slide up FAB into view if it was hidden by scrolling
        }

        viewModel.dinerData.observe(viewLifecycleOwner, { data -> lifecycleScope.launch { recyclerAdapter.updateDataSet(dinerData = data) } })

        viewModel.selections.observe(viewLifecycleOwner, { selections ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(selections = selections) }

            if(selections.isNullOrEmpty()) {
                binding.fab.hide()
            } else {
                binding.fab.show()
            }
        })

        viewModel.selectAllButtonDisabled.observe(viewLifecycleOwner) { binding.selectAll.isEnabled = !it }
    }

    private fun closeSearchView() {
        val searchView = binding.toolbar.menu.findItem(R.id.edit_item_name).actionView as SearchView
        searchView.setQuery("", false)
        searchView.isIconified = true
        viewModel.stopNameEdit()
    }

    private fun setupEnterTransition() {
        binding.itemEntryContainer.transitionName = "container" + args.editedItemId!!

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
                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })

        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

        postponeEnterTransition()
    }

    private fun setupExitTransition() {
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

        binding.itemEntryContainer.transitionName = "container" + args.editedItemId!!

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

            val newDinerItems = newDiner.items.value
            if(newDinerItems.isEmpty()) {
                viewBinding.message.text = context.resources.getString(R.string.itementry_zero_items)
            } else {
                viewBinding.message.text = context.resources.getQuantityString(
                    R.plurals.itementry_num_items_with_subtotal,
                    newDinerItems.size,
                    newDinerItems.size,
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