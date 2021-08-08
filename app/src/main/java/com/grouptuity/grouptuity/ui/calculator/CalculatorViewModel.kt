package com.grouptuity.grouptuity.ui.calculator

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.*


class CalculatorViewModel(app: Application): UIViewModel(app) {
    val numberPadInputLocked = MutableStateFlow(false).also { addInputLock(it) }

    private val calcData = CalculatorData(CalculationType.TIP_PERCENT)

    val formattedPrice: LiveData<String> = calcData.displayValue.withOutputSwitch(isOutputFlowing).asLiveData()
    val backspaceButtonVisible: LiveData<Boolean> = calcData.backspaceButtonVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val zeroButtonEnabled: LiveData<Boolean> = calcData.zeroButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val nonZeroButtonsEnabled: LiveData<Boolean> = calcData.nonZeroButtonsEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val decimalButtonEnabled: LiveData<Boolean> = calcData.decimalButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val acceptButtonEnabled: LiveData<Boolean> = calcData.acceptButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()

    fun initialize(type: CalculationType) {
        unFreezeOutput()
        calcData.reset(type,null, showNumberPad = true)
    }

    fun addDigitToPrice(digit: Char) = calcData.addDigit(digit)
    fun addDecimalToPrice() = calcData.addDecimal()
    fun removeDigitFromPrice() = calcData.removeDigit()
    fun resetPrice() = calcData.clearValue()

    fun acceptValue() = if (calcData.tryAcceptValue()) calcData.numericalValue.value else null
}