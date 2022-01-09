package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.data.BaseUIViewModel
import com.grouptuity.grouptuity.data.entities.Item
import com.grouptuity.grouptuity.data.asLiveData
import kotlinx.coroutines.flow.map


class ItemsViewModel(application: Application): BaseUIViewModel(application) {
    val items = repository.items.asLiveData(isOutputLocked)
    val numberOfDiners = repository.diners.map { it.size }.asLiveData(isOutputLocked)

    fun removeItem(item: Item) { repository.removeItem(item) }
}
