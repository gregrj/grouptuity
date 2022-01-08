package com.grouptuity.grouptuity.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.grouptuity.grouptuity.data.entities.Contact
import kotlinx.coroutines.flow.Flow


@Dao
abstract class ContactDao: BaseDao<Contact>() {
    @Query("SELECT lookupKey FROM diner_table WHERE billId = :billId")
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