package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.combine

data class BillSplitToolbarState(
    val activePage: Int,
    val showIncludeSelf: Boolean,
    val showClearDiners: Boolean,
    val showClearItems: Boolean,
    val checkTaxIsTipped: Boolean,
    val checkDiscountsReduceTip: Boolean,
    val showGeneralMenuGroup: Boolean,
    val showDinersMenuGroup: Boolean,
    val showItemsMenuGroup: Boolean,
    val showTaxTipMenuGroup: Boolean,
    val showPayMenuGroup: Boolean)


class BillSplitViewModel(application: Application): BaseUIViewModel(application) {
    val dinerCount = repository.numberOfDiners.asLiveData()
    val itemCount = repository.numberOfItems.asLiveData()

    val activeFragmentIndex = repository.activeFragmentIndex

    val isProcessingPayments = repository.processingPayments.asLiveData(isOutputLocked)

    val toolbarState: LiveData<BillSplitToolbarState> = combine(
        activeFragmentIndex,
        repository.billIncludesSelf,
        repository.numberOfDiners,
        repository.numberOfItems,
        repository.taxIsTipped,
        repository.discountsReduceTip,
        repository.processingPayments
    ) {
        val page = it[0] as Int
        val notProcessingPayments = !(it[6] as Boolean)

        BillSplitToolbarState(
            activePage = page,
            showIncludeSelf = !(it[1] as Boolean),
            showClearDiners = (it[2] as Int) > 0,
            showClearItems = (it[3] as Int) > 0,
            checkTaxIsTipped = it[4] as Boolean,
            checkDiscountsReduceTip = it[5] as Boolean,
            showGeneralMenuGroup = notProcessingPayments,
            showDinersMenuGroup = page == FRAG_DINERS,
            showItemsMenuGroup = page == FRAG_ITEMS,
            showTaxTipMenuGroup = page == FRAG_TAX_TIP,
            showPayMenuGroup = page == FRAG_PAYMENTS && notProcessingPayments)
    }.asLiveData()

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