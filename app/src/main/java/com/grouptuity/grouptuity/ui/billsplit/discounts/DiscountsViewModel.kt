package com.grouptuity.grouptuity.ui.billsplit.discounts

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.Discount
import com.grouptuity.grouptuity.data.UIViewModel


class DiscountsViewModel(app: Application): UIViewModel(app) {
    val discounts: LiveData<List<Discount>> = repository.discounts.asLiveData()
    val diners = repository.diners.asLiveData()
    val items = repository.items.asLiveData()

    fun removeDiscount(discount: Discount) = repository.removeDiscount(discount)
}