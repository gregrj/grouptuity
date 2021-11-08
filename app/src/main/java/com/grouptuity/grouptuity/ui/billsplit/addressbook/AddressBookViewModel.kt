package com.grouptuity.grouptuity.ui.billsplit.addressbook

import android.Manifest
import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.Event
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.util.*

class AddressBookViewModel(app: Application): UIViewModel(app) {

    // TODO selected contact subsequently hidden. Need to discard selection

    private val addressBook = AddressBook.getInstance(app)

    private val _showHiddenContacts = MutableStateFlow(false)

    private val mSelections = mutableSetOf<String>()
    private val _selections = MutableStateFlow(mSelections.toSet())

    private val searchQuery = MutableStateFlow<String?>(null)

    private val _displayedContacts = combine(
        addressBook.allContacts,
        repository.diners,
        _showHiddenContacts,
        searchQuery) { allContacts, diners, showHidden, query ->

        if (query != null && query.isBlank()) {
            // Search is active, but a valid query has not been entered so display no contacts
            return@combine emptyList<Contact>()
        }

        val combinedContacts = mutableListOf<Contact>()

        // Do not display contacts already assigned to the bill as diners
        val excludeList = diners.map { it.lookupKey }.toMutableSet()

        // Add contacts saved in app database
        if (showHidden) {
            allContacts.forEach {
                if (!excludeList.contains(it.key)) {
                    combinedContacts.add(it.value)
                    excludeList.add(it.key) // to avoid duplicate addition later
                }
            }
        }
        else {
            allContacts.forEach {
                if(!excludeList.contains(it.key)) {
                    if(it.value.visibility != Contact.HIDDEN) {
                        combinedContacts.add(it.value)
                    }
                    excludeList.add(it.key) // to avoid duplicate addition later
                }
            }
        }

        if (query != null) {
            sortContactsByQuery(combinedContacts, query)
        } else {
            sortContactsByName(combinedContacts)
        }
    }.flowOn(Dispatchers.Main)

    private val acquireReadContactsPermissionEventMutable = MutableLiveData<Event<Int>>()

