package com.grouptuity.grouptuity.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bill_table")
data class Bill(@PrimaryKey(autoGenerate = true) val id: Long,
                val title: String,
                val timeCreated: Long,
                val tax: Double,
                val taxAsPercent: Boolean,
                val tip: Double,
                val tipAsPercent: Boolean,
                val isTaxTipped: Boolean?,
                val discountsReduceTip: Boolean?)

