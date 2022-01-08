package com.grouptuity.grouptuity.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.grouptuity.grouptuity.data.entities.Bill
import kotlinx.coroutines.flow.Flow


@Dao
abstract class BillDao: BaseDao<Bill>() {
    @Query("SELECT * FROM bill_table")
    abstract fun getSavedBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bill_table WHERE id = :id LIMIT 1")
    abstract suspend fun getBill(id: String): Bill?
}