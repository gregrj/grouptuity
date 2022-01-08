package com.grouptuity.grouptuity.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index


@Entity(tableName = "diner_item_join_table",
    primaryKeys = ["dinerId", "itemId"],
    foreignKeys = [ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class,  parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")])
data class DinerItemJoin(val dinerId: String, val itemId: String)


@Entity(tableName = "debt_debtor_join_table",
    primaryKeys = ["debtId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Debt::class,  parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DebtDebtorJoin(val debtId: String, val dinerId: String)


@Entity(tableName = "debt_creditor_join_table",
    primaryKeys = ["debtId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Debt::class,  parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DebtCreditorJoin(val debtId: String, val dinerId: String)


@Entity(tableName = "discount_recipient_join_table",
    primaryKeys = ["discountId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DiscountRecipientJoin(val discountId: String, val dinerId: String)


@Entity(tableName = "discount_purchaser_join_table",
    primaryKeys = ["discountId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DiscountPurchaserJoin(val discountId: String, val dinerId: String)


@Entity(tableName = "discount_item_join_table",
    primaryKeys = ["discountId", "itemId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")])
data class DiscountItemJoin(val discountId: String, val itemId: String)


@Entity(tableName = "payment_payer_join_table",
    primaryKeys = ["paymentId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Payment::class,  parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class PaymentPayerJoin(val paymentId: String, val dinerId: String)


@Entity(tableName = "payment_payee_join_table",
    primaryKeys = ["paymentId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Payment::class,  parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class PaymentPayeeJoin(val paymentId: String, val dinerId: String)