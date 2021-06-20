package com.grouptuity.grouptuity.ui.billsplit.discounts

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.grouptuity.grouptuity.Event
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.max


class DiscountsViewModel(app: Application): UIViewModel(app) {
    val discounts: LiveData<Array<Discount>> = repository.discounts.asLiveData()
    val dinerIdMap = repository.dinerIdMap.asLiveData()
    val itemIdMap = repository.itemIdMap.asLiveData()

    fun removeDiscount(discount: Discount) = repository.deleteDiscount(discount)
}