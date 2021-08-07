package com.grouptuity.grouptuity.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.*
import androidx.room.*
import com.grouptuity.grouptuity.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max


fun <T> Flow<T>.withOutputSwitch(switch: Flow<Boolean>): Flow<T> { return combineTransform(switch) { data, enabled -> if(enabled) emit(data) } }


val Context.preferenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "grouptuity_preferences")


abstract class UIViewModel(app: Application): AndroidViewModel(app) {
    protected val repository = Repository.getInstance(app)

    private val transitionInputLocked = MutableStateFlow(false)
    private val inputLocks = mutableListOf<Flow<Boolean>>(transitionInputLocked)
    var isInputLocked: StateFlow<Boolean> = transitionInputLocked
        private set

    private val _isOutputFlowing = MutableStateFlow(true)
    protected val isOutputFlowing: Flow<Boolean> = _isOutputFlowing

    protected fun addInputLock(lock: Flow<Boolean>) {
        inputLocks.add(lock)
        isInputLocked = combine(inputLocks) { locks ->
            locks.any { it }
        }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, true)
    }
    fun unLockInput() { inputLocks.forEach { if(it is MutableStateFlow) it.value = false } }

    fun freezeOutput() { _isOutputFlowing.value = false }
    fun unFreezeOutput() { _isOutputFlowing.value = true }

    open fun notifyTransitionStarted() { transitionInputLocked.value = true }
    open fun notifyTransitionFinished() { transitionInputLocked.value = false }

    fun isPermissionGranted(permissionString: String) = getApplication<Application>().checkSelfPermission(permissionString) == PackageManager.PERMISSION_GRANTED
}


class Repository(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                val instance = Repository(context)
                INSTANCE = instance
                instance
            }
        }

        // Preference Keys
        val LOADED_BILL_ID_KEY = stringPreferencesKey("loaded_bill_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_PHOTO_URI_KEY = stringPreferencesKey("user_photo_uri")
        val TAX_PERCENT_KEY = doublePreferencesKey("default_tax_percent")
        val TIP_PERCENT_KEY = doublePreferencesKey("default_tip_percent")
        val TAX_IS_TIPPED_KEY = booleanPreferencesKey("default_tax_is_tipped")
        val DISCOUNTS_REDUCE_TIP_KEY = booleanPreferencesKey("default_discounts_reduce_tip")
    }

    private val database = AppDatabase.getDatabase(context)
    private val preferenceDataStore = context.preferenceDataStore

    // Preferences
    private val loadedBillId: StateFlow<String> = getPreferenceFlow(LOADED_BILL_ID_KEY, "", "pending")
    val userName: StateFlow<String> = getPreferenceFlow(USER_NAME_KEY, "You") //TODO parameterize
    val userPhotoUri: StateFlow<String> = getPreferenceFlow(USER_PHOTO_URI_KEY, "")
    val defaultTaxPercent: StateFlow<Double> = getPreferenceFlow(TAX_PERCENT_KEY, 7.25)
    val defaultTipPercent: StateFlow<Double> = getPreferenceFlow(TIP_PERCENT_KEY, 15.0)
    val taxIsTipped: StateFlow<Boolean> = getPreferenceFlow(TAX_IS_TIPPED_KEY, false)
    val discountsReduceTip: StateFlow<Boolean> = getPreferenceFlow(DISCOUNTS_REDUCE_TIP_KEY, false)

    // App-level data
    val loadInProgress = MutableStateFlow(true)
    val bills = database.getSavedBills()
    val selfContact = combine(userName, userPhotoUri) { userName, userPhotoUri ->
        Contact.updateSelfContactData(userName, userPhotoUri)
        //TODO database.contactDao().save(Contact.self)
        Contact.self
    }.stateIn(CoroutineScope(Dispatchers.Unconfined), SharingStarted.Eagerly, Contact.self)

    // Private backing fields for bill-specific entity flows
    private var mBill = Bill("", "", 0L, 0.0, true, 0.0, tipAsPercent = true, isTaxTipped = false, discountsReduceTip = false)
    private var mRestaurant = Diner("", "", Contact.restaurant, PaymentPreferences())
    private var mDiners = mutableListOf<Diner>()
    private var mItems = mutableListOf<Item>()
    private var mDebts = mutableListOf<Debt>()
    private var mDiscounts = mutableListOf<Discount>()
    private var mPayments = mutableListOf<Payment>()
    private val _bill = MutableStateFlow(mBill)
    private val _restaurant = MutableStateFlow(mRestaurant)
    private val _diners: MutableStateFlow<List<Diner>> = MutableStateFlow(mDiners)
    private val _items: MutableStateFlow<List<Item>> = MutableStateFlow(mItems)
    private val _debts: MutableStateFlow<List<Debt>> = MutableStateFlow(mDebts)
    private val _discounts: MutableStateFlow<List<Discount>> = MutableStateFlow(mDiscounts)
    private val _payments: MutableStateFlow<List<Payment>> = MutableStateFlow(mPayments)

    // Bill entities
    val bill: StateFlow<Bill> = _bill
    val restaurant: StateFlow<Diner> = _restaurant
    val diners: StateFlow<List<Diner>> = _diners
    val items: StateFlow<List<Item>> = _items
    val debts: StateFlow<List<Debt>> = _debts
    val discounts: StateFlow<List<Discount>> = _discounts
    val payments: StateFlow<List<Payment>> = _payments

    // Group bill calculation results
    private val _groupSubtotal = MutableStateFlow(0.0)
    private val _groupDiscountAmount = MutableStateFlow(0.0)
    private val _groupSubtotalWithDiscounts = MutableStateFlow(0.0)
    private val _taxPercent = MutableStateFlow(0.0)
    private val _groupTaxAmount = MutableStateFlow(0.0)
    private val _groupSubtotalWithDiscountsAndTax = MutableStateFlow(0.0)
    private val _tipPercent = MutableStateFlow(0.0)
    private val _groupTipAmount = MutableStateFlow(0.0)
    private val _groupTotal = MutableStateFlow(0.0)
    val groupSubtotal: Flow<Double> = _groupSubtotal
    val groupDiscountAmount: Flow<Double> = _groupDiscountAmount
    val groupSubtotalWithDiscounts: Flow<Double> = _groupSubtotalWithDiscounts
    val taxPercent: Flow<Double> = _taxPercent
    val groupTaxAmount: Flow<Double> = _groupTaxAmount
    val groupSubtotalWithDiscountsAndTax: Flow<Double> = _groupSubtotalWithDiscountsAndTax
    val tipPercent: Flow<Double> = _tipPercent
    val groupTipAmount: Flow<Double> = _groupTipAmount
    val groupTotal: Flow<Double> = _groupTotal

    // Individual bill calculation results
    private val _individualSubtotals = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualDiscountShares = MutableStateFlow(emptyMap<Diner, Map<Discount, Double>>())
    private val _individualDiscountTotals = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualExcessDiscountsReleased = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualExcessDiscountsAcquired = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualSubtotalsWithDiscounts = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualReimbursementDebts = MutableStateFlow(emptyMap<Diner, Map<Discount, Double>>())
    private val _individualReimbursementCredits = MutableStateFlow(emptyMap<Diner, Map<Discount, Double>>())
    private val _individualDebtOwed = MutableStateFlow(emptyMap<Diner, Map<Debt, Double>>())
    private val _individualDebtHeld = MutableStateFlow(emptyMap<Diner, Map<Debt, Double>>())
    private val _individualTax = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualSubtotalWithDiscountsAndTax = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualTip = MutableStateFlow(emptyMap<Diner, Double>())
    private val _individualTotal = MutableStateFlow(emptyMap<Diner, Double>())
    val individualSubtotals: Flow<Map<Diner, Double>> = _individualSubtotals
    val individualDiscountShares: Flow<Map<Diner, Map<Discount, Double>>> = _individualDiscountShares
    val individualDiscountTotals: Flow<Map<Diner, Double>> = _individualDiscountTotals
    val individualExcessDiscountsReleased: Flow<Map<Diner, Double>> = _individualExcessDiscountsReleased
    val individualExcessDiscountsAcquired: Flow<Map<Diner, Double>> = _individualExcessDiscountsAcquired
    val individualSubtotalsWithDiscounts: Flow<Map<Diner, Double>> = _individualSubtotalsWithDiscounts
    val individualReimbursementDebts: Flow<Map<Diner, Map<Discount, Double>>> = _individualReimbursementDebts
    val individualReimbursementCredits: Flow<Map<Diner, Map<Discount, Double>>> = _individualReimbursementCredits
    val individualDebtOwed: Flow<Map<Diner, Map<Debt, Double>>> = _individualDebtOwed
    val individualDebtHeld: Flow<Map<Diner, Map<Debt, Double>>> = _individualDebtHeld
    val individualTax: Flow<Map<Diner, Double>> = _individualTax
    val individualSubtotalWithDiscountsAndTax: Flow<Map<Diner, Double>> = _individualSubtotalWithDiscountsAndTax
    val individualTip: Flow<Map<Diner, Double>> = _individualTip
    val individualTotal: Flow<Map<Diner, Double>> = _individualTotal

    private fun <T> getPreferenceFlow(key: Preferences.Key<T>, defaultValue: T, initialValue: T? = null): StateFlow<T> = preferenceDataStore.data.mapLatest { preferences ->
        preferences[key] ?: defaultValue
    }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, initialValue ?: defaultValue)
    private fun <T> setPreferenceValue(key: Preferences.Key<T>, newValue: T) = CoroutineScope(Dispatchers.IO).launch {
        preferenceDataStore.edit { preferences -> preferences[key] = newValue }
    }

    private fun newUUID() = UUID.randomUUID().toString()

    private fun commitBill() = CoroutineScope(Dispatchers.Main).launch {

        val billCalculation = BillCalculation(mBill, mRestaurant, mDiners, mItems, mDebts, mDiscounts, mPayments)

        _bill.value = mBill
        _diners.value = mDiners.toList()
        _items.value = mItems.toList()
        _debts.value = mDebts.toList()
        _discounts.value = mDiscounts.toList()
        _payments.value = mPayments.toList()

        _groupSubtotal.value = billCalculation.groupSubtotal
        _groupDiscountAmount.value = billCalculation.groupDiscountAmount
        _groupSubtotalWithDiscounts.value = billCalculation.groupSubtotalWithDiscounts
        _taxPercent.value = billCalculation.groupTaxPercent
        _groupTaxAmount.value = billCalculation.groupTaxAmount
        _groupSubtotalWithDiscountsAndTax.value = billCalculation.groupSubtotalWithDiscountsAndTax
        _tipPercent.value = billCalculation.groupTipPercent
        _groupTipAmount.value = billCalculation.groupTipAmount
        _groupTotal.value = billCalculation.groupTotal

        _individualSubtotals.value = billCalculation.individualSubtotals
        _individualDiscountShares.value = billCalculation.individualDiscountShares
        _individualDiscountTotals.value = billCalculation.individualDiscountTotals
        _individualExcessDiscountsReleased.value = billCalculation.individualExcessDiscountsReleased
        _individualExcessDiscountsAcquired.value = billCalculation.individualExcessDiscountsAcquired
        _individualSubtotalsWithDiscounts.value = billCalculation.individualSubtotalsWithDiscounts
        _individualReimbursementDebts.value = billCalculation.individualReimbursementDebts
        _individualReimbursementCredits.value = billCalculation.individualReimbursementCredits
        _individualDebtOwed.value = billCalculation.individualDebtOwed
        _individualDebtHeld.value = billCalculation.individualDebtHeld
        _individualTax.value = billCalculation.individualTax
        _individualSubtotalWithDiscountsAndTax.value = billCalculation.individualSubtotalWithDiscountsAndTax
        _individualTip.value = billCalculation.individualTip
        _individualTotal.value = billCalculation.individualTotal

        loadInProgress.value = false
    }

    // Bill Functions
    fun createAndLoadNewBill() {
        loadInProgress.value = true
        val timestamp = System.currentTimeMillis()

        val newBill = Bill(
            newUUID(),
            "Bill from " + SimpleDateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT).format(Date(timestamp)),
            timestamp,
            defaultTaxPercent.value,
            true,
            defaultTipPercent.value,
            true,
            taxIsTipped.value,
            discountsReduceTip.value)

        val restaurant = Diner(newUUID(), newBill.id, Contact.restaurant)

        // Commit updated objects to UI
        mBill = newBill
        mRestaurant = restaurant
        mDiners = mutableListOf()
        mItems = mutableListOf()
        mDebts = mutableListOf()
        mDiscounts = mutableListOf()
        mPayments = mutableListOf()
        commitBill()

        // Write updates to the database
        database.saveBill(newBill).invokeOnCompletion { setPreferenceValue(LOADED_BILL_ID_KEY, newBill.id) }
        database.saveDiner(restaurant)
    }
    fun loadSavedBill(billId: String) = CoroutineScope(Dispatchers.IO).launch {
        loadInProgress.value = true

        database.loadBill(billId)?.also { payload ->
            withContext(Dispatchers.Main) {
                mBill = payload.bill
                mRestaurant = payload.restaurant
                mDiners = payload.diners
                mItems = payload.items
                mDebts = payload.debts
                mDiscounts = payload.discounts
                mPayments = payload.payments

                commitBill()
            }

            // Save ID of newly loaded bill so it will reload automatically on app relaunch
            setPreferenceValue(LOADED_BILL_ID_KEY, payload.bill.id)
        }
    }
    fun deleteBill(bill: Bill) {
        if(bill == mBill) {
            // Load a new bill if the current one is being deleted
            createAndLoadNewBill()
        }

        database.deleteBill(bill)
    }

    // Diner Functions
    fun addSelfAsDiner(includeWithEveryone: Boolean = true) {
        val selfDiner = Diner(newUUID(), mBill.id, Contact.self)

        if (includeWithEveryone) {
            // TODO
        }

        mDiners.add(selfDiner)
        commitBill()

        database.saveDiner(selfDiner)
        //TODO commit updates for other entities affected by includeWithEveryone
    }
    fun addContactsAsDiners(dinerContacts: Collection<Contact>, includeWithEveryone: Boolean = true) {
        val diners: List<Diner> = if (includeWithEveryone) {
            dinerContacts.map { contact ->
                Diner(newUUID(), mBill.id, contact).also {
                    // TODO
                }
            }
        } else {
            dinerContacts.map { contact -> Diner(newUUID(), mBill.id, contact) }
        }

        mDiners.addAll(diners)
        commitBill()

        database.saveDiners(diners)
        //TODO commit updates for other entities affected by includeWithEveryone
    }
    fun removeDiner(diner: Diner) {
        mDiners.remove(diner)
        diner.items.forEach { it.removeDiner(diner) }
        diner.debtsOwed.forEach { it.removeDebtor(diner) }
        diner.debtsHeld.forEach { it.removeCreditor(diner) }
        diner.discountsReceived.forEach { it.removeRecipient(diner) }
        diner.discountsPurchased.forEach { it.removePurchaser(diner) }
        diner.paymentsSent.forEach { mPayments.remove(it) }
        diner.paymentsReceived.forEach { mPayments.remove(it) }

        commitBill()

        database.deleteDiner(diner)
        // TODO warn if any entities are without diners
    }

    // Item Functions
    fun addItem(price: Double, name: String, diners: Collection<Diner>) {
        val item = Item(newUUID(), mBill.id, price, name)
        diners.forEach { diner ->
            item.addDiner(diner)
            diner.addItem(item)
        }

        mItems.add(item)
        commitBill()

        database.saveItem(item)
    }
    fun editItem(editedItem: Item, price: Double, name: String, diners: Collection<Diner>) {
        // Remove edited item from the associated diners
        editedItem.diners.forEach { it.removeItem(editedItem) }

        // Add new item to diners
        val newItem = Item(editedItem.id, editedItem.billId, price, name)
        diners.forEach { diner ->
            newItem.addDiner(diner)
            diner.addItem(newItem)
        }

        // Replace old item with new item
        val index = mItems.indexOf(editedItem)
        mItems.remove(editedItem)
        mItems.add(index, newItem)

        commitBill()

        database.saveItem(newItem)
    }
    fun removeItem(item: Item) {
        mItems.remove(item)
        item.diners.forEach { it.removeItem(item) }
        item.discounts.forEach { it.removeItem(item) }

        commitBill()

        database.deleteItem(item)
        // TODO warn if any item discounts are without items
    }

    // Debt Functions
    fun addDebt(amount: Double, debtors: Collection<Diner>, creditors: Collection<Diner>) {
        val debt = Debt(newUUID(), mBill.id, amount)
        debtors.forEach { debtor ->
            debt.addDebtor(debtor)
            debtor.addOwedDebt(debt)
        }
        creditors.forEach { creditor ->
            debt.addCreditor(creditor)
            creditor.addHeldDebt(debt)
        }

        mDebts.add(debt)
        commitBill()

        database.saveDebt(debt)
    }
    fun removeDebt(debt: Debt) {
        mDebts.remove(debt)
        debt.debtors.forEach { it.removeOwedDebt(debt) }
        debt.creditors.forEach { it.removeHeldDebt(debt) }

        commitBill()

        database.deleteDebt(debt)
    }

    // Discount Functions
    fun addDiscount(asPercent: Boolean, onItems: Boolean, value: Double, cost: Double?, items: List<Item>, recipients: List<Diner>, purchasers: List<Diner>) {
        val discount = Discount(newUUID(), mBill.id, asPercent, onItems, value, cost)
        items.forEach { item ->
            discount.addItem(item)
            item.addDiscount(discount)
        }
        recipients.forEach { recipient ->
            discount.addRecipient(recipient)
            recipient.addReceivedDiscount(discount)
        }
        purchasers.forEach { purchaser ->
            discount.addPurchaser(purchaser)
            purchaser.addPurchasedDiscount(discount)
        }

        mDiscounts.add(discount)
        commitBill()

        database.saveDiscount(discount)
    }
    fun editDiscount(editedDiscount: Discount, asPercent: Boolean, onItems: Boolean, value: Double, cost: Double?, items: List<Item>, recipients: List<Diner>, purchasers: List<Diner>) {
        // Remove discount from associated items, recipients, and purchasers
        editedDiscount.items.forEach { it.removeDiscount(editedDiscount) }
        editedDiscount.recipients.forEach { it.removeReceivedDiscount(editedDiscount) }
        editedDiscount.purchasers.forEach { it.removePurchasedDiscount(editedDiscount) }

        // Add new discount to item, recipient, and purchaser objects
        val newDiscount = Discount(editedDiscount.id, editedDiscount.billId, asPercent, onItems, value, cost)
        items.forEach { item ->
            newDiscount.addItem(item)
            item.addDiscount(newDiscount)
        }
        recipients.forEach { recipient ->
            newDiscount.addRecipient(recipient)
            recipient.addReceivedDiscount(newDiscount)
        }
        purchasers.forEach { purchaser ->
            newDiscount.addPurchaser(purchaser)
            purchaser.addPurchasedDiscount(newDiscount)
        }

        // Replace oldDiscount with new Discount
        val index = mDiscounts.indexOf(editedDiscount)
        mDiscounts.remove(editedDiscount)
        mDiscounts.add(index, newDiscount)

        commitBill()

        database.saveDiscount(newDiscount)
    }
    fun removeDiscount(discount: Discount) {
        mDiscounts.remove(discount)
        discount.items.forEach { it.removeDiscount(discount) }
        discount.recipients.forEach { it.removeReceivedDiscount(discount) }
        discount.purchasers.forEach { it.removePurchasedDiscount(discount) }

        commitBill()

        database.deleteDiscount(discount)
    }

    init {
        ProcessLifecycleOwner.get().lifecycleScope.launchWhenResumed {
            when(val billId = loadedBillId.value) {
                "", "pending" -> createAndLoadNewBill()
                mBill.id -> { /* Correct bill already loaded */ }
                else -> {
                    // TODO check if bill is expired
                    loadSavedBill(billId)
                }
            }
        }
    }
}


class AddressBook(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: AddressBook? = null

        fun getInstance(context: Context): AddressBook {
            return INSTANCE ?: synchronized(this) {
                val instance = AddressBook(context)
                INSTANCE = instance
                instance
            }
        }
    }

    private val database = AppDatabase.getDatabase(context)

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
            fromApp.forEach { (lookupKey, appContact) ->
                this.merge(lookupKey, appContact) { oldContact, newContact ->
                    if (oldContact.name != newContact.name ||
                        oldContact.photoUri != newContact.photoUri) {
                        newContact.withUpdatedExternalInfo(oldContact.name, newContact.name).also {
                            updatedAppContacts.add(it)
                        }
                    } else {
                        newContact
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

    fun saveContact(contact: Contact) = database.saveContact(contact)
    fun removeContact(contact: Contact) = database.removeContact(contact)

    fun favoriteContact(lookupKey: String) = CoroutineScope(Dispatchers.IO).launch { database.favoriteContact(lookupKey) }
    fun favoriteContacts(lookupKeys: Collection<String>) = CoroutineScope(Dispatchers.IO).launch { database.favoriteContacts(lookupKeys) }
    fun unfavoriteContact(lookupKey: String) = CoroutineScope(Dispatchers.IO).launch { database.resetContactVisibility(lookupKey) }
    fun unfavoriteContacts(lookupKeys: Collection<String>) = CoroutineScope(Dispatchers.IO).launch { database.resetContactVisibilities(lookupKeys) }
    fun unfavoriteFavoriteContacts() = CoroutineScope(Dispatchers.IO).launch { database.unfavoriteFavoriteContacts() }
    fun hideContact(lookupKey: String) = CoroutineScope(Dispatchers.IO).launch { database.hideContact(lookupKey) }
    fun hideContacts(lookupKeys: Collection<String>) = CoroutineScope(Dispatchers.IO).launch { database.hideContacts(lookupKeys) }
    fun unhideContact(lookupKey: String) = CoroutineScope(Dispatchers.IO).launch { database.resetContactVisibility(lookupKey) }
    fun unhideContacts(lookupKeys: Collection<String>) = CoroutineScope(Dispatchers.IO).launch { database.resetContactVisibilities(lookupKeys) }
    fun unhideHiddenContacts() = CoroutineScope(Dispatchers.IO).launch { database.unhideHiddenContacts() }

    fun refreshDeviceContacts(context: Context) = CoroutineScope(Dispatchers.IO).launch {
        _refreshingDeviceContacts.value = true

        val timestamp = System.currentTimeMillis()

        val contacts = mutableMapOf<String, Contact>()

        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_URI),
            null,
            null,
            null)?.apply {

            while(moveToNext())
            {
                val name = getString(2)

                if(name.isNullOrBlank() || excludedNames.contains(name))
                    continue

                contacts[getString(1)] = Contact(getString(1),
                                                 name.trim(),
                                                 getString(3),
                                                 Contact.VISIBLE)
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


@Database(entities = [Bill::class, Diner::class, Item::class, Debt::class, Discount::class,
    Payment::class, Contact::class, DinerItemJoin::class, DebtDebtorJoin::class,
    DebtCreditorJoin::class, DiscountRecipientJoin::class, DiscountPurchaserJoin::class,
    DiscountItemJoin::class, PaymentPayerJoin::class, PaymentPayeeJoin::class],
    exportSchema = false, version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun billDao(): BillDao
    abstract fun dinerDao(): DinerDao
    abstract fun itemDao(): ItemDao
    abstract fun debtDao(): DebtDao
    abstract fun discountDao(): DiscountDao
    abstract fun paymentDao(): PaymentDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) { INSTANCE ?: buildDatabase(context).also { INSTANCE = it } }

        private fun buildDatabase(context: Context): AppDatabase {
            val newDatabase = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "grouptuity.db").build()
            populateInitialData(newDatabase)
            return newDatabase
        }

        private fun populateInitialData(newDatabase: AppDatabase) = runBlocking {
            newDatabase.contactDao().save(Contact.restaurant)
            newDatabase.contactDao().save(Contact.self)
        }
    }

    fun getSavedBills() = billDao().getSavedBills()

    fun saveContact(contact: Contact) = CoroutineScope(Dispatchers.IO).launch { contactDao().save(contact) }
    fun saveContacts(contacts: Collection<Contact>) = CoroutineScope(Dispatchers.IO).launch { contactDao().save(contacts) }
    fun removeContact(contact: Contact) = CoroutineScope(Dispatchers.IO).launch { contactDao().delete(contact) }
    suspend fun resetContactVisibility(lookupKey: String) = contactDao().resetVisibility(lookupKey)
    suspend fun resetContactVisibilities(lookupKeys: Collection<String>) = contactDao().resetVisibility(lookupKeys)
    suspend fun favoriteContact(lookupKey: String) = contactDao().favorite(lookupKey)
    suspend fun favoriteContacts(lookupKeys: Collection<String>) = contactDao().favorite(lookupKeys)
    suspend fun unfavoriteFavoriteContacts() = contactDao().unfavoriteAllFavorites()
    suspend fun hideContact(lookupKey: String) = contactDao().hide(lookupKey)
    suspend fun hideContacts(lookupKeys: Collection<String>) = contactDao().hide(lookupKeys)
    suspend fun unhideHiddenContacts() = contactDao().unhideAllHidden()

    fun deleteBill(bill: Bill) = CoroutineScope(Dispatchers.IO).launch { billDao().delete(bill) }
    fun deleteDiner(diner: Diner) = CoroutineScope(Dispatchers.IO).launch { dinerDao().delete(diner) }
    fun deleteItem(item: Item) = CoroutineScope(Dispatchers.IO).launch { itemDao().delete(item) }
    fun deleteDebt(debt: Debt) = CoroutineScope(Dispatchers.IO).launch { debtDao().delete(debt) }
    fun deleteDiscount(discount: Discount) = CoroutineScope(Dispatchers.IO).launch { discountDao().delete(discount) }
    fun deletePayment(payment: Payment) = CoroutineScope(Dispatchers.IO).launch { paymentDao().delete(payment) }

    fun deleteBills(bills: Collection<Bill>) = CoroutineScope(Dispatchers.IO).launch { billDao().delete(bills) }
    fun deleteDiners(diners: Collection<Diner>) = CoroutineScope(Dispatchers.IO).launch { dinerDao().delete(diners) }
    fun deleteItems(items: Collection<Item>) = CoroutineScope(Dispatchers.IO).launch { itemDao().delete(items) }
    fun deleteDebts(debts: Collection<Debt>) = CoroutineScope(Dispatchers.IO).launch { debtDao().delete(debts) }
    fun deleteDiscounts(discounts: Collection<Discount>) = CoroutineScope(Dispatchers.IO).launch { discountDao().delete(discounts) }
    fun deletePayments(payments: Collection<Payment>) = CoroutineScope(Dispatchers.IO).launch { paymentDao().delete(payments) }

    fun saveBill(bill: Bill) = CoroutineScope(Dispatchers.IO).launch { billDao().save(bill) }
    fun saveDiner(diner: Diner) = CoroutineScope(Dispatchers.IO).launch { dinerDao().save(diner) }
    fun saveDiners(diners: Collection<Diner>) = CoroutineScope(Dispatchers.IO).launch { dinerDao().save(diners) }
    fun saveItem(item: Item) = CoroutineScope(Dispatchers.IO).launch { itemDao().save(item) }
    fun saveItems(items: Collection<Item>) = CoroutineScope(Dispatchers.IO).launch { itemDao().save(items) }
    fun saveDebt(debt: Debt) = CoroutineScope(Dispatchers.IO).launch { debtDao().save(debt) }
    fun saveDiscount(discount: Discount) = CoroutineScope(Dispatchers.IO).launch { discountDao().save(discount) }
    fun savePayment(payment: Payment) = CoroutineScope(Dispatchers.IO).launch { paymentDao().save(payment) }

    suspend fun loadBill(billId: String): LoadBillPayload? {
        val bill = billDao().getBill(billId) ?: return null
        val restaurant = dinerDao().getRestaurantOnBill(billId) ?: return null
        val dinerMap = dinerDao().getDinersOnBill(billId).associateBy { it.id }
        val itemMap = itemDao().getItemsOnBill(billId).associateBy { it.id }
        val debtMap = debtDao().getDebtsOnBill(billId).associateBy { it.id }
        val discountMap = discountDao().getDiscountsOnBill(billId).associateBy { it.id }
        val paymentMap = paymentDao().getPaymentsOnBill(billId).associateBy { it.id }

        dinerMap.forEach { (_, diner) -> diner.populateEntityLists(itemMap, debtMap, discountMap, paymentMap) }
        itemMap.forEach { (_, item) -> item.populateEntityLists(dinerMap, discountMap) }
        debtMap.forEach { (_, debt) -> debt.populateEntityLists(dinerMap) }
        discountMap.forEach { (_, discount) -> discount.populateEntityLists(dinerMap, itemMap) }
        paymentMap.forEach { (_, payment) -> payment.populateEntities(dinerMap) }

        return LoadBillPayload(
            bill,
            restaurant,
            dinerMap.values.toMutableList(),
            itemMap.values.toMutableList(),
            debtMap.values.toMutableList(),
            discountMap.values.toMutableList(),
            paymentMap.values.toMutableList())
    }

    suspend fun loadSavedContacts() = contactDao().getSavedContacts()
}


data class LoadBillPayload(val bill: Bill,
                           val restaurant: Diner,
                           val diners: MutableList<Diner>,
                           val items: MutableList<Item>,
                           val debts: MutableList<Debt>,
                           val discounts: MutableList<Discount>,
                           val payments: MutableList<Payment>)
