package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.FRAG_DINERS
import com.grouptuity.grouptuity.data.FRAG_ITEMS
import com.grouptuity.grouptuity.data.FRAG_PAYMENTS
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.*


class BillSplitViewModel(application: Application): UIViewModel(application) {
    val dinerCount = repository.diners.mapLatest { it.size }.asLiveData()
    val itemCount = repository.items.mapLatest { it.size }.asLiveData()

    val activeFragmentIndex = repository.activeFragmentIndex

    val fabDrawableId: LiveData<Int?> = activeFragmentIndex.mapLatest {
        when (it) {
            FRAG_DINERS -> R.drawable.ic_add_person
            FRAG_ITEMS -> R.drawable.ic_add_item
            else -> null
        }
    }.asLiveData()

    val showProcessPaymentsButton: LiveData<Boolean> = combine(
        activeFragmentIndex,
        repository.payments,
        repository.activePaymentAndMethod) { activeIndex, payments, (activePayment, _) ->

        activeIndex == FRAG_PAYMENTS
                && activePayment == null
                && payments.any { it.unprocessed() }
    }.asLiveData()

    fun requestSendEmailReceipt() {

    }
}