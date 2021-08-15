package com.grouptuity.grouptuity.data

import androidx.lifecycle.MutableLiveData
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
    TOTAL(false, true, currencyMaxDecimals, currencyMaxIntegers)
}

class CalculatorData(initialCalculationType: CalculationType, val autoHideNumberPad: Boolean=true, private val acceptValueCallback: () -> Unit = {}) {
    private val calculationType = MutableStateFlow(initialCalculationType)

    val isNumberPadVisible = MutableStateFlow(false)
    val isInPercent: StateFlow<Boolean> = calculationType.mapLatest {
        it.isPercent
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.isPercent)
    private val isZeroAcceptable: StateFlow<Boolean> = calculationType.mapLatest {
        it.isZeroAcceptable
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.isZeroAcceptable)
    private val maxDecimalsAllowed: StateFlow<Int> = calculationType.mapLatest {
        it.maxDecimals
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.maxDecimals)
    private val maxIntegersAllowed: StateFlow<Int> = calculationType.mapLatest {
        it.maxIntegers
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.maxIntegers)

    private val rawInputValue: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _lastCompletedRawValue = MutableLiveData<String?>(null)

    var editedValue: Boolean? = null // null == not set, false == prior value unchanged, true == new value set
        private set
    val rawInputIsBlank: StateFlow<Boolean> = rawInputValue.mapLatest { it == null }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, true)
    val numericalValue: StateFlow<Double?> = rawInputValue.mapLatest {
        when(it) {
            null, "." -> { null }
            else -> { it.toDouble() }
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)
    val displayValue: StateFlow<String> = combine(isNumberPadVisible, rawInputValue, isInPercent) { numberPadVisible, rawValue, inPercent ->
        formatRawValue(numberPadVisible, rawValue, inPercent)
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, "")

    val editButtonVisible: Flow<Boolean> = isNumberPadVisible.mapLatest { !it }
    val backspaceButtonVisible: Flow<Boolean> = combine(isNumberPadVisible, rawInputValue) { visible, rawValue -> visible && rawValue != null }
    val zeroButtonEnabled: Flow<Boolean> = combine(rawInputValue, maxDecimalsAllowed, maxIntegersAllowed) { rawValue, maxDecimals, maxIntegers ->
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
    }
    val nonZeroButtonsEnabled: Flow<Boolean> = combine(rawInputValue, maxDecimalsAllowed, maxIntegersAllowed) { rawValue, maxDecimals, maxIntegers ->
        when(val numDecimals = numDecimalPlaces(rawValue)) {
            null -> { rawValue == null || rawValue.length < maxIntegers }
            else -> { numDecimals < maxDecimals }
        }
    }
    val decimalButtonEnabled: Flow<Boolean> = combine(rawInputValue, maxDecimalsAllowed) { rawValue, maxDecimals -> maxDecimals > 0 && numDecimalPlaces(rawValue) == null }
    val acceptButtonEnabled: Flow<Boolean> = combine(rawInputValue, isZeroAcceptable) { rawValue, zeroAcceptable -> zeroAcceptable || (rawValue != null && rawValue.contains(Regex("[1-9]"))) }

    private val _acceptEvents = MutableStateFlow<Event<String>?>(null)
    val acceptEvents: Flow<Event<String>> = _acceptEvents.filterNotNull()

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

        isNumberPadVisible.value = showNumberPad
    }

    fun switchCalculationType(type: CalculationType) { calculationType.value = type }

    fun showNumberPad() { isNumberPadVisible.value = true }
    fun hideNumberPad() { isNumberPadVisible.value = false }

    fun addDigit(digit: Char) = when(val rawValue = rawInputValue.value) {
        null -> {
            if(digit == '0'){
                false
            } else {
                rawInputValue.value = digit.toString()
                true
            }
        }
        else -> {
            val newRawValue = rawValue + digit
            when(numDecimalPlaces(newRawValue)) {
                null -> {
                    when(newRawValue.length) {
                        in 0 until maxIntegersAllowed.value -> {
                            rawInputValue.value = newRawValue
                            true
                        }
                        maxIntegersAllowed.value -> {
                            if (maxDecimalsAllowed.value == 0) {
                                acceptValue(newRawValue)
                            } else {
                                rawInputValue.value = newRawValue
                            }
                            true
                        }
                        else -> {
                            false
                        }
                    }
                }
                in 0 until maxDecimalsAllowed.value -> {
                    rawInputValue.value = newRawValue
                    true
                }
                maxDecimalsAllowed.value -> {
                    acceptValue(newRawValue)
                    true
                }
                else -> {
                    false
                }
            }
        }
    }
    fun addDecimal(): Boolean {
        if(maxDecimalsAllowed.value < 1)
            return false

        return when (val rawValue = rawInputValue.value) {
            null -> {
                rawInputValue.value = "."
                true
            }
            else -> {
                if (!rawValue.contains('.')) {
                    rawInputValue.value = "$rawValue."
                    true
                } else {
                    false
                }
            }
        }
    }
    fun removeDigit() {
        when(val rawInput = rawInputValue.value) {
            null -> return
            else -> {
                if(rawInput.length == 1) {
                    rawInputValue.value = null
                } else {
                    rawInputValue.value = rawInput.substring(0, rawInput.length - 1)
                }
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
        val value = rawInputValue.value

        return if(value == null) {
            if(isZeroAcceptable.value) {
                acceptValue("0")
                true
            } else {
                false
            }
        } else if(isZeroAcceptable.value || value.contains(Regex("[1-9]"))) {
            acceptValue(value)
            true
        } else {
            false
        }
    }

    private fun acceptValue(value: String) {
        rawInputValue.value = value
        _lastCompletedRawValue.value = value
        editedValue = true

        if (autoHideNumberPad) {
            hideNumberPad()
        }

        acceptValueCallback()
        _acceptEvents.value = Event(value)
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