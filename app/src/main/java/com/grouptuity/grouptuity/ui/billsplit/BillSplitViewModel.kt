package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest


class BillSplitViewModel(application: Application): UIViewModel(application) {
    val dinerCount = repository.numberOfDiners.asLiveData()
    val itemCount = repository.numberOfItems.asLiveData()

    val activeFragmentIndex = repository.activeFragmentIndex
    val isProcessingPayments = repository.processingPayments.withOutputSwitch(isOutputFlowing).asLiveData()

    private val showProcessPaymentsButtonFlow = combine(
        activeFragmentIndex,
        repository.processingPayments,
        repository.hasUnprocessedPayments,
        repository.activePaymentAndMethod) { activeIndex, processing, hasUnprocessed, (activePayment, _) ->

        activeIndex == FRAG_PAYMENTS && !processing && activePayment == null && hasUnprocessed
    }

    val showProcessPaymentsButton: LiveData<Boolean> = showProcessPaymentsButtonFlow.asLiveData()

    val fabDrawableId: LiveData<Int?> = combine(
        activeFragmentIndex,
        repository.processingPayments,
        showProcessPaymentsButtonFlow) { index, isProcessing, showingButton ->

        when (index) {
            FRAG_DINERS -> R.drawable.ic_add_person
            FRAG_ITEMS -> R.drawable.ic_add_item
            FRAG_PAYMENTS -> {
                if (!isProcessing && !showingButton) {
                    R.drawable.ic_email
                } else {
                    null
                }
            }
            else -> null
        }
    }.asLiveData()

    fun createNewBill() {
        repository.createAndLoadNewBill()
    }

    fun requestProcessPayments() {
        if (repository.payments.value.any { it.unprocessed() })
            repository.processingPayments.value = true
    }
    fun stopProcessingPayments() { repository.processingPayments.value = false }

    fun requestSendEmailReceipt() {

    }
}