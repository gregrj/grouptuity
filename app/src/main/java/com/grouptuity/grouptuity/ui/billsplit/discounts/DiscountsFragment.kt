package com.grouptuity.grouptuity.ui.billsplit.discounts

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.animation.doOnStart
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.transition.Transition
import androidx.transition.TransitionValues
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Discount
import com.grouptuity.grouptuity.data.Item
import com.grouptuity.grouptuity.databinding.FragDiscountsBinding
import com.grouptuity.grouptuity.databinding.FragDiscountsListitemBinding
import com.grouptuity.grouptuity.ui.custom.transitions.*
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat


//TODO prevent click on item calling navigate after already having clicked once and waiting for transition to start
//TODO prevent update to list item during removal showing empty discount data
//TODO menu button to clear all discounts

class DiscountsFragment: Fragment(), Revealable by RevealableImpl() {
    private val args: DiscountsFragmentArgs by navArgs()
    private var binding by setNullOnDestroy<FragDiscountsBinding>()
    private lateinit var discountsViewModel: DiscountsViewModel
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var recyclerAdapter: DiscountRecyclerViewAdapter
    private var suppressAutoScroll = false
    private var discountIdForReturnTransition = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragDiscountsBinding.inflate(inflater, container, false)
        discountsViewModel = ViewModelProvider(this).get(DiscountsViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Intercept user interactions while while fragment transitions and animations are running
        binding.rootLayout.attachLock(discountsViewModel.isInputLocked)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { closeFragment() } }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { closeFragment() }

        when ((requireActivity() as MainActivity).getNavigator().previousDestination) {
            R.id.billSplitFragment -> {
                // Opening from TaxTipFragment hosted in BillSplitFragment
                setupEnterTransitionFromTaxTip()
            }
            R.id.discountEntryFragment -> {
                args.newDiscount?.apply {
                    // Simulated return transition from DiscountEntryFragment for first new discount
                    // setupEnterTransitionFromDiscountEntry(this)
                }
            }
        }

        setupList()

        binding.fab.setOnClickListener {
            findNavController().navigate(
                DiscountsFragmentDirections.editDiscount(
                    editedDiscount = null,
                    CircularRevealTransition.OriginParams(binding.fab)
                )
            )
        }

