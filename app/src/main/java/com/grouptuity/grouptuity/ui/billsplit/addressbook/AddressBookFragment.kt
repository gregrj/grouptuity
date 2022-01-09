package com.grouptuity.grouptuity.ui.billsplit.addressbook

import android.Manifest
import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import androidx.transition.Transition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.Contact
import com.grouptuity.grouptuity.databinding.FragAddressBookBinding
import com.grouptuity.grouptuity.databinding.FragAddressBookListitemBinding
import com.grouptuity.grouptuity.ui.util.BaseUIFragment
import com.grouptuity.grouptuity.ui.util.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.util.views.RecyclerViewListener
import com.grouptuity.grouptuity.ui.util.views.setupToolbarSecondaryTertiaryAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO rewind when someone is favorited

// TODO Re-load in background when opening
// TODO hiding non-device contacts (e.g., Contact 6-x-F)
// TODO after selecting contact in search mode, should list re-sort? otherwise, stays in search order
// TODO AddressBook Sections by letter for rapidly finding right place when scrolling
// TODO remove redundant updateContactList calls during mode transitions
// TODO recyclerview retains old contact data set during re-opening and has unnecessarily item animations
// TODO fade view pops up during orientation change
// TODO intercept user interaction during transitions
// TODO fix voice search
// TODO cross talk with item entry searchView. Old search appears in other fragment


const val READ_CONTACTS_RATIONALE_KEY = "read_contacts_rationale_key"
const val RESET_HIDDEN_CONTACTS_KEY = "reset_hidden_contacts_key"
const val RESET_FAVORITE_CONTACTS_KEY = "reset_favorite_contacts_key"


class AddressBookFragment: BaseUIFragment<FragAddressBookBinding, AddressBookViewModel>() {
    private val args: AddressBookFragmentArgs by navArgs()
    private lateinit var recyclerAdapter: AddressBookRecyclerViewAdapter
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>
    private var pendingRewind: Boolean = false

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragAddressBookBinding.inflate(inflater, container, false)

    override fun createViewModel() = ViewModelProvider(requireActivity())[AddressBookViewModel::class.java]

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        enterTransition = CircularRevealTransition(
            binding.fadeView,
            binding.revealedLayout,
            args.originParams,
            resources.getInteger(R.integer.frag_transition_duration).toLong(),
            true).addListener(object : Transition.TransitionListener {
            override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
            override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        /* Need a way to discriminate between user dismissal of keyboard and system dismissal from starting voice search
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            // Close the SearchView if no search query exists when keyboard is dismissed except if
            // a voice search is running
            if(!insets.isVisible(WindowInsetsCompat.Type.ime()) && viewModel.isSearchQueryBlank()) {
                viewModel.stopSearch()
            }
            insets
        }
        */

        permissionRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            if (it) {
                // Permission granted so proceed with contacts refresh
                viewModel.requestContactsRefresh()
            } else {
                // Permission denied
                viewModel.requestContactsRefresh(acquirePermissionsIfNeeded = false)
                Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_no_read_contacts_permission, Snackbar.LENGTH_LONG).show()
            }
        }

