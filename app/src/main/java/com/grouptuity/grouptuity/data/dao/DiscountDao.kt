package com.grouptuity.grouptuity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.grouptuity.grouptuity.data.entities.Discount
import com.grouptuity.grouptuity.data.entities.DiscountItemJoin
import com.grouptuity.grouptuity.data.entities.DiscountPurchaserJoin
import com.grouptuity.grouptuity.data.entities.DiscountRecipientJoin


data class DiscountLoadData(val discount: Discount,
                            val itemIds: Collection<String>,
                            val recipientIds: Collection<String>,
                            val purchaserIds: Collection<String>)


@Dao
abstract class DiscountDao: BaseDao<Discount>() {
    @Query("SELECT id FROM discount_table WHERE billId = :billId")
    abstract suspend fun getDiscountIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM discount_table WHERE id = :discountId")
    abstract suspend fun getBaseDiscount(discountId: String): Discount?

    @Query("SELECT id FROM item_table INNER JOIN discount_item_join_table ON item_table.id=discount_item_join_table.itemId WHERE discount_item_join_table.discountId=:discountId")
    abstract suspend fun getItemIdsForDiscount(discountId: String): List<String>

    @Query("SELECT id FROM diner_table INNER JOIN discount_recipient_join_table ON diner_table.id=discount_recipient_join_table.dinerId WHERE discount_recipient_join_table.discountId=:discountId")
    abstract suspend fun getRecipientIdsForDiscount(discountId: String): List<String>

    @Query("SELECT id FROM diner_table INNER JOIN discount_purchaser_join_table ON diner_table.id=discount_purchaser_join_table.dinerId WHERE discount_purchaser_join_table.discountId=:discountId")
    abstract suspend fun getPurchaserIdsForDiscount(discountId: String): List<String>

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
        addItemsForDiscount(discount.items.value.map { DiscountItemJoin(discount.id, it.id) })
        addRecipientsForDiscount(discount.recipients.value.map { DiscountRecipientJoin(discount.id, it.id) })
        addPurchasersForDiscount(discount.purchasers.value.map { DiscountPurchaserJoin(discount.id, it.id) })
    }

    @Transaction
    open suspend fun getDiscountLoadDataForBill(billId: String): Map<String, DiscountLoadData> =
        getDiscountIdsOnBill(billId).mapNotNull { discountId ->
            getBaseDiscount(discountId)?.let { discount ->
                DiscountLoadData(
                    discount,
                    getItemIdsForDiscount(discountId),
                    getRecipientIdsForDiscount(discountId),
                    getPurchaserIdsForDiscount(discountId),
                )
            }
        }.associateBy { it.discount.id }
}