        postponeEnterTransition()
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Long>("discountIdKey")?.observe(viewLifecycleOwner) { discountIdForReturnTransition = it }
        binding.list.doOnPreDraw { startPostponedEnterTransition() }
    }

    private fun setupList() {
        recyclerAdapter = DiscountRecyclerViewAdapter(requireContext(), discountsViewModel,
            object: RecyclerViewListener {
                override fun onClick(view: View) {
                    suppressAutoScroll = true // Retain scroll position when returning to this fragment

                    // Exit transition is needed to prevent next fragment from appearing immediately
                    exitTransition = Hold().apply {
                        duration = 0L
                        addTarget(requireView())
                    }

                    val viewBinding = FragDiscountsListitemBinding.bind(view)

                    findNavController().navigate(
                        DiscountsFragmentDirections.editDiscount(view.tag as Discount, null),
                        FragmentNavigatorExtras(viewBinding.cardBackground to viewBinding.cardBackground.transitionName)
                    )
                }
                override fun onLongClick(view: View): Boolean { return false }
        })

        recyclerAdapter.registerAdapterDataObserver(object: RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)

                if(suppressAutoScroll) {
                    suppressAutoScroll = false
                    return
                }

                val count = recyclerAdapter.itemCount
                val lastVisiblePosition = (binding.list.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()

                if (lastVisiblePosition == -1 || positionStart >= count-1 && lastVisiblePosition == positionStart-1) {
                    binding.list.smoothScrollToPosition(positionStart)
                } else {
                    binding.list.smoothScrollToPosition(recyclerAdapter.itemCount - 1)
                }
            }
        })

        binding.list.apply {
            adapter = recyclerAdapter

            // Add a spacer to the last item in the list to ensure it is not cut off when the toolbar
            // and floating action button are visible
            addItemDecoration(RecyclerViewBottomOffset(resources.getDimension(R.dimen.recyclerview_bottom_offset).toInt()))

            // When adding additional items, the RecyclerViewBottomOffset decoration is removed from
            // the former last item. This setting prevents an unnecessary flashing animation.
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        discountsViewModel.discounts.observe(viewLifecycleOwner) { lifecycleScope.launch { recyclerAdapter.updateDataSet(discounts = it) } }
        discountsViewModel.diners.observe(viewLifecycleOwner) { lifecycleScope.launch { recyclerAdapter.updateDataSet(diners = it) } }
        discountsViewModel.items.observe(viewLifecycleOwner) { lifecycleScope.launch { recyclerAdapter.updateDataSet(items = it) } }
    }

    private fun closeFragment() {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

        setupReturnTransitionToTaxTip(requireView())

        requireActivity().onBackPressed()
    }

    private fun setupEnterTransitionFromTaxTip() {
        val propBottomInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_bottom_inset"
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_top_inset"
        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_corner_radius"

        binding.addDiscountButton.transitionName = null
        binding.addDiscountButton.visibility = View.GONE
        binding.editDiscountsButton.transitionName = "discountButtonTransitionName"
        binding.editDiscountsButton.visibility = View.VISIBLE

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, true).addElement(
            binding.editDiscountsButton.transitionName,
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

                        // Adjust shape of discount button to match background container
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
        )

        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)
    }

    private fun setupReturnTransitionToTaxTip(view: View) {
        val propBottomInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_bottom_inset"
        val propTopInset = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_top_inset"
        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:discount_button_corner_radius"

        val button = if(discountsViewModel.discounts.value.isNullOrEmpty()) {
            binding.addDiscountButton.transitionName = "discountButtonTransitionName"
            binding.addDiscountButton.visibility = View.VISIBLE
            binding.editDiscountsButton.transitionName = null
            binding.editDiscountsButton.visibility = View.GONE
            binding.addDiscountButton
        } else {
            binding.addDiscountButton.transitionName = null
            binding.addDiscountButton.visibility = View.GONE
            binding.editDiscountsButton.transitionName = "discountButtonTransitionName"
            binding.editDiscountsButton.visibility = View.VISIBLE
            binding.editDiscountsButton
        }

        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, false).addElement(
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

                        // Adjust shape of discount button to match background container
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
        )

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(view)
        }
    }

    private inner class DiscountRecyclerViewAdapter(
        private val context: Context,
        private val discountViewModel: DiscountsViewModel,
        private val listener: RecyclerViewListener): RecyclerView.Adapter<DiscountRecyclerViewAdapter.ViewHolder>() {

        private var mDiscountList = emptyList<Discount>()
        private var mDiners = emptyList< Diner>()
        private var mItems = emptyList<Item>()
        private var mMaxDiners = Integer.MAX_VALUE
        private var mMaxItems = Integer.MAX_VALUE
        private val percentMaxDecimals = context.resources.getInteger(R.integer.percent_max_decimals)

        inner class ViewHolder(val viewBinding: FragDiscountsListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
            init {
                itemView.setOnClickListener(listener)
                itemView.setOnLongClickListener(listener)
            }
        }

        override fun getItemCount() = mDiscountList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(FragDiscountsListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val newDiscount = mDiscountList[position]

            holder.apply {
                itemView.tag = newDiscount // store updated list item

                viewBinding.discountValue.text = if(newDiscount.asPercent) {
                    //TODO replace with actual value
                    NumberFormat.getPercentInstance().also { it.maximumFractionDigits = percentMaxDecimals }.format(0.01*newDiscount.value)
                } else {
                    NumberFormat.getCurrencyInstance().format(newDiscount.value)
                }

                viewBinding.name.text = if(newDiscount.onItems) {
                    if(newDiscount.asPercent) {
                        val percentValue = NumberFormat.getPercentInstance().also { it.maximumFractionDigits = percentMaxDecimals }.format(0.01*newDiscount.value)
                        when(newDiscount.items.size) {
                            1 -> { newDiscount.items[0].name.let { context.resources.getString(R.string.discounts_onitems_percent_single, percentValue, it) } }
                            mMaxItems -> { context.resources.getString(R.string.discounts_onitems_percent_all, percentValue) }
                            else -> {
                                context.resources.getQuantityString(
                                    R.plurals.discounts_onitems_percent_multiple,
                                    newDiscount.itemIds.size,
                                    newDiscount.itemIds.size,
                                    percentValue)
                            }
                        }
                    } else {
                        when(newDiscount.itemIds.size) {
                            1 -> { newDiscount.items[0].name.let { context.resources.getString(R.string.discounts_onitems_currency_single, it) } }
                            mMaxItems -> { context.resources.getString(R.string.discounts_onitems_currency_all) }
                            else -> {
                                context.resources.getQuantityString(R.plurals.discounts_onitems_currency_multiple, newDiscount.itemIds.size, newDiscount.itemIds.size)
                            }
                        }
                    }
                } else {
                    if(newDiscount.asPercent) {
                        val percentValue = NumberFormat.getPercentInstance().also { it.maximumFractionDigits = percentMaxDecimals }.format(0.01*newDiscount.value)
                        when(newDiscount.recipientIds.size) {
                            1 -> { newDiscount.recipients[0].name.let { context.resources.getString(R.string.discounts_fordiners_percent_single, percentValue, it) } }
                            mMaxDiners -> { context.resources.getString(R.string.discounts_fordiners_percent_all, percentValue) }
                            else -> {
                                context.resources.getQuantityString(
                                    R.plurals.discounts_fordiners_percent_multiple,
                                    newDiscount.recipientIds.size,
                                    newDiscount.recipientIds.size,
                                    percentValue)
                            }
                        }
                    } else {
                        when(newDiscount.recipientIds.size) {
                            1 -> { newDiscount.recipients[0].name.let { context.resources.getString(R.string.discounts_fordiners_currency_single, it) } }
                            mMaxDiners -> { context.resources.getString(R.string.discounts_fordiners_currency_all) }
                            else -> {
                                context.resources.getQuantityString(
                                    R.plurals.discounts_fordiners_currency_multiple,
                                    newDiscount.recipientIds.size,
                                    newDiscount.recipientIds.size)
                            }
                        }
                    }
                }

                if(newDiscount.cost == null) {
                    viewBinding.reimbursementSummary.visibility = View.GONE
                } else {
                    val costString = NumberFormat.getCurrencyInstance().format(newDiscount.cost)
                    viewBinding.reimbursementSummary.visibility = View.VISIBLE
                    viewBinding.reimbursementSummary.text = when(newDiscount.purchaserIds.size) {
                        1 -> { newDiscount.purchasers[0].name.let { context.resources.getString(R.string.discounts_reimbursement_single, costString, it) } }
                        mMaxDiners -> { context.resources.getString(R.string.discounts_reimbursement_all, costString) }
                        else -> {
                            context.resources.getQuantityString(
                                R.plurals.discounts_reimbursement_multiple,
                                newDiscount.purchaserIds.size,
                                newDiscount.purchaserIds.size,
                                costString)
                        }
                    }
                }

                viewBinding.remove.setOnClickListener {
                    discountViewModel.removeDiscount(newDiscount)
                }

                viewBinding.cardBackground.transitionName = "container" + newDiscount.id
            }
        }

        suspend fun updateDataSet(discounts: List<Discount>? = null, diners: List<Diner>? = null, items: List<Item>? = null) {
            val newDiscounts = discounts ?: mDiscountList
            val newDiners = diners ?: mDiners
            val newItems = items ?: mItems

            val adapter = this

            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = mDiscountList.size

                override fun getNewListSize() = newDiscounts.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newDiscounts[newPosition].id == mDiscountList[oldPosition].id

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val newDiscount = newDiscounts[newPosition]
                    val oldDiscount = mDiscountList[oldPosition]

                    return newDiscount.id == oldDiscount.id &&
                            newDiscount.value == oldDiscount.value &&
                            newDiscount.asPercent == oldDiscount.asPercent &&
                            newDiscount.onItems == oldDiscount.onItems &&
                            newDiscount.itemIds == oldDiscount.itemIds &&
                            newDiscount.recipientIds == oldDiscount.recipientIds &&
                            newDiscount.cost == oldDiscount.cost &&
                            newDiscount.purchaserIds == oldDiscount.purchaserIds
                }
            })

            withContext(Dispatchers.Main) {
                adapter.notifyItemChanged(mDiscountList.size - 1) // Clears BottomOffset from old last item
                adapter.notifyItemChanged(mDiscountList.size - 2) // Needed to add BottomOffset in case last item is removed

                mDiscountList = newDiscounts
                mDiners = newDiners
                mMaxDiners = mDiners.size
                mItems = newItems
                mMaxItems = mItems.size

                diffResult.dispatchUpdatesTo(adapter)
            }
        }
    }
}