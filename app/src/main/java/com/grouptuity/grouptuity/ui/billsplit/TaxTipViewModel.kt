package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.data.BaseUIViewModel
import kotlinx.coroutines.flow.map
import java.math.BigDecimal


class TaxTipViewModel(app: Application): BaseUIViewModel(app) {

    val hasNoDiscounts: LiveData<Boolean> = repository.discounts.map { it.isEmpty() }.asLiveData()

    val subtotal = repository.groupSubtotal.asLiveData()
    val discountAmount = repository.groupDiscountAmount.asLiveData()
    val subtotalWithDiscounts = repository.groupSubtotalWithDiscounts.asLiveData()
    val taxPercent = repository.taxPercent.asLiveData()
    val taxAmount = repository.groupTaxAmount.asLiveData()
    val subtotalWithDiscountsAndTax = repository.groupSubtotalWithDiscountsAndTax.asLiveData()
    val tipPercent = repository.tipPercent.asLiveData()
    val tipAmount = repository.groupTipAmount.asLiveData()
    val total = repository.groupTotal.asLiveData()

    fun setTaxPercent(percent: BigDecimal) = repository.setTaxPercent(percent)
    fun setTaxAmount(amount: BigDecimal) = repository.setTaxAmount(amount)
    fun setTipPercent(percent: BigDecimal) = repository.setTipPercent(percent)
    fun setTipAmount(amount: BigDecimal) = repository.setTipAmount(amount)
}
