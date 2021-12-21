package com.grouptuity.grouptuity.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.*
import androidx.room.*
import com.grouptuity.grouptuity.BuildConfig
import com.grouptuity.grouptuity.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max


const val FRAG_DINERS = 0
const val FRAG_ITEMS = 1
const val FRAG_TAX_TIP = 2
const val FRAG_PAYMENTS = 3


fun newUUID(): String = UUID.randomUUID().toString()


val Context.preferenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "grouptuity_preferences")


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
    }

    inner class StoredPreference<T>(private val key: Preferences.Key<T>, private val defaultValue: T) {
        var isSet: Boolean? = null
            private set

        val stateFlow = preferenceDataStore.data.mapLatest { preferences ->
            preferences[key].also { isSet = true } ?: defaultValue.also { isSet = false }
        }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, defaultValue)

        init {
            keyStoredPreferenceMap[key.name] = this
        }

        var value: T
            get() = runBlocking { preferenceDataStore.data.map { it[key] ?: defaultValue }.first() }
            set(newValue) {
                CoroutineScope(Dispatchers.IO).launch {
                    preferenceDataStore.edit { preferences -> preferences[key] = newValue }
                }
            }
    }

    private val database = AppDatabase.getDatabase(context)
    private val preferenceDataStore = context.preferenceDataStore
    val keyStoredPreferenceMap = mutableMapOf<String, StoredPreference<*>>()

    // Preferences
    val prefAppVersion = StoredPreference(stringPreferencesKey(context.getString(R.string.preference_key_app_version)), BuildConfig.VERSION_NAME)
    val prefLoadedBillId = StoredPreference(stringPreferencesKey(context.getString(R.string.preference_key_loaded_bill_id)), "uninitialized")
    val prefUserName = StoredPreference(stringPreferencesKey(context.getString(R.string.preference_key_user_name)), context.getString(R.string.default_user_name))
    val prefUserEmail = StoredPreference(stringPreferencesKey(context.getString(R.string.preference_key_user_email)), "")
    val prefUserPhotoUri = StoredPreference(stringPreferencesKey(context.getString(R.string.preference_key_user_photo_uri)), "")
    val prefDefaultTaxPercent = StoredPreference(stringPreferencesKey(context.getString(R.string.preference_key_default_tax_percent)), context.getString(R.string.default_default_tax_percent))
    val prefDefaultTipPercent = StoredPreference(stringPreferencesKey(context.getString(R.string.preference_key_default_tip_percent)), context.getString(R.string.default_default_tip_percent))
    val prefTaxIsTipped = StoredPreference(booleanPreferencesKey(context.getString(R.string.preference_key_tax_is_tipped)), context.resources.getBoolean(R.bool.default_tax_is_tipped))
    val prefDiscountsReduceTip = StoredPreference(booleanPreferencesKey(context.getString(R.string.preference_key_discounts_reduce_tip)), context.resources.getBoolean(R.bool.default_discounts_reduce_tip))
    val prefAutoAddSelf = StoredPreference(booleanPreferencesKey(context.getString(R.string.preference_key_auto_add_self)), context.resources.getBoolean(R.bool.default_auto_add_self))
    val prefSearchWithTypoAssist = StoredPreference(booleanPreferencesKey(context.getString(R.string.preference_key_contact_search_typo_assist)), context.resources.getBoolean(R.bool.default_contact_search_typo_assist))

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
    val selfContact = combine(prefUserName.stateFlow, prefUserPhotoUri.stateFlow) { userName, userPhotoUri ->
        Contact.updateSelfContactData(userName, userPhotoUri)
        //TODO database.contactDao().save(Contact.self)
        Contact.self
    }.stateIn(CoroutineScope(Dispatchers.Unconfined), SharingStarted.Eagerly, Contact.self)
    val voiceInputMutable = MutableLiveData<Event<String>>()

    // Private backing fields for bill-specific entity flows
    private var mBill = Bill("", "", 0L, 0.0, true, 0.0, tipAsPercent = true, isTaxTipped = false, discountsReduceTip = false)
    private var mCashPool = Diner("", "", -1, Contact.cashPool)
    private var mRestaurant = Diner("", "", -0, Contact.restaurant)
    private var mSelfDiner: Diner? = null
    private var mDiners = mutableListOf<Diner>()
    private var mItems = mutableListOf<Item>()
    private var mDebts = mutableListOf<Debt>()
    private var mDiscounts = mutableListOf<Discount>()
    private var mPayments = mutableListOf<Payment>()
    private val _bill = MutableStateFlow(mBill)
    private val _cashPool = MutableStateFlow(mCashPool)
    private val _restaurant = MutableStateFlow(mRestaurant)
    private val _selfDiner = MutableStateFlow(mSelfDiner)
    private val _diners: MutableStateFlow<List<Diner>> = MutableStateFlow(mDiners)
    private val _items: MutableStateFlow<List<Item>> = MutableStateFlow(mItems)
    private val _debts: MutableStateFlow<List<Debt>> = MutableStateFlow(mDebts)
    private val _discounts: MutableStateFlow<List<Discount>> = MutableStateFlow(mDiscounts)
    private val _payments: MutableStateFlow<List<Payment>> = MutableStateFlow(mPayments)
    private var paymentStableId = 0L
    private val paymentStableIdMap = mutableMapOf<String, Long>()
    private var maxDinerListIndex = 0
    private var maxItemListIndex = 0
    private var maxDebtListIndex = 0
    private var maxDiscountListIndex = 0

    // Bill entities
    val bill: StateFlow<Bill> = _bill
    val cashPool: StateFlow<Diner> = _cashPool
    val restaurant: StateFlow<Diner> = _restaurant
    val selfDiner: StateFlow<Diner?> = _selfDiner
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
    val taxIsTipped: Flow<Boolean> = bill.mapLatest { it.isTaxTipped }
    val discountsReduceTip: Flow<Boolean> = bill.mapLatest { it.discountsReduceTip }

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

    // Other calculation results
    private val _discountValues = MutableStateFlow(emptyMap<Discount, Double>())
    val discountValues: StateFlow<Map<Discount, Double>> = _discountValues
    val numberOfDiners = diners.mapLatest { it.size }.stateIn(CoroutineScope(Dispatchers.Unconfined), SharingStarted.Eagerly, 0)
    val numberOfItems = items.mapLatest { it.size }.stateIn(CoroutineScope(Dispatchers.Unconfined), SharingStarted.Eagerly, 0)
    val billIncludesSelf = diners.mapLatest { it.any { diner -> diner.isSelf() } }.stateIn(CoroutineScope(Dispatchers.Unconfined), SharingStarted.Eagerly, false)
    val hasUnprocessedPayments = payments.mapLatest { paymentsList -> paymentsList.any { it.unprocessed()} }

    private fun commitBill() = CoroutineScope(Dispatchers.Default).launch {

        val billCalculation = BillCalculation(mBill, mCashPool, mRestaurant, mDiners, mItems, mDebts, mDiscounts, mPayments)

        mPayments.clear()
        mPayments.addAll(billCalculation.sortedPayments)

        for (payment in mPayments) {
            if (payment.committed) {
                if (!paymentStableIdMap.containsKey(payment.id)) {
                    paymentStableIdMap[payment.id] = paymentStableId
                    paymentStableId++
                }
            } else {
                if (!paymentStableIdMap.containsKey(payment.payerId + payment.payeeId)) {
                    paymentStableIdMap[payment.payerId + payment.payeeId] = paymentStableId
                    paymentStableId++
                }
            }
        }

        _bill.value = mBill
        _cashPool.value = mCashPool
        _restaurant.value = mRestaurant
        _selfDiner.value = mSelfDiner
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

        _discountValues.value = billCalculation.discountValues

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
            prefDefaultTaxPercent.value.toDouble(),
            true,
            prefDefaultTipPercent.value.toDouble(),
            true,
            prefTaxIsTipped.value,
            prefDiscountsReduceTip.value)

        maxDinerListIndex = 0
        maxItemListIndex = 0
        maxDebtListIndex = 0
        maxDiscountListIndex = 0

        // Commit updated objects to UI
        mBill = newBill
        mCashPool = Diner(newUUID(), newBill.id, -1, Contact.cashPool)
        mRestaurant = Diner(newUUID(), newBill.id, -1, Contact.restaurant)
        if (prefAutoAddSelf.value) {
            Diner(newUUID(), newBill.id, ++maxDinerListIndex, Contact.self).also {
                mSelfDiner = it
                mDiners = mutableListOf(it)
            }
        } else {
            mSelfDiner = null
            mDiners = mutableListOf()
        }
        mItems = mutableListOf()
        mDebts = mutableListOf()
        mDiscounts = mutableListOf()
        mPayments = mutableListOf()

        paymentStableId = 0L
        paymentStableIdMap.clear()

        commitBill()

        // Write updates to the database
        database.saveBill(newBill).invokeOnCompletion {
            prefLoadedBillId.value = newBill.id

            database.saveDiner(mCashPool)
            database.saveDiner(mRestaurant)
            mSelfDiner?.also { database.saveDiner(it) }
        }
    }
    fun loadSavedBill(context: Context, billId: String) = CoroutineScope(Dispatchers.IO).launch {
        loadInProgress.value = true

        val payload = database.loadBill(context, billId)
        if (payload == null) {
            createAndLoadNewBill()
        } else {
            withContext(Dispatchers.Main) {
                mBill = payload.bill
                mCashPool = payload.cashPool
                mRestaurant = payload.restaurant
                mSelfDiner = payload.selfDiner
                mDiners = payload.diners.sortedBy { it.listIndex }.toMutableList()
                mItems = payload.items.sortedBy { it.listIndex }.toMutableList()
                mDebts = payload.debts.sortedBy { it.listIndex }.toMutableList()
                mDiscounts = payload.discounts.sortedBy { it.listIndex }.toMutableList()
                mPayments = payload.payments

                paymentStableId = 0L
                paymentStableIdMap.clear()

                maxDinerListIndex = if (payload.diners.isEmpty()) 0 else payload.diners.maxOf { it.listIndex }
                maxItemListIndex = if (payload.items.isEmpty()) 0 else payload.items.maxOf { it.listIndex }
                maxDebtListIndex = if (payload.debts.isEmpty()) 0 else payload.debts.maxOf { it.listIndex }
                maxDiscountListIndex = if (payload.discounts.isEmpty()) 0 else payload.discounts.maxOf { it.listIndex }

                commitBill()
            }

            // Save ID of newly loaded bill so it will reload automatically on app relaunch
            prefLoadedBillId.value = payload.bill.id
        }
    }
    fun deleteBill(bill: Bill) {
        if(bill == mBill) {
            // Load a new bill if the current one is being deleted
            createAndLoadNewBill()
        }

        database.deleteBill(bill)
    }
    fun setTitle(title: String) {
        mBill = mBill.withTitle(title)
        commitBill()
        database.saveBill(mBill)
    }
    fun setTaxPercent(taxPercent: Double) {
        mBill = mBill.withTaxPercent(taxPercent)
        commitBill()
        database.saveBill(mBill)
    }
    fun setTaxAmount(taxAmount: Double) {
        mBill = mBill.withTaxAmount(taxAmount)
        commitBill()
        database.saveBill(mBill)
    }
    fun setTipPercent(tipPercent: Double) {
        mBill = mBill.withTipPercent(tipPercent)
        commitBill()
        database.saveBill(mBill)
    }
    fun setTipAmount(tipAmount: Double) {
        mBill = mBill.withTipAmount(tipAmount)
        commitBill()
        database.saveBill(mBill)
    }
    fun setTaxTipped(taxTipped: Boolean) {
        mBill = mBill.withTaxTipped(taxTipped)
        commitBill()
        database.saveBill(mBill)
    }
    fun setDiscountsReduceTip(reduceTip: Boolean) {
        mBill = mBill.withDiscountsReduceTip(reduceTip)
        commitBill()
        database.saveBill(mBill)
    }
    fun resetTaxAndTip(): () -> Unit {
        val oldTaxAsPercent = mBill.taxAsPercent
        val oldTipAsPercent = mBill.tipAsPercent
        val oldTax = mBill.tax
        val oldTip = mBill.tip

        mBill = mBill
            .withTaxPercent(prefDefaultTaxPercent.value.toDouble())
            .withTipPercent(prefDefaultTipPercent.value.toDouble())
        commitBill()
        database.saveBill(mBill)

        return {
            mBill = mBill
                .let { if (oldTaxAsPercent) it.withTaxPercent(oldTax) else it.withTaxAmount(oldTax) }
                .let { if (oldTipAsPercent) it.withTipPercent(oldTip) else it.withTipAmount(oldTip) }
            commitBill()
            database.saveBill(mBill)
        }
    }
    fun toggleTaxIsTipped() { setTaxTipped(!mBill.isTaxTipped) }
    fun toggleDiscountsReduceTip() { setDiscountsReduceTip(!mBill.discountsReduceTip) }

    // Diner Functions
    fun addSelfAsDiner(name: String? = null, includeWithEveryone: Boolean = true) {
        // Return if self is already on the bill
        if (mDiners.any { it.isSelf() }) {
            Log.e("addSelfAsDiner", "Self is already included on the bill")
            return
        }

        // Save new name to Contact.self before instantiating the self Diner
        if (name != null) {
            Contact.updateSelfContactData(name, Contact.self.photoUri)
            //TODO database.contactDao().save(Contact.self)
        }

        val selfDiner = Diner(newUUID(), mBill.id, ++maxDinerListIndex, Contact.self)

        if (includeWithEveryone) {
            // TODO
        }

        mDiners.add(selfDiner)
        mSelfDiner = selfDiner
        commitBill()

        // TODO save contact to database?

        database.saveDiner(selfDiner)
        // TODO commit updates for other entities affected by includeWithEveryone
    }
    fun createNewDiner(name: String,
                       paymentAddresses: Map<PaymentMethod, String>,
                       saveContact: Boolean = false,
                       includeWithEveryone: Boolean = true): Diner {

        val newContact = Contact(name, paymentAddresses)

        val newDiner = Diner(newUUID(), mBill.id, ++maxDinerListIndex, newContact).also {
            if(includeWithEveryone) {
                // TODO add all-diner items/debts/discounts to this diner
            }
        }

        mDiners.add(newDiner)

        commitBill()

        if (saveContact) {
            database.saveContact(newContact)
        }

        database.saveDiner(newDiner)
        //TODO commit updates for other entities affected by includeWithEveryone

        return newDiner
    }
    fun addContactsAsDiners(context: Context, dinerContacts: List<Contact>, includeWithEveryone: Boolean = true) {
        val diners: List<Diner> = dinerContacts.map { contact ->
            Diner(newUUID(), mBill.id, ++maxDinerListIndex, contact).also {
                if(includeWithEveryone) {
                    // TODO add all-diner items/debts/discounts to this diner
                }

                CoroutineScope(Dispatchers.IO).launch {
                    AddressBook.getContactData(context, contact.lookupKey)?.apply {
                        // Update name, photoUri, and email addresses
                        it.name = name
                        it.photoUri = photoUri
                        it.emailAddresses = emailAddresses
                    }
                }
            }
        }

        mDiners.addAll(diners)

        commitBill()

        database.saveContacts(dinerContacts)
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

        if (diner.isSelf()) {
            mSelfDiner = null
        }

        commitBill()

        database.deleteDiner(diner)
        // TODO warn if any entities are without diners
    }
    fun removeAllDiners() {
        val oldDiners = mDiners

        mDiners.forEach { diner ->
            diner.items.forEach { it.removeDiner(diner) }
            diner.debtsOwed.forEach { it.removeDebtor(diner) }
            diner.debtsHeld.forEach { it.removeCreditor(diner) }
            diner.discountsReceived.forEach { it.removeRecipient(diner) }
            diner.discountsPurchased.forEach { it.removePurchaser(diner) }
            diner.paymentsSent.forEach { mPayments.remove(it) }
            diner.paymentsReceived.forEach { mPayments.remove(it) }
        }
        mDiners = mutableListOf()
        mSelfDiner = null

        commitBill()

        database.deleteDiners(oldDiners)

        // TODO warn if any entities are without items
    }

    // Item Functions
    fun createNewItem(price: Double, name: String, diners: Collection<Diner>): Item {
        val newItem = Item(newUUID(), mBill.id, ++maxItemListIndex, price, name)

        diners.forEach { diner ->
            newItem.addDiner(diner)
            diner.addItem(newItem)
        }

        mItems.add(newItem)
        commitBill()

        database.saveItem(newItem)

        return newItem
    }
    fun editItem(editedItem: Item, price: Double, name: String, diners: Collection<Diner>) {
        // Remove edited item from its associated diners and discounts
        editedItem.diners.forEach { it.removeItem(editedItem) }
        editedItem.discounts.forEach { it.removeItem(editedItem) }

        // Add new item to diners and discounts
        val newItem = Item(editedItem.id, editedItem.billId, editedItem.listIndex, price, name)
        diners.forEach { diner ->
            newItem.addDiner(diner)
            diner.addItem(newItem)
        }
        editedItem.discounts.forEach { discount ->
            newItem.addDiscount(discount)
            discount.addItem(newItem)
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
    fun removeAllItems() {
        val oldItems = mItems

        mItems.forEach { item ->
            item.diners.forEach { it.removeItem(item) }
            item.discounts.forEach { it.removeItem(item) }
        }

        mItems = mutableListOf()

        commitBill()

        database.deleteItems(oldItems)
        // TODO warn if any item discounts are without items
    }

    // Debt Functions
    fun createNewDebt(
        amount: Double,
        name: String,
        debtors: Collection<Diner>,
        creditors: Collection<Diner>
    ): Debt {
        val debt = Debt(newUUID(), mBill.id, ++maxDebtListIndex, amount, name)

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

        return debt
    }
    fun removeDebt(debt: Debt) {
        mDebts.remove(debt)
        debt.debtors.forEach { it.removeOwedDebt(debt) }
        debt.creditors.forEach { it.removeHeldDebt(debt) }

        commitBill()

        database.deleteDebt(debt)
    }

    // Discount Functions
    fun createNewDiscount(
        asPercent: Boolean,
        onItems: Boolean,
        value: Double,
        cost: Double?,
        items: List<Item>,
        recipients: List<Diner>,
        purchasers: List<Diner>
    ): Discount {
        val discount = Discount(newUUID(), mBill.id, ++maxDiscountListIndex, asPercent, onItems, value, cost)

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

        return discount
    }
    fun editDiscount(editedDiscount: Discount, asPercent: Boolean, onItems: Boolean, value: Double, cost: Double?, items: List<Item>, recipients: List<Diner>, purchasers: List<Diner>) {
        // Remove discount from associated items, recipients, and purchasers
        editedDiscount.items.forEach { it.removeDiscount(editedDiscount) }
        editedDiscount.recipients.forEach { it.removeReceivedDiscount(editedDiscount) }
        editedDiscount.purchasers.forEach { it.removePurchasedDiscount(editedDiscount) }

        // Add new discount to item, recipient, and purchaser objects
        val newDiscount = Discount(
            editedDiscount.id,
            editedDiscount.billId,
            editedDiscount.listIndex,
            asPercent,
            onItems,
            value,
            cost)
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

    // Payment Functions
    fun getPaymentStableId(payment: Payment?): Long? {
        return when {
            payment == null -> { null }
            payment.committed -> { paymentStableIdMap[payment.id] }
            else -> { paymentStableIdMap[payment.payerId + payment.payeeId] }
        }
    }
    fun setPaymentTemplate(
        method: PaymentMethod,
        payer: Diner,
        payee: Diner,
        surrogate: Diner? = null,
        payerAddress: String? = null,
        payeeAddress: String? = null,
        surrogateAddress: String? = null) {

        val newTemplate = PaymentTemplate(
            method,
            payer.id,
            payee.id,
            surrogate?.id,
            payerAddress,
            payeeAddress,
            surrogateAddress)

        payer.setPaymentTemplate(newTemplate)

        if (payerAddress != null && payer.getDefaultAddressForMethod(method) == null) {
            payer.setDefaultAddressForMethod(method, payerAddress)
        }

        if (payeeAddress != null && payee.getDefaultAddressForMethod(method) == null) {
            payee.setDefaultAddressForMethod(method, payeeAddress)
            database.saveDiner(payee)
        }

        if (surrogateAddress != null && surrogate!!.getDefaultAddressForMethod(method) == null) {
            surrogate.setDefaultAddressForMethod(method, surrogateAddress)
            database.saveDiner(surrogate)
        }

        commitBill()

        database.saveDiner(payer)
    }
    fun resetAllPaymentTemplates() {
        // TODO
    }

    init {
        ProcessLifecycleOwner.get().lifecycleScope.launchWhenResumed {
            when(val billId = prefLoadedBillId.value) {
                "uninitialized" -> createAndLoadNewBill()
                mBill.id -> { /* Correct bill already loaded */ }
                else -> {
                    // TODO check if bill is expired
                    loadSavedBill(context, billId)
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
                lookupKey == Contact.self.lookupKey -> {
                    // TODO pull data for self
                    return null
                }
                lookupKey.startsWith(Contact.GROUPTUITY_LOOKUPKEY_PREFIX) -> {
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
            fromApp.forEach { (lookupKey, contact) ->
                this.merge(lookupKey, contact) { deviceContact, appContact ->
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


@Database(entities = [Bill::class, Diner::class, Item::class, Debt::class, Discount::class,
    Payment::class, Contact::class, DinerItemJoin::class, DebtDebtorJoin::class,
    DebtCreditorJoin::class, DiscountRecipientJoin::class, DiscountPurchaserJoin::class,
    DiscountItemJoin::class, PaymentPayerJoin::class, PaymentPayeeJoin::class], exportSchema = false, version = 1)
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
            newDatabase.contactDao().insert(Contact.cashPool)
            newDatabase.contactDao().insert(Contact.restaurant)
            newDatabase.contactDao().insert(Contact.self)
        }
    }

    fun getSavedBills() = billDao().getSavedBills()

    fun saveContact(contact: Contact) = CoroutineScope(Dispatchers.IO).launch { contactDao().upsert(contact) }
    fun saveContacts(contacts: List<Contact>) = CoroutineScope(Dispatchers.IO).launch { contactDao().upsert(contacts) }
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

    fun saveBill(bill: Bill) = CoroutineScope(Dispatchers.IO).launch { billDao().upsert(bill) }
    fun saveDiner(diner: Diner) = CoroutineScope(Dispatchers.IO).launch { dinerDao().save(diner) }
    fun saveDiners(diners: Collection<Diner>) = CoroutineScope(Dispatchers.IO).launch { dinerDao().save(diners) }
    fun saveItem(item: Item) = CoroutineScope(Dispatchers.IO).launch { itemDao().save(item) }
    fun saveItems(items: Collection<Item>) = CoroutineScope(Dispatchers.IO).launch { itemDao().save(items) }
    fun saveDebt(debt: Debt) = CoroutineScope(Dispatchers.IO).launch { debtDao().save(debt) }
    fun saveDiscount(discount: Discount) = CoroutineScope(Dispatchers.IO).launch { discountDao().save(discount) }
    fun savePayment(payment: Payment) = CoroutineScope(Dispatchers.IO).launch { paymentDao().save(payment) }

    suspend fun loadBill(context: Context, billId: String): LoadBillPayload? {
        val bill = billDao().getBill(billId) ?: return null
        val cashPool = dinerDao().getCashPoolOnBill(billId) ?: return null
        val restaurant = dinerDao().getRestaurantOnBill(billId) ?: return null
        var selfDiner: Diner? = null
        val dinerMap = dinerDao().getDinersOnBill(billId).associateBy { it.id }
        val itemMap = itemDao().getItemsOnBill(billId).associateBy { it.id }
        val debtMap = debtDao().getDebtsOnBill(billId).associateBy { it.id }
        val discountMap = discountDao().getDiscountsOnBill(billId).associateBy { it.id }
        val paymentMap = paymentDao().getPaymentsOnBill(billId).associateBy { it.id }

        dinerMap.forEach { (_, diner) ->
            AddressBook.getContactData(context, diner.lookupKey)?.apply {
                // Update name, photoUri, and email addresses
                diner.name = name
                diner.photoUri = photoUri
                diner.emailAddresses = emailAddresses
            }
            diner.populateEntityLists(itemMap, debtMap, discountMap, paymentMap)

            if (diner.isSelf()) {
                selfDiner = diner
            }
        }
        itemMap.forEach { (_, item) -> item.populateEntityLists(dinerMap, discountMap) }
        debtMap.forEach { (_, debt) -> debt.populateEntityLists(dinerMap) }
        discountMap.forEach { (_, discount) -> discount.populateEntityLists(dinerMap, itemMap) }
        paymentMap.forEach { (_, payment) -> payment.populateEntities(dinerMap) }

        return LoadBillPayload(
            bill,
            cashPool,
            restaurant,
            selfDiner,
            dinerMap.values.toMutableList(),
            itemMap.values.toMutableList(),
            debtMap.values.toMutableList(),
            discountMap.values.toMutableList(),
            paymentMap.values.toMutableList())
    }

    suspend fun loadSavedContacts() = contactDao().getSavedContacts()
}


data class LoadBillPayload(val bill: Bill,
                           val cashPool: Diner,
                           val restaurant: Diner,
                           val selfDiner: Diner?,
                           val diners: MutableList<Diner>,
                           val items: MutableList<Item>,
                           val debts: MutableList<Debt>,
                           val discounts: MutableList<Discount>,
                           val payments: MutableList<Payment>)
