package com.grouptuity.grouptuity.ui.billsplit

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Item
import com.grouptuity.grouptuity.databinding.FragItemsBinding
import com.grouptuity.grouptuity.databinding.FragItemsListitemBinding
import com.grouptuity.grouptuity.ui.util.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.util.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.util.views.setNullOnDestroy
import com.grouptuity.grouptuity.ui.util.views.ContactIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat


class ItemsFragment: Fragment() {
    companion object {
        @JvmStatic
        fun newInstance() = ItemsFragment()
    }

    private var binding by setNullOnDestroy<FragItemsBinding>()
    private lateinit var itemsViewModel: ItemsViewModel
    private var suppressAutoScroll = false
    private var itemIdForNewItemTransition: String? = null


    // TODO align text in each list item based on max length of price to avoid offsets due to different numbers?
    // TODO Contact Chips text alignment
    // TODO snackbar note when removing item

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        itemsViewModel = ViewModelProvider(requireActivity()).get(ItemsViewModel::class.java)

        val recyclerAdapter = ItemRecyclerViewAdapter(requireContext(), itemsViewModel, object:
            RecyclerViewListener {
            override fun onClick(view: View) {
                suppressAutoScroll = true // Retain scroll position when returning to this fragment

                // Exit transition is needed to prevent next fragment from appearing immediately
                requireParentFragment().exitTransition = Hold().apply {
                    duration = 0L
                    addTarget(requireParentFragment().requireView())
                }

                val viewBinding = FragItemsListitemBinding.bind(view)

                (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

                findNavController().navigate(
                    BillSplitFragmentDirections.createNewItem(view.tag as Item, null),
                    FragmentNavigatorExtras(
                        viewBinding.cardBackground to viewBinding.cardBackground.transitionName,
                    )
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

//            requireParentFragment().apply {
//                postponeEnterTransition()
//                viewTreeObserver.addOnPreDrawListener {
//                    startPostponedEnterTransition()
//                    true
//                }
//            }

            // Add a spacer to the last item in the list to ensure it is not cut off when the toolbar
            // and floating action button are visible
            addItemDecoration(RecyclerViewBottomOffset(resources.getDimension(R.dimen.recyclerview_bottom_offset).toInt()))

            // When adding additional items, the RecyclerViewBottomOffset decoration is removed from
            // the former last item. This setting prevents an unnecessary flashing animation.
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        itemsViewModel.items.observe(viewLifecycleOwner) { items ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(items=items) }
            binding.addItemsHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        itemsViewModel.numberOfDiners.observe(viewLifecycleOwner) { count ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(numberOfDiners = count) }
        }

        if (itemIdForNewItemTransition == null) {
            requireParentFragment().postponeEnterTransition()
            binding.list.viewTreeObserver.addOnPreDrawListener(object: ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.list.viewTreeObserver.removeOnPreDrawListener(this)
                    requireParentFragment().startPostponedEnterTransition()
                    return true
                }
            })
        }
    }

    fun setSharedElementItemId(itemId: String) {
        itemIdForNewItemTransition = itemId
    }

    private inner class ItemRecyclerViewAdapter(private val context: Context,
                                          private val itemViewModel: ItemsViewModel,
                                          private val listener: RecyclerViewListener): RecyclerView.Adapter<ItemRecyclerViewAdapter.ViewHolder>() {
        private var mItems = emptyList<Item>()
        private var mNumberOfDiners = 0

        private val errorTextColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data
        private val normalTextColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnSurface, it, true) }.data

        inner class ViewHolder(val viewBinding: FragItemsListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
            var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
            init {
                itemView.setOnClickListener(listener)
                itemView.setOnLongClickListener(listener)
            }
        }

        override fun getItemCount() = mItems.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(FragItemsListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val newItem = mItems[position]

            holder.apply {
                itemView.tag = newItem // store updated item

                viewBinding.name.text = newItem.name

                viewBinding.itemPrice.text = NumberFormat.getCurrencyInstance().format(newItem.price)

                viewBinding.dinerIcons.removeAllViews()

                when(newItem.diners.size) {
                    0 -> {
                        viewBinding.dinerSummary.setText(R.string.items_no_diners_warning)
                        viewBinding.dinerSummary.setTextColor(errorTextColor)
                    }
                    mNumberOfDiners -> {
                        viewBinding.dinerSummary.setText(R.string.items_shared_by_everyone)
                        viewBinding.dinerSummary.setTextColor(normalTextColor)
                    }
                    else -> {
                        viewBinding.dinerSummary.text = ""
                        newItem.diners.forEach { diner ->
                            val icon = ContactIcon(context)
                            icon.setSelectable(false)

                            val dim = (24 * context.resources.displayMetrics.density).toInt()
                            val params = LinearLayout.LayoutParams(dim, dim)
                            params.marginEnd = (2 * context.resources.displayMetrics.density).toInt()
                            icon.layoutParams = params

                            icon.setContact(diner.asContact(), false)
                            viewBinding.dinerIcons.addView(icon)
                        }
                    }
                }

                viewBinding.remove.setOnClickListener {
                    itemViewModel.removeItem(newItem)
                    //TODO handle orphan items / discounts from removed item
                }

                viewBinding.cardBackground.transitionName = "container" + newItem.id

                if (newItem.id == itemIdForNewItemTransition) {
                    // Remove existing OnPreDrawListener
                    preDrawListener?.also {
                        viewBinding.cardBackground.viewTreeObserver.removeOnPreDrawListener(it)
                    }

                    // Create, store, and add new OnPreDrawListener
                    preDrawListener = object: ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            viewBinding.cardBackground.viewTreeObserver.removeOnPreDrawListener(this)
                            itemIdForNewItemTransition = null

                            (requireParentFragment() as? BillSplitFragment)?.startNewItemReturnTransition(viewBinding, newItem)
                            return true
                        }
                    }
                    viewBinding.cardBackground.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
            }
        }

        suspend fun updateDataSet(items: List<Item>?=null, numberOfDiners: Int?=null) {
            val newItems = items ?: mItems

            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = mItems.size

                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newItems[newPosition].id == mItems[oldPosition].id

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val newItem = newItems[newPosition]
                    val oldItem = mItems[oldPosition]

                    return newItem.id == oldItem.id &&
                            newItem.name == oldItem.name &&
                            newItem.price == oldItem.price &&
                            newItem.dinerIds == oldItem.dinerIds
                }
            })

            val adapter = this
            withContext(Dispatchers.Main) {
                adapter.notifyItemChanged(newItems.size - 1) // Clears BottomOffset from old last item
                adapter.notifyItemChanged(newItems.size - 2) // Needed to add BottomOffset in case last item is removed

                mItems = newItems
                mNumberOfDiners = numberOfDiners ?: mNumberOfDiners
                diffResult.dispatchUpdatesTo(adapter)
            }
        }
    }
}