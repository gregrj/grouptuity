package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.*
import com.grouptuity.grouptuity.Event
import com.grouptuity.grouptuity.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn


class BillSplitViewModel(application: Application): AndroidViewModel(application) {
    private val repository = Repository.getInstance(getApplication())

    val dinerCount = repository.diners.mapLatest { it.size }.asLiveData()
    val itemCount = repository.items.mapLatest { it.size }.asLiveData()

    val hasPaymentsToProcess = repository.payments.mapLatest { payments ->
        payments.any { it.unprocessed() }
    }.asLiveData()

    fun requestProcessPayments() {
        repository.requestProcessPaymentsEvent.value = Event(true)
    }
}