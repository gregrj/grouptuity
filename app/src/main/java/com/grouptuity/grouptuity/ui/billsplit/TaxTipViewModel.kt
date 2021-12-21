package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.BaseUIViewModel
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.map


class TaxTipViewModel(app: Application): BaseUIViewModel(app) {

    val hasNoDiscounts: LiveData<Boolean> = repository.discounts.map { it.isEmpty() }.asLiveData()

    val subtotal = repository.groupSubtotal.asLiveData()
    val discountAmount = repository.groupDiscountAmount.asLiveData()
    val subtotalWithDiscounts = repository.groupSubtotalWithDiscounts.asLiveData()
    val taxPercent: LiveData<Double> = repository.taxPercent.asLiveData()
    val taxAmount = repository.groupTaxAmount.asLiveData()
    val subtotalWithDiscountsAndTax = repository.groupSubtotalWithDiscountsAndTax.asLiveData()
    val tipPercent: LiveData<Double> = repository.tipPercent.asLiveData()
    val tipAmount = repository.groupTipAmount.asLiveData()
    val total = repository.groupTotal.asLiveData()

    fun setTaxPercent(percent: Double) = repository.setTaxPercent(percent)
    fun setTaxAmount(amount: Double) = repository.setTaxAmount(amount)
    fun setTipPercent(percent: Double) = repository.setTipPercent(percent)
    fun setTipAmount(amount: Double) = repository.setTipAmount(amount)
}