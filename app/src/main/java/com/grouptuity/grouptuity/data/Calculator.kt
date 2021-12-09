package com.grouptuity.grouptuity.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.DecimalFormat
import java.text.NumberFormat

private val decimalSymbol = (DecimalFormat.getInstance() as DecimalFormat).decimalFormatSymbols.decimalSeparator
private const val percentMaxDecimals = 3
private const val percentMaxIntegers = 3
private val currencyMaxDecimals = NumberFormat.getCurrencyInstance().maximumFractionDigits
private val currencyMaxIntegers = 9 - currencyMaxDecimals


enum class CalculationType(val isPercent: Boolean,
                           val isZeroAcceptable: Boolean,
                           val maxDecimals: Int,
                           val maxIntegers: Int) {
    ITEM_PRICE(false, false, currencyMaxDecimals, currencyMaxIntegers),
    ITEMIZED_DISCOUNT_PERCENT(true, false, percentMaxDecimals, percentMaxIntegers),
    ITEMIZED_DISCOUNT_AMOUNT(false, false, currencyMaxDecimals, currencyMaxIntegers),
    REIMBURSEMENT_AMOUNT(false, false, currencyMaxDecimals, currencyMaxIntegers),
    OVERALL_DISCOUNT_PERCENT(true, true, percentMaxDecimals, percentMaxIntegers),
    OVERALL_DISCOUNT_AMOUNT(false, true, currencyMaxDecimals, currencyMaxIntegers),
    TAX_PERCENT(true, true, percentMaxDecimals, percentMaxIntegers),
    TAX_AMOUNT(false, true, currencyMaxDecimals, currencyMaxIntegers),
    TIP_PERCENT(true, true, percentMaxDecimals, percentMaxIntegers),
    TIP_AMOUNT(false, true, currencyMaxDecimals, currencyMaxIntegers),
    SUBTOTAL(false, true, currencyMaxDecimals, currencyMaxIntegers),
    AFTER_DISCOUNT(false, true, currencyMaxDecimals, currencyMaxIntegers),
    AFTER_TAX(false, true, currencyMaxDecimals, currencyMaxIntegers),
    TOTAL(false, true, currencyMaxDecimals, currencyMaxIntegers),
    DEBT_AMOUNT(false, false, currencyMaxDecimals, currencyMaxIntegers);

    companion object {
        fun getCurrencyCounterpartTo(calculationType: CalculationType) = when(calculationType) {
            ITEMIZED_DISCOUNT_PERCENT -> ITEMIZED_DISCOUNT_AMOUNT
            ITEMIZED_DISCOUNT_AMOUNT -> ITEMIZED_DISCOUNT_AMOUNT
            OVERALL_DISCOUNT_PERCENT -> OVERALL_DISCOUNT_AMOUNT
            OVERALL_DISCOUNT_AMOUNT -> OVERALL_DISCOUNT_AMOUNT
            TAX_PERCENT -> TAX_AMOUNT
            TAX_AMOUNT -> TAX_AMOUNT
            TIP_PERCENT -> TIP_AMOUNT
            TIP_AMOUNT -> TIP_AMOUNT
            else -> null
        }

        fun getPercentCounterpartTo(calculationType: CalculationType) = when(calculationType) {
            ITEMIZED_DISCOUNT_PERCENT -> ITEMIZED_DISCOUNT_PERCENT
            ITEMIZED_DISCOUNT_AMOUNT -> ITEMIZED_DISCOUNT_PERCENT
            OVERALL_DISCOUNT_PERCENT -> OVERALL_DISCOUNT_PERCENT
            OVERALL_DISCOUNT_AMOUNT -> OVERALL_DISCOUNT_PERCENT
            TAX_PERCENT -> TAX_PERCENT
            TAX_AMOUNT -> TAX_PERCENT
            TIP_PERCENT -> TIP_PERCENT
            TIP_AMOUNT -> TIP_PERCENT
            else -> null
        }
    }
}


class CalculatorData(
    initialCalculationType: CalculationType,
    val inputLock: MutableStateFlow<Boolean>,
    isParentOutputFlowing: Flow<Boolean>,
    val autoHideNumberPad: Boolean=true,
    private val acceptValueCallback: () -> Unit = {}) {

    private val isOutputFlowing = inputLock.mapLatest { !it }.withOutputSwitch(isParentOutputFlowing)

    private val calculationType = MutableStateFlow(initialCalculationType)
    private val isZeroAcceptable: StateFlow<Boolean> = calculationType.mapLatest {
        it.isZeroAcceptable
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.isZeroAcceptable)
    private val maxDecimalsAllowed: StateFlow<Int> = calculationType.mapLatest {
        it.maxDecimals
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.maxDecimals)
    private val maxIntegersAllowed: StateFlow<Int> = calculationType.mapLatest {
        it.maxIntegers
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.maxIntegers)
    val isInPercent: LiveData<Boolean> = calculationType.mapLatest {
        it.isPercent
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.isPercent).asLiveData(isOutputFlowing)

    private val rawInputValue: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _lastCompletedRawValue = MutableLiveData<String?>(null)
    private val _isNumberPadVisible = MutableStateFlow(false)
    val isNumberPadVisible = _isNumberPadVisible.asLiveData()

    var editedValue: Boolean? = null // null == not set, false == prior value unchanged, true == new value set
        private set
    val rawInputIsBlank: StateFlow<Boolean> = rawInputValue.mapLatest { it == null }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, true)
    val numericalValue: StateFlow<Double?> = rawInputValue.mapLatest {
        when(it) {
            null, "." -> { null }
            else -> { it.toDouble() }
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)
    val displayValue: LiveData<String> = combine(_isNumberPadVisible, rawInputValue, isInPercent.asFlow()) { numberPadVisible, rawValue, inPercent ->
        formatRawValue(numberPadVisible, rawValue, inPercent)
    }.asLiveData(isParentOutputFlowing)

    val editButtonVisible: LiveData<Boolean> = _isNumberPadVisible.mapLatest { !it }.asLiveData(isOutputFlowing)
    val backspaceButtonVisible: LiveData<Boolean> = combine(_isNumberPadVisible, rawInputValue) { visible, rawValue ->
        visible && rawValue != null
    }.asLiveData(isOutputFlowing)
    val zeroButtonEnabled: LiveData<Boolean> = combine(rawInputValue, maxDecimalsAllowed, maxIntegersAllowed) { rawValue, maxDecimals, maxIntegers ->
        when(rawValue) {
            null -> false
            else -> {
                val numDecimals = numDecimalPlaces(rawValue)
                if(numDecimals == null) {
                    rawValue.length < maxIntegers
                } else {
                    if(isZeroAcceptable.value || rawValue.contains(Regex("[1-9]"))) {
                        numDecimals < maxDecimals
                    } else {
                        numDecimals < maxDecimals - 1
                    }
                }
            }
        }
    }.asLiveData(isOutputFlowing)
    val nonZeroButtonsEnabled: LiveData<Boolean> = combine(rawInputValue, maxDecimalsAllowed, maxIntegersAllowed) { rawValue, maxDecimals, maxIntegers ->
        when(val numDecimals = numDecimalPlaces(rawValue)) {
            null -> { rawValue == null || rawValue.length < maxIntegers }
            else -> { numDecimals < maxDecimals }
        }
    }.asLiveData(isOutputFlowing)
    val decimalButtonEnabled: LiveData<Boolean> = combine(rawInputValue, maxDecimalsAllowed) {
            rawValue, maxDecimals -> maxDecimals > 0 && numDecimalPlaces(rawValue) == null
    }.asLiveData(isOutputFlowing)
    val acceptButtonEnabled: LiveData<Boolean> = combine(rawInputValue, isZeroAcceptable) { rawValue, zeroAcceptable ->
        zeroAcceptable || (rawValue != null && rawValue.contains(Regex("[1-9]")))
    }.asLiveData(isOutputFlowing)

    private val _acceptEvents = MutableStateFlow<Event<String>?>(null)
    val acceptEvents: LiveData<Event<String>> = _acceptEvents.filterNotNull().asLiveData()

    fun reset(type: CalculationType, newRawInputValue: Double?, showNumberPad: Boolean = false) {
        // Invalidate any unconsumed events
        _acceptEvents.value?.consume()

        calculationType.value = type

        if (newRawInputValue == null) {
            editedValue = null
            _lastCompletedRawValue.value = null
            rawInputValue.value = null
        } else {
            editedValue = false
            _lastCompletedRawValue.value = newRawInputValue.toString()
            rawInputValue.value = newRawInputValue.toString()
        }

        _isNumberPadVisible.value = showNumberPad
    }

    fun switchToCurrency() {
        CalculationType.getCurrencyCounterpartTo(calculationType.value)?.also {
            calculationType.value = it
        }
    }
    fun switchToPercent() {
        CalculationType.getPercentCounterpartTo(calculationType.value)?.also {
            calculationType.value = it
        }
    }

    fun showNumberPad() { _isNumberPadVisible.value = true }
    fun hideNumberPad() { _isNumberPadVisible.value = false }
    fun openCalculator() {
        clearValue()
        _isNumberPadVisible.value = true
    }

    fun addDigit(digit: Char) {
        val rawValue = rawInputValue.value
        if (rawValue == null) {
            if(digit != '0') {
                // Assumes maxIntegersAllowed is greater than one
                rawInputValue.value = digit.toString()
            }
        }
        else {
            val newRawValue = rawValue + digit
            when(numDecimalPlaces(newRawValue)) {
                null -> {
                    // Input does not have a decimal point
                    if (newRawValue.length <= maxIntegersAllowed.value) {
                        rawInputValue.value = newRawValue
                    } else if (maxDecimalsAllowed.value == 0) {
                        acceptValue(newRawValue)
                    }
                }
                in 0 until maxDecimalsAllowed.value -> {
                    rawInputValue.value = newRawValue
                }
                maxDecimalsAllowed.value -> {
                    acceptValue(newRawValue)
                }
            }
        }
    }
    fun addDecimal() {
        if(maxDecimalsAllowed.value < 1)
            return

        val rawValue = rawInputValue.value
        if (rawValue == null) {
            rawInputValue.value = "."
        } else if (!rawValue.contains('.')) {
            rawInputValue.value = "$rawValue."
        }
    }
    fun removeDigit() {
        val rawInput = rawInputValue.value
        if (rawInput != null) {
            if(rawInput.length == 1) {
                rawInputValue.value = null
            } else {
                rawInputValue.value = rawInput.substring(0, rawInput.length - 1)
            }
        }
    }
    fun clearValue() { rawInputValue.value = null }
    fun tryRevertToLastValue(): Boolean {
        return if (_lastCompletedRawValue.value != null) {
            rawInputValue.value = _lastCompletedRawValue.value
            if (autoHideNumberPad) {
                hideNumberPad()
            }
            true
        } else {
            false
        }
    }
    fun discardEntry() {
        rawInputValue.value = null
        if(_lastCompletedRawValue.value != null) {
            _lastCompletedRawValue.value = null
            editedValue = true
        }

        if (autoHideNumberPad) {
            hideNumberPad()
        }
    }
    fun tryAcceptValue(): Boolean {
        val inputValue = rawInputValue.value
        return when {
            inputValue == null -> {
                if (isZeroAcceptable.value) {
                    acceptValue(inputValue)
                    true
                } else {
                    false
                }
            }
            inputValue.contains(Regex("[1-9]")) -> {
                acceptValue(inputValue)
                true
            }
            isZeroAcceptable.value -> {
                // Value is combination of zeros and/or decimal
                acceptValue(inputValue)
                true
            }
            else -> {
                false
            }
        }
    }

    private fun acceptValue(value: String?) {
        inputLock.value = true

        rawInputValue.value = value
        _lastCompletedRawValue.value = value
        editedValue = true

        if (autoHideNumberPad) {
            hideNumberPad()
        }

        acceptValueCallback()
        _acceptEvents.value = if (value == null || value == ".") {
            Event("0")
        } else {
            Event(value)
        }
    }

    companion object {
        private fun numDecimalPlaces(value: String?): Int? = value?.let {
            if(value.indexOf('.') == -1) null else value.length - value.indexOf('.') - 1
        }

        private fun appendDecimalSymbol(baseString: String): String =
            if(baseString.last().toString().matches(Regex("[0-9]"))) {
                baseString + decimalSymbol
            } else {
                val suffix = Regex("\\D*$").find(baseString)
                if(suffix == null || suffix.value.first() == decimalSymbol)
                    baseString
                else
                    baseString.substring(0, baseString.length - suffix.value.length) + decimalSymbol + suffix.value
            }

        fun formatRawValue(numberPadVisible: Boolean, rawValue: String?, inPercent: Boolean): String =
            if(numberPadVisible) {
                if(inPercent) {
                    val formatter = NumberFormat.getPercentInstance()

                    when(rawValue) {
                        null -> {
                            formatter.maximumFractionDigits = 0
                            formatter.format(0)
                        }
                        "." -> {
                            formatter.maximumFractionDigits = 0
                            appendDecimalSymbol(formatter.format(0))
                        }
                        else -> {
                            when(val numDecimals = numDecimalPlaces(rawValue)) {
                                null -> {
                                    formatter.maximumFractionDigits = 0
                                    formatter.format(rawValue.toDouble()*0.01)
                                }
                                0 -> {
                                    formatter.maximumFractionDigits = 0
                                    appendDecimalSymbol(formatter.format(rawValue.toDouble()*0.01))
                                }
                                else -> {
                                    formatter.minimumFractionDigits = numDecimals
                                    formatter.format(rawValue.toDouble()*0.01)
                                }
                            }
                        }
                    }
                } else {
                    val formatter = NumberFormat.getCurrencyInstance()

                    when(rawValue) {
                        null -> {
                            formatter.minimumFractionDigits = 0
                            formatter.format(0)
                        }
                        "." -> {
                            formatter.minimumFractionDigits = 0
                            appendDecimalSymbol(formatter.format(0))
                        }
                        else -> {
                            when(val numDecimals = numDecimalPlaces(rawValue)) {
                                null -> {
                                    formatter.maximumFractionDigits = 0
                                    formatter.format(rawValue.toDouble())
                                }
                                0 -> {
                                    formatter.maximumFractionDigits = 0
                                    appendDecimalSymbol(formatter.format(rawValue.toDouble()))
                                }
                                else -> {
                                    formatter.minimumFractionDigits = numDecimals
                                    formatter.format(rawValue.toDouble())
                                }
                            }
                        }
                    }
                }
            } else {
                if(inPercent) {
                    val formatter = NumberFormat.getPercentInstance()

                    if(rawValue == null || !rawValue.contains(Regex("[1-9]"))) {
                        formatter.minimumFractionDigits = 0
                        formatter.format(0)
                    } else if(rawValue.contains('.')) {
                        val trimmedValue = rawValue.replace(Regex("0*$"), "").replace(Regex("\\.$"), "")
                        formatter.minimumFractionDigits = trimmedValue.length - trimmedValue.indexOf('.') - 1
                        formatter.format(trimmedValue.toDouble()*0.01)
                    } else {
                        formatter.minimumFractionDigits = 0
                        formatter.format(rawValue.toDouble()*0.01)
                    }
                } else {
                    NumberFormat.getCurrencyInstance().format(when(rawValue) {
                        null, "." -> 0
                        else -> rawValue.toDouble()
                    })
                }
            }
    }
}