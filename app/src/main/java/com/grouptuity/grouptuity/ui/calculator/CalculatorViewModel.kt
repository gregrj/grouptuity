package com.grouptuity.grouptuity.ui.calculator

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.*


class CalculatorViewModel(app: GrouptuityApplication): UIViewModel<CalculationType, Pair<Double, String>?>(app) {

    private val calcData = CalculatorData(CalculationType.TIP_PERCENT, autoHideNumberPad = false) {
        // Freeze UI for number pad will update before exit transition starts
        notifyTransitionStarted()
    }
    val calculator = CalculatorImpl(this, calcData)

    val toolBarInTertiaryState: LiveData<Boolean> = calculator.acceptButtonEnabled

    init {
        viewModelScope.launch {
            calcData.acceptEvents.collect {
                it.consume()?.apply {
                    finishFragment(
                        Pair(
                            this.value.toDouble(),
                            CalculatorData.formatRawValue(
                                false,
                                this.value,
                                calcData.isInPercent.value
                            )
                        )
                    )
                }
            }
        }
    }.asLiveData()

    fun initialize(type: CalculationType) {
        unFreezeOutput()
        calcData.reset(type,null, showNumberPad = true)
    }

    fun addDigitToPrice(digit: Char) = calcData.addDigit(digit)
    fun addDecimalToPrice() = calcData.addDecimal()
    fun removeDigitFromPrice() = calcData.removeDigit()
    fun resetPrice() = calcData.clearValue()

    fun tryAcceptValue() = calcData.tryAcceptValue()
}
