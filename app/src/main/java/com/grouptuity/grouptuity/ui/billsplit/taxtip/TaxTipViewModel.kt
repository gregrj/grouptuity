package com.grouptuity.grouptuity.ui.billsplit.taxtip

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.mapLatest


class TaxTipViewModel(app: Application): UIViewModel(app) {

    val subtotal = repository.groupSubtotal.asLiveData()
    val discountAmount = repository.groupDiscountAmount.asLiveData()
    val subtotalWithDiscounts = repository.groupSubtotalWithDiscounts.asLiveData()
    val taxPercent: LiveData<Double> = repository.taxPercent.asLiveData()
    val taxAmount = repository.groupTaxAmount.asLiveData()
    val subtotalWithDiscountsAndTax = repository.groupSubtotalWithDiscountsAndTax.asLiveData()
    val tipPercent: LiveData<Double> = repository.tipPercent.asLiveData()
    val tipAmount = repository.groupTipAmount.asLiveData()
    val total = repository.groupTotal.asLiveData()

    val hasNoDiscounts: LiveData<Boolean> = repository.numberOfDiscounts.mapLatest { it == 0 }.asLiveData()
}