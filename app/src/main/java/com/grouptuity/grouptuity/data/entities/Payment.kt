package com.grouptuity.grouptuity.data.entities

import androidx.room.*
import com.grouptuity.grouptuity.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal


enum class PaymentMethod(val acceptedByRestaurant: Boolean,
                         val processedWithinApp: Boolean,
                         val addressCodeScannable: Boolean,
                         val addressCanBeEmail: Boolean,
                         val addressCanBePhone: Boolean,
                         val displayNameStringId: Int,
                         val addressNameStringId: Int,
                         val paymentInstructionStringId: Int,
                         val addressSelectionStringId: Int,
                         val paymentIconId: Int,
                         val isIconColorless: Boolean) {
    CASH(
        true,
        false,
        false,
        false,
        false,
        R.string.payment_method_display_name_cash,
        R.string.payment_method_address_name_cash_address,
        R.string.payments_instruction_cash,
        R.string.placeholder_text,
        R.drawable.ic_payment_cash,
        true),
    CREDIT_CARD_SPLIT(
        true,
        false,
        false,
        false,
        false,
        R.string.payment_method_display_name_credit_card_split,
        R.string.payment_method_address_name_credit_card_split,
        R.string.payments_instruction_credit_card_split,
        R.string.placeholder_text,
        R.drawable.ic_payment_credit_card_split,
        true),
    CREDIT_CARD_INDIVIDUAL(
        true,
        false,
        false,
        false,
        false,
        R.string.payment_method_display_name_credit_card_individual,
        R.string.payment_method_address_name_credit_card_individual,
        R.string.payments_instruction_credit_card_individual,
        R.string.placeholder_text,
        R.drawable.ic_payment_credit_card,
        true),
    PAYBACK_LATER(
        false,
        true,
        false,
        true,
        true,
        R.string.payment_method_display_name_payback_later,
        R.string.payment_method_address_name_payback_later,
        R.string.payments_instruction_payback_later,
        R.string.payments_address_entry_iou_email,
        R.drawable.ic_payment_payback_later,
        true),
    VENMO(
        false,
        true,
        true,
        true,
        true,
        R.string.payment_method_display_name_venmo,
        R.string.payment_method_address_name_venmo,
        R.string.payments_instruction_venmo,
        R.string.payments_address_entry_venmo,
        R.drawable.ic_payment_venmo,
        false),
    CASH_APP(
        false,
        true,
        true,
        true,
        true,
        R.string.payment_method_display_name_cash_app,
        R.string.payment_method_address_name_cash_app,
        R.string.payments_instruction_cash_app,
        R.string.payments_address_entry_cash_app,
        R.drawable.ic_payment_cash_app,
        false),
    ALGO(
        false,
        true,
        true,
        false,
        false,
        R.string.payment_method_display_name_algorand,
        R.string.payment_method_address_name_algorand,
        R.string.payments_instruction_algorand,
        R.string.payments_address_entry_algorand,
        R.drawable.ic_payment_algorand,
        true)
}




data class PaymentTemplate(val method: PaymentMethod,
                           val payerId: String,
                           val payeeId: String,
                           val surrogateId: String? = null,
                           val payerAddress: String? = null,
                           val payeeAddress: String? = null,
                           val surrogateAddress: String? = null)


@Entity(tableName = "payment_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["payerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["payeeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["surrogateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId"), Index("payerId"), Index("payeeId"), Index("surrogateId")])
class Payment(@PrimaryKey val id: String,
              val billId: String,
              val amountInput: String,
              val method: PaymentMethod,
              var committed: Boolean,
              val payerId: String,
              val payeeId: String,
              val surrogateId: String?) {

    @Ignore var stableId: Long = 0L

    @Ignore val amount = BigDecimal(amountInput)
    @Ignore val amountFlow: StateFlow<BigDecimal> = MutableStateFlow(amount)

    @Ignore lateinit var payer: Diner
    @Ignore lateinit var payee: Diner
    @Ignore var surrogate: Diner? = null

    val hasNonZeroAmount: Boolean
        get() = amount.compareTo(BigDecimal.ZERO) == 1
    val isUnprocessed: Boolean
        get() = !committed && method.processedWithinApp

    fun attachDiners(dinerMap: Map<String, Diner>) {
        payer = dinerMap[payerId]!!
        payee = dinerMap[payeeId]!!
        surrogate = dinerMap[surrogateId]
    }
}