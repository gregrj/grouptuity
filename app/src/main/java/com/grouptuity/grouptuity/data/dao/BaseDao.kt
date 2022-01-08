package com.grouptuity.grouptuity.data.dao

import androidx.room.*


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