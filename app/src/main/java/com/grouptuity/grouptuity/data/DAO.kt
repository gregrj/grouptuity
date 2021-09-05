package com.grouptuity.grouptuity.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

abstract class BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(item: T): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(item: Collection<T>): List<Long>

    @Update
    abstract suspend fun update(item: T)

    @Update
    abstract suspend fun update(item: Collection<T>)

    @Transaction
    open suspend fun upsert(item: T) {
        if (insert(item) == -1L) {
            update(item)
        }
    }

    @Transaction
    open suspend fun upsert(items: List<T>) {
        val newItems = insert(items).mapIndexedNotNull { index, result ->
            if (result == -1L) {
                items[index]
            } else {
                null
            }
        }

        if (newItems.isNotEmpty()) {
            update(newItems)
        }
    }

    @Delete
    abstract suspend fun delete(t: T)

    @Delete
    abstract suspend fun delete(t: Collection<T>)
}


@Dao
abstract class ContactDao: BaseDao<Contact>() {
    @Query("SELECT contact_lookupKey FROM diner_table WHERE billId = :billId")
    abstract fun getContactLookupKeysOnBill(billId: Long): Flow<List<String>>

    @Query("SELECT * FROM contact_table WHERE lookupKey!='grouptuity_cash_pool_contact_lookupKey' AND lookupKey!='grouptuity_restaurant_contact_lookupKey'")
    abstract suspend fun getSavedContacts(): List<Contact>

    @Query("SELECT COUNT(1) FROM contact_table WHERE lookupKey = :lookupKey")
    abstract suspend fun hasLookupKey(lookupKey: String): Boolean

    @Query("UPDATE contact_table SET visibility = 0 WHERE lookupKey = :lookupKey")
    abstract suspend fun resetVisibility(lookupKey: String)

    @Transaction
    open suspend fun resetVisibility(lookupKeys: Collection<String>) {
        lookupKeys.forEach { resetVisibility(it) }
    }

    @Query("UPDATE contact_table SET visibility = 1 WHERE lookupKey = :lookupKey")
    abstract suspend fun favorite(lookupKey: String)

    @Transaction
    open suspend fun favorite(lookupKeys: Collection<String>) { lookupKeys.forEach { favorite(it) } }

    @Query("UPDATE contact_table SET visibility = 0 WHERE visibility = 1")
    abstract suspend fun unfavoriteAllFavorites()

    @Query("UPDATE contact_table SET visibility = 2 WHERE lookupKey = :lookupKey")
    abstract suspend fun hide(lookupKey: String)

    @Transaction
    open suspend fun hide(lookupKeys: Collection<String>) { lookupKeys.forEach { hide(it) } }

    @Query("UPDATE contact_table SET visibility = 0 WHERE visibility = 2")
    abstract suspend fun unhideAllHidden()
}


@Dao
abstract class BillDao: BaseDao<Bill>() {
    @Query("SELECT * FROM bill_table")
    abstract fun getSavedBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bill_table WHERE id = :id LIMIT 1")
    abstract suspend fun getBill(id: String): Bill?
}


@Dao
abstract class DinerDao: BaseDao<Diner>() {
    @Query("SELECT id FROM diner_table WHERE billId = :billId AND contact_lookupKey!='grouptuity_cash_pool_contact_lookupKey' AND contact_lookupKey!='grouptuity_restaurant_contact_lookupKey'")
    abstract suspend fun _getDinerIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM diner_table WHERE id = :dinerId")
    abstract suspend fun _getBaseDiner(dinerId: String): Diner?

    @Query("SELECT * FROM diner_table WHERE billId = :billId AND contact_lookupKey='grouptuity_cash_pool_contact_lookupKey' LIMIT 1")
    abstract suspend fun _getBaseCashPool(billId: String): Diner?

    @Query("SELECT * FROM diner_table WHERE billId = :billId AND contact_lookupKey='grouptuity_restaurant_contact_lookupKey' LIMIT 1")
    abstract suspend fun _getBaseRestaurant(billId: String): Diner?

