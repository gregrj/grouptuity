package com.grouptuity.grouptuity.ui.billsplit.discountentry

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Item
import com.grouptuity.grouptuity.databinding.FragDiscountEntryListBinding
import com.grouptuity.grouptuity.databinding.FragDiscountEntryPropertiesBinding
import com.grouptuity.grouptuity.databinding.ListDinerBinding
import com.grouptuity.grouptuity.databinding.ListItemBinding
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import com.grouptuity.grouptuity.ui.custom.views.ContactIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

class PropertiesFragment: Fragment() {
    private var binding by setNullOnDestroy<FragDiscountEntryPropertiesBinding>()
    private lateinit var discountEntryViewModel: DiscountEntryViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        discountEntryViewModel = ViewModelProvider(requireActivity()).get(DiscountEntryViewModel::class.java)
        binding = FragDiscountEntryPropertiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.overScrollMode = View.OVER_SCROLL_NEVER
        binding.viewPager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when(position) {
                    0 -> { discountEntryViewModel.switchDiscountBasisToItems() }
                    1 -> { discountEntryViewModel.switchDiscountBasisToDiners() }
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                when(state) {
                    ViewPager2.SCROLL_STATE_IDLE -> { discountEntryViewModel.discountBasisInputLocked.value = false }
                    else -> { discountEntryViewModel.discountBasisInputLocked.value = true }
                }
            }
        })
        binding.viewPager.adapter = object: FragmentStateAdapter(this) {
            var fragments = mutableListOf(Fragment(), Fragment())

            init {
                discountEntryViewModel.loadItemListFragmentEvent.observe(viewLifecycleOwner) {
                    it.consume()?.also {
                        if(fragments[0] !is ItemsListFragment) {
                            fragments[0] = ItemsListFragment()
                            notifyItemChanged(0)
                        }
                    }
                }

                discountEntryViewModel.loadDinerListFragmentEvent.observe(viewLifecycleOwner) {
                    it.consume()?.also {
                        if(fragments[1] !is DinersListFragment) {
                            fragments[1] = DinersListFragment()
                            notifyItemChanged(1)
                        }
                    }
                }
            }

            override fun getItemCount() = 2
            override fun getItemId(position: Int) = when(position) {
                0 -> if(fragments[0] is ItemsListFragment) 0L else 1L
                1 -> if(fragments[1] is DinersListFragment) 2L else 3L
                else -> RecyclerView.NO_ID
            }
            override fun containsItem(itemId: Long) = when(itemId) {
                0L -> fragments[0] is ItemsListFragment
                1L -> fragments[0] !is ItemsListFragment
                2L -> fragments[1] is DinersListFragment
                3L -> fragments[1] !is DinersListFragment
                else -> false
            }
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        discountEntryViewModel.isDiscountOnItems.observe(viewLifecycleOwner) { binding.viewPager.currentItem = if(it) 0 else 1 }

        discountEntryViewModel.discountBasisButtonState.observe(viewLifecycleOwner) { (onItems, inTertiary) ->
            binding.buttonItems.isEnabled = !onItems
            binding.buttonItems.hasColor = !inTertiary

            binding.buttonDiners.isEnabled = onItems
            binding.buttonDiners.hasColor = !inTertiary
        }

        discountEntryViewModel.formattedPrice.observe(viewLifecycleOwner, { price: String -> binding.priceTextview.text = price })
        discountEntryViewModel.priceBackspaceButtonVisible.observe(viewLifecycleOwner) { binding.buttonBackspace.visibility = if(it) View.VISIBLE else View.GONE }
        discountEntryViewModel.priceEditButtonVisible.observe(viewLifecycleOwner) { binding.buttonEdit.visibility = if(it) View.VISIBLE else View.GONE }

        discountEntryViewModel.priceNumberPadVisible.observe(viewLifecycleOwner, {
            if(it) {
                binding.priceTextview.setOnClickListener(null)
                binding.priceTextview.setOnTouchListener { _, _ -> true }
            } else {
                binding.priceTextview.setOnClickListener { discountEntryViewModel.editPrice() }
                binding.priceTextview.setOnTouchListener(null)
            }
        })

        binding.buttonBackspace.setOnClickListener { discountEntryViewModel.removeDigitFromPrice() }
        binding.buttonBackspace.setOnLongClickListener {
            discountEntryViewModel.resetPrice()
            true
        }

        binding.buttonItems.setOnClickListener {
            if(discountEntryViewModel.dinerSelections.value?.isNotEmpty() == true) {
                MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
                    .setTitle(resources.getString(R.string.discountentry_alert_switch_to_items_title))
                    .setMessage(resources.getString(R.string.discountentry_alert_switch_to_items_message))
                    .setCancelable(false)
                    .setNegativeButton(resources.getString(R.string.keep)) { _, _ -> }
                    .setPositiveButton(resources.getString(R.string.discard)) { _, _ ->
                        discountEntryViewModel.switchDiscountBasisToItems()
                        discountEntryViewModel.clearDinerSelections()
                    }
                    .show()
            } else {
                discountEntryViewModel.switchDiscountBasisToItems()
            }
        }

        binding.buttonDiners.setOnClickListener {
            if(discountEntryViewModel.itemSelections.value?.isNotEmpty() == true) {
                MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
                    .setTitle(resources.getString(R.string.discountentry_alert_switch_to_diners_title))
                    .setMessage(resources.getString(R.string.discountentry_alert_switch_to_diners_message))
                    .setCancelable(false)
                    .setNegativeButton(resources.getString(R.string.keep)) { _, _ -> }
                    .setPositiveButton(resources.getString(R.string.discard)) { _, _ ->
                        discountEntryViewModel.switchDiscountBasisToDiners()
                        discountEntryViewModel.clearItemSelections()
                    }
                    .show()
            } else {
                discountEntryViewModel.switchDiscountBasisToDiners()
            }
        }
    }
}


