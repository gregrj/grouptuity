package com.grouptuity.grouptuity.ui.billsplit.discountentry

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.databinding.FragDiscountEntryReimbursementBinding
import com.grouptuity.grouptuity.databinding.ListDinerBinding
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.math.abs


class ReimbursementFragment: Fragment() {
    private var binding by setNullOnDestroy<FragDiscountEntryReimbursementBinding>()
    private lateinit var discountEntryViewModel: DiscountEntryViewModel
    private lateinit var reimburseeRecyclerAdapter: ReimburseeSelectionRecyclerViewAdapter
    private var observing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        discountEntryViewModel = ViewModelProvider(requireActivity()).get(DiscountEntryViewModel::class.java)
        binding = FragDiscountEntryReimbursementBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reimburseeRecyclerAdapter = ReimburseeSelectionRecyclerViewAdapter(
            requireContext(),
            object: RecyclerViewListener {
                override fun onClick(view: View) {
                    discountEntryViewModel.toggleReimburseeSelection(view.tag as Diner)
                }
                override fun onLongClick(view: View): Boolean { return false }
            }
        )

        binding.selections.list.apply {
            adapter = reimburseeRecyclerAdapter

            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

            val colorBackground = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
            val colorBackgroundVariant = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

            itemAnimator = object: DefaultItemAnimator() {
                override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean { return true }

                override fun animateChange(oldHolder: RecyclerView.ViewHolder?, newHolder: RecyclerView.ViewHolder?, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
                    oldHolder?.apply {
                        if (oldHolder === newHolder) {
                            val diner = (this as ReimburseeSelectionRecyclerViewAdapter.ViewHolder).itemView.tag as Diner

                            oldHolder.itemView.setBackgroundColor(if(discountEntryViewModel.isReimburseeSelected(diner)) colorBackgroundVariant else colorBackground)
                            dispatchAnimationFinished(newHolder)
                        }
                    }
                    return false
                }
            }
        }

        discountEntryViewModel.formattedCost.observe(viewLifecycleOwner, { cost: String -> binding.priceTextview.text = cost })
        discountEntryViewModel.costBackspaceButtonVisible.observe(viewLifecycleOwner) { binding.buttonBackspace.visibility = if(it) View.VISIBLE else View.GONE }
        discountEntryViewModel.costEditButtonVisible.observe(viewLifecycleOwner) { binding.buttonEdit.visibility = if(it) View.VISIBLE else View.GONE }

        binding.buttonBackspace.setOnClickListener { discountEntryViewModel.removeDigitFromCost() }
        binding.buttonBackspace.setOnLongClickListener {
            discountEntryViewModel.resetCost()
            true
        }

        binding.selections.selectAll.text = getString(R.string.discountentry_button_selectalldiners)
        binding.selections.selectAll.setOnClickListener { discountEntryViewModel.selectAllReimbursees() }
        discountEntryViewModel.selectAllReimburseesButtonDisabled.observe(viewLifecycleOwner) { binding.selections.selectAll.isEnabled = !it }

