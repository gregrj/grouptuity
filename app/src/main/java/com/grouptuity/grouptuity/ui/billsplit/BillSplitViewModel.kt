package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.*


class BillSplitViewModel(application: Application): UIViewModel(application) {
    companion object {
        const val FRAG_DINERS = 0
        const val FRAG_ITEMS = 1
        const val FRAG_TAX_TIP = 2
        const val FRAG_PAYMENTS = 3
    }

    val dinerCount = repository.diners.mapLatest { it.size }.asLiveData()
    val itemCount = repository.items.mapLatest { it.size }.asLiveData()

    val activeFragmentIndex = MutableStateFlow(FRAG_DINERS)

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

        Log.e("d", ""+activeIndex +" "+ (activePayment==null))

        activeIndex == FRAG_PAYMENTS
                && activePayment == null
                && payments.any { it.unprocessed() }
    }.asLiveData()

    init {
        // Clear active payment if active fragment changes to non
        activeFragmentIndex.onEach {
            if (it != FRAG_PAYMENTS) {
                repository.activePaymentAndMethod.value = Pair(null, null)
            }
        }.launchIn(viewModelScope)
    }

    fun requestSendEmailReceipt() {

    }
}