internal class DinersListFragment: Fragment() {
    private var binding by setNullOnDestroy<FragDiscountEntryListBinding>()
    private lateinit var discountEntryViewModel: DiscountEntryViewModel
    private lateinit var dinerRecyclerAdapter: DinerSelectionRecyclerViewAdapter
    private var observing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        discountEntryViewModel = ViewModelProvider(requireActivity()).get(DiscountEntryViewModel::class.java)
        binding = FragDiscountEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.selectAll.text = getString(R.string.discountentry_button_selectalldiners)

        dinerRecyclerAdapter = DinerSelectionRecyclerViewAdapter(
            requireContext(),
            object: RecyclerViewListener {
                override fun onClick(view: View) {
                    discountEntryViewModel.toggleDinerSelection(view.tag as Diner)
                }
                override fun onLongClick(view: View): Boolean { return false }
            }
        )

        binding.list.apply {
            adapter = dinerRecyclerAdapter

            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

            val colorBackground = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
            val colorBackgroundVariant = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

            itemAnimator = object: DefaultItemAnimator() {
                override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean { return true }

                override fun animateChange(oldHolder: RecyclerView.ViewHolder?, newHolder: RecyclerView.ViewHolder?, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
                    oldHolder?.apply {
                        if (oldHolder === newHolder) {
                            val diner = (this as DinerSelectionRecyclerViewAdapter.ViewHolder).itemView.tag as Diner

                            oldHolder.itemView.setBackgroundColor(if(discountEntryViewModel.isDinerSelected(diner)) colorBackgroundVariant else colorBackground)
                            dispatchAnimationFinished(newHolder)
                        }
                    }
                    return false
                }
            }

            discountEntryViewModel.selectAllDinersButtonDisabled.observe(viewLifecycleOwner) { binding.selectAll.isEnabled = !it }
            binding.selectAll.setOnClickListener { discountEntryViewModel.selectAllDiners() }

            val textColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnBackground, it, true) }.data
            val textColorDeemphasized = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnBackgroundLowEmphasis, it, true) }.data
            discountEntryViewModel.clearDinersButtonDeemphasized.observe(viewLifecycleOwner) {
                binding.clearSelections.setTextColor(if(it) textColorDeemphasized else textColor)
            }
            binding.clearSelections.setOnClickListener { discountEntryViewModel.clearDinerSelections() }

            binding.swipeRefreshLayout.isEnabled = false
            binding.swipeRefreshLayout.isRefreshing = true

            discountEntryViewModel.startTransition()
            discountEntryViewModel.loadDinerRecyclerViewEvent.observe(viewLifecycleOwner) {
                it.consume()?.also {
                    if(!observing) {
                        observing = true
                        discountEntryViewModel.recipientData.observe(viewLifecycleOwner) { data ->
                            lifecycleScope.launch { dinerRecyclerAdapter.updateDataSet(data) }
                        }
                    }
                }
            }
        }
    }

    private inner class DinerSelectionRecyclerViewAdapter(
        val context: Context,
        val listener: RecyclerViewListener): RecyclerView.Adapter<DinerSelectionRecyclerViewAdapter.ViewHolder>() {

        private var mRecipientData: List<Triple<Diner, Boolean, String>> = emptyList()

        val colorBackground = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
        val colorBackgroundVariant = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

        inner class ViewHolder(val viewBinding: ListDinerBinding): RecyclerView.ViewHolder(viewBinding.root) {
            init {
                itemView.setOnClickListener(listener)
                itemView.setOnLongClickListener(listener)
            }
        }

        override fun getItemCount() = mRecipientData.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ListDinerBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (newDiner, isSelected, message) = mRecipientData[position]

            holder.apply {
                itemView.tag = newDiner // store updated data

                viewBinding.contactIcon.setContact(newDiner.contact, isSelected)

                viewBinding.name.text = newDiner.name

                viewBinding.message.text = message

                if(isSelected) {
                    itemView.setBackgroundColor(colorBackgroundVariant)
                    viewBinding.message.setTypeface(viewBinding.message.typeface, Typeface.BOLD)

                } else {
                    itemView.setBackgroundColor(colorBackground)
                    viewBinding.message.setTypeface(Typeface.create(viewBinding.message.typeface, Typeface.NORMAL), Typeface.NORMAL)
                }
            }
        }

        suspend fun updateDataSet(newRecipientData: List<Triple<Diner, Boolean, String>>) {
            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = mRecipientData.size

                override fun getNewListSize() = newRecipientData.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                    newRecipientData[newPosition].first.id == mRecipientData[oldPosition].first.id

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val newDiner = newRecipientData[newPosition].first
                    val oldDiner = mRecipientData[oldPosition].first

                    return newRecipientData[newPosition].second == mRecipientData[oldPosition].second &&
                            newRecipientData[newPosition].third == mRecipientData[oldPosition].third &&
                            newDiner.name == oldDiner.name &&
                            newDiner.photoUri == oldDiner.photoUri
                }
            })

            val adapter = this
            withContext(Dispatchers.Main) {
                mRecipientData = newRecipientData
                diffResult.dispatchUpdatesTo(adapter)
                binding.list.doOnPreDraw { binding.swipeRefreshLayout.isRefreshing = false }
            }
        }
    }
}