    @Query("SELECT id FROM item_table INNER JOIN diner_item_join_table ON item_table.id=diner_item_join_table.itemId WHERE diner_item_join_table.dinerId=:dinerId")
    abstract suspend fun _getItemIdsForDiner(dinerId: String): MutableList<String>

    @Query("SELECT id FROM debt_table INNER JOIN debt_debtor_join_table ON debt_table.id=debt_debtor_join_table.debtId WHERE debt_debtor_join_table.dinerId=:dinerId")
    abstract suspend fun _getDebtIdsOwedByDiner(dinerId: String): MutableList<String>

    @Query("SELECT id FROM debt_table INNER JOIN debt_creditor_join_table ON debt_table.id=debt_creditor_join_table.debtId WHERE debt_creditor_join_table.dinerId=:dinerId")
    abstract suspend fun _getDebtIdsHeldByDiner(dinerId: String): MutableList<String>

    @Query("SELECT id FROM discount_table INNER JOIN discount_recipient_join_table ON discount_table.id=discount_recipient_join_table.discountId WHERE discount_recipient_join_table.dinerId=:dinerId")
    abstract suspend fun _getDiscountIdsReceivedByDiner(dinerId: String): MutableList<String>

    @Query("SELECT id FROM discount_table INNER JOIN discount_purchaser_join_table ON discount_table.id=discount_purchaser_join_table.discountId WHERE discount_purchaser_join_table.dinerId=:dinerId")
    abstract suspend fun _getDiscountIdsPurchasedByDiner(dinerId: String): MutableList<String>

    @Query("SELECT id FROM payment_table INNER JOIN payment_payer_join_table ON payment_table.id=payment_payer_join_table.paymentId WHERE payment_payer_join_table.dinerId=:dinerId")
    abstract suspend fun _getPaymentIdsSentByDiner(dinerId: String): MutableList<String>

    @Query("SELECT id FROM payment_table INNER JOIN payment_payee_join_table ON payment_table.id=payment_payee_join_table.paymentId WHERE payment_payee_join_table.dinerId=:dinerId")
    abstract suspend fun _getPaymentIdsReceivedByDiner(dinerId: String): MutableList<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun _addContactForDiner(contact: Contact)

    @Insert
    abstract suspend fun _addItemsForDiner(joins: List<DinerItemJoin>)

    @Insert
    abstract suspend fun _addDebtsOwedByDiner(joins: List<DebtDebtorJoin>)

    @Insert
    abstract suspend fun _addDebtsHeldByDiner(joins: List<DebtCreditorJoin>)

    @Insert
    abstract suspend fun _addDiscountsReceivedByDiner(joins: List<DiscountRecipientJoin>)

    @Insert
    abstract suspend fun _addDiscountsPurchasedByDiner(joins: List<DiscountPurchaserJoin>)

    @Insert
    abstract suspend fun _addPaymentsSentByDiner(joins: List<PaymentPayerJoin>)

    @Insert
    abstract suspend fun _addPaymentsReceivedByDiner(joins: List<PaymentPayeeJoin>)

    @Transaction
    open suspend fun save(diner: Diner) {
        delete(diner)
        insert(diner)
        _addContactForDiner(diner.contact)
        _addItemsForDiner(diner.itemIds.map { DinerItemJoin(diner.id, it) })
        _addDebtsOwedByDiner(diner.debtOwedIds.map { DebtDebtorJoin(it, diner.id) })
        _addDebtsHeldByDiner(diner.debtHeldIds.map { DebtCreditorJoin(it, diner.id) })
        _addDiscountsReceivedByDiner(diner.discountReceivedIds.map { DiscountRecipientJoin(it, diner.id) })
        _addDiscountsPurchasedByDiner(diner.discountPurchasedIds.map { DiscountPurchaserJoin(it, diner.id) })
        _addPaymentsSentByDiner(diner.paymentSentIds.map { PaymentPayerJoin(it, diner.id) })
        _addPaymentsReceivedByDiner(diner.paymentReceivedIds.map { PaymentPayeeJoin(it, diner.id) })
    }

    @Transaction
    open suspend fun save(diners: Collection<Diner>) { diners.forEach { save(it) } }

