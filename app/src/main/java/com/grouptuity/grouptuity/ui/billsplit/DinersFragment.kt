package com.grouptuity.grouptuity.ui.billsplit

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Item
import com.grouptuity.grouptuity.databinding.FragDinersBinding
import com.grouptuity.grouptuity.databinding.FragDinersListitemBinding
import com.grouptuity.grouptuity.databinding.FragItemsListitemBinding
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DinersFragment: Fragment() {
    companion object {
        @JvmStatic
        fun newInstance() = DinersFragment()
    }

    // TODO snackbar note when removing diner
    // TODO Removing diner causes subtotal updates before removal completes -> merge into single dataset

    private var binding by setNullOnDestroy<FragDinersBinding>()
    private lateinit var dinersViewModel: DinersViewModel
    private var suppressAutoScroll = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dinersViewModel = ViewModelProvider(this).get(DinersViewModel::class.java)
        binding = FragDinersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerAdapter = DinersRecyclerViewAdapter(requireContext(), dinersViewModel, object:
            RecyclerViewListener {
            override fun onClick(view: View) {
                suppressAutoScroll = true // Retain scroll position when returning to this fragment

                // Exit transition is needed to prevent next fragment from appearing immediately
                requireParentFragment().exitTransition = Hold().apply {
                    duration = 0L
                    addTarget(requireParentFragment().requireView())
                }

                val viewBinding = FragDinersListitemBinding.bind(view)

                findNavController().navigate(
                    BillSplitFragmentDirections.viewDinerDetails(view.tag as Diner),
                    FragmentNavigatorExtras(
                        viewBinding.cardBackground to viewBinding.cardBackground.transitionName,
                        viewBinding.contactIcon.image to viewBinding.contactIcon.image.transitionName
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

            requireParentFragment().apply {
                postponeEnterTransition()
                viewTreeObserver.addOnPreDrawListener {
                    startPostponedEnterTransition()
                    true
                }
            }

            // Add a spacer to the last item in the list to ensure it is not cut off when the toolbar
            // and floating action button are visible
            addItemDecoration(RecyclerViewBottomOffset(resources.getDimension(R.dimen.recyclerview_bottom_offset).toInt()))

            // When adding additional items, the RecyclerViewBottomOffset decoration is removed from
            // the former last item. This setting prevents an unnecessary flashing animation.
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        binding.addSelf.button.setOnClickListener { dinersViewModel.addSelfToBill() }

        dinersViewModel.dinerData.observe(viewLifecycleOwner, { dinerData ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(dinerData) }

            if (dinerData.isEmpty()){
                // TODO binding.addDinersHint.visibility = View.VISIBLE
                binding.addSelf.button.visibility = View.VISIBLE
            }
            else {
                //TODO
                //binding.addDinersHint.visibility =  View.GONE
                binding.addSelf.button.visibility = View.GONE
            }
        })
    }
}

class DinersRecyclerViewAdapter(private val context: Context,
                                private val dinersViewModel: DinersViewModel,
                                private val listener: RecyclerViewListener): RecyclerView.Adapter<DinersRecyclerViewAdapter.ViewHolder>() {

    private var mDataSet = emptyList<Pair<Diner,String>>()

    inner class ViewHolder(val viewBinding: FragDinersListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
        init {
            itemView.setOnClickListener(listener)
            itemView.setOnLongClickListener(listener)
        }
    }

    override fun getItemCount() = mDataSet.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(FragDinersListitemBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (newDiner, dinerSubtotal) = mDataSet[position]

        holder.apply {
            itemView.tag = newDiner // store updated data

            /* Bug Fix: Return CardViewExpandTransition from the details fragment sets these views
               to transparent and fades them to opaque. If the first diner is removed after
               returning from the details fragment, these views are transparent if the "self" diner
               is added.
            */
            viewBinding.contactIcon.alpha = 1f
            viewBinding.name.alpha = 1f
            viewBinding.message.alpha = 1f
            viewBinding.individualSubtotal.alpha = 1f
            viewBinding.remove.alpha = 1f

            viewBinding.contactIcon.setContact(newDiner.asContact(), false)

            viewBinding.name.text = newDiner.name

            viewBinding.remove.setOnClickListener {
                dinersViewModel.removeDiner(newDiner)
                //TODO handle orphan items / discounts from removed diner
            }

            if(newDiner.itemIds.isEmpty()) {
                viewBinding.message.text = context.resources.getString(R.string.diners_zero_items)
            } else {
                viewBinding.message.text = context.resources.getQuantityString(
                    R.plurals.diners_num_items_with_subtotal,
                    newDiner.itemIds.size,
                    newDiner.itemIds.size,
                    dinerSubtotal)
            }

            viewBinding.cardBackground.transitionName = "container" + newDiner.lookupKey
            viewBinding.contactIcon.image.transitionName = "image" + newDiner.lookupKey
        }
    }

    suspend fun updateDataSet(newDataSet: List<Pair<Diner, String>>) {
        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mDataSet.size

            override fun getNewListSize() = newDataSet.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newDataSet[newPosition].first.id == mDataSet[oldPosition].first.id

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val newDiner = newDataSet[newPosition].first
                val oldDiner = mDataSet[oldPosition].first

                return newDiner.id == oldDiner.id &&
                        newDiner.name == oldDiner.name &&
                        newDiner.photoUri == oldDiner.photoUri &&
                        newDiner.itemIds.size == oldDiner.itemIds.size &&
                        newDataSet[newPosition].second == mDataSet[oldPosition].second
            }
        })

        val adapter = this
        withContext(Dispatchers.Main) {
            adapter.notifyItemChanged(mDataSet.size - 1) // Clears BottomOffset from old last item
            adapter.notifyItemChanged(mDataSet.size - 2) // Needed to add BottomOffset in case last item is removed

            mDataSet = newDataSet
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}