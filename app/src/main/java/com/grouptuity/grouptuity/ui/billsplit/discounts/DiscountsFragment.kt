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
import com.grouptuity.grouptuity.data.Discount
import com.grouptuity.grouptuity.databinding.FragDiscountsBinding
import com.grouptuity.grouptuity.databinding.FragDiscountsListitemBinding
import com.grouptuity.grouptuity.ui.custom.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.custom.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


//TODO prevent click on item calling navigate after already having clicked once and waiting for transition to start
//TODO prevent update to list item during removal showing empty discount data
//TODO menu button to clear all discounts

class DiscountsFragment: Fragment() {
    private val args: DiscountsFragmentArgs by navArgs()
    private var binding by setNullOnDestroy<FragDiscountsBinding>()
    private lateinit var discountsViewModel: DiscountsViewModel
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var recyclerAdapter: DiscountRecyclerViewAdapter
    private var suppressAutoScroll = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragDiscountsBinding.inflate(inflater, container, false)
        discountsViewModel = ViewModelProvider(this).get(DiscountsViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Intercept user interactions while fragment transitions and animations are running
        binding.rootLayout.attachLock(discountsViewModel.isInputLocked)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { closeFragment() } }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { closeFragment() }

        postponeEnterTransition()
        setupEnterTransitionFromTaxTip()
        binding.list.doOnPreDraw { startPostponedEnterTransition() }

        setupList()

        binding.fab.setOnClickListener {
            (requireActivity() as MainActivity).storeViewAsBitmap(requireView())
            findNavController().navigate(
                DiscountsFragmentDirections.editDiscount(
                    editedDiscount = null,
                    CircularRevealTransition.OriginParams(binding.fab)
                )
            )
        }
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

                    (requireActivity() as MainActivity).storeViewAsBitmap(requireView())

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

        discountsViewModel.discountData.observe(viewLifecycleOwner) { lifecycleScope.launch { recyclerAdapter.updateDataSet(it) } }
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

        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
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

        private var mData = emptyList<Pair<Discount, Triple<String, String, String>>>()

        inner class ViewHolder(val viewBinding: FragDiscountsListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
            init {
                itemView.setOnClickListener(listener)
                itemView.setOnLongClickListener(listener)
            }
        }

        override fun getItemCount() = mData.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(FragDiscountsListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (newDiscount, strings) = mData[position]
            val (valueString, descriptionString, reimbursementString) = strings

            holder.apply {
                itemView.tag = newDiscount // store updated list item

                viewBinding.discountValue.text = valueString
                viewBinding.name.text = descriptionString

                if(newDiscount.cost == null) {
                    viewBinding.reimbursementSummary.visibility = View.GONE
                } else {
                    viewBinding.reimbursementSummary.visibility = View.VISIBLE
                    viewBinding.reimbursementSummary.text = reimbursementString
                }

                viewBinding.remove.setOnClickListener {
                    discountViewModel.removeDiscount(newDiscount)
                }

                viewBinding.cardBackground.transitionName = "container" + newDiscount.id
            }
        }

        suspend fun updateDataSet(newData: List<Pair<Discount, Triple<String, String, String>>>) {
            val adapter = this

            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = mData.size

                override fun getNewListSize() = newData.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newData[newPosition].first.id == mData[oldPosition].first.id

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    return newData[newPosition].first.id == mData[oldPosition].first.id &&
                            newData[newPosition].second.first == mData[oldPosition].second.first &&
                            newData[newPosition].second.second == mData[oldPosition].second.second &&
                            newData[newPosition].second.third == mData[oldPosition].second.third
                }
            })

            withContext(Dispatchers.Main) {
                adapter.notifyItemChanged(mData.size - 1) // Clears BottomOffset from old last item
                adapter.notifyItemChanged(mData.size - 2) // Needed to add BottomOffset in case last item is removed

                mData = newData
                diffResult.dispatchUpdatesTo(adapter)
            }
        }
    }
}