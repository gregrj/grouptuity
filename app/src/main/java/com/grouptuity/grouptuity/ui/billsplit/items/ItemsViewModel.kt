package com.grouptuity.grouptuity.ui.billsplit.items

import android.app.Application
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.Item
import com.grouptuity.grouptuity.data.UIViewModel
import com.grouptuity.grouptuity.data.withOutputSwitch

class ItemsViewModel(application: Application): UIViewModel(application) {
    val items = repository.items.withOutputSwitch(isOutputFlowing).asLiveData()
    val dinerIdMap = repository.dinerIdMap.withOutputSwitch(isOutputFlowing).asLiveData()

    fun removeItem(item: Item) { repository.deleteItem(item) }
}