    @Transaction
    open suspend fun getDinersOnBill(billId: String): List<Diner> = _getDinerIdsOnBill(billId).mapNotNull { dinerId ->
        _getBaseDiner(dinerId).let {
            it?.withIdLists(
                _getItemIdsForDiner(dinerId),
                _getDebtIdsOwedByDiner(dinerId),
                _getDebtIdsHeldByDiner(dinerId),
                _getDiscountIdsReceivedByDiner(dinerId),
                _getDiscountIdsPurchasedByDiner(dinerId),
                _getPaymentIdsSentByDiner(dinerId),
                _getPaymentIdsReceivedByDiner(dinerId)
            )
        }
    }

    @Transaction
    open suspend fun getCashPoolOnBill(billId: String): Diner? = _getBaseCashPool(billId)?.let{
        it.withIdLists(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            _getPaymentIdsSentByDiner(it.id),
            _getPaymentIdsReceivedByDiner(it.id)
        )
    }

    @Transaction
    open suspend fun getRestaurantOnBill(billId: String): Diner? = _getBaseRestaurant(billId)?.let{
        it.withIdLists(
            _getItemIdsForDiner(it.id),
            _getDebtIdsOwedByDiner(it.id),
            _getDebtIdsHeldByDiner(it.id),
            _getDiscountIdsReceivedByDiner(it.id),
            _getDiscountIdsPurchasedByDiner(it.id),
            _getPaymentIdsSentByDiner(it.id),
            _getPaymentIdsReceivedByDiner(it.id)
        )
    }
}


@Dao
abstract class ItemDao: BaseDao<Item>() {
    @Query("SELECT id FROM item_table WHERE billId = :billId")
    abstract suspend fun _getItemIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM item_table WHERE id = :itemId")
    abstract suspend fun _getBaseItem(itemId: String): Item?

    @Query("SELECT id FROM diner_table INNER JOIN diner_item_join_table ON diner_table.id=diner_item_join_table.dinerId WHERE diner_item_join_table.itemId=:itemId")
    abstract suspend fun _getDinersForItem(itemId: String): List<String>

    @Query("SELECT id FROM discount_table INNER JOIN discount_item_join_table ON discount_table.id=discount_item_join_table.discountId WHERE discount_item_join_table.itemId=:itemId")
    abstract suspend fun _getDiscountsForItem(itemId: String): List<String>

    @Insert
    abstract suspend fun addDinersForItem(joins: List<DinerItemJoin>)

    @Insert
    abstract suspend fun addDiscountsForItem(joins: List<DiscountItemJoin>)

    @Transaction
    open suspend fun save(item: Item) {
        delete(item)
        insert(item)
        addDinersForItem(item.dinerIds.map { DinerItemJoin(it, item.id) })
        addDiscountsForItem(item.discountIds.map { DiscountItemJoin(it, item.id) })
    }

    @Transaction
    open suspend fun save(items: Collection<Item>) { items.forEach { save(it) } }

    @Transaction
    open suspend fun getItemsOnBill(billId: String): List<Item> = _getItemIdsOnBill(billId).mapNotNull { itemId ->
        _getBaseItem(itemId)?.withIdLists(_getDinersForItem(itemId), _getDiscountsForItem(itemId))
    }
}


@Dao
abstract class DebtDao: BaseDao<Debt>() {
    @Query("SELECT id FROM debt_table WHERE billId = :billId")
    abstract suspend fun _getDebtIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM debt_table WHERE id = :debtId")
    abstract suspend fun _getBaseDebt(debtId: String): Debt?

    @Query("SELECT id FROM diner_table INNER JOIN debt_debtor_join_table ON diner_table.id=debt_debtor_join_table.dinerId WHERE debt_debtor_join_table.debtId=:debtId")
    abstract suspend fun _getDebtorsForDebt(debtId: String): List<String>

    @Query("SELECT id FROM diner_table INNER JOIN debt_creditor_join_table ON diner_table.id=debt_creditor_join_table.dinerId WHERE debt_creditor_join_table.debtId=:debtId")
    abstract suspend fun _getCreditorsForDebt(debtId: String): List<String>

    @Insert
    abstract suspend fun addDebtorsForDebt(joins: List<DebtDebtorJoin>)

