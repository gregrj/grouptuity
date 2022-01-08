package com.grouptuity.grouptuity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.grouptuity.grouptuity.data.entities.DebtCreditorJoin
import com.grouptuity.grouptuity.data.entities.DebtDebtorJoin
import com.grouptuity.grouptuity.data.entities.Diner
import com.grouptuity.grouptuity.data.entities.DinerItemJoin
import com.grouptuity.grouptuity.data.entities.DiscountPurchaserJoin
import com.grouptuity.grouptuity.data.entities.DiscountRecipientJoin
import com.grouptuity.grouptuity.data.entities.PaymentPayerJoin
import com.grouptuity.grouptuity.data.entities.PaymentPayeeJoin


data class DinerLoadData(val diner: Diner)


@Dao
abstract class DinerDao: BaseDao<Diner>() {
    @Query("SELECT id FROM diner_table WHERE billId = :billId AND lookupKey!='grouptuity_cash_pool_contact_lookupKey' AND lookupKey!='grouptuity_restaurant_contact_lookupKey'")
    abstract suspend fun getDinerIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM diner_table WHERE id = :dinerId")
    abstract suspend fun getBaseDiner(dinerId: String): Diner?

    @Query("SELECT * FROM diner_table WHERE billId = :billId AND lookupKey='grouptuity_cash_pool_contact_lookupKey' LIMIT 1")
    abstract suspend fun getBaseCashPool(billId: String): Diner?

    @Query("SELECT * FROM diner_table WHERE billId = :billId AND lookupKey='grouptuity_restaurant_contact_lookupKey' LIMIT 1")
    abstract suspend fun getBaseRestaurant(billId: String): Diner?

    @Insert
    abstract suspend fun addItemsForDiner(joins: List<DinerItemJoin>)

    @Insert
    abstract suspend fun addDebtsOwedByDiner(joins: List<DebtDebtorJoin>)

    @Insert
    abstract suspend fun addDebtsHeldByDiner(joins: List<DebtCreditorJoin>)

    @Insert
    abstract suspend fun addDiscountsReceivedByDiner(joins: List<DiscountRecipientJoin>)

    @Insert
    abstract suspend fun addDiscountsPurchasedByDiner(joins: List<DiscountPurchaserJoin>)

    @Insert
    abstract suspend fun addPaymentsSentByDiner(joins: List<PaymentPayerJoin>)

    @Insert
    abstract suspend fun addPaymentsReceivedByDiner(joins: List<PaymentPayeeJoin>)

    @Transaction
    open suspend fun save(diner: Diner) {
        delete(diner)
        insert(diner)
        addItemsForDiner(diner.items.value.map { DinerItemJoin(diner.id, it.id) })
        addDiscountsReceivedByDiner(diner.dinerDiscountsReceived.value.map { DiscountRecipientJoin(it.id, diner.id) })
        addDiscountsPurchasedByDiner(diner.discountsPurchased.value.map { DiscountPurchaserJoin(it.id, diner.id) })
        addDebtsOwedByDiner(diner.debtsOwed.value.map { DebtDebtorJoin(it.id, diner.id) })
        addDebtsHeldByDiner(diner.debtsHeld.value.map { DebtCreditorJoin(it.id, diner.id) })
        addPaymentsSentByDiner(diner.paymentsSent.value.map { PaymentPayerJoin(it.id, diner.id) })
        addPaymentsReceivedByDiner(diner.paymentsReceived.value.map { PaymentPayeeJoin(it.id, diner.id) })
    }

    @Transaction
    open suspend fun save(diners: Collection<Diner>) { diners.forEach { save(it) } }

    @Transaction
    open suspend fun getCashPoolLoadDataForBill(billId: String): DinerLoadData? =
        getBaseCashPool(billId)?.let { DinerLoadData(it) }

    @Transaction
    open suspend fun getRestaurantLoadDataForBill(billId: String): DinerLoadData? =
        getBaseRestaurant(billId)?.let { DinerLoadData(it) }

    @Transaction
    open suspend fun getDinerLoadDataForBill(billId: String): Map<String, DinerLoadData> =
        getDinerIdsOnBill(billId).mapNotNull { dinerId ->
            getBaseDiner(dinerId)?.let { diner ->
                DinerLoadData(diner)
            }
        }.associateBy { it.diner.id }
}