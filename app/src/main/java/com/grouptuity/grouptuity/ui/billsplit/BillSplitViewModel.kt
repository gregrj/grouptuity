package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.*
import com.grouptuity.grouptuity.data.Repository


class BillSplitViewModel(application: Application): AndroidViewModel(application) {
    private val repository = Repository.getInstance(getApplication())

    val dinerCount = repository.numberOfDiners.asLiveData()
    val itemCount = repository.numberOfItems.asLiveData()
}