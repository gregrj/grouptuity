package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest


class BillSplitViewModel(application: Application): UIViewModel(application) {
    val dinerCount = repository.numberOfDiners.asLiveData()
    val itemCount = repository.numberOfItems.asLiveData()
    val billIncludesSelf = repository.billIncludesSelf.asLiveData()
    val taxIsTipped = repository.taxIsTipped.asLiveData()
    val discountsReduceTip = repository.discountsReduceTip.asLiveData()

    val activeFragmentIndex = repository.activeFragmentIndex
    val activeFragmentIndexLiveData = activeFragmentIndex.asLiveData()

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
        repository.activePaymentAndMethod,
        showProcessPaymentsButtonFlow) { index, isProcessing, (payment, _), showingProcessButton ->

        when (index) {
            FRAG_DINERS -> R.drawable.ic_add_person
            FRAG_ITEMS -> R.drawable.ic_add_item
            FRAG_PAYMENTS -> {
                if (payment == null && !isProcessing && !showingProcessButton) {
                    R.drawable.ic_send_receipt
                } else {
                    null
                }
            }
            else -> null
        }
    }.asLiveData()

    fun createNewBill() { repository.createAndLoadNewBill() }
    fun includeSelfAsDiner() { repository.addSelfAsDiner() }

    fun removeAllDiners() = repository.removeAllDiners()
    fun removeAllItems() = repository.removeAllItems()
    fun resetTaxAndTip() = repository.resetTaxAndTip()
    fun resetAllPayments() = repository.resetAllPaymentTemplates()

    fun toggleTaxIsTipped() { repository.toggleTaxIsTipped() }
    fun toggleDiscountsReduceTip() { repository.toggleDiscountsReduceTip() }

    fun requestProcessPayments() {
        if (repository.payments.value.any { it.unprocessed() })
            repository.processingPayments.value = true
    }
    fun stopProcessingPayments() { repository.processingPayments.value = false }

    fun requestSendEmailReceipt() {

    }
}