    // Live Data Output
    val animateListUpdates: LiveData<Boolean> = searchQuery.mapLatest { it == null }.withOutputSwitch(isOutputFlowing).asLiveData()
    val showRefreshAnimation: LiveData<Boolean> = addressBook.refreshingDeviceContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val showHiddenContacts: LiveData<Boolean> = _showHiddenContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val acquireReadContactsPermissionEvent: LiveData<Event<Int>> = acquireReadContactsPermissionEventMutable
    val displayedContacts = _displayedContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val selections: LiveData<Set<String>> = _selections.mapLatest {
        stopSearch()
        it
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val toolBarState: LiveData<ToolBarState> = combine(_selections, searchQuery, addressBook.allContacts) { selectedContacts, query, allContacts ->
        val isSearching = query != null

        if(selectedContacts.isEmpty()) {
            ToolBarState(
                context.resources.getString(R.string.addressbook_toolbar_select_diners),
                navIconAsClose = if(isSearching) null else false,
                searchInactive = !isSearching,
                tertiaryBackground = false,
                hideVisibilityButtons = true,
                showAsUnfavorite = false,
                showAsUnhide = false,
                showOtherButtons = !isSearching)
        } else {
            ToolBarState(
                context.resources.getQuantityString(R.plurals.addressbook_toolbar_num_selected, selectedContacts.size, selectedContacts.size),
                navIconAsClose = if(isSearching) null else true,
                searchInactive = !isSearching,
                tertiaryBackground = !isSearching,
                hideVisibilityButtons = isSearching,
                showAsUnfavorite = selectedContacts.isNotEmpty() && selectedContacts.all {
                    allContacts[it]?.visibility == Contact.FAVORITE
                },
                showAsUnhide = selectedContacts.isNotEmpty() && selectedContacts.all {
                    allContacts[it]?.visibility == Contact.HIDDEN
                },
                showOtherButtons = false)
        }
    }.distinctUntilChanged().withOutputSwitch(isOutputFlowing).asLiveData()
    val fabExtended: LiveData<Boolean?> = combine(_selections, searchQuery) { selectedContacts, query ->
        when {
            query != null -> null
            selectedContacts.isEmpty() -> true
            else -> false
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()

    override fun notifyTransitionFinished() {
        super.notifyTransitionFinished()

        // When transition finishes, read the device contacts if they have yet to be read
        if(addressBook.readDeviceContactPending.value) {
            requestContactsRefresh()
        }
    }

    fun initialize() {
        unFreezeOutput()
        searchQuery.value = null
        deselectAllContacts()

        // Invalidate any unconsumed events
        acquireReadContactsPermissionEventMutable.value?.consume()
    }

    //TODO why three values? Simplify across all ViewModels
    fun handleOnBackPressed(): Boolean? {
        when {
            isInputLocked.value -> { }
            searchQuery.value != null -> { stopSearch() }
            _selections.value.isNotEmpty() -> { deselectAllContacts() }
            else -> { return false }
        }
        return null
    }

    fun updateSearchQuery(query: String?) { if(searchQuery.value != null) { searchQuery.value = query ?: "" } }
    fun startSearch() { searchQuery.value = "" }
    fun stopSearch() {
        if(searchQuery.value != null) {
            searchQuery.value = null
        }
    }

    fun isContactSelected(contact: Contact) = mSelections.contains(contact.lookupKey)
    fun toggleContactSelection(contact: Contact) {
        if (isContactSelected(contact)) {
            mSelections.remove(contact.lookupKey)
        } else {
            mSelections.add(contact.lookupKey)
        }
        _selections.value = mSelections.toSet()
    }
    private fun deselectAllContacts() {
        mSelections.clear()
        _selections.value = mSelections.toSet()
    }

    fun favoriteSelectedContacts(): (() -> Unit)? {
        if (mSelections.isEmpty())
            return null

        val originallyVisible = mutableListOf<String>()
        val originallyHidden = mutableListOf<String>()

        addressBook.allContacts.value.also { allContacts ->
            mSelections.forEach { lookupKey ->
                when (allContacts[lookupKey]?.visibility) {
                    Contact.VISIBLE -> originallyVisible.add(lookupKey)
                    Contact.HIDDEN -> originallyHidden.add(lookupKey)
                }
            }
        }

        val job = addressBook.favoriteContacts(mSelections)

        // Function for reverting this action
        return {
            job.invokeOnCompletion {
                addressBook.unfavoriteContacts(originallyVisible)
                addressBook.hideContacts(originallyHidden)
            }
        }
    }
    fun unfavoriteSelectedContacts(): (() -> Unit)? {
        if (mSelections.isEmpty())
            return null

        val originallyFavorited = addressBook.allContacts.value.let { allContacts ->
            mSelections.mapNotNull { lookupKey ->
                if (allContacts[lookupKey]?.visibility == Contact.FAVORITE) {
                    lookupKey
                } else {
                    null
                }
            }
        }

        val job = addressBook.unfavoriteContacts(originallyFavorited)

        // Function for reverting this action
        return { job.invokeOnCompletion { addressBook.favoriteContacts(originallyFavorited) } }
    }
    fun unfavoriteFavoriteContacts() = addressBook.unfavoriteFavoriteContacts()

    fun hideSelectedContacts(): (() -> Unit)? {
        if (mSelections.isEmpty())
            return null

        val originallyVisible = mutableListOf<String>()
        val originallyFavorited = mutableListOf<String>()

        addressBook.allContacts.value.also { allContacts ->
            mSelections.forEach { lookupKey ->
                when (allContacts[lookupKey]?.visibility) {
                    Contact.VISIBLE -> originallyVisible.add(lookupKey)
                    Contact.FAVORITE -> originallyFavorited.add(lookupKey)
                }
            }
        }

        val job = addressBook.hideContacts(mSelections)

        // Function for reverting this action
        return {
            job.invokeOnCompletion {
                addressBook.unhideContacts(originallyVisible)
                addressBook.favoriteContacts(originallyFavorited)
            }
        }
    }
    fun unhideSelectedContacts(): (() -> Unit)? {
        if (mSelections.isEmpty())
            return null

        val originallyHidden = addressBook.allContacts.value.let { allContacts ->
            mSelections.mapNotNull { lookupKey ->
                if (allContacts[lookupKey]?.visibility == Contact.HIDDEN) {
                    lookupKey
                } else {
                    null
                }
            }
        }

        val job = addressBook.unhideContacts(originallyHidden)

        // Function for reverting this action
        return { job.invokeOnCompletion { addressBook.hideContacts(originallyHidden) } }
    }
    fun unhideHiddenContacts() = addressBook.unhideHiddenContacts()

    fun toggleShowHiddenContacts() { _showHiddenContacts.value = !_showHiddenContacts.value }

    fun requestContactsRefresh(acquirePermissionsIfNeeded: Boolean = true) {
        when {
            isPermissionGranted(Manifest.permission.READ_CONTACTS) -> {
                addressBook.refreshDeviceContacts(getApplication())
            }
            acquirePermissionsIfNeeded -> {
                acquireReadContactsPermissionEventMutable.value = Event(R.integer.permission_read_contacts_addressbook)
            }
            else -> {
                addressBook.bypassRefreshDeviceContacts()
            }
        }
    }

    fun addSelectedContactsToBill() {
        repository.addContactsAsDiners(context, addressBook.allContacts.value.let { allContacts ->
            mSelections.mapNotNull { lookupKey ->
                allContacts[lookupKey]
            }
        })

        deselectAllContacts()
    }

    companion object {
        data class ToolBarState(val title: String,
                                val navIconAsClose: Boolean?, // null -> hide navigation icon
                                val searchInactive: Boolean,
                                val tertiaryBackground: Boolean,
                                val hideVisibilityButtons: Boolean,
                                val showAsUnfavorite: Boolean,
                                val showAsUnhide: Boolean,
                                val showOtherButtons: Boolean)
    }
}

private fun sortContactsByName(contacts: List<Contact>): List<Contact> {
    // Return favorites in alphabetical order followed by all other contacts in alphabetical order
    val (favorites, others) = contacts.partition { it.visibility == Contact.FAVORITE }
    return favorites.sortedBy { it.name } + others.sortedBy { it.name }
}

private fun sortContactsByQuery(contacts: List<Contact>, query: String): List<Contact> {
    val searchString = query.trim()

    return if (query.isBlank())
        contacts.sortedBy { it.name } // sort alphabetically
    else
        contacts.map { Pair(computeMatchScore(it, searchString), it) } // sort by match score
            .sortedByDescending { it.first }
            .map { it.second }
}

private fun computeMatchScore(contact: Contact, searchString: String): Double {
    val contactNames = contact.name.split(" ").toMutableList()
    contactNames[0] = contact.name

    val maxScore = contactNames.mapIndexed { nameIndex, name ->
        val searchChars = searchString.lowercase(Locale.getDefault()).toCharArray()
        val contactChars = name.lowercase(Locale.getDefault()).toCharArray()

        var score = 0.0
        for(charIndex in searchChars.indices) {
            val multiplier = 10.0 / (1 + charIndex) * (100.0 / (1 + 0.3 * nameIndex))

            if(charIndex == 0) {
                score += if(contactChars.size > 1)
                    multiplier*(2*scoreCharacter(searchChars[0],contactChars[0]) + scoreCharacter(searchChars[0],contactChars[1]))
                else
                    multiplier*(2*scoreCharacter(searchChars[0],contactChars[0]))
            }
            else {
                score += if(contactChars.size > charIndex + 1)
                    multiplier*(2*scoreCharacter(searchChars[charIndex],contactChars[charIndex]) + scoreCharacter(searchChars[charIndex],contactChars[charIndex+1]) + scoreCharacter(searchChars[charIndex],contactChars[charIndex-1]))
                else if(contactChars.size > charIndex)
                    multiplier*(2*scoreCharacter(searchChars[charIndex],contactChars[charIndex]) + scoreCharacter(searchChars[charIndex],contactChars[charIndex-1]))
                else if(contactChars.size > charIndex - 1)
                    multiplier*(scoreCharacter(searchChars[charIndex],contactChars[charIndex-1]))
                else
                    break
            }
        }
        score
    }.maxOrNull()

    return maxScore ?: 0.0
}

// Only valid for QWERTY keyboard layout
private val charScoreMap = mapOf(
        'a' to setOf('q','w','s','z'),
        'b' to setOf('v','g','h','j','n'),
        'c' to setOf('x','d','f','g','v'),
        'd' to setOf('s','e','r','f','c','x'),
        'e' to setOf('w','s','d','f','r'),
        'f' to setOf('d','r','t','g','v','c','x'),
        'g' to setOf('f','t','y','h','b','v','c'),
        'h' to setOf('g','y','u','j','n','b','v'),
        'i' to setOf('u','j','k','o'),
        'j' to setOf('h','u','i','k','m','n','b'),
        'k' to setOf('j','i','o','l','m','n'),
        'l' to setOf('k','o','p','m'),
        'm' to setOf('n','j','k','l'),
        'n' to setOf('b','h','j','k','m'),
        'o' to setOf('i','k','l','p'),
        'p' to setOf('o','l'),
        'q' to setOf('w','a'),
        'r' to setOf('e','d','f','t'),
        's' to setOf('a','w','e','d','x','z'),
        't' to setOf('r','f','g','y'),
        'u' to setOf('y','h','j','i'),
        'v' to setOf('c','f','g','h','b'),
        'w' to setOf('q','a','s','e'),
        'x' to setOf('z','s','d','c'),
        'y' to setOf('t','g','h','u'),
        'z' to setOf('a','s','x'))

private fun scoreCharacter(searchChar: Char, matchChar: Char): Double {
    // requires lowercase chars
    if(searchChar == matchChar)
        return 3.0

    charScoreMap[searchChar]?.apply {
        return if(this.contains(matchChar)) 1.0 else 0.0
    }

    return 0.0
}