        val textColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnBackground, it, true) }.data
        val textColorDeemphasized = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnBackgroundLowEmphasis, it, true) }.data
        discountEntryViewModel.clearReimburseeButtonDeemphasized.observe(viewLifecycleOwner) {
            binding.selections.clearSelections.setTextColor(if(it) textColorDeemphasized else textColor)
        }
        binding.selections.clearSelections.setOnClickListener { discountEntryViewModel.clearReimburseeSelections() }

        discountEntryViewModel.costNumberPadVisible.observe(viewLifecycleOwner, {
            if(it) {
                binding.priceTextview.setOnClickListener(null)
                binding.priceTextview.setOnTouchListener { _, _ -> true }

            } else {
                binding.priceTextview.setOnClickListener { discountEntryViewModel.editCost() }
                binding.priceTextview.setOnTouchListener(null)
            }
        })

        binding.selections.swipeRefreshLayout.isEnabled = false
        binding.selections.swipeRefreshLayout.isRefreshing = true

        discountEntryViewModel.diners.observe(viewLifecycleOwner, { diners -> lifecycleScope.launch { reimburseeRecyclerAdapter.updateDataSet(diners = diners) }})
        discountEntryViewModel.reimburseeSelections.observe(viewLifecycleOwner, { selections -> lifecycleScope.launch { reimburseeRecyclerAdapter.updateDataSet(selections = selections) }})
        discountEntryViewModel.recipientReimbursementDebts.observe(viewLifecycleOwner, { debts -> lifecycleScope.launch { reimburseeRecyclerAdapter.updateDataSet(reimbursementDebts = debts) }})
        discountEntryViewModel.purchaserReimbursementCredits.observe(viewLifecycleOwner, { credits -> lifecycleScope.launch { reimburseeRecyclerAdapter.updateDataSet(reimbursementCredits = credits) }})
    }

    private inner class ReimburseeSelectionRecyclerViewAdapter(val context: Context, val listener: RecyclerViewListener): RecyclerView.Adapter<ReimburseeSelectionRecyclerViewAdapter.ViewHolder>() {
        private var mDinerList = emptyArray<Diner>()
        private var mSelections = emptySet<Long>()
        private var mReimbursementDebts = emptyMap<Long, Double>()
        private var mReimbursementCredits = emptyMap<Long, Double>()
        private val currencyFormatter = NumberFormat.getCurrencyInstance()

        val colorBackground = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
        val colorBackgroundVariant = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorBackgroundVariant, it, true) }.data

        inner class ViewHolder(val viewBinding: ListDinerBinding): RecyclerView.ViewHolder(viewBinding.root) {
            init {
                itemView.setOnClickListener(listener)
                itemView.setOnLongClickListener(listener)
            }
        }

        override fun getItemCount() = mDinerList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ListDinerBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val newDiner = mDinerList[position]

            holder.apply {
                itemView.tag = newDiner // store updated data

                val isSelected = mSelections.contains(newDiner.id)

                viewBinding.contactIcon.setContact(newDiner.contact, isSelected)

                viewBinding.name.text = newDiner.name

                itemView.setBackgroundColor(if(isSelected) colorBackgroundVariant else colorBackground)

                if(newDiner.id in mReimbursementCredits) {
                    viewBinding.message.visibility = View.VISIBLE
                    viewBinding.message.setTypeface(viewBinding.message.typeface, Typeface.BOLD)

                    if(newDiner.id in mReimbursementDebts) {
                        val netReimbursementNumerical = mReimbursementCredits[newDiner.id]!! - mReimbursementDebts[newDiner.id]!!
                        val netReimbursementString = currencyFormatter.format(abs(netReimbursementNumerical))

                        viewBinding.message.text = if(netReimbursementString == currencyFormatter.format(0.0)) {
                            context.getString(R.string.discountentry_reimbursement_neutral)
                        } else {
                            val fullDebtString = currencyFormatter.format(mReimbursementDebts[newDiner.id])
                            val fullCreditString = currencyFormatter.format(mReimbursementCredits[newDiner.id])
                            if (netReimbursementNumerical > 0.0) {
                                context.getString(R.string.discountentry_reimbursement_receive_reduced, netReimbursementString, fullCreditString, fullDebtString)
                            } else {
                                context.getString(R.string.discountentry_reimbursement_pay_reduced, netReimbursementString, fullDebtString, fullCreditString)
                            }
                        }
                    } else {
                        viewBinding.message.text = context.getString(R.string.discountentry_reimbursement_receive, currencyFormatter.format(mReimbursementCredits[newDiner.id]))
                    }
                } else if(newDiner.id in mReimbursementDebts) {
                    viewBinding.message.visibility = View.VISIBLE
                    viewBinding.message.setTypeface(Typeface.create(viewBinding.message.typeface, Typeface.NORMAL), Typeface.NORMAL)
                    viewBinding.message.text = context.getString(R.string.discountentry_reimbursement_pay, currencyFormatter.format(mReimbursementDebts[newDiner.id]))
                } else {
                    viewBinding.message.visibility = View.GONE
                }
            }
        }

        suspend fun updateDataSet(diners: Array<Diner>?=null, selections: Set<Long>?=null, reimbursementDebts: Map<Long, Double>?=null, reimbursementCredits: Map<Long, Double>?=null) {
            val newDiners = diners ?: mDinerList
            val newSelections = selections ?: mSelections
            val newReimbursementDebts = reimbursementDebts ?: mReimbursementDebts
            val newReimbursementCredits = reimbursementCredits ?: mReimbursementCredits

            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = mDinerList.size

                override fun getNewListSize() = newDiners.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newDiners[newPosition].id == mDinerList[oldPosition].id

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val newDiner = newDiners[newPosition]
                    val oldDiner = mDinerList[oldPosition]

                    return newDiner.id == oldDiner.id &&
                            newSelections.contains(newDiner.id) == mSelections.contains(oldDiner.id) &&
                            newReimbursementDebts[newDiner.id] == mReimbursementDebts[oldDiner.id] &&
                            newReimbursementCredits[newDiner.id] == mReimbursementCredits[oldDiner.id]
                }
            })

            val adapter = this
            withContext(Dispatchers.Main) {
                mDinerList = newDiners
                mSelections = newSelections
                mReimbursementDebts = newReimbursementDebts
                mReimbursementCredits = newReimbursementCredits

                diffResult.dispatchUpdatesTo(adapter)
                binding.selections.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}