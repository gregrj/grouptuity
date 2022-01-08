package com.grouptuity.grouptuity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.grouptuity.grouptuity.data.entities.Payment
import com.grouptuity.grouptuity.data.entities.PaymentPayeeJoin
import com.grouptuity.grouptuity.data.entities.PaymentPayerJoin


data class PaymentLoadData(val payment: Payment)


@Dao
abstract class PaymentDao: BaseDao<Payment>() {
    @Query("SELECT id FROM payment_table WHERE billId = :billId")
    abstract suspend fun getPaymentIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM payment_table WHERE id = :paymentId")
    abstract suspend fun getBasePayment(paymentId: String): Payment?

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
    open suspend fun getPaymentLoadDataForBill(billId: String): Map<String, PaymentLoadData> =
        getPaymentIdsOnBill(billId).mapNotNull { paymentId ->
            getBasePayment(paymentId)?.let { payment ->
                PaymentLoadData(payment)
            }
        }.associateBy { it.payment.id }
}