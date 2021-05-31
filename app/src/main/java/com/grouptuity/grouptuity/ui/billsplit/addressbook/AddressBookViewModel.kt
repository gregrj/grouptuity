package com.grouptuity.grouptuity.ui.billsplit.addressbook

import android.Manifest
import android.app.Application
import android.provider.ContactsContract
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.grouptuity.grouptuity.Event
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Contact
import com.grouptuity.grouptuity.data.UIViewModel
import com.grouptuity.grouptuity.data.withOutputSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class AddressBookViewModel(app: Application): UIViewModel(app) {

    private val appSavedContacts = repository.appContacts
    private val deviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _showHiddenContacts = MutableStateFlow(false)
    private val excludedNames = getApplication<Application>().resources.getStringArray(R.array.addressbook_excluded_names)

    private val selectionsMap = mutableMapOf<String, Contact>()
    private val _selections = MutableStateFlow(selectionsMap.toMap())

    private val searchQuery = MutableStateFlow<String?>(null)

    private val closeFragmentEventMutable = MutableLiveData<Event<Boolean>>()
    private val acquireReadContactsPermissionEventMutable = MutableLiveData<Event<Int>>()
    private val hasAttemptedReadDeviceContact = MutableStateFlow(false)
    private val refreshingDeviceContacts = MutableStateFlow(false)

    private val _displayedContacts = combine(
        appSavedContacts,
        deviceContacts,
        repository.dinerContactLookupKeys,
        _showHiddenContacts,
        searchQuery) { contactsApp, contactsDevice, lookupKeys, showHidden, query ->

        if (query != null && query.isBlank()) {
            // Search is active, but a valid query has not been entered so display no contacts
            return@combine emptyList<Contact>()
        }

        val combinedContacts = mutableListOf<Contact>()

        // Do not display contacts already assigned to the bill as diners
        val excludeList = lookupKeys.toMutableSet().also {
            it.add(Contact.restaurant.lookupKey) // also avoid showing restaurant as a contact
        }

        // Add contacts saved in app database
        if (showHidden) {
            contactsApp.forEach {
                if (!excludeList.contains(it.lookupKey)) {
                    combinedContacts.add(it)
                    excludeList.add(it.lookupKey) // to avoid duplicate addition later
                }
            }
        }
        else {
            contactsApp.forEach {
                if(!excludeList.contains(it.lookupKey)) {
                    if(it.visibility != Contact.HIDDEN) {
                        combinedContacts.add(it)
                    }
                    excludeList.add(it.lookupKey) // to avoid duplicate addition later
                }
            }
        }

        // Add contacts from device address book
        contactsDevice.forEach {
            if(!excludeList.contains(it.lookupKey)) {
                combinedContacts.add(it)
            }
            else {
                // TODO update latest changes to contact data (name, photo, etc.)
            }
        }

        //TODO Refresh selections when contact selected but contact no longer appears in displayed contacts

        if (query != null) {
            sortContactsByQuery(combinedContacts, query)
        } else {
            sortContactsByName(combinedContacts)
        }
    }.flowOn(Dispatchers.Main).withOutputSwitch(hasAttemptedReadDeviceContact)

    // Live Data Output
    val closeFragmentEvent: LiveData<Event<Boolean>> = closeFragmentEventMutable
    val animateListUpdates: LiveData<Boolean> = searchQuery.mapLatest { it == null }.withOutputSwitch(isOutputFlowing).asLiveData()
    val showRefreshAnimation: LiveData<Boolean> = refreshingDeviceContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val showHiddenContacts: LiveData<Boolean> = _showHiddenContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val acquireReadContactsPermissionEvent: LiveData<Event<Int>> = acquireReadContactsPermissionEventMutable
    val displayedContacts = _displayedContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val selections: LiveData<Map<String, Contact>> = _selections.mapLatest {
        stopSearch()
        it
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val toolBarState: LiveData<ToolBarState> = combine(_selections, searchQuery) { selectedContacts, query ->
        val isSearching = query != null

        if(selectedContacts.isEmpty()) {
            ToolBarState(
                getApplication<Application>().resources.getString(R.string.addressbook_toolbar_select_diners),
                navButtonAsClose = if(isSearching) null else false,
                searchInactive = !isSearching,
                alternateBackground = false,
                hideVisibilityButtons = true,
                showAsUnfavorite = false,
                showAsUnhide = false,
                showOtherButtons = !isSearching)
        } else {
            ToolBarState(
                getApplication<Application>().resources.getQuantityString(R.plurals.addressbook_toolbar_num_selected, selectedContacts.size, selectedContacts.size),
                navButtonAsClose = if(isSearching) null else true,
                searchInactive = !isSearching,
                alternateBackground = !isSearching,
                hideVisibilityButtons = isSearching,
                showAsUnfavorite = selectedContacts.isNotEmpty() && selectedContacts.all { it.value.visibility == Contact.FAVORITE },
                showAsUnhide = selectedContacts.isNotEmpty() && selectedContacts.all { it.value.visibility == Contact.HIDDEN },
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
        if(!hasAttemptedReadDeviceContact.value) {
            requestContactsRefresh()
        }
    }

    fun initialize() {
        unFreezeOutput()
        searchQuery.value = null
        deselectAllContacts()
        refreshingDeviceContacts.value = false
    }

    fun handleOnBackPressed() {
        when {
            searchQuery.value != null -> { stopSearch() }
            _selections.value.isNotEmpty() -> { deselectAllContacts() }
            else -> { closeFragmentEventMutable.value = Event(false) }
        }
    }

    fun updateSearchQuery(query: String?) { if(searchQuery.value != null) { searchQuery.value = query ?: "" } }
    fun startSearch() { searchQuery.value = "" }
    fun stopSearch() {
        if(searchQuery.value != null) {
            searchQuery.value = null
        }
    }

    fun isContactSelected(contact: Contact) = selectionsMap.contains(contact.lookupKey)
    fun toggleContactSelection(contact: Contact) {
        if (isContactSelected(contact)) {
            selectionsMap.remove(contact.lookupKey)
        } else {
            selectionsMap[contact.lookupKey] = contact
        }
        _selections.value = selectionsMap.toMap()
    }
    private fun deselectAllContacts() {
        selectionsMap.clear()
        _selections.value = selectionsMap.toMap()
    }

    fun favoriteSelectedContacts(): (() -> Unit)? {
        if (selectionsMap.isEmpty())
            return null

        val selectedContacts = selectionsMap.values.toList()

        val job = repository.saveContacts(selectedContacts.map{ it.withVisibility(Contact.FAVORITE) })

        // Update selectionsMap to be consistent with new visibilities
        selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it.withVisibility(Contact.FAVORITE)) })
        _selections.value = selectionsMap.toMap()

        return {
            job.invokeOnCompletion {
                repository.saveContacts(selectedContacts)
                selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it) })
                _selections.value = selectionsMap.toMap()
            }
        }
    }
    fun unfavoriteSelectedContacts(): (() -> Unit)? {
        if (selectionsMap.isEmpty())
            return null

        val selectedContacts = selectionsMap.values.toList()

        val job = repository.saveContacts(selectedContacts.map{ it.withVisibility(Contact.VISIBLE) })

        // Update selectionsMap to be consistent with new visibilities
        selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it.withVisibility(Contact.VISIBLE)) })
        _selections.value = selectionsMap.toMap()

        return {
            job.invokeOnCompletion {
                repository.saveContacts(selectedContacts)
                selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it) })
                _selections.value = selectionsMap.toMap()
            }
        }
    }
    fun unfavoriteFavoriteContacts() = repository.unfavoriteFavoriteContacts()

    fun hideSelectedContacts(): (() -> Unit)? {
        if (selectionsMap.isEmpty())
            return null

        val selectedContacts = selectionsMap.values.toList()

        val job = repository.saveContacts(selectedContacts.map{ it.withVisibility(Contact.HIDDEN) })

        if(!_showHiddenContacts.value) {
            deselectAllContacts()
        } else {
            // Update selectionsMap to be consistent with new visibilities
            selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it.withVisibility(Contact.HIDDEN)) })
            _selections.value = selectionsMap.toMap()
        }

        return {
            job.invokeOnCompletion {
                selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it) })
                _selections.value = selectionsMap.toMap()
                repository.saveContacts(selectedContacts) // Restores original visibility flags
            }
        }
    }
    fun unhideSelectedContacts(): (() -> Unit)? {
        if (selectionsMap.isEmpty())
            return null

        val selectedContacts = selectionsMap.values.toList()

        val job = repository.saveContacts(selectedContacts.map{ it.withVisibility(Contact.VISIBLE) })

        // Update selectionsMap to be consistent with new visibilities
        selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it.withVisibility(Contact.VISIBLE)) })
        _selections.value = selectionsMap.toMap()

        return {
            job.invokeOnCompletion {
                repository.saveContacts(selectedContacts)
                selectionsMap.putAll(selectedContacts.map { Pair(it.lookupKey, it) })
                _selections.value = selectionsMap.toMap()
            }
        }
    }
    fun unhideHiddenContacts() = repository.unhideHiddenContacts()

    fun toggleShowHiddenContacts() { _showHiddenContacts.value = !_showHiddenContacts.value }

    fun requestContactsRefresh(acquirePermissionsIfNeeded: Boolean = true) {
        refreshingDeviceContacts.value = true

        when {
            isPermissionGranted(Manifest.permission.READ_CONTACTS) -> {
                viewModelScope.launch(Dispatchers.IO) {

                    // TODO clear Search

                    delay(300L) // Slight delay to give SwipeRefreshLayout time to animate to indicate refresh is taking place

                    val contactsCursor = getApplication<Application>().contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(ContactsContract.Contacts._ID,
                            ContactsContract.Contacts.LOOKUP_KEY,
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                            ContactsContract.Contacts.PHOTO_URI),null,null,null)

                    val contacts = ArrayList<Contact>()

                    contactsCursor?.run {
                        while(moveToNext())
                        {
                            val name = getString(2)

                            if(name.isNullOrBlank() || excludedNames.contains(name))
                                continue

                            contacts.add(Contact(getString(1),
                                name.trim(),
                                getString(3),
                                Contact.VISIBLE))
                        }
                        contactsCursor.close()
                    }

                    deviceContacts.value = contacts
                }.invokeOnCompletion {
                    refreshingDeviceContacts.value = false
                    hasAttemptedReadDeviceContact.value = true
                }
                return
            }
            acquirePermissionsIfNeeded -> {
                acquireReadContactsPermissionEventMutable.value = Event(R.integer.permission_read_contacts_addressbook)
                return
            }
            else -> {
                deviceContacts.value = emptyList()
                refreshingDeviceContacts.value = false
                hasAttemptedReadDeviceContact.value = true
                return
            }
        }
    }

    fun addSelectedContactsToBill() {
        selections.value?.values?.apply {
            repository.createDinersForContacts(this)
        }
        deselectAllContacts()
    }

    companion object {
        data class ToolBarState(val title: String,
                                val navButtonAsClose: Boolean?, // null -> hide navigation icon
                                val searchInactive: Boolean,
                                val alternateBackground: Boolean,
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
        val searchChars = searchString.toLowerCase(Locale.getDefault()).toCharArray()
        val contactChars = name.toLowerCase(Locale.getDefault()).toCharArray()

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