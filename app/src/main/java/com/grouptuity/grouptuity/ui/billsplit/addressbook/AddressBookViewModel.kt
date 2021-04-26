package com.grouptuity.grouptuity.ui.billsplit.addressbook

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.Locale

class AddressBookViewModel(app: Application): UIViewModel(app) {

    private val appSavedContacts = repository.appContacts
    private val deviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _showHiddenContacts = MutableStateFlow(false)
    private val excludedNames = getApplication<Application>().resources.getStringArray(R.array.addressbook_excluded_names)

    private val selectionsMap = mutableMapOf<String, Contact>()
    private val _selections = MutableLiveData(selectionsMap.toMap())
    val selections: LiveData<Map<String, Contact>> = _selections

    private val _searchQuery = MutableStateFlow("")

    private val acquireReadContactsPermissionEventMutable = MutableLiveData<Event<Int>>()
    private val hasAttemptedReadDeviceContact = MutableStateFlow(false)
    private val refreshingDeviceContacts = MutableStateFlow(false)

    // Live Data Output
    val showRefreshAnimation = refreshingDeviceContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val showHiddenContacts: LiveData<Boolean> = _showHiddenContacts.withOutputSwitch(isOutputFlowing).asLiveData()
    val searchQuery: LiveData<String> = _searchQuery.withOutputSwitch(isOutputFlowing).asLiveData()
    val acquireReadContactsPermissionEvent: LiveData<Event<Int>> = acquireReadContactsPermissionEventMutable

    val displayedContacts = combine(
            appSavedContacts,
            deviceContacts,
            repository.dinerContactLookupKeys,
            _showHiddenContacts,
            _searchQuery) { contactsApp, contactsDevice, lookupKeys , showHidden, query ->

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

        sortContacts(combinedContacts, query)
    }.flowOn(Dispatchers.Main).withOutputSwitch(hasAttemptedReadDeviceContact).withOutputSwitch(isOutputFlowing).asLiveData()

    override fun notifyTransitionFinished() {
        super.notifyTransitionFinished()

        // When transition finishes, read the device contacts if they have not been read already
        if(!hasAttemptedReadDeviceContact.value) {
            requestContactsRefresh()
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
    fun deselectAllContacts() {
        selectionsMap.clear()
        _selections.value = selectionsMap.toMap()
    }

    fun requestContactsRefresh(acquirePermissionsIfNeeded: Boolean = true) {
        refreshingDeviceContacts.value = true

        when {
            isPermissionGranted(Manifest.permission.READ_CONTACTS) -> {
                readDeviceContactData().invokeOnCompletion {
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

    private fun readDeviceContactData() = viewModelScope.launch(Dispatchers.IO) {

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
    }
}


private fun sortContacts(contacts: List<Contact>, query: String): List<Contact> {
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