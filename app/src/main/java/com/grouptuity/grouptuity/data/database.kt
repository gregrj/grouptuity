package com.grouptuity.grouptuity.data

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.grouptuity.grouptuity.data.dao.*
import com.grouptuity.grouptuity.data.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Type


class Converters {
    companion object {
        val aliasMapType: Type = object : TypeToken<MutableMap<PaymentMethod, String>>() {}.type
        val paymentTemplateMapType: Type = object : TypeToken<MutableMap<String, PaymentTemplate>>() {}.type
    }

    @TypeConverter
    fun aliasMapToJSON(aliasMap: MutableMap<PaymentMethod, String>) = Gson().toJson(aliasMap)

    @TypeConverter
    fun jsonToAddressMap(json: String): MutableMap<PaymentMethod, String> = Gson().fromJson(json, aliasMapType)

    @TypeConverter
    fun paymentTemplateMapToJSON(template: MutableMap<String, PaymentTemplate>) = Gson().toJson(template)

    @TypeConverter
    fun jsonToPaymentTemplate(json: String): MutableMap<String, PaymentTemplate> = Gson().fromJson(json, paymentTemplateMapType)
}


@Database(entities = [Bill::class, Diner::class, Item::class, Debt::class, Discount::class,
    Payment::class, Contact::class, DinerItemJoin::class, DebtDebtorJoin::class,
    DebtCreditorJoin::class, DiscountRecipientJoin::class, DiscountPurchaserJoin::class,
    DiscountItemJoin::class, PaymentPayerJoin::class, PaymentPayeeJoin::class], exportSchema = false, version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun billDao(): BillDao
    abstract fun contactDao(): ContactDao
    abstract fun dinerDao(): DinerDao
    abstract fun itemDao(): ItemDao
    abstract fun discountDao(): DiscountDao
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            val newDatabase = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "grouptuity.db"
            ).build()
            insertInitialData(newDatabase)
            return newDatabase
        }

        private fun insertInitialData(newDatabase: AppDatabase) = runBlocking {
            newDatabase.contactDao().insert(Contact.cashPool)
            newDatabase.contactDao().insert(Contact.restaurant)
            newDatabase.contactDao().insert(Contact.user)
        }
    }

    fun getSavedBills() = billDao().getSavedBills()

    fun saveContact(contact: Contact) = CoroutineScope(Dispatchers.IO).launch { contactDao().upsert(contact) }
    fun saveContacts(contacts: List<Contact>) = CoroutineScope(Dispatchers.IO).launch { contactDao().upsert(contacts) }
    fun removeContact(contact: Contact) = CoroutineScope(Dispatchers.IO).launch { contactDao().delete(contact) }

    fun resetContactVisibilities(lookupKeys: Collection<String>) = CoroutineScope(Dispatchers.IO).launch { contactDao().resetVisibility(lookupKeys) }
    fun favoriteContacts(lookupKeys: Collection<String>) = CoroutineScope(Dispatchers.IO).launch { contactDao().favorite(lookupKeys) }
    fun unfavoriteFavoriteContacts() = CoroutineScope(Dispatchers.IO).launch { contactDao().unfavoriteAllFavorites() }
    fun hideContacts(lookupKeys: Collection<String>) = CoroutineScope(Dispatchers.IO).launch { contactDao().hide(lookupKeys) }
    fun unhideHiddenContacts() = CoroutineScope(Dispatchers.IO).launch { contactDao().unhideAllHidden() }

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

    suspend fun loadBill(context: Context, billId: String): Bill? =
        billDao().getBill(billId)?.also { bill ->
            bill.loadEntities(
                dinerDao().getCashPoolLoadDataForBill(billId) ?: return null,
                dinerDao().getRestaurantLoadDataForBill(billId) ?: return null,
                dinerDao().getDinerLoadDataForBill(billId).onEach { (_, loadData) ->
                    AddressBook.getContactData(context, loadData.diner.lookupKey)?.apply {
                        loadData.diner.applyContactInfo(this)
                    }
                },
                itemDao().getItemLoadDataForBill(billId),
                discountDao().getDiscountLoadDataForBill(billId),
                debtDao().getDebtLoadDataForBill(billId),
                paymentDao().getPaymentLoadDataForBill(billId)
            )
        }

    suspend fun loadSavedContacts() = contactDao().getSavedContacts()
}