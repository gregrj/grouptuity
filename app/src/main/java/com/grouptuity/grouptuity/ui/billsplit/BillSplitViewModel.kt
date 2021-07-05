package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.*
import com.grouptuity.grouptuity.data.Repository
import kotlinx.coroutines.flow.mapLatest


class BillSplitViewModel(application: Application): AndroidViewModel(application) {
    private val repository = Repository.getInstance(getApplication())

    val dinerCount = repository.diners.mapLatest { it.size }.asLiveData()
    val itemCount = repository.items.mapLatest { it.size }.asLiveData()
}