    @Insert
    abstract suspend fun addCreditorsForDebt(joins: List<DebtCreditorJoin>)

    @Transaction
    open suspend fun save(debt: Debt) {
        delete(debt)
        insert(debt)
        addDebtorsForDebt(debt.debtorIds.map { DebtDebtorJoin(debt.id, it) })
        addCreditorsForDebt(debt.creditorIds.map { DebtCreditorJoin(debt.id, it) })
    }

    @Transaction
    open suspend fun getDebtsOnBill(billId: String): List<Debt> = _getDebtIdsOnBill(billId).mapNotNull { debtId ->
        _getBaseDebt(debtId)?.withIdLists(_getDebtorsForDebt(debtId), _getCreditorsForDebt(debtId))
    }
}


@Dao
abstract class DiscountDao: BaseDao<Discount>() {
    @Query("SELECT id FROM discount_table WHERE billId = :billId")
    abstract suspend fun _getDiscountIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM discount_table WHERE id = :discountId")
    abstract suspend fun _getBaseDiscount(discountId: String): Discount?

    @Query("SELECT id FROM item_table INNER JOIN discount_item_join_table ON item_table.id=discount_item_join_table.itemId WHERE discount_item_join_table.discountId=:discountId")
    abstract suspend fun _getItemsForDiscount(discountId: String): List<String>

    @Query("SELECT id FROM diner_table INNER JOIN discount_recipient_join_table ON diner_table.id=discount_recipient_join_table.dinerId WHERE discount_recipient_join_table.discountId=:discountId")
    abstract suspend fun _getRecipientsForDiscount(discountId: String): List<String>

    @Query("SELECT id FROM diner_table INNER JOIN discount_purchaser_join_table ON diner_table.id=discount_purchaser_join_table.dinerId WHERE discount_purchaser_join_table.discountId=:discountId")
    abstract suspend fun _getPurchasersForDiscount(discountId: String): List<String>

    @Insert
    abstract suspend fun addItemsForDiscount(joins: List<DiscountItemJoin>)

    @Insert
    abstract suspend fun addRecipientsForDiscount(joins: List<DiscountRecipientJoin>)

    @Insert
    abstract suspend fun addPurchasersForDiscount(joins: List<DiscountPurchaserJoin>)

    @Transaction
    open suspend fun save(discount: Discount) {
        delete(discount)
        insert(discount)
        addItemsForDiscount(discount.itemIds.map { DiscountItemJoin(discount.id, it) })
        addRecipientsForDiscount(discount.recipientIds.map { DiscountRecipientJoin(discount.id, it) })
        addPurchasersForDiscount(discount.purchaserIds.map { DiscountPurchaserJoin(discount.id, it) })
    }

    @Transaction
    open suspend fun getDiscountsOnBill(billId: String): List<Discount> =
        _getDiscountIdsOnBill(billId).mapNotNull { discountId ->
            _getBaseDiscount(discountId)?.withIdLists(
                _getItemsForDiscount(discountId),
                _getRecipientsForDiscount(discountId),
                _getPurchasersForDiscount(discountId))
        }
}


@Dao
abstract class PaymentDao: BaseDao<Payment>() {
    @Query("SELECT id FROM payment_table WHERE billId = :billId")
    abstract suspend fun _getPaymentIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM payment_table WHERE id = :paymentId")
    abstract suspend fun _getBasePayment(paymentId: String): Payment?

    @Insert
    abstract suspend fun addPayerForPayment(join: PaymentPayerJoin)

    @Insert
    abstract suspend fun addPayeeForPayment(join: PaymentPayeeJoin)

    @Transaction
    open suspend fun save(payment: Payment) {
        delete(payment)
        insert(payment)
        addPayerForPayment(PaymentPayerJoin(payment.id, payment.payerId))
        addPayeeForPayment(PaymentPayeeJoin(payment.id, payment.payeeId))
    }

    @Transaction
    open suspend fun getPaymentsOnBill(billId: String): List<Payment> =
        _getPaymentIdsOnBill(billId).mapNotNull { paymentId ->
            _getBasePayment(paymentId)
        }
}