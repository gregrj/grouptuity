package com.grouptuity.grouptuity.ui.billsplit

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.FRAG_DINERS
import com.grouptuity.grouptuity.data.FRAG_ITEMS
import com.grouptuity.grouptuity.data.FRAG_PAYMENTS
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.*



class BillSplitViewModel(application: GrouptuityApplication): BaseUIViewModel(application) {
    val dinerCount = repository.numberOfDiners.asLiveData()
    val itemCount = repository.numberOfItems.asLiveData()

    val activeFragmentIndex = repository.activeFragmentIndex

    val isProcessingPayments = repository.processingPayments.asLiveData(isOutputLocked)

    val toolbarState: LiveData<BillSplitToolbarState> = combine(
        activeFragmentIndex,
        repository.isUserOnBill,
        repository.numberOfDiners,
        repository.numberOfItems,
        repository.isTaxTipped,
        repository.doDiscountsReduceTip,
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
            else -> null
        }
    }.asLiveData()

    fun createNewBill() { repository.createAndLoadNewBill() }
    fun includeSelfAsDiner() {
        // TODO if applicable query for includeWithEveryone
        repository.addUserAsDiner(false)
    }

    fun removeAllDiners() = repository.removeAllDiners()
    fun removeAllItems() = repository.removeAllItems()
    fun resetTaxAndTip() = repository.resetTaxAndTip()
    fun resetAllPayments() = repository.resetAllPaymentTemplates()

    fun toggleTaxIsTipped() { repository.toggleTaxIsTipped() }
    fun toggleDiscountsReduceTip() { repository.toggleDiscountsReduceTip() }

    fun requestProcessPayments() {
        if (repository.hasUnprocessedPayments.value)
            repository.processingPayments.value = true
    }
    fun stopProcessingPayments() { repository.processingPayments.value = false }

    fun requestSendEmailReceipt() {

    }
}