internal class ItemsListFragment: Fragment() {
    private var binding by setNullOnDestroy<FragDiscountEntryListBinding>()
    private lateinit var discountEntryViewModel: DiscountEntryViewModel
    private lateinit var itemRecyclerAdapter: ItemSelectionRecyclerViewAdapter
    private var observing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        discountEntryViewModel = ViewModelProvider(requireActivity()).get(DiscountEntryViewModel::class.java)
        binding = FragDiscountEntryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.selectAll.text = getString(R.string.discountentry_button_selectallitems)

        itemRecyclerAdapter = ItemSelectionRecyclerViewAdapter(
            requireContext(),
            object: RecyclerViewListener {
                override fun onClick(view: View) {
                    discountEntryViewModel.toggleItemSelection(view.tag as Item)
                }
                override fun onLongClick(view: View): Boolean { return false }
            }
        )

        binding.list.apply {
            adapter = itemRecyclerAdapter

            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

            val colorBackground = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
            val colorBackgroundVariant = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

            itemAnimator = object: DefaultItemAnimator() {
                override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean { return true }

                override fun animateChange(oldHolder: RecyclerView.ViewHolder?, newHolder: RecyclerView.ViewHolder?, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
                    oldHolder?.apply {
                        if (oldHolder === newHolder) {
                            val item = (this as ItemSelectionRecyclerViewAdapter.ViewHolder).itemView.tag as Item

                            newHolder.itemView.setBackgroundColor(if(discountEntryViewModel.isItemSelected(item)) colorBackgroundVariant else colorBackground)
                            dispatchAnimationFinished(newHolder)
                        }
                    }
                    return false
                }
            }

            discountEntryViewModel.selectAllItemsButtonDisabled.observe(viewLifecycleOwner) { binding.selectAll.isEnabled = !it }
            binding.selectAll.setOnClickListener { discountEntryViewModel.selectAllItems() }

            val textColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnBackground, it, true) }.data
            val textColorDeemphasized = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnBackgroundLowEmphasis, it, true) }.data
            discountEntryViewModel.clearItemsButtonDeemphasized.observe(viewLifecycleOwner) {
                binding.clearSelections.setTextColor(if(it) textColorDeemphasized else textColor)
            }
            binding.clearSelections.setOnClickListener { discountEntryViewModel.clearItemSelections() }

            binding.swipeRefreshLayout.isEnabled = false
            binding.swipeRefreshLayout.isRefreshing = true

            discountEntryViewModel.startTransition()
            discountEntryViewModel.loadItemRecyclerViewEvent.observe(viewLifecycleOwner) {
                it.consume()?.also{
                    if(!observing) {
                        observing = true
                        discountEntryViewModel.items.observe(viewLifecycleOwner) { items ->
                            lifecycleScope.launch { itemRecyclerAdapter.updateDataSet(items = items) }
                        }
                        discountEntryViewModel.itemSelections.observe(viewLifecycleOwner) { selections ->
                            lifecycleScope.launch { itemRecyclerAdapter.updateDataSet(selections = selections) }
                        }
                        discountEntryViewModel.numberOfDiners.observe(viewLifecycleOwner) { numDiners ->
                            lifecycleScope.launch { itemRecyclerAdapter.updateDataSet(numberOfDiners = numDiners) }
                        }
                    }
                }
            }
        }
    }

    private inner class ItemSelectionRecyclerViewAdapter(
        val context: Context,
        val listener: RecyclerViewListener):
        RecyclerView.Adapter<ItemSelectionRecyclerViewAdapter.ViewHolder>() {

        private var mItemList = emptyList<Item>()
        private var mSelections = emptySet<Item>()
        private var mNumberOfDiners = 0

        val colorPrimary = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data
        val colorOnBackground = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnBackground, it, true) }.data
        val colorBackground = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
        val colorBackgroundVariant = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

        inner class ViewHolder(val viewBinding: ListItemBinding): RecyclerView.ViewHolder(viewBinding.root) {
            init {
                itemView.setOnClickListener(listener)
                itemView.setOnLongClickListener(listener)
            }
        }

        override fun getItemCount() = mItemList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ListItemBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val newItem = mItemList[position]

            holder.apply {
                itemView.tag = newItem // store updated data

                viewBinding.name.text = newItem.name

                viewBinding.itemPrice.text = NumberFormat.getCurrencyInstance().format(newItem.price)

                // TODO show discount magnitude on item prices

                viewBinding.dinerIcons.removeAllViews()

                when(newItem.dinerIds.size) {
                    0 -> {
                        viewBinding.dinerSummary.setText(R.string.discountentry_foritems_no_diners_warning)
                        viewBinding.dinerSummary.setTextColor(colorPrimary)
                    }
                    mNumberOfDiners -> {
                        viewBinding.dinerSummary.setText(R.string.discountentry_foritems_shared_by_everyone)
                        viewBinding.dinerSummary.setTextColor(colorOnBackground)
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

                            icon.setContact(diner.contact, false)
                            viewBinding.dinerIcons.addView(icon)
                        }
                    }
                }

                val isSelected = mSelections.contains(newItem)

                itemView.setBackgroundColor(if(isSelected) colorBackgroundVariant else colorBackground)
            }
        }

        // TODO consolidate into data
        suspend fun updateDataSet(items: List<Item>?=null, selections: Set<Item>?=null, numberOfDiners: Int? = null) {
            val newItems = items ?: mItemList
            val newSelections = selections ?: mSelections
            val newNumberOfDiners = numberOfDiners ?: mNumberOfDiners

            val adapter = this

            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = mItemList.size

                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newItems[newPosition].id == mItemList[oldPosition].id

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val newItem = newItems[newPosition]
                    val oldItem = mItemList[oldPosition]

                    return newItem.id == oldItem.id && newSelections.contains(newItem) == mSelections.contains(oldItem)
                }
            })

            withContext(Dispatchers.Main) {
                mItemList = newItems
                mSelections = newSelections
                mNumberOfDiners = newNumberOfDiners

                diffResult.dispatchUpdatesTo(adapter)

                binding.list.doOnPreDraw { binding.swipeRefreshLayout.isRefreshing = false }
            }
        }
    }
}