        setFragmentResultListener(READ_CONTACTS_RATIONALE_KEY) { _, bundle ->
            if(bundle.getBoolean("resultKey")) {
                // User indicated to continue so try to acquire permission
                permissionRequestLauncher.launch(Manifest.permission.READ_CONTACTS)
            } else {
                // User dismissed dialog so do not acquire permission
                viewModel.requestContactsRefresh(acquirePermissionsIfNeeded = false)
                Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_no_read_contacts_permission, Snackbar.LENGTH_LONG).show()
            }
        }

        setFragmentResultListener(RESET_HIDDEN_CONTACTS_KEY) { _, bundle ->
            if(bundle.getBoolean("resultKey")) {
                viewModel.unhideHiddenContacts()
                Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_unhide_all, Snackbar.LENGTH_SHORT).show()
            }
        }

        setFragmentResultListener(RESET_FAVORITE_CONTACTS_KEY) { _, bundle ->
            if(bundle.getBoolean("resultKey")) {
                viewModel.unfavoriteFavoriteContacts()
                Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_unfavorite_all, Snackbar.LENGTH_SHORT).show()
            }
        }

        setupToolbar()

        setupSearchView()

        setupContactList()

        binding.fab.doOnPreDraw {
            // For return transitions, set the size of the container so it matches the FAB
            binding.newContactButtonContainer.layoutParams = CoordinatorLayout.LayoutParams(
                binding.fab.width,
                binding.fab.height
            ).also {
                it.leftMargin = binding.fab.x.toInt()
                it.topMargin = binding.fab.y.toInt()
            }
        }

        binding.fab.setOnClickListener {


            if(viewModel.selections.value.isNullOrEmpty()) {
                // Exit transition is needed to prevent next fragment from appearing immediately
                exitTransition = Hold().apply {
                    duration = 0L
                    addTarget(requireView())
                }

                // Set the size of the container for the fragment transition to match the FAB
                binding.newContactButtonContainer.layoutParams = CoordinatorLayout.LayoutParams(
                    binding.fab.width,
                    binding.fab.height
                ).also {
                    it.leftMargin = binding.fab.x.toInt()
                    it.topMargin = binding.fab.y.toInt()
                }

                (requireActivity() as MainActivity).storeViewAsBitmap(requireView())

                findNavController().navigate(AddressBookFragmentDirections.createDiner(),
                    FragmentNavigatorExtras(
                        binding.newContactButtonContainer to binding.newContactButtonContainer.transitionName,
                        binding.fab to binding.fab.transitionName
                    ))
            } else {
                viewModel.addSelectedContactsToBill()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.fadeView.visibility = View.GONE
    }

    override fun onFinish(output: Unit?) {
        // Closing animation shrinking fragment into the FAB of the previous fragment.
        // Transition is defined here to incorporate dynamic changes to window insets.
        returnTransition = CircularRevealTransition(
            binding.fadeView,
            binding.revealedLayout,
            args.originParams.withInsetsOn(binding.fab),
            resources.getInteger(R.integer.frag_transition_duration).toLong(),
            false)

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.toolbar_addressbook)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { viewModel.handleOnBackPressed() }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.action_refresh -> {
                    viewModel.requestContactsRefresh()
                    true
                }
                R.id.action_show_hidden -> {
                    viewModel.toggleShowHiddenContacts()
                    true
                }
                R.id.action_reset_hidden -> {
                    ResetHiddenContactsDialogFragment().show(parentFragmentManager, RESET_HIDDEN_CONTACTS_KEY)
                    true
                }
                R.id.action_reset_favorites -> {
                    ResetFavoriteContactsDialogFragment().show(parentFragmentManager, RESET_FAVORITE_CONTACTS_KEY)
                    true
                }
                R.id.action_favorite -> {
                    viewModel.favoriteSelectedContacts()?.apply {
                        Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_favorited, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.undo) { this() }.show()
                    }
                    true
                }
                R.id.action_unfavorite -> {
                    viewModel.unfavoriteSelectedContacts()?.apply {
                        Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_unfavorited, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.undo) { this() }.show()
                    }
                    true
                }
                R.id.action_hide -> {
                    viewModel.hideSelectedContacts()?.apply {
                        Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_hidden, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.undo) { this() }.show()
                    }
                    true
                }
                R.id.action_unhide -> {
                    viewModel.unhideSelectedContacts()?.apply {
                        Snackbar.make(binding.coordinatorLayout, R.string.addressbook_snackbar_unhidden, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.undo) { this() }.show()
                    }
                    true
                }
                else -> { false }
            }
        }

        setupToolbarSecondaryTertiaryAnimation(
            requireContext(),
            viewLifecycleOwner,
            viewModel.toolBarInTertiaryState,
            binding.toolbar,
            binding.statusBarBackgroundView)

        viewModel.toolBarState.observe(viewLifecycleOwner) { toolBarState ->
            binding.toolbar.title = toolBarState.title

            if(toolBarState.navIconAsClose == null) {
                binding.toolbar.navigationIcon = null
            } else {
                binding.toolbar.setNavigationIcon(if(toolBarState.navIconAsClose) R.drawable.ic_close else R.drawable.ic_arrow_back_light)
            }

            if(toolBarState.searchInactive) {
                val searchView = binding.toolbar.menu.findItem(R.id.search_view).actionView as SearchView

                // Note: The ViewModel should stop processing query updates before this block runs
                // or the call to searchView.setQuery() will trigger a new search
                searchView.setQuery("", false)

                // Note: The searchView needs to be iconified before updating the menu group
                // visibilities or the updates may not always be drawn
                searchView.isIconified = true
            }

            if(toolBarState.hideVisibilityButtons) {
                binding.toolbar.menu.setGroupVisible(R.id.group_favorite, false)
                binding.toolbar.menu.setGroupVisible(R.id.group_unfavorite, false)
                binding.toolbar.menu.setGroupVisible(R.id.group_hide, false)
                binding.toolbar.menu.setGroupVisible(R.id.group_unhide, false)
            }
            else {
                binding.toolbar.menu.setGroupVisible(R.id.group_favorite, !toolBarState.showAsUnfavorite)
                binding.toolbar.menu.setGroupVisible(R.id.group_unfavorite, toolBarState.showAsUnfavorite)
                binding.toolbar.menu.setGroupVisible(R.id.group_hide, !toolBarState.showAsUnhide)
                binding.toolbar.menu.setGroupVisible(R.id.group_unhide, toolBarState.showAsUnhide)
            }

            binding.toolbar.menu.setGroupVisible(R.id.group_other, toolBarState.showOtherButtons)
        }

        viewModel.showHiddenContacts.observe(viewLifecycleOwner, {
            binding.toolbar.menu.findItem(R.id.action_show_hidden).isChecked = it
        })
    }

    private fun setupSearchView() {
        val searchView = binding.toolbar.menu.findItem(R.id.search_view).actionView as SearchView
        searchView.setSearchableInfo((requireActivity().getSystemService(Context.SEARCH_SERVICE) as SearchManager).getSearchableInfo(requireActivity().componentName))
        searchView.queryHint = resources.getString(R.string.addressbook_toolbar_search_instructions)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.isIconified = true

        searchView.setOnSearchClickListener { viewModel.startSearch() }

        searchView.setOnCloseListener {
            viewModel.stopSearch()
            false
        }

        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextChange(string: String?): Boolean {
                pendingRewind = true
                viewModel.updateSearchQuery(string)
                return true
            }

            override fun onQueryTextSubmit(string: String?): Boolean { return true }
        })

        // For handling voice input
        viewModel.voiceInput.observe(viewLifecycleOwner, {
            it.consume()?.apply {
                // Update displayed text, but do not submit. The event cascades to a separate
                // QueryTextListener, which is responsible for running the search.
                viewModel.startSearch()
                searchView.setQuery(this.value, false)
            }
        })
    }

    private fun setupContactList() {
        recyclerAdapter = AddressBookRecyclerViewAdapter(requireContext(), object :
            RecyclerViewListener {
            override fun onClick(view: View) { viewModel.toggleContactSelection(view.tag as Contact) }

            override fun onLongClick(view: View): Boolean { return false }
        })

        binding.list.apply {
            adapter = recyclerAdapter

            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

            val defaultItemAnimator = object : DefaultItemAnimator() {
                override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean {
                    return true
                }

                override fun animateChange(oldHolder: RecyclerView.ViewHolder?, newHolder: RecyclerView.ViewHolder?, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
                    oldHolder?.apply {
                        if (oldHolder === newHolder) {
                            val contact = (this as AddressBookRecyclerViewAdapter.ViewHolder).itemView.tag as Contact

                            if (viewModel.isContactSelected(contact)) {
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

            viewModel.animateListUpdates.observe(viewLifecycleOwner) {
                itemAnimator = if(it) defaultItemAnimator else null
            }
        }

        viewModel.displayedContacts.observe(viewLifecycleOwner) {
            lifecycleScope.launch { recyclerAdapter.updateDataSet(contacts = it) }
                .invokeOnCompletion {
                    Handler(Looper.getMainLooper()).post {
                        if (pendingRewind) {
                            pendingRewind = false
                            binding.list.scrollToPosition(0)
                        }
                    }
                }
        }
        viewModel.selections.observe(viewLifecycleOwner) {
            lifecycleScope.launch { recyclerAdapter.updateDataSet(selections = it) }
                .invokeOnCompletion {
                    Handler(Looper.getMainLooper()).post {
                        if (pendingRewind) {
                            pendingRewind = false
                            binding.list.scrollToPosition(0)
                        }
                    }
                }
        }

        viewModel.showRefreshAnimation.observe(viewLifecycleOwner) { binding.swipeRefreshLayout.isRefreshing = it }
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.requestContactsRefresh() } // TODO need to clear search in model?

        viewModel.fabExtended.observe(viewLifecycleOwner) {
            when(it) {
                null -> { binding.fab.hide() }
                true -> {
                    binding.fab.extendFABToCenter(R.drawable.ic_add_person)
                    binding.fab.show()
                }
                false -> {
                    binding.fab.shrinkFABToCorner(R.drawable.ic_arrow_forward)
                    binding.fab.show()
                }
            }
        }

        viewModel.acquireReadContactsPermissionEvent.observe(viewLifecycleOwner) {
            // User permission needed to proceed with reading device contacts
            it.consume()?.apply {
                if(shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                    ReadContactsRationaleDialogFragment().show(parentFragmentManager, READ_CONTACTS_RATIONALE_KEY)
                }
                else {
                    permissionRequestLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        }
    }
}


class ReadContactsRationaleDialogFragment : DialogFragment() {
    init {
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.addressbook_alert_read_contacts_title))
            .setMessage(resources.getString(R.string.addressbook_alert_read_contacts_message))
            .setCancelable(false)
            .setPositiveButton(resources.getString(R.string.proceed)) { _, _ -> setFragmentResult(READ_CONTACTS_RATIONALE_KEY, bundleOf("resultKey" to true)) }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ -> setFragmentResult(READ_CONTACTS_RATIONALE_KEY, bundleOf("resultKey" to false)) }
            .create()
    }
}


class ResetHiddenContactsDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.addressbook_alert_reset_hidden_title))
            .setMessage(resources.getString(R.string.addressbook_alert_reset_hidden_message))
            .setPositiveButton(resources.getString(R.string.proceed)) { _, _ -> setFragmentResult(RESET_HIDDEN_CONTACTS_KEY, bundleOf("resultKey" to true)) }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ -> setFragmentResult(RESET_HIDDEN_CONTACTS_KEY, bundleOf("resultKey" to false)) }
            .create()
    }
}


class ResetFavoriteContactsDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogPosSuggestionSecondary)
            .setTitle(resources.getString(R.string.addressbook_alert_reset_favorites_title))
            .setMessage(resources.getString(R.string.addressbook_alert_reset_favorites_message))
            .setPositiveButton(resources.getString(R.string.proceed)) { _, _ -> setFragmentResult(RESET_FAVORITE_CONTACTS_KEY, bundleOf("resultKey" to true)) }
            .setNegativeButton(resources.getString(R.string.cancel)) { _, _ -> setFragmentResult(RESET_FAVORITE_CONTACTS_KEY, bundleOf("resultKey" to false)) }
            .create()
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

            itemView.setBackgroundColor(if (isSelected) selectedSurfaceColor else surfaceColor)

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

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
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
            mContacts = newContacts
            mSelections = newSelections
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}