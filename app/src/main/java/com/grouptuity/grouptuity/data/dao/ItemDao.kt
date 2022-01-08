package com.grouptuity.grouptuity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.grouptuity.grouptuity.data.entities.DinerItemJoin
import com.grouptuity.grouptuity.data.entities.DiscountItemJoin
import com.grouptuity.grouptuity.data.entities.Item


data class ItemLoadData(val item: Item, val dinerIds: Collection<String>)


@Dao
abstract class ItemDao: BaseDao<Item>() {
    @Query("SELECT id FROM item_table WHERE billId = :billId")
    abstract suspend fun getItemIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM item_table WHERE id = :itemId")
    abstract suspend fun getBaseItem(itemId: String): Item?

    @Query("SELECT id FROM diner_table INNER JOIN diner_item_join_table ON diner_table.id=diner_item_join_table.dinerId WHERE diner_item_join_table.itemId=:itemId")
    abstract suspend fun getDinerIdsForItem(itemId: String): List<String>

    @Insert
    abstract suspend fun addDinersForItem(joins: List<DinerItemJoin>)

    @Insert
    abstract suspend fun addDiscountsForItem(joins: List<DiscountItemJoin>)

    @Transaction
    open suspend fun save(item: Item) {
        delete(item)
        insert(item)
        addDinersForItem(item.diners.value.map { DinerItemJoin(it.id, item.id) })
        addDiscountsForItem(item.discounts.value.map { DiscountItemJoin(it.id, item.id) })
    }

    @Transaction
    open suspend fun save(items: Collection<Item>) { items.forEach { save(it) } }

    @Transaction
    open suspend fun getItemLoadDataForBill(billId: String): Map<String, ItemLoadData> =
        getItemIdsOnBill(billId).mapNotNull { itemId ->
            getBaseItem(itemId)?.let { item ->
                ItemLoadData(item, getDinerIdsForItem(itemId))
            }
        }.associateBy { it.item.id }
}