package com.grouptuity.grouptuity.ui.billsplit.addressbook

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.transition.Transition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.grouptuity.grouptuity.AppViewModel
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Contact
import com.grouptuity.grouptuity.databinding.FragAddressBookBinding
import com.grouptuity.grouptuity.databinding.FragAddressBookListitemBinding
import com.grouptuity.grouptuity.ui.custom.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.setNullOnDestroy
import com.grouptuity.grouptuity.ui.custom.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.custom.transitions.Revealable
import com.grouptuity.grouptuity.ui.custom.transitions.RevealableImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class AddressBookFragment: Fragment(), Revealable by RevealableImpl() {
    private val args: AddressBookFragmentArgs by navArgs()
    private lateinit var appViewModel: AppViewModel
    private lateinit var addressBookViewModel: AddressBookViewModel
    private var binding by setNullOnDestroy<FragAddressBookBinding>()
    private lateinit var recyclerAdapter: AddressBookRecyclerViewAdapter
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        appViewModel = ViewModelProvider(requireActivity()).get(AppViewModel::class.java)
        addressBookViewModel = ViewModelProvider(requireActivity()).get(AddressBookViewModel::class.java)

        binding = FragAddressBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)

        enterTransition = CircularRevealTransition(
                binding.fadeView,
                binding.revealedLayout,
                args.originParams,
                resources.getInteger(R.integer.frag_transition_duration).toLong(),
                true).addListener(object: Transition.TransitionListener {
            override fun onTransitionStart(transition: Transition) { addressBookViewModel.notifyTransitionStarted() }
            override fun onTransitionEnd(transition: Transition) { addressBookViewModel.notifyTransitionFinished() }
            override fun onTransitionCancel(transition: Transition) { }
            override fun onTransitionPause(transition: Transition) { }
            override fun onTransitionResume(transition: Transition) { }
        })

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        backPressedCallback = object: OnBackPressedCallback(true) { override fun handleOnBackPressed() { onBackPressed() } }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        permissionRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            if (it) {
                addressBookViewModel.requestContactsRefresh()
            } else {
                addressBookViewModel.requestContactsRefresh(acquirePermissionsIfNeeded = false)
                Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_no_read_contacts_permission, Snackbar.LENGTH_LONG).show()
            }
        }

        setupContactList()
    }

    override fun onResume() {
        super.onResume()
        binding.fadeView.visibility = View.GONE


    }

    private fun onBackPressed() {
        closeFragment(false)
    }

    private fun closeFragment(addSelectionsToBill: Boolean) {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

        // Freeze UI in place as the fragment closes
        addressBookViewModel.freezeOutput()

        // Add selected contacts to bill before closing fragment
        if(addSelectionsToBill) {
            // TODO addressBookViewModel.addSelectedContactsToBill()
        }

        // Closing animation shrinking fragment into the FAB of the previous fragment.
        // Transition is defined here to incorporate dynamic changes to window insets.
        returnTransition = CircularRevealTransition(
                binding.fadeView,
                binding.revealedLayout,
                args.originParams.withInsetsOn(binding.fab),
                resources.getInteger(R.integer.frag_transition_duration).toLong(),
                false)

        requireActivity().onBackPressed()
    }

    private fun setupToolbar() { }

    private fun setupContactList() {
        recyclerAdapter = AddressBookRecyclerViewAdapter(requireContext(), object : RecyclerViewListener {
            override fun onClick(view: View) {
                //TODO
            }

            override fun onLongClick(view: View): Boolean {
                return false
            }
        })

        binding.list.apply {
            adapter = recyclerAdapter

            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

            itemAnimator = object : DefaultItemAnimator() {
                override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean {
                    return true
                }

                override fun animateChange(oldHolder: RecyclerView.ViewHolder?, newHolder: RecyclerView.ViewHolder?, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
                    oldHolder?.apply {
                        if (oldHolder === newHolder) {
                            val contact = (this as AddressBookRecyclerViewAdapter.ViewHolder).itemView.tag as Contact

                            if (addressBookViewModel.isContactSelected(contact)) {
                                oldHolder.itemView.setBackgroundColor(TypedValue().also { context.theme.resolveAttribute(R.attr.colorSurfaceVariant, it, true) }.data)
                            } else {
                                oldHolder.itemView.setBackgroundColor(TypedValue().also { context.theme.resolveAttribute(R.attr.colorSurface, it, true) }.data)
                            }
                            dispatchAnimationFinished(newHolder)
                        }
                    }
                    return false
                }
            }
        }

        addressBookViewModel.displayedContacts.observe(viewLifecycleOwner) { lifecycleScope.launch { recyclerAdapter.updateDataSet(contacts = it) } }
        addressBookViewModel.selections.observe(viewLifecycleOwner) { lifecycleScope.launch { recyclerAdapter.updateDataSet(selections = it.keys) } }

        addressBookViewModel.showRefreshAnimation.observe(viewLifecycleOwner) { binding.swipeRefreshLayout.isRefreshing = it }
        binding.swipeRefreshLayout.setOnRefreshListener { addressBookViewModel.requestContactsRefresh() }

        addressBookViewModel.acquireReadContactsPermissionEvent.observe(viewLifecycleOwner) {
            // Permission needed to proceed with reading device contacts
            it.consume()?.apply {
                if(shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
                            .setTitle(resources.getString(R.string.addressbook_alert_read_contacts_title))
                            .setMessage(resources.getString(R.string.addressbook_alert_read_contacts_message))
                            .setCancelable(false)
                            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ ->
                                addressBookViewModel.requestContactsRefresh(acquirePermissionsIfNeeded = false)
                                Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_no_read_contacts_permission, Snackbar.LENGTH_LONG).show()
                            }
                            .setPositiveButton(resources.getString(R.string.proceed)) { _, _ ->
                                permissionRequestLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                            .show()
                }
                else {
                    permissionRequestLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        }
    }
}

class AddressBookRecyclerViewAdapter(val context: Context, val listener: RecyclerViewListener): RecyclerView.Adapter<AddressBookRecyclerViewAdapter.ViewHolder>() {
    private var mContacts = emptyList<Contact>()
    private var mSelections = emptySet<String>()

    private val surfaceColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorSurface, it, true) }.data
    private val selectedSurfaceColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorSurfaceVariant, it, true) }.data
    private val contentColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnSurface, it, true) }.data
    private val hiddenContentColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnSurfaceLowEmphasis, it, true) }.data

    inner class ViewHolder(val viewBinding: FragAddressBookListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {

        init {
            itemView.setOnClickListener(listener)
            itemView.setOnLongClickListener(listener)
        }
    }

    override fun getItemCount() = mContacts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(FragAddressBookListitemBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val newContact = mContacts[position]

        holder.apply {
            itemView.tag = newContact // store updated data

            val isSelected = mSelections.contains(newContact.lookupKey)

            viewBinding.contactIcon.setContact(newContact, isSelected)

            viewBinding.name.text = newContact.name

            itemView.setBackgroundColor(if(isSelected) selectedSurfaceColor else surfaceColor)

            when(newContact.visibility) {
                Contact.FAVORITE -> {
                    viewBinding.name.setTextColor(contentColor)

                    viewBinding.visibilityDescription.visibility = View.GONE

                    viewBinding.unhide.visibility = View.GONE
                    viewBinding.unfavorite.visibility = View.VISIBLE
                }
                Contact.HIDDEN -> {
                    viewBinding.name.setTextColor(hiddenContentColor)

                    viewBinding.visibilityDescription.apply {
                        setTextColor(hiddenContentColor)
                        text = resources.getString(R.string.addressbook_listitem_hidden)
                        visibility = View.VISIBLE
                    }

                    viewBinding.unfavorite.visibility = View.GONE
                    viewBinding.unhide.visibility = View.VISIBLE
                }
                else -> {
                    viewBinding.name.setTextColor(contentColor)

                    viewBinding.visibilityDescription.visibility = View.GONE

                    viewBinding.unhide.visibility = View.GONE
                    viewBinding.unfavorite.visibility = View.GONE
                }
            }
        }
    }

    suspend fun updateDataSet(contacts: List<Contact>? = null, selections: Set<String>? = null) {
        val newContacts = contacts ?: mContacts
        val newSelections = selections ?: mSelections

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mContacts.size

            override fun getNewListSize() = newContacts.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newContacts[newPosition].lookupKey == mContacts[oldPosition].lookupKey

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val newContact = newContacts[newPosition]
                val oldContact = mContacts[oldPosition]

                return newContact.lookupKey == oldContact.lookupKey &&
                        newSelections.contains(newContact.lookupKey) == mSelections.contains(oldContact.lookupKey) &&
                        newContact.visibility == oldContact.visibility
            }
        })

        val adapter = this
        withContext(Dispatchers.Main) {
//            adapter.notifyItemChanged(mDataSet.size - 1) // clears BottomOffset from old last item
            mContacts = newContacts
            mSelections = newSelections
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}