package com.grouptuity.grouptuity.ui.billsplit

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.Diner
import com.grouptuity.grouptuity.databinding.FragDinersBinding
import com.grouptuity.grouptuity.databinding.FragDinersListitemBinding
import com.grouptuity.grouptuity.ui.util.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.util.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.util.views.focusAndShowKeyboard
import com.grouptuity.grouptuity.ui.util.views.setNullOnDestroy
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
    private var dinerIdForNewDinerTransition: String? = null

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

                (requireActivity() as MainActivity).storeViewAsBitmap(requireParentFragment().requireView())

                findNavController().navigate(
                    BillSplitFragmentDirections.viewDinerDetails((view.tag as Diner).id),
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

            // Add a spacer to the last item in the list to ensure it is not cut off when the toolbar
            // and floating action button are visible
            addItemDecoration(RecyclerViewBottomOffset(resources.getDimension(R.dimen.recyclerview_bottom_offset).toInt()))

            // When adding additional items, the RecyclerViewBottomOffset decoration is removed from
            // the former last item. This setting prevents an unnecessary flashing animation.
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        binding.addSelf.button.setOnClickListener {
            dinersViewModel.addUserOrGetAccounts()?.apply {
                // User name has not been set so prompt user before adding self to the bill
                promptForUserName(this)
            }
        }

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

        if (dinerIdForNewDinerTransition == null) {
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

    fun setSharedElementDinerId(dinerId: String) {
        dinerIdForNewDinerTransition = dinerId
    }

    private fun promptForUserName(suggestions: List<String>) {
        val editTextDialog = MaterialAlertDialogBuilder(ContextThemeWrapper(requireContext(), R.style.AlertDialog))
            .setIcon(R.drawable.ic_person_24dp)
            .setTitle("Displaying your name")
            .setView(R.layout.dialog_edit_text_user)
            .setNegativeButton(resources.getString(R.string.skip)) { _, _ ->
                dinersViewModel.addSelfToBill(null)
            }
            .setPositiveButton(resources.getString(R.string.save)) { dialog, _ ->
                (dialog as? AlertDialog)?.findViewById<EditText>(R.id.edit_text)?.text?.toString()?.apply {
                    dinersViewModel.addSelfToBill(this)
                }
            }.create()
        editTextDialog.show()

        // Positive button is only enabled when the EditText is not empty
        editTextDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        editTextDialog.findViewById<EditText>(R.id.edit_text)?.also { editText ->
            editText.requestFocus()
            editText.addTextChangedListener {
                editTextDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !it.isNullOrBlank()
            }
            editText.focusAndShowKeyboard()
        }
    }

    private inner class DinersRecyclerViewAdapter(private val context: Context,
                                    private val dinersViewModel: DinersViewModel,
                                    private val listener: RecyclerViewListener): RecyclerView.Adapter<DinersRecyclerViewAdapter.ViewHolder>() {

        private var mDataSet = emptyList<Triple<Diner, String, String>>()

        inner class ViewHolder(val viewBinding: FragDinersListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
            var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
            init {
                itemView.setOnClickListener(listener)
                itemView.setOnLongClickListener(listener)
            }
        }

        override fun getItemCount() = mDataSet.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(FragDinersListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (newDiner, dinerSubtotal, dinerItemsAndSubtotalMessage) = mDataSet[position]

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
                }

                viewBinding.message.text = dinerItemsAndSubtotalMessage

                viewBinding.cardBackground.transitionName = "container" + newDiner.id
                viewBinding.contactIcon.image.transitionName = "image" + newDiner.id

                if (newDiner.id == dinerIdForNewDinerTransition) {
                    // Remove existing OnPreDrawListener
                    preDrawListener?.also {
                        viewBinding.cardBackground.viewTreeObserver.removeOnPreDrawListener(it)
                    }

                    // Create, store, and add new OnPreDrawListener
                    preDrawListener = object: ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            viewBinding.cardBackground.viewTreeObserver.removeOnPreDrawListener(this)
                            dinerIdForNewDinerTransition = null

                            (requireParentFragment() as? BillSplitFragment)?.startNewDinerReturnTransition(viewBinding, newDiner, dinerSubtotal)
                            return true
                        }
                    }
                    viewBinding.cardBackground.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
            }
        }

        suspend fun updateDataSet(newDataSet: List<Triple<Diner, String, String>>) {
            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = mDataSet.size

                override fun getNewListSize() = newDataSet.size

                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                    newDataSet[newPosition].first.id == mDataSet[oldPosition].first.id

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val newDiner = newDataSet[newPosition].first
                    val oldDiner = mDataSet[oldPosition].first

                    return newDiner.id == oldDiner.id &&
                            newDiner.name == oldDiner.name &&
                            newDiner.photoUri == oldDiner.photoUri &&
                            newDataSet[newPosition].second == mDataSet[oldPosition].second &&
                            newDataSet[newPosition].third == mDataSet[oldPosition].third
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
}