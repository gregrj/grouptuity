package com.grouptuity.grouptuity.data

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.icu.text.NumberFormat
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.*
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max


const val FRAG_DINERS = 0
const val FRAG_ITEMS = 1
const val FRAG_TAX_TIP = 2
const val FRAG_PAYMENTS = 3


class Repository(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        private lateinit var scope: CoroutineScope

        fun getInstance(application: Application): Repository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    scope = (application as GrouptuityApplication).scope
                    StoredPreference.initialize(application)
                    val instance = Repository(application.applicationContext)
                    INSTANCE = instance
                    instance
                }
            }
        }

        fun <T> Flow<T>.asState(initialValue: T): StateFlow<T> =
            this.stateIn(scope, SharingStarted.WhileSubscribed(), initialValue)

        fun <T> Flow<Set<T>>.toSortedEntityList(selector: (T) -> Long): StateFlow<List<T>> =
            this.stateIn(scope, SharingStarted.WhileSubscribed(), emptySet())
                .map { entitySet -> entitySet.sortedBy { selector(it) } }
                .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())
    }

    private fun Flow<BigDecimal>.toCurrencyString(): StateFlow<String> =
        combine(
            this,
            bill.flatMapLatest { it.currencyFlow }.map {
                NumberFormat.getCurrencyInstance()
            }
        ) { number, formatter ->
            formatter.format(number)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), "")

    private fun Flow<BigDecimal>.toPercentString(): StateFlow<String> = this.map {
        NumberFormat.getPercentInstance().format(it)
    }.stateIn(scope, SharingStarted.WhileSubscribed(), "")

    private val database = AppDatabase.getDatabase(context)
    val addressBook = AddressBook(context, database)

    // App-level data
    val loadInProgress = MutableStateFlow(true)
    val processingPayments = MutableStateFlow(false)
    val activePaymentAndMethod = MutableStateFlow<Pair<Payment?, PaymentMethod?>>(Pair(null, null))
    val activeFragmentIndex = MutableStateFlow(FRAG_DINERS).also {
        // Clear active payment if active fragment changes to something other than payments
        it.onEach { index ->
            if (index != FRAG_PAYMENTS) {
                activePaymentAndMethod.value = Pair(null, null)
            }
        }.launchIn(CoroutineScope(Dispatchers.Unconfined))
    }
    val bills = database.getSavedBills()
    val voiceInputMutable = MutableLiveData<Event<String>>()

    private var mBill = Bill("", "", 0L)
    private val _bill = MutableStateFlow(mBill)

    // Bill entities
    val bill: StateFlow<Bill> = _bill
    val diners: StateFlow<List<Diner>> =
        bill.flatMapLatest { it.diners }.toSortedEntityList { it.listIndex }
    val items: StateFlow<List<Item>> =
        bill.flatMapLatest { it.items }.toSortedEntityList { it.listIndex }
    val discounts: StateFlow<List<Discount>> =
        bill.flatMapLatest { it.discounts }.toSortedEntityList { it.listIndex }
    val debts: StateFlow<List<Debt>> =
        bill.flatMapLatest { it.debts }.toSortedEntityList { it.listIndex }
    val payments: StateFlow<List<Payment>> =
        bill.flatMapLatest { it.allPayments }.asState(emptyList())

    // Group Bill Data
    val groupSubtotal: StateFlow<String> = bill.flatMapLatest { it.groupSubtotal }.toCurrencyString()
    val groupDiscountAmount: StateFlow<String> = bill.flatMapLatest { it.groupDiscountAmount }.toCurrencyString()
    val groupSubtotalWithDiscounts: StateFlow<String> = bill.flatMapLatest { it.groupSubtotalWithDiscounts }.toCurrencyString()
    val taxPercent: StateFlow<String> = bill.flatMapLatest { it.groupTaxPercent }.toPercentString()
    val groupTaxAmount: StateFlow<String> = bill.flatMapLatest { it.groupTaxAmount }.toCurrencyString()
    val groupSubtotalWithDiscountsAndTax: StateFlow<String> = bill.flatMapLatest { it.groupSubtotalWithDiscountsAndTax }.toCurrencyString()
    val tipPercent: StateFlow<String> = bill.flatMapLatest { it.groupTipPercent }.toPercentString()
    val groupTipAmount: StateFlow<String> = bill.flatMapLatest { it.groupTipAmount }.toCurrencyString()
    val groupTotal: StateFlow<String> = bill.flatMapLatest { it.groupTotal }.toCurrencyString()

    // Individual Diner Data
    val individualSubtotals: StateFlow<Map<Diner,BigDecimal>> = diners.assemble(scope) {
        it.displayedSubtotal
    }

    // Other derived data
    val numberOfDiners: StateFlow<Int> = diners.map { it.size }.asState(0)
    val numberOfItems: StateFlow<Int> = items.map { it.size }.asState(0)
    val currencyDigits: StateFlow<Int> = bill.flatMapLatest { it.currencyFlow }.map{ it.defaultFractionDigits }.asState(2)
    val isUserOnBill: StateFlow<Boolean> = bill.flatMapLatest { it.isUserOnBill }.asState(false)
    val isTaxTipped: StateFlow<Boolean> = bill.flatMapLatest { it.isTaxTippedFlow }.asState(false)
    val doDiscountsReduceTip: StateFlow<Boolean> = bill.flatMapLatest { it.discountsReduceTipFlow }.asState(false)
    val hasUnprocessedPayments = bill.flatMapLatest { it.hasUnprocessedPayments }.asState(false)
    val discountRoundingMode: StateFlow<RoundingMode> = bill.flatMapLatest { it.discountRoundingModeFlow }.asState(RoundingMode.HALF_UP)

    // Bill Functions
    fun createAndLoadNewBill() {
        loadInProgress.value = true

        val oldBill = mBill

        val timestamp = System.currentTimeMillis()
        mBill = Bill(
            newUUID(),
            "Bill from " +
                    SimpleDateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
                        .format(Date(timestamp)),
            timestamp)

        if (StoredPreference.autoAddUser.value) {
            addUserAsDiner(false, skipDatabaseSave=true)
        }

        // Write updates to the database
        database.saveBill(mBill).invokeOnCompletion {
            database.saveDiner(mBill.cashPool)
            database.saveDiner(mBill.restaurant)
            mBill.userDiner?.also { database.saveDiner(it) }

            StoredPreference.loadedBillId.value = mBill.id
        }

        _bill.value = mBill
        loadInProgress.value = false

        oldBill.delete()
    }
    fun loadSavedBill(context: Context, billId: String) = CoroutineScope(Dispatchers.IO).launch {
        loadInProgress.value = true

        val loadedBill = database.loadBill(context, billId)
        if (loadedBill == null) {
            createAndLoadNewBill()
            // TODO notify of failure?
        } else {
            mBill = loadedBill
            _bill.value = mBill
            loadInProgress.value = false

            // Save ID of newly loaded bill so it will reload automatically on app relaunch
            StoredPreference.loadedBillId.value = loadedBill.id
        }
    }
    fun deleteBill(bill: Bill) {
        if(bill == mBill) {
            // Load a new bill if the current one is being deleted
            createAndLoadNewBill()
        }
        TODO()
        database.deleteBill(bill)
    }
    
    fun resetTaxAndTip(): () -> Unit {
        val oldTaxAsPercent = mBill.taxAsPercent
        val oldTipAsPercent = mBill.tipAsPercent
        val oldTax = mBill.taxInput
        val oldTip = mBill.tipInput

        mBill.taxAsPercent = true
        mBill.tipAsPercent = true
        mBill.taxInput = StoredPreference.defaultTaxPercent.value
        mBill.tipInput = StoredPreference.defaultTipPercent.value

        database.saveBill(mBill)

        return {
            mBill.taxAsPercent = oldTaxAsPercent
            mBill.tipAsPercent = oldTipAsPercent
            mBill.taxInput = oldTax
            mBill.tipInput = oldTip

            database.saveBill(mBill)
        }
    }
    fun setTaxPercent(percent: BigDecimal) {
        mBill.taxAsPercent = true
        mBill.taxInput = percent.toString()
        database.saveBill(mBill)
    }
    fun setTaxAmount(amount: BigDecimal) {
        mBill.taxAsPercent = false
        mBill.taxInput = amount.toString()
        database.saveBill(mBill)
    }
    fun setTipPercent(percent: BigDecimal) {
        mBill.tipAsPercent = true
        mBill.tipInput = percent.toString()
        database.saveBill(mBill)
    }
    fun setTipAmount(amount: BigDecimal) {
        mBill.tipAsPercent = false
        mBill.tipInput = amount.toString()
        database.saveBill(mBill)
    }
    fun toggleTaxIsTipped() {
        mBill.isTaxTipped = !mBill.isTaxTipped
        database.saveBill(mBill)
    }
    fun toggleDiscountsReduceTip() {
        mBill.discountsReduceTip = !mBill.discountsReduceTip
        database.saveBill(mBill)
    }

    // Diner Functions
    fun getDiner(dinerId: String?): Diner? = dinerId?.let {
        mBill.diners.value.find { it.id == dinerId }
    }
    fun addUserAsDiner(
        includeWithEveryone: Boolean,
        name: String? = null,
        skipDatabaseSave: Boolean = false
    ) {
        // Retrieve or create a Contact representing the user
        val userContact = if (name != null) {
            // Save new name to preferences, but also manually create a copy of Contact.user with
            // the new name because Contact.user updates asynchronously
            StoredPreference.userName.value = name
            Contact.user.withName(name)
        } else {
            Contact.user
        }

        // Add a new diner to the Bill associated with the user Contact
        mBill.createDiners(listOf(userContact), includeWithEveryone)[0]?.also {
            if (!skipDatabaseSave) {
                database.saveDiner(it)
            }
        }
    }
    fun createDinerFromNewContact(
        name: String,
        addresses: Map<PaymentMethod, String>,
        includeWithEveryone: Boolean
    ): Diner {
        // Create a new Contact and save its data to the database
        val newContact = Contact(name, addresses)
        database.saveContact(newContact)

        // Add a new diner to the Bill associated with the new Contact
        val newDiner = mBill.createDiners(listOf(newContact), includeWithEveryone)[0]?.also {
            database.saveDiner(it)
        }
        
        return newDiner
    }
    fun addContactsAsDiners(
        context: Context,
        dinerContacts: List<Contact>,
        includeWithEveryone: Boolean
    ) {
        // Add diners to the bill corresponding to the list of Contacts and asynchronously check for
        // updated contact info for each Diner using the AddressBook
        val newDiners = mBill.createDiners(dinerContacts, includeWithEveryone).onEach { newDiner ->
            // Launch coroutine to query device for updates to the Diner's contact info
            CoroutineScope(Dispatchers.IO).launch {
                AddressBook.getContactData(context, newDiner.lookupKey)?.apply {
                    newDiner.applyContactInfo(this)
                    // TODO save updates into the app database?
                }
            }
        }

        // Save contacts and diners into the app database
        database.saveContacts(dinerContacts)
        database.saveDiners(newDiners)
    }

    fun removeDiner(diner: Diner) {
        mBill.removeDiners(setOf(diner))
        database.deleteDiner(diner)
        // TODO warn if any entities are without diners
    }
    fun removeAllDiners() {
        val allDiners = diners.value
        mBill.removeDiners(allDiners)
        database.deleteDiners(allDiners)
        // TODO warn if any entities are without items
    }
    fun saveAddressForDiner(diner: Diner, method: PaymentMethod, address: String?) {
        diner.setAddressForMethod(method, address)
        database.saveDiner(diner)

        if (diner.isUser) {
            when (method) {
                PaymentMethod.PAYBACK_LATER -> StoredPreference.userEmail.value = address
                PaymentMethod.VENMO -> StoredPreference.userVenmoAddress.value = address
                PaymentMethod.CASH_APP -> StoredPreference.userCashtag.value = address
                PaymentMethod.ALGO -> StoredPreference.userAlgorandAddress.value = address
                else -> { }
            }
        } else {
            addressBook.saveAddressForContact(diner.lookupKey, method, address)
        }
    }

    // Item Functions
    fun getItem(itemId: String?): Item? = itemId?.let { mBill.items.value.find { it.id == itemId } }
    fun createNewItem(
        price: String,
        name: String,
        diners: Set<Diner>,
        applyEntireBillDiscounts: Boolean): Item =
        mBill.createItem(price, name, diners, applyEntireBillDiscounts).also {
            database.saveItem(it)
        }
    fun editItem(editedItem: Item, name: String, price: String, diners: Set<Diner>) {
        editedItem.name = name
        editedItem.priceInput = price
        editedItem.setDiners(diners)

        database.saveItem(editedItem)
    }
    fun removeItem(item: Item) {
        mBill.removeItems(setOf(item))
        database.deleteItem(item)
        // TODO warn if any item discounts are without items
    }
    fun removeAllItems() {
        val allItems = items.value
        mBill.removeItems(allItems)
        database.deleteItems(allItems)
        // TODO warn if any item discounts are without items
    }

    // Discount Functions
    fun getDiscount(discountId: String?): Discount? = discountId?.let {
        mBill.discounts.value.first { it.id == discountId }
    }
    fun createNewDiscountForItemsByPercent(
        percent: BigDecimal,
        cost: BigDecimal,
        items: Collection<Item>,
        purchasers: Collection<Diner>
    ): Discount =
        mBill.createDiscountForItemsByPercent(
            percent.toString(),
            cost.toString(),
            items.toSet(),
            purchasers.toSet()
        ).also {
            database.saveDiscount(it)
        }
    fun createNewDiscountForItemsByValue(
        value: BigDecimal,
        cost: BigDecimal,
        items: Collection<Item>,
        purchasers: Collection<Diner>
    ): Discount =
        mBill.createDiscountForItemsByValue(
            value.toString(),
            cost.toString(),
            items.toSet(),
            purchasers.toSet()
        ).also {
            database.saveDiscount(it)
        }
    fun createNewDiscountForDinersByPercent(
        percent: BigDecimal,
        cost: BigDecimal,
        recipients: Collection<Diner>,
        purchasers: Collection<Diner>
    ): Discount =
        mBill.createDiscountForDinersByPercent(
            percent.toString(),
            cost.toString(),
            recipients.toSet(),
            purchasers.toSet()
        ).also {
            database.saveDiscount(it)
        }
    fun createNewDiscountForDinersByValue(
        value: BigDecimal,
        cost: BigDecimal,
        recipients: Collection<Diner>,
        purchasers: Collection<Diner>
    ): Discount =
        mBill.createDiscountForDinersByValue(
            value.toString(),
            cost.toString(),
            recipients.toSet(),
            purchasers.toSet()
        ).also {
            database.saveDiscount(it)
        }
    fun editDiscount(
        editedDiscount: Discount,
        asPercent: Boolean,
        onItems: Boolean,
        amount: BigDecimal,
        cost: BigDecimal,
        items: Collection<Item>,
        recipients: Collection<Diner>,
        purchasers: Collection<Diner>
    ) {
        editedDiscount.apply {
            asPercentInput = asPercent
            onItemsInput = onItems
            amountInput = amount.toString()
            costInput = cost.toString()
            setItems(items.toSet())
            setRecipients(recipients.toSet())
            setPurchasers(purchasers.toSet())
        }

        database.saveDiscount(editedDiscount)
    }
    fun removeDiscount(discount: Discount) {
        mBill.removeDiscounts(setOf(discount))
        database.deleteDiscount(discount)
    }
    fun removeAllDiscounts() {
        val allDiscounts = discounts.value
        mBill.removeDiscounts(allDiscounts)
        database.deleteDiscounts(allDiscounts)
    }

    // Debt Functions
    fun createNewDebt(
        amount: String,
        name: String,
        debtors: Set<Diner>,
        creditors: Set<Diner>
    ): Debt = mBill.createDebt(amount, name, debtors, creditors).also {
        database.saveDebt(it)
    }
    fun removeDebt(debt: Debt) {
        mBill.removeDebts(setOf(debt))
        database.deleteDebt(debt)
    }

    fun setPaymentTemplate(
        method: PaymentMethod,
        payer: Diner,
        payee: Diner,
        surrogate: Diner? = null,
        payerAddress: String? = null,
        payeeAddress: String? = null,
        surrogateAddress: String? = null
    ) {
        val newTemplate = PaymentTemplate(
            method,
            payer.id,
            payee.id,
            surrogate?.id,
            payerAddress,
            payeeAddress,
            surrogateAddress)

        payer.setPaymentTemplate(newTemplate)
        // TODO trigger payment algorithm
        database.saveDiner(payer)
    }
    fun resetAllPaymentTemplates() {
        // TODO
    }
    fun commitPayment(payment: Payment) {
        mBill.commitPayment(payment)
        database.savePayment(payment)
    }
    fun unCommitPayment(payment: Payment) {
        mBill.unCommitPayment(payment)
        database.deletePayment(payment)
    }

    init {
        ProcessLifecycleOwner.get().lifecycleScope.launchWhenStarted {
            when(val billId = StoredPreference.loadedBillId.value) {
                null -> createAndLoadNewBill()
                mBill.id -> { /* Correct bill already loaded */ }
                else -> {
                    // TODO check if bill is expired
                    loadSavedBill(context, billId)
                }
            }

            combine(
                StoredPreference.userName.stateFlow,
                StoredPreference.userPhotoUri.stateFlow,
                StoredPreference.userEmail.stateFlow,
                StoredPreference.userVenmoAddress.stateFlow,
                StoredPreference.userCashtag.stateFlow,
                StoredPreference.userAlgorandAddress.stateFlow
            ) {
                val defaultPaymentAddresses = mutableMapOf<PaymentMethod, String>()
                it[2]?.apply { defaultPaymentAddresses[PaymentMethod.PAYBACK_LATER] = this }
                it[3]?.apply { defaultPaymentAddresses[PaymentMethod.VENMO] = this }
                it[4]?.apply { defaultPaymentAddresses[PaymentMethod.CASH_APP] = this }
                it[5]?.apply { defaultPaymentAddresses[PaymentMethod.ALGO] = this }

                Contact(
                    Contact.GROUPTUITY_USER_CONTACT_LOOKUPKEY,
                    it[0]!!,
                    Contact.VISIBLE,
                    defaultPaymentAddresses).also { user ->
                        it[1]?.apply { user.photoUri = this }
                }
            }.collect {
                Contact.user = it

                CoroutineScope(Dispatchers.IO).launch {
                    database.contactDao().update(Contact.user)
                }
            }
        }
    }
}


class AddressBook(context: Context, private val database: AppDatabase) {
    companion object {
        data class ContactInfo(val name: String, val photoUri: String?, val emailAddresses: List<String>)

        private fun getEmailAddressesForContactId(context: Context, contactId: String): List<String> {
            val emailAddresses = mutableListOf<String>()
            var cursor: Cursor? = null

            try {
                cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = " + contactId,
                    null,
                    null
                )?.apply {
                    while(this.moveToNext()) {
                        emailAddresses.add(this.getString(0))
                    }
                }
            } catch (e: Exception) {
                Log.e("Failed email query", e.stackTraceToString())
            } finally {
                cursor?.close()
            }

            return emailAddresses
        }

        fun getContactData(context: Context, lookupKey: String): ContactInfo? {
            when {
                lookupKey == Contact.GROUPTUITY_USER_CONTACT_LOOKUPKEY -> {
                    // TODO pull data for user
                    return null
                }
                lookupKey.startsWith(Contact.GROUPTUITY_LOOKUPKEY_PREFIX) ||
                    lookupKey == Contact.GROUPTUITY_CASH_POOL_LOOKUPKEY ||
                        lookupKey == Contact.GROUPTUITY_RESTAURANT_LOOKUPKEY -> {
                    // Contact created by user in app. All information is already loaded.
                    return null
                }
            }

            var contactName: String? = null
            var contactPhotoUri: String? = null
            var contactEmailAddresses: List<String>? = null
            var cursor: Cursor? = null

            try {
                val lookupUri: Uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)

                cursor = context.contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.Contacts.PHOTO_URI),
                    null,
                    null,
                    null)?.apply {

                    if (this.moveToFirst()) {
                        contactName = this.getString(2).trim()
                        contactPhotoUri = this.getString(3)
                        contactEmailAddresses = getEmailAddressesForContactId(context, this.getString(0))
                    }
                }
            } catch (e: Exception) {
                Log.e("Failed contact query", e.stackTraceToString())
            } finally {
                cursor?.close()
            }

            return if (contactName != null) {
                ContactInfo(contactName!!, contactPhotoUri, contactEmailAddresses ?: emptyList())
            } else {
                null
            }
        }
    }

    private val excludedNames = context.resources.getStringArray(R.array.addressbook_excluded_names)

    private val _refreshingDeviceContacts = MutableStateFlow(false)
    private val _readDeviceContactPending = MutableStateFlow(true)
    val refreshingDeviceContacts = _refreshingDeviceContacts
    val readDeviceContactPending = _readDeviceContactPending

    private var appContacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    private val deviceContacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val allContacts: StateFlow<Map<String, Contact>> = combineTransform(
        appContacts,
        deviceContacts,
        _readDeviceContactPending) { fromApp, fromDevice, readPending ->

        if(readPending)
            return@combineTransform

        val updatedAppContacts = mutableListOf<Contact>()

        // Start with device contacts and supplement with saved app contacts
        emit(fromDevice.toMutableMap().apply {
            fromApp.forEach { (lookupKey, contact) ->
                this.merge(lookupKey, contact) { deviceContact, appContact ->
                    // Cache contact so it can be saved in the database after updating
                    updatedAppContacts.add(appContact)

                    appContact.also {
                        it.name = deviceContact.name
                        it.photoUri = deviceContact.photoUri
                        // Email addresses not needed for contacts displayed in the AddressBook and
                        // will be queried only if the Contacts are added to the Bill as Diners
                    }
                }
            }
        })

        database.saveContacts(updatedAppContacts)
    }.stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.WhileSubscribed(), emptyMap())

    init {
        CoroutineScope(Dispatchers.IO).launch {
            appContacts.value = database.loadSavedContacts().associateBy { it.lookupKey }.toMutableMap()
        }
    }

    fun favoriteContacts(lookupKeys: Collection<String>) = database.favoriteContacts(lookupKeys)
    fun unfavoriteContacts(lookupKeys: Collection<String>) = database.resetContactVisibilities(lookupKeys)
    fun unfavoriteFavoriteContacts() = database.unfavoriteFavoriteContacts()
    fun hideContacts(lookupKeys: Collection<String>) = database.hideContacts(lookupKeys)
    fun unhideContacts(lookupKeys: Collection<String>) = database.resetContactVisibilities(lookupKeys)
    fun unhideHiddenContacts() = database.unhideHiddenContacts()

    fun saveAddressForContact(lookupKey: String, method: PaymentMethod, address: String?) =
        appContacts.value[lookupKey]?.apply {
            this.setDefaultAddressForMethod(method, address)
            database.saveContact(this)
        }

    fun refreshDeviceContacts(context: Context) = CoroutineScope(Dispatchers.IO).launch {
        _refreshingDeviceContacts.value = true

        val timestamp = System.currentTimeMillis()

        val contacts = mutableMapOf<String, Contact>()

        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER),
            null,
            null,
            null)?.apply {

            while(moveToNext())
            {
                val name = getString(2)

                if(name.isNullOrBlank() || excludedNames.contains(name))
                    continue

                contacts[getString(1)] = Contact(
                    getString(1),
                    name.trim(),
                    Contact.VISIBLE).also {
                        it.photoUri = getString(3)
                    }
            }
        }?.close()

        // Slight delay to give SwipeRefreshLayout time to animate to indicate refresh is taking place
        delay(max(0L, 500L + timestamp - System.currentTimeMillis()))

        deviceContacts.value = contacts

        _refreshingDeviceContacts.value = false
        _readDeviceContactPending.value = false
    }
    fun bypassRefreshDeviceContacts() {
        deviceContacts.value = emptyMap()
        _refreshingDeviceContacts.value = false
        _readDeviceContactPending.value = false
    }
}
