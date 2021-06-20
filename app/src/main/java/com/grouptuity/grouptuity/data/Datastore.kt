package com.grouptuity.grouptuity.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.*
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


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
    private val database = AppDatabase.getDatabase(context)
    private val preferenceDataStore = context.preferenceDataStore

    // Data Access Objects
    private val billDao = database.billDao()
    private val dinerDao = database.dinerDao()
    private val itemDao = database.itemDao()
    private val debtDao = database.debtDao()
    private val discountDao = database.discountDao()
    private val paymentDao = database.paymentDao()
    private val contactDao = database.contactDao()

    // Preferences
    private val loadedBillId: StateFlow<Long> = getPreferenceFlow(LOADED_BILL_ID_KEY, 0L, -1L)
    val userName: StateFlow<String> = getPreferenceFlow(USER_NAME_KEY, "You") //TODO parameterize
    val userPhotoUri: StateFlow<String> = getPreferenceFlow(USER_PHOTO_URI_KEY, "")
    val defaultTaxPercent: StateFlow<Double> = getPreferenceFlow(TAX_PERCENT_KEY, 7.25)
    val defaultTipPercent: StateFlow<Double> = getPreferenceFlow(TIP_PERCENT_KEY, 15.0)
    val taxIsTipped: StateFlow<Boolean> = getPreferenceFlow(TAX_IS_TIPPED_KEY, false)
    val discountsReduceTip: StateFlow<Boolean> = getPreferenceFlow(DISCOUNTS_REDUCE_TIP_KEY, false)

    // App-level data
    val bills = billDao.getSavedBills()
    val appContacts = contactDao.getSavedContacts()

    // Bill entity lists
    val loadedBill: Flow<Bill> = loadedBillId.transformLatest{ emitAll(billDao.getBill(it)) }.flowOn(Dispatchers.IO).filterNotNull()
    val diners: Flow<Array<Diner>> = loadedBill.transformLatest{ emitAll(dinerDao.getDinersOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val items: Flow<Array<Item>> = loadedBill.transformLatest{ emitAll(itemDao.getItemsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val debts: Flow<Array<Debt>> = loadedBill.transformLatest{ emitAll(debtDao.getDebtsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val discounts: Flow<Array<Discount>> = loadedBill.transformLatest{ emitAll(discountDao.getDiscountsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val payments: Flow<Array<Payment>> = loadedBill.transformLatest{ emitAll(paymentDao.getPaymentsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val restaurant: Flow<Diner> = loadedBill.transformLatest{ emitAll(billDao.getRestaurantId(it.id)) }.transformLatest{ emitAll(dinerDao.getDiner(it)) }.flowOn(Dispatchers.IO)
    val dinerContactLookupKeys: Flow<List<String>> = diners.mapLatest { it.map { diner -> diner.lookupKey } }

    // Bill entity by ID getters
    fun getDiner(dinerId: Flow<Long>): StateFlow<Diner?> = dinerId.transformLatest { id ->
        if(id == 0L) {
            emitAll(flow { emit(null) })
        } else {
            emitAll(dinerDao.getDiner(id).flowOn(Dispatchers.IO))
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)
    fun getItem(itemId: Flow<Long>): StateFlow<Item?> = itemId.transformLatest { id ->
        if(id == 0L) {
            emitAll(flow { emit(null) })
        } else {
            emitAll(itemDao.getItem(id).flowOn(Dispatchers.IO))
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)
    fun getDebt(debtId: Flow<Long>): StateFlow<Debt?> = debtId.transformLatest { id ->
        if(id == 0L) {
            emitAll(flow { emit(null) })
        } else {
            emitAll(debtDao.getDebt(id).flowOn(Dispatchers.IO))
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)
    fun getDiscount(discountId: Flow<Long>): StateFlow<Discount?> = discountId.transformLatest { id ->
        if(id == 0L) {
            emitAll(flow { emit(null) })
        } else {
            emitAll(discountDao.getDiscount(id).flowOn(Dispatchers.IO))
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)

    // Bill entity ID maps
    val dinerIdMap = diners.mapLatest { dinerArray -> dinerArray.map { Pair(it.id, it) }.toMap() }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyMap())
    val itemIdMap = items.mapLatest { itemArray -> itemArray.map { Pair(it.id, it) }.toMap() }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyMap())
    val debtIdMap = debts.mapLatest { debtArray -> debtArray.map { Pair(it.id, it) }.toMap() }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyMap())
    val discountIdMap = discounts.mapLatest { discountArray -> discountArray.map { Pair(it.id, it) }.toMap() }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyMap())
    val paymentIdMap = payments.mapLatest { paymentArray -> paymentArray.map { Pair(it.id, it) }.toMap() }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyMap())

    // Bill entity counts
    val numberOfDiners = diners.mapLatest { it.size }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 0)
    val numberOfItems = items.mapLatest { it.size }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 0)
    val numberOfDebts = debts.mapLatest { it.size }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 0)
    val numberOfDiscounts = discounts.mapLatest { it.size }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 0)
    val numberOfPayments = payments.mapLatest { it.size }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, 0)

    private val billCalculation: Flow<BillCalculation> = combine(loadedBill, dinerIdMap, itemIdMap, debtIdMap, discountIdMap, paymentIdMap, restaurant) {
        val currentBill = it[0] as Bill
        val billDiners = it[1] as Map<Long, Diner>
        val billItems = it[2] as Map<Long, Item>
        val billDebts = it[3] as Map<Long, Debt>
        val billDiscounts = it[4] as Map<Long, Discount>
        val billPayments = it[5] as Map<Long, Payment>
        val currentRestaurant = it[6] as Diner

        // TODO consistency validation

        BillCalculation(currentBill, billDiners, billItems, billDebts, billDiscounts, billPayments, currentRestaurant)
    }.stateIn(CoroutineScope(Dispatchers.Default),
        SharingStarted.Eagerly,
        BillCalculation(
            bill=Bill(0L, "", 0L, 0.0, true,0.0, tipAsPercent = true, isTaxTipped = false, discountsReduceTip = false),
            restaurant = Diner(0L, 0L, Contact.restaurant)
        )
    )

    // Bill results
    val dinerSubtotals: Flow<Map<Long, Double>> = billCalculation.mapLatest { it.individualSubtotals }
    val groupSubtotal: Flow<Double> = billCalculation.mapLatest { it.groupSubtotal }
    val groupDiscountAmount: Flow<Double> = billCalculation.mapLatest { it.groupDiscountTotal }
    val groupSubtotalWithDiscounts: Flow<Double> = billCalculation.mapLatest { it.groupSubtotalWithDiscounts }
    val taxPercent: Flow<Double> = billCalculation.mapLatest { it.groupTaxPercent }
    val groupTaxAmount: Flow<Double> = billCalculation.mapLatest { it.groupTaxValue }
    val groupSubtotalWithDiscountsAndTax: Flow<Double> = billCalculation.mapLatest { it.groupSubtotalWithDiscountsAndTax }
    val tipPercent: Flow<Double> = billCalculation.mapLatest { it.groupTipPercent }
    val groupTipAmount: Flow<Double> = billCalculation.mapLatest { it.groupTipValue }
    val groupTotal: Flow<Double> = billCalculation.mapLatest { it.groupTotal }


    init {
        combine(userName, userPhotoUri) { userName, userPhotoUri ->
            Contact.updateSelfContactData(userName, userPhotoUri)
            //TODO database.contactDao().save(Contact.self)
        }

        ProcessLifecycleOwner.get().lifecycleScope.launchWhenResumed {
            loadedBillId.collect {
                if (it == 0L) {
                    //TODO check if old bill is expired
                    createAndLoadNewBill()
                }
            }
        }
    }

    private fun <T> getPreferenceFlow(key: Preferences.Key<T>, defaultValue: T, initialValue: T? = null): StateFlow<T> =
        preferenceDataStore.data.mapLatest { preferences ->
            preferences[key] ?: defaultValue
        }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, initialValue ?: defaultValue)
    private fun <T> setPreferenceValue(key: Preferences.Key<T>, newValue: T) =
        CoroutineScope(Dispatchers.IO).launch {
            preferenceDataStore.edit { preferences -> preferences[key] = newValue }
        }

    // Bill Functions
    fun loadBill(billId: Long) = setPreferenceValue(LOADED_BILL_ID_KEY, billId)
    fun createAndLoadNewBill() = CoroutineScope(Dispatchers.IO).launch {
        // TODO assign title and date to new bill
        loadBill(billDao.save(Bill(0L,
                "New Bill",
                0L,
                defaultTaxPercent.value,
                true,
                defaultTipPercent.value,
                true,
                taxIsTipped.value,
                discountsReduceTip.value), addSelf = false))
    }
    fun deleteBill(bill: Bill) = CoroutineScope(Dispatchers.IO).launch { billDao.delete(bill) }

    // Contact Functions
    fun saveContact(contact: Contact) = CoroutineScope(Dispatchers.IO).launch { contactDao.save(contact) }
    fun saveContacts(contacts: List<Contact>) = CoroutineScope(Dispatchers.IO).launch { contactDao.save(contacts) }
    fun removeContact(contact: Contact) = CoroutineScope(Dispatchers.IO).launch { contactDao.delete(contact) }
    fun hasContact(lookupKey: String): Boolean = contactDao.hasLookupKey(lookupKey)
    fun unfavoriteFavoriteContacts() = CoroutineScope(Dispatchers.IO).launch { contactDao.unfavoriteAllFavorites() }
    fun unhideHiddenContacts() = CoroutineScope(Dispatchers.IO).launch { contactDao.unhideAllHidden() }

    // Diner Functions
    fun createDinerForSelf() = CoroutineScope(Dispatchers.IO).launch { billDao.addDinerToBill(Diner(0L, loadedBillId.value, Contact.self)) }
    fun createDinersForContacts(dinerContacts: Collection<Contact>, billId: Long? = null) = CoroutineScope(Dispatchers.IO).launch {
        (billId ?: loadedBillId.value).let { billId ->
            dinerDao.save(dinerContacts.map { Diner(0L, billId, it) }) //TODO pull payment preference from contact
        }
    }
    fun deleteDiner(diner: Diner) = CoroutineScope(Dispatchers.IO).launch { dinerDao.delete(diner) }

    // Item Functions
    fun getItemsForDiner(dinerId: Long) = itemDao.getItemsForDiner(dinerId).flowOn(Dispatchers.IO)
    fun createItem(price: Double, name: String, diners: Collection<Diner>) = CoroutineScope(Dispatchers.IO).launch {
        itemDao.save(Item(0, loadedBillId.value, price, name, diners.map { it.id }, emptyList()))
    }
    fun deleteItem(item: Item) = CoroutineScope(Dispatchers.IO).launch { itemDao.delete(item) }

    // Debt Functions
    suspend fun createDebt(amount: Double, debtors: Collection<Diner> = emptyList(), creditors: Collection<Diner> = emptyList()) = CoroutineScope(Dispatchers.IO).launch {
        debtDao.save(Debt(0, loadedBillId.value, amount, debtors.map { it.id }, creditors.map { it.id }))
    }
    fun deleteDebt(debt: Debt) = CoroutineScope(Dispatchers.IO).launch { debtDao.delete(debt) }
    fun getDebtsOwedByDiner(dinerId: Long) = debtDao.getDebtsOwedByDiner(dinerId).flowOn(Dispatchers.IO)
    fun getDebtsHeldByDiner(dinerId: Long) = debtDao.getDebtsHeldByDiner(dinerId).flowOn(Dispatchers.IO)

    // Discount Functions
    suspend fun createDinerDiscount(discountId: Long, asPercent: Boolean, value: Double, cost: Double?, recipients: List<Long> = emptyList(), purchasers: List<Long> = emptyList()) =
        discountDao.save(Discount(discountId, loadedBillId.value, asPercent, false, value, cost, emptyList(), recipients, purchasers))
    suspend fun createItemDiscount(discountId: Long, asPercent: Boolean, value: Double, cost: Double?, items: List<Long> = emptyList(), purchasers: List<Long> = emptyList()) =
        discountDao.save(Discount(discountId, loadedBillId.value, asPercent, true, value, cost, items, emptyList(), purchasers))
    fun deleteDiscount(discount: Discount) = CoroutineScope(Dispatchers.IO).launch { discountDao.delete(discount) }

    fun getDiscountsReceivedByDiner(dinerId: Long) = discountDao.getDiscountsReceivedByDiner(dinerId).flowOn(Dispatchers.IO)
    fun getDiscountsPurchasedByDiner(dinerId: Long) = discountDao.getDiscountsPurchasedByDiner(dinerId).flowOn(Dispatchers.IO)

    // Payment Functions
    fun getPaymentsSentByDiner(dinerId: Long) = paymentDao.getPaymentsSentByDiner(dinerId).flowOn(Dispatchers.IO)
    fun getPaymentsReceivedByDiner(dinerId: Long) = paymentDao.getPaymentsReceivedByDiner(dinerId).flowOn(Dispatchers.IO)

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
        val LOADED_BILL_ID_KEY = longPreferencesKey("loaded_bill_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_PHOTO_URI_KEY = stringPreferencesKey("user_photo_uri")
        val TAX_PERCENT_KEY = doublePreferencesKey("default_tax_percent")
        val TIP_PERCENT_KEY = doublePreferencesKey("default_tip_percent")
        val TAX_IS_TIPPED_KEY = booleanPreferencesKey("default_tax_is_tipped")
        val DISCOUNTS_REDUCE_TIP_KEY = booleanPreferencesKey("default_discounts_reduce_tip")
    }
}

@Database(entities = [Bill::class, Diner::class, Item::class, Debt::class, Discount::class,
    Payment::class, Contact::class, DinerItemJoin::class, DebtDebtorJoin::class,
    DebtCreditorJoin::class, DiscountRecipientJoin::class, DiscountPurchaserJoin::class,
    DiscountItemJoin::class, PaymentPayerJoin::class, PaymentPayeeJoin::class],
    exportSchema = false, version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
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
}