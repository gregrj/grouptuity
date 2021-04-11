package com.grouptuity.grouptuity.data

import androidx.room.*
import kotlinx.coroutines.flow.*


interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(t: T): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(t: List<T>)

    @Delete
    suspend fun delete(t: T)

    @Delete
    suspend fun delete(t: List<T>)
}


@Dao
interface ContactDao: BaseDao<Contact> {
    @Query("SELECT * FROM contact_table")
    fun getSavedContacts(): Flow<List<Contact>>

    @Query("SELECT COUNT(1) FROM contact_table WHERE lookupKey = :lookupKey")
    fun hasLookupKey(lookupKey: String): Boolean

    @Transaction
    suspend fun save(contact: Contact) {
        // no deletion before insert due to foreign key use in diner_table and no join tables needing to be refreshed
        _insert(contact)
    }

    @Transaction
    suspend fun save(contacts: List<Contact>) {
        // no deletion before insert due to foreign key use in diner_table and no join tables needing to be refreshed
        for (contact in contacts) {
            _insert(contact)
        }
    }

    @Query("UPDATE contact_table SET visibility = 1 WHERE visibility = 2")
    suspend fun unfavoriteAllFavorites()

    @Query("UPDATE contact_table SET visibility = 1 WHERE visibility = 3")
    suspend fun unhideAllHidden()
}


@Dao
interface BillDao: BaseDao<Bill> {
    @Query("SELECT * FROM bill_table")
    fun getSavedBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bill_table WHERE id = :id LIMIT 1")
    fun getBill(id: Long): Flow<Bill?>

    @Query("SELECT id FROM diner_table WHERE billId = :billId AND contact_lookupKey='grouptuity_restaurant_contact_lookupKey' LIMIT 1")
    fun getRestaurantId(billId: Long): Flow<Long>

    @Query("SELECT contact_lookupKey FROM diner_table WHERE billId = :billId")
    fun getContactLookupKeys(billId: Long): Flow<List<String>>

    @Transaction
    suspend fun save(bill: Bill, addSelf: Boolean=true): Long {
        val billId = _insert(bill)

        addDinerToBill(Diner(0L, billId, Contact.restaurant))

        if(addSelf) {
            addDinerToBill(Diner(0L, billId, Contact.self))
        }

        return billId
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addDinerToBill(diner: Diner): Long
}


@Dao
interface DinerDao: BaseDao<Diner> {
    @Query("SELECT id FROM diner_table WHERE billId = :billId AND contact_lookupKey!='grouptuity_restaurant_contact_lookupKey'")
    fun _getDinerIdsOnBill(billId: Long): Flow<List<Long>>

    @Query("SELECT * FROM diner_table WHERE id = :dinerId")
    fun _getBaseDiner(dinerId: Long): Flow<Diner?>

    @Query("SELECT id FROM item_table INNER JOIN diner_item_join_table ON item_table.id=diner_item_join_table.itemId WHERE diner_item_join_table.dinerId=:dinerId")
    fun _getItemIdsForDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM debt_table INNER JOIN debt_debtor_join_table ON debt_table.id=debt_debtor_join_table.debtId WHERE debt_debtor_join_table.dinerId=:dinerId")
    fun _getDebtIdsOwedByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM debt_table INNER JOIN debt_creditor_join_table ON debt_table.id=debt_creditor_join_table.debtId WHERE debt_creditor_join_table.dinerId=:dinerId")
    fun _getDebtIdsHeldByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM discount_table INNER JOIN discount_recipient_join_table ON discount_table.id=discount_recipient_join_table.discountId WHERE discount_recipient_join_table.dinerId=:dinerId")
    fun _getDiscountIdsReceivedByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM discount_table INNER JOIN discount_purchaser_join_table ON discount_table.id=discount_purchaser_join_table.discountId WHERE discount_purchaser_join_table.dinerId=:dinerId")
    fun _getDiscountIdsPurchasedByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM payment_table INNER JOIN payment_payer_join_table ON payment_table.id=payment_payer_join_table.paymentId WHERE payment_payer_join_table.dinerId=:dinerId")
    fun _getPaymentIdsSentByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM payment_table INNER JOIN payment_payee_join_table ON payment_table.id=payment_payee_join_table.paymentId WHERE payment_payee_join_table.dinerId=:dinerId")
    fun _getPaymentIdsReceivedByDiner(dinerId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun _addContactForDiner(contact: Contact)

    @Insert
    suspend fun _addItemsForDiner(joins: List<DinerItemJoin>)

    @Insert
    suspend fun _addDebtsOwedByDiner(joins: List<DebtDebtorJoin>)

    @Insert
    suspend fun _addDebtsHeldByDiner(joins: List<DebtCreditorJoin>)

    @Insert
    suspend fun _addDiscountsReceivedByDiner(joins: List<DiscountRecipientJoin>)

    @Insert
    suspend fun _addDiscountsPurchasedByDiner(joins: List<DiscountPurchaserJoin>)

    @Insert
    suspend fun _addPaymentsSentByDiner(joins: List<PaymentPayerJoin>)

    @Insert
    suspend fun _addPaymentsReceivedByDiner(joins: List<PaymentPayeeJoin>)

    @Transaction
    suspend fun save(diner: Diner) {
        delete(diner)
        val dinerId = _insert(diner)
        _addItemsForDiner(diner.items.map { DinerItemJoin(dinerId, it) })
        _addDebtsOwedByDiner(diner.debtsOwed.map { DebtDebtorJoin(it, dinerId) })
        _addDebtsHeldByDiner(diner.debtsHeld.map { DebtCreditorJoin(it, dinerId) })
        _addDiscountsReceivedByDiner(diner.discountsReceived.map { DiscountRecipientJoin(it, dinerId) })
        _addDiscountsPurchasedByDiner(diner.discountsPurchased.map { DiscountPurchaserJoin(it, dinerId) })
        _addPaymentsSentByDiner(diner.paymentsSent.map { PaymentPayerJoin(it, dinerId) })
        _addPaymentsReceivedByDiner(diner.paymentsReceived.map { PaymentPayeeJoin(it, dinerId) })
    }

    @Transaction
    suspend fun save(diners: List<Diner>) {
        for (diner in diners) {
            _addContactForDiner(diner.contact) // TODO incorporate into single diner counterpart save function?
            save(diner)
        }
    }

    fun getDiner(dinerId: Long): Flow<Diner> = _getBaseDiner(dinerId).distinctUntilChanged().combine(
        combine(
            _getItemIdsForDiner(dinerId).distinctUntilChanged(),
            _getDebtIdsOwedByDiner(dinerId).distinctUntilChanged(),
            _getDebtIdsHeldByDiner(dinerId).distinctUntilChanged(),
            _getDiscountIdsReceivedByDiner(dinerId).distinctUntilChanged(),
            _getDiscountIdsPurchasedByDiner(dinerId).distinctUntilChanged(),
            _getPaymentIdsSentByDiner(dinerId).distinctUntilChanged(),
            _getPaymentIdsReceivedByDiner(dinerId).distinctUntilChanged()
        ) { it }) { diner, lists ->
        diner?.withLists(lists[0], lists[1], lists[2], lists[3], lists[4], lists[5], lists[6])
    }.filterNotNull()

    fun getDinersOnBill(billId: Long): Flow<Array<Diner>> = _getDinerIdsOnBill(billId).distinctUntilChanged()
        .transformLatest { dinerIds ->
            if(dinerIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Diner>()) })
            } else {
                emitAll(combine(dinerIds.map { getDiner(it) }){ it })
            }
        }
}


@Dao
interface ItemDao: BaseDao<Item> {
    @Query("SELECT id FROM item_table WHERE billId = :billId")
    fun _getItemIdsOnBill(billId: Long): Flow<List<Long>>

    @Query("SELECT id FROM item_table INNER JOIN diner_item_join_table ON item_table.id=diner_item_join_table.itemId WHERE diner_item_join_table.dinerId=:dinerId")
    fun _getItemIdsForDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT * FROM item_table WHERE id = :itemId")
    fun _getBaseItem(itemId: Long): Flow<Item?>

    @Query("SELECT id FROM diner_table INNER JOIN diner_item_join_table ON diner_table.id=diner_item_join_table.dinerId WHERE diner_item_join_table.itemId=:itemId")
    fun _getDinersForItem(itemId: Long): Flow<List<Long>>

    @Query("SELECT id FROM discount_table INNER JOIN discount_item_join_table ON discount_table.id=discount_item_join_table.discountId WHERE discount_item_join_table.itemId=:itemId")
    fun _getDiscountsForItem(itemId: Long): Flow<List<Long>>

    @Insert
    suspend fun addDinersForItem(joins: List<DinerItemJoin>)

    @Insert
    suspend fun addDiscountsForItem(joins: List<DiscountItemJoin>)

    @Transaction
    suspend fun save(item: Item): Long {
        delete(item)
        val itemId = _insert(item)
        addDinersForItem(item.diners.map { DinerItemJoin(it, itemId) })
        addDiscountsForItem(item.discounts.map { DiscountItemJoin(it, itemId) })
        return itemId
    }

    fun getItem(itemId: Long): Flow<Item> = combine(_getBaseItem(itemId).distinctUntilChanged(),
        _getDinersForItem(itemId).distinctUntilChanged(),
        _getDiscountsForItem(itemId).distinctUntilChanged()) { item, diners, discounts ->
        item?.withLists(diners, discounts)
    }.filterNotNull()

    fun getItemsOnBill(billId: Long): Flow<Array<Item>> = _getItemIdsOnBill(billId).distinctUntilChanged()
        .transformLatest { itemIds ->
            if(itemIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Item>()) })
            } else {
                emitAll(combine(itemIds.map { getItem(it) }){ it })
            }
        }

    fun getItemsForDiner(dinerId: Long): Flow<Array<Item>> = _getItemIdsForDiner(dinerId).distinctUntilChanged()
        .transformLatest { itemIds ->
            if(itemIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Item>()) })
            } else {
                emitAll(combine(itemIds.map { getItem(it) }){ it })
            }
        }
}


@Dao
interface DebtDao: BaseDao<Debt> {
    @Query("SELECT id FROM debt_table WHERE billId = :billId")
    fun _getDebtIdsOnBill(billId: Long): Flow<List<Long>>

    @Query("SELECT id FROM debt_table INNER JOIN debt_debtor_join_table ON debt_table.id=debt_debtor_join_table.debtId WHERE debt_debtor_join_table.dinerId=:dinerId")
    fun _getDebtIdsOwedByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM debt_table INNER JOIN debt_creditor_join_table ON debt_table.id=debt_creditor_join_table.debtId WHERE debt_creditor_join_table.dinerId=:dinerId")
    fun _getDebtIdsHeldByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT * FROM debt_table WHERE id = :debtId")
    fun _getBaseDebt(debtId: Long): Flow<Debt?>

    @Query("SELECT id FROM diner_table INNER JOIN debt_debtor_join_table ON diner_table.id=debt_debtor_join_table.dinerId WHERE debt_debtor_join_table.debtId=:debtId")
    fun _getDebtorsForDebt(debtId: Long): Flow<List<Long>>

    @Query("SELECT id FROM diner_table INNER JOIN debt_creditor_join_table ON diner_table.id=debt_creditor_join_table.dinerId WHERE debt_creditor_join_table.debtId=:debtId")
    fun _getCreditorsForDebt(debtId: Long): Flow<List<Long>>

    @Insert
    suspend fun addDebtorsForDebt(joins: List<DebtDebtorJoin>)

    @Insert
    suspend fun addCreditorsForDebt(joins: List<DebtCreditorJoin>)

    @Transaction
    suspend fun save(debt: Debt) {
        delete(debt)
        val debtId = _insert(debt)
        addDebtorsForDebt(debt.debtors.map { DebtDebtorJoin(debtId, it) })
        addCreditorsForDebt(debt.creditors.map { DebtCreditorJoin(debtId, it) })
    }

    fun getDebt(debtId: Long): Flow<Debt> = combine(_getBaseDebt(debtId).distinctUntilChanged(),
        _getDebtorsForDebt(debtId).distinctUntilChanged(),
        _getCreditorsForDebt(debtId).distinctUntilChanged()) { debt, debtors, creditors ->
        debt?.withLists(debtors, creditors)
    }.filterNotNull()

    fun getDebtsOnBill(billId: Long): Flow<Array<Debt>> = _getDebtIdsOnBill(billId).distinctUntilChanged()
        .transformLatest { debtIds ->
            if(debtIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Debt>()) })
            } else {
                emitAll(combine(debtIds.map { getDebt(it) }){ it })
            }
        }

    fun getDebtsOwedByDiner(dinerId: Long): Flow<Array<Debt>> = _getDebtIdsOwedByDiner(dinerId).distinctUntilChanged()
        .transformLatest { debtIds ->
            if(debtIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Debt>()) })
            } else {
                emitAll(combine(debtIds.map { getDebt(it) }){ it })
            }
        }

    fun getDebtsHeldByDiner(dinerId: Long): Flow<Array<Debt>> = _getDebtIdsHeldByDiner(dinerId).distinctUntilChanged()
        .transformLatest { debtIds ->
            if(debtIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Debt>()) })
            } else {
                emitAll(combine(debtIds.map { getDebt(it) }){ it })
            }
        }
}


@Dao
interface DiscountDao: BaseDao<Discount> {
    @Query("SELECT id FROM discount_table WHERE billId = :billId")
    fun _getDiscountIdsOnBill(billId: Long): Flow<List<Long>>

    @Query("SELECT id FROM discount_table INNER JOIN discount_recipient_join_table ON discount_table.id=discount_recipient_join_table.discountId WHERE discount_recipient_join_table.dinerId=:dinerId")
    fun _getDiscountIdsReceivedByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM discount_table INNER JOIN discount_purchaser_join_table ON discount_table.id=discount_purchaser_join_table.discountId WHERE discount_purchaser_join_table.dinerId=:dinerId")
    fun _getDiscountIdsPurchasedByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT * FROM discount_table WHERE id = :discountId")
    fun _getBaseDiscount(discountId: Long): Flow<Discount?>

    @Query("SELECT id FROM item_table INNER JOIN discount_item_join_table ON item_table.id=discount_item_join_table.itemId WHERE discount_item_join_table.discountId=:discountId")
    fun _getItemsForDiscount(discountId: Long): Flow<List<Long>>

    @Query("SELECT id FROM diner_table INNER JOIN discount_recipient_join_table ON diner_table.id=discount_recipient_join_table.dinerId WHERE discount_recipient_join_table.discountId=:discountId")
    fun _getRecipientsForDiscount(discountId: Long): Flow<List<Long>>

    @Query("SELECT id FROM diner_table INNER JOIN discount_purchaser_join_table ON diner_table.id=discount_purchaser_join_table.dinerId WHERE discount_purchaser_join_table.discountId=:discountId")
    fun _getPurchasersForDiscount(discountId: Long): Flow<List<Long>>

    @Insert
    suspend fun addItemsForDiscount(joins: List<DiscountItemJoin>)

    @Insert
    suspend fun addRecipientsForDiscount(joins: List<DiscountRecipientJoin>)

    @Insert
    suspend fun addPurchasersForDiscount(joins: List<DiscountPurchaserJoin>)

    @Transaction
    suspend fun save(discount: Discount): Long {
        delete(discount)
        val discountId = _insert(discount)
        addItemsForDiscount(discount.items.map { DiscountItemJoin(discountId, it) })
        addRecipientsForDiscount(discount.recipients.map { DiscountRecipientJoin(discountId, it) })
        addPurchasersForDiscount(discount.purchasers.map { DiscountPurchaserJoin(discountId, it) })
        return discountId
    }

    fun getDiscount(discountId: Long): Flow<Discount> = combine(_getBaseDiscount(discountId).distinctUntilChanged(),
        _getItemsForDiscount(discountId).distinctUntilChanged(),
        _getRecipientsForDiscount(discountId).distinctUntilChanged(),
        _getPurchasersForDiscount(discountId).distinctUntilChanged()) { discount, items, recipients, purchasers ->
        discount?.withLists(items, recipients, purchasers)
    }.filterNotNull()

    fun getDiscountsOnBill(billId: Long): Flow<Array<Discount>> = _getDiscountIdsOnBill(billId).distinctUntilChanged()
        .transformLatest { discountIds ->
            if(discountIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Discount>()) })
            } else {
                emitAll(combine(discountIds.map { getDiscount(it) }){ it })
            }
        }

    fun getDiscountsReceivedByDiner(dinerId: Long): Flow<Array<Discount>> = _getDiscountIdsReceivedByDiner(dinerId).distinctUntilChanged()
        .transformLatest { discountIds ->
            if(discountIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Discount>()) })
            } else {
                emitAll(combine(discountIds.map { getDiscount(it) }){ it })
            }
        }

    fun getDiscountsPurchasedByDiner(dinerId: Long): Flow<Array<Discount>> = _getDiscountIdsPurchasedByDiner(dinerId).distinctUntilChanged()
        .transformLatest { discountIds ->
            if(discountIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Discount>()) })
            } else {
                emitAll(combine(discountIds.map { getDiscount(it) }){ it })
            }
        }
}


@Dao
interface PaymentDao: BaseDao<Payment> {
    @Query("SELECT id FROM payment_table WHERE billId = :billId")
    fun _getPaymentIdsOnBill(billId: Long): Flow<List<Long>>

    @Query("SELECT id FROM payment_table INNER JOIN payment_payer_join_table ON payment_table.id=payment_payer_join_table.paymentId WHERE payment_payer_join_table.dinerId=:dinerId")
    fun _getPaymentIdsSentByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT id FROM payment_table INNER JOIN payment_payee_join_table ON payment_table.id=payment_payee_join_table.paymentId WHERE payment_payee_join_table.dinerId=:dinerId")
    fun _getPaymentIdsReceivedByDiner(dinerId: Long): Flow<List<Long>>

    @Query("SELECT * FROM payment_table WHERE id = :paymentId")
    fun _getBasePayment(paymentId: Long): Flow<Payment?>

    @Insert
    suspend fun addPayerForPayment(join: PaymentPayerJoin)

    @Insert
    suspend fun addPayeeForPayment(join: PaymentPayeeJoin)

    @Transaction
    suspend fun save(payment: Payment) {
        delete(payment)
        val paymentId = _insert(payment)
        addPayerForPayment(PaymentPayerJoin(paymentId, payment.payerId))
        addPayeeForPayment(PaymentPayeeJoin(paymentId, payment.payeeId))
    }

    fun getPayment(paymentId: Long): Flow<Payment> = _getBasePayment(paymentId).distinctUntilChanged().filterNotNull()

    fun getPaymentsOnBill(billId: Long): Flow<Array<Payment>> = _getPaymentIdsOnBill(billId).distinctUntilChanged()
        .transformLatest { paymentIds ->
            if(paymentIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Payment>()) })
            } else {
                emitAll(combine(paymentIds.map { getPayment(it) }){ it })
            }
        }

    fun getPaymentsSentByDiner(dinerId: Long): Flow<Array<Payment>> = _getPaymentIdsSentByDiner(dinerId).distinctUntilChanged()
        .transformLatest { paymentIds ->
            if(paymentIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Payment>()) })
            } else {
                emitAll(combine(paymentIds.map { getPayment(it) }){ it })
            }
        }

    fun getPaymentsReceivedByDiner(dinerId: Long): Flow<Array<Payment>> = _getPaymentIdsReceivedByDiner(dinerId).distinctUntilChanged()
        .transformLatest { paymentIds ->
            if(paymentIds.isEmpty()) {
                emitAll(flow { emit(emptyArray<Payment>()) })
            } else {
                emitAll(combine(paymentIds.map { getPayment(it) }){ it })
            }
        }
}


