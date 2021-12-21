package com.grouptuity.grouptuity.data

import androidx.lifecycle.LiveData
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


interface Calculator {
    val inputLock: MutableStateFlow<Boolean>

    val displayValue: LiveData<String>
    val isNumberPadVisible: LiveData<Boolean>
    val isInPercent: LiveData<Boolean>
    val decimalButtonEnabled: LiveData<Boolean>
    val zeroButtonEnabled: LiveData<Boolean>
    val nonZeroButtonsEnabled: LiveData<Boolean>
    val acceptButtonEnabled: LiveData<Boolean>
    val backspaceButtonVisible: LiveData<Boolean>
    val editButtonVisible: LiveData<Boolean>

    fun switchToCurrency()
    fun switchToPercent()

    fun clearValue()
    fun showNumberPad()

    fun addDecimal()
    fun addDigit(digit: Char)
    fun removeDigit()

    fun tryAcceptValue()
}


class CalculatorImpl(viewModel: UIViewModel<*, *>, private val data: CalculatorData): Calculator {
    override val inputLock = viewModel.createInputLock()
    override val displayValue: LiveData<String> = data.displayValue.asLiveData(viewModel.isOutputLocked)
    override val isNumberPadVisible: LiveData<Boolean> = data.isNumberPadVisible.asLiveData(viewModel.isOutputLocked)
    override val isInPercent: LiveData<Boolean> = data.isInPercent.asLiveData(viewModel.isOutputLocked)
    override val decimalButtonEnabled: LiveData<Boolean> = data.decimalButtonEnabled.asLiveData(viewModel.isOutputLocked)
    override val zeroButtonEnabled: LiveData<Boolean> = data.zeroButtonEnabled.asLiveData(viewModel.isOutputLocked)
    override val nonZeroButtonsEnabled: LiveData<Boolean> = data.nonZeroButtonsEnabled.asLiveData(viewModel.isOutputLocked)
    override val acceptButtonEnabled: LiveData<Boolean> = data.acceptButtonEnabled.asLiveData(viewModel.isOutputLocked)
    override val backspaceButtonVisible: LiveData<Boolean> = data.backspaceButtonVisible.asLiveData(viewModel.isOutputLocked)
    override val editButtonVisible: LiveData<Boolean> = data.editButtonVisible.asLiveData(viewModel.isOutputLocked)

    override fun switchToCurrency() { data.switchToCurrency() }
    override fun switchToPercent() { data.switchToPercent() }
    override fun clearValue() { data.clearValue() }
    override fun showNumberPad() { data.showNumberPad() }
    override fun addDecimal() { data.addDecimal() }
    override fun addDigit(digit: Char) { data.addDigit(digit) }
    override fun removeDigit() { data.removeDigit() }
    override fun tryAcceptValue() { data.tryAcceptValue() }
}


class CalculatorData(
    initialCalculationType: CalculationType,
    val autoHideNumberPad: Boolean=true,
    private val acceptValueCallback: () -> Unit = {}
) {
    private val calculationType = MutableStateFlow(initialCalculationType)
    private val isZeroAcceptable: StateFlow<Boolean> = calculationType.map {
        it.isZeroAcceptable
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.isZeroAcceptable)
    private val maxDecimalsAllowed: StateFlow<Int> = calculationType.map {
        it.maxDecimals
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.maxDecimals)
    private val maxIntegersAllowed: StateFlow<Int> = calculationType.map {
        it.maxIntegers
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.maxIntegers)
    val isInPercent = calculationType.map {
        it.isPercent
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, initialCalculationType.isPercent)

    private val rawInputValue: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _lastCompletedRawValue = MutableStateFlow<String?>(null)
    private val _isNumberPadVisible = MutableStateFlow(false)
    val isNumberPadVisible: StateFlow<Boolean> = _isNumberPadVisible

    var editedValue: Boolean? = null // null == not set, false == prior value unchanged, true == new value set
        private set
    val rawInputIsBlank: StateFlow<Boolean> = rawInputValue.map { it == null }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, true)
    val numericalValue: StateFlow<Double?> = rawInputValue.map {
        when(it) {
            null, "." -> { null }
            else -> { it.toDouble() }
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)
    val displayValue = combine(_isNumberPadVisible, rawInputValue, isInPercent) { numberPadVisible, rawValue, inPercent ->
        formatRawValue(numberPadVisible, rawValue, inPercent)
    }

    val editButtonVisible = _isNumberPadVisible.map { !it }
    val backspaceButtonVisible = combine(_isNumberPadVisible, rawInputValue) { visible, rawValue ->
        visible && rawValue != null
    }
    val zeroButtonEnabled = combine(rawInputValue, maxDecimalsAllowed, maxIntegersAllowed) { rawValue, maxDecimals, maxIntegers ->
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
    val nonZeroButtonsEnabled = combine(
        rawInputValue,
        maxDecimalsAllowed,
        maxIntegersAllowed
    ) { rawValue, maxDecimals, maxIntegers ->
        when(val numDecimals = numDecimalPlaces(rawValue)) {
            null -> { rawValue == null || rawValue.length < maxIntegers }
            else -> { numDecimals < maxDecimals }
        }
    }
    val decimalButtonEnabled = combine(rawInputValue, maxDecimalsAllowed) { rawValue, maxDecimals ->
        maxDecimals > 0 && numDecimalPlaces(rawValue) == null
    }
    val acceptButtonEnabled = combine(rawInputValue, isZeroAcceptable) { rawValue, zeroAcceptable ->
        zeroAcceptable || (rawValue != null && rawValue.contains(Regex("[1-9]")))
    }

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
            if (autoHideNumberPad) {
                _isNumberPadVisible.value = false
            }
            rawInputValue.value = _lastCompletedRawValue.value
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
            _isNumberPadVisible.value = false
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
        acceptValueCallback()

        rawInputValue.value = value
        _lastCompletedRawValue.value = value
        editedValue = true

        if (autoHideNumberPad) {
            _isNumberPadVisible.value = false
        }

        _acceptEvents.value = Event(if (value == null || value == ".") "0" else value)
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