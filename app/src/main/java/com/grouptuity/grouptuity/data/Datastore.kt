package com.grouptuity.grouptuity.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


val Context.preferenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "grouptuity_preferences")


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
    val taxPercent: StateFlow<Double> = getPreferenceFlow(TAX_PERCENT_KEY, 7.25)
    val tipPercent: StateFlow<Double> = getPreferenceFlow(TIP_PERCENT_KEY, 15.0)
    val taxIsTipped: StateFlow<Boolean> = getPreferenceFlow(TAX_IS_TIPPED_KEY, false)
    val discountsReduceTip: StateFlow<Boolean> = getPreferenceFlow(DISCOUNTS_REDUCE_TIP_KEY, false)

    // App-level Data
    val bills = billDao.getSavedBills()
    val appContacts = contactDao.getSavedContacts()

    // Bill entity lists
    val loadedBill = loadedBillId.transformLatest{ emitAll(billDao.getBill(it)) }.flowOn(Dispatchers.IO).filterNotNull()
    val diners = loadedBill.transformLatest{ emitAll(dinerDao.getDinersOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val items = loadedBill.transformLatest{ emitAll(itemDao.getItemsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val debts = loadedBill.transformLatest{ emitAll(debtDao.getDebtsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val discounts = loadedBill.transformLatest{ emitAll(discountDao.getDiscountsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val payments = loadedBill.transformLatest{ emitAll(paymentDao.getPaymentsOnBill(it.id)) }.flowOn(Dispatchers.IO)
    val restaurant = loadedBill.transformLatest{ emitAll(billDao.getRestaurantId(it.id)) }.transformLatest{ emitAll(dinerDao.getDiner(it)) }.flowOn(Dispatchers.IO)

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

    // Subtotals
    val dinerSubtotals: Flow<Map<Long, Double>> = combine(diners, itemIdMap) { diners, itemMap -> diners.associate { diner -> diner.id to getDinerSubtotal(diner, itemMap) } }

    private fun <T> getPreferenceFlow(key: Preferences.Key<T>, defaultValue: T, initialValue: T? = null): StateFlow<T> =
        preferenceDataStore.data.mapLatest { preferences ->
            preferences[key] ?: defaultValue
        }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, initialValue ?: defaultValue)
    private fun <T> setPreferenceValue(key: Preferences.Key<T>, newValue: T) =
        CoroutineScope(Dispatchers.IO).launch {
            preferenceDataStore.edit { preferences -> preferences[key] = newValue }
        }

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