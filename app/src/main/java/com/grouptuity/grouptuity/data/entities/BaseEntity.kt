package com.grouptuity.grouptuity.data.entities

import android.app.Application
import android.util.Log
import androidx.room.Ignore
import com.grouptuity.grouptuity.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*


val mathContext: MathContext = MathContext.DECIMAL64
private val percentFormatter = NumberFormat.getPercentInstance().also {
    it.maximumFractionDigits = 3 // TODO parameterize
}


fun newUUID(): String = UUID.randomUUID().toString()

fun BigDecimal.toPercentString(): String = percentFormatter.format(this.movePointLeft(2))

fun BigDecimal.divideWithZeroBypass(divisor: Int): BigDecimal = when (divisor) {
    0 -> this
    else -> this.divide(BigDecimal(divisor), mathContext)
}

fun BigDecimal.divideWithZeroBypass(divisor: BigDecimal): BigDecimal = when {
    divisor.compareTo(BigDecimal.ZERO) == 0 -> this
    else -> this.divide(divisor, mathContext)
}

fun <T> MutableMap<T, BigDecimal>.add(key: T, addend: BigDecimal) {
    this.merge(key, addend, BigDecimal::plus)
}

fun <T> MutableMap<T, BigDecimal>.subtract(key: T, subtrahend: BigDecimal) {
    this.merge(key, subtrahend, BigDecimal::minus)
}


inline fun <T, reified S> Flow<Collection<T>>.assemble(
    scope: CoroutineScope,
    crossinline selector: (T) -> Flow<S>
): StateFlow<Map<T, S>> {
    var innerScope: CoroutineScope? = null
    val mutableStateFlow = MutableStateFlow(emptyMap<T, S>())

    scope.launch {
        this@assemble.map { elementSet ->
            innerScope?.cancel()

            val sources = elementSet.associateWith {
                selector(it)
            }

            innerScope = CoroutineScope(Dispatchers.Default).apply {
                launch {
                    combine(sources.values) {
                        sources.keys.zip(it).toMap()
                    }.collect { output ->
                        mutableStateFlow.value = output
                    }
                }
            }
        }.collect()
    }

    return mutableStateFlow
}


abstract class BillComponent: BaseEntity() {
    abstract val billId: String

    protected val bill: Bill
        get() = billIdMap[billId]!!.get()!!

    override val currencyFlow: StateFlow<Currency>
        get() = bill.currencyFlow

    override val isTaxTippedFlow: StateFlow<Boolean>
        get() = bill.isTaxTippedFlow

    override val discountsReduceTipFlow: StateFlow<Boolean>
        get() = bill.discountsReduceTipFlow

    override val itemRoundingModeFlow: StateFlow<RoundingMode>
        get() = bill.itemRoundingModeFlow

    override val discountRoundingModeFlow: StateFlow<RoundingMode>
        get() = bill.discountRoundingModeFlow

    override val taxRoundingModeFlow: StateFlow<RoundingMode>
        get() = bill.taxRoundingModeFlow

    override val tipRoundingModeFlow: StateFlow<RoundingMode>
        get() = bill.tipRoundingModeFlow
}


abstract class BaseEntity {
    @Ignore val scope = newScope()

    protected abstract val currencyFlow: StateFlow<Currency>
    protected abstract val isTaxTippedFlow: StateFlow<Boolean>
    protected abstract val discountsReduceTipFlow: StateFlow<Boolean>
    protected abstract val itemRoundingModeFlow: StateFlow<RoundingMode>
    protected abstract val discountRoundingModeFlow: StateFlow<RoundingMode>
    protected abstract val taxRoundingModeFlow: StateFlow<RoundingMode>
    protected abstract val tipRoundingModeFlow: StateFlow<RoundingMode>

    protected val currencyFractionDigits: Int
        get() = currencyFlow.value.defaultFractionDigits

    protected val itemRoundingMode: RoundingMode
        get() = itemRoundingModeFlow.value

    protected val discountRoundingMode: RoundingMode
        get() = discountRoundingModeFlow.value

    fun newScope() = CoroutineScope(Dispatchers.Unconfined)

    fun delete() {
        scope.cancel()
        onDelete()
    }

    protected abstract fun onDelete()

    fun Flow<BigDecimal>.asRounded(
        roundingMode: Flow<RoundingMode>
    ): StateFlow<BigDecimal> =
        combine(
            this,
            currencyFlow,
            roundingMode
        ) { value, currency, rounding ->
            value.setScale(currency.defaultFractionDigits, rounding)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    fun <T> Flow<Collection<T>>.size(): StateFlow<Int> =
        this.map { it.size }.stateIn(scope, SharingStarted.WhileSubscribed(), 0)

    fun Flow<BigDecimal>.plus(flow: Flow<BigDecimal>): StateFlow<BigDecimal> =
        combine(this, flow) { addend, augend ->
            addend + augend
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    fun Flow<BigDecimal>.minus(flow: Flow<BigDecimal>): StateFlow<BigDecimal> =
        combine(this, flow) { minuend, subtrahend ->
            minuend - subtrahend
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @JvmName("divideInt")
    fun Flow<BigDecimal>.divide(flow: Flow<Int>): StateFlow<BigDecimal> =
        combine(this, flow
        ) { dividend, divisor ->
            dividend.divideWithZeroBypass(divisor)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    fun Flow<BigDecimal>.divide(flow: Flow<BigDecimal>): StateFlow<BigDecimal> =
        combine(this, flow
        ) { dividend, divisor ->
            dividend.divideWithZeroBypass(divisor)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    interface CachedEntityMapInterface {
        val rawTotal: StateFlow<BigDecimal>
        val roundedTotal: StateFlow<BigDecimal>
    }

    inner class CachedEntityMap<E>(
        private val roundingModeFlow: Flow<RoundingMode>? = null,
        private val elementFlowSelector: (E) -> Flow<BigDecimal>
    ): CachedEntityMapInterface {

        private val mElements = mutableSetOf<E>()
        private val mElementScopes = mutableMapOf<E, CoroutineScope>()
        private val mRawValues = mutableMapOf<E, BigDecimal>()
        private val mRoundedValues: MutableMap<E, BigDecimal> =
            if (roundingModeFlow == null) {
                mRawValues
            } else {
                mutableMapOf()
            }

        private val _elements = MutableStateFlow<Set<E>>(emptySet())
        private val _rawValues = MutableStateFlow<Map<E, BigDecimal>>(emptyMap())
        private val _roundedValues: MutableStateFlow<Map<E, BigDecimal>> =
            if (roundingModeFlow == null) {
                _rawValues
            } else {
                MutableStateFlow(emptyMap())
            }
        private val _rawValueFlows = mutableMapOf<E, StateFlow<BigDecimal>>()
        private val _roundedValueFlows: MutableMap<E, StateFlow<BigDecimal>> =
            if (roundingModeFlow == null) {
                _rawValueFlows
            } else {
                mutableMapOf()
            }
        private val _rawTotal = MutableStateFlow(BigDecimal.ZERO)
        private val _roundedTotal =
            if (roundingModeFlow == null) {
                _rawTotal
            } else {
                MutableStateFlow(BigDecimal.ZERO)
            }

        val elements: StateFlow<Set<E>> = _elements
        val rawValues: StateFlow<Map<E, BigDecimal>> = _rawValues
        val roundedValues: StateFlow<Map<E, BigDecimal>> = _roundedValues
        val rawValueFlows: Map<E, StateFlow<BigDecimal>> = _rawValueFlows
        val roundedValueFlows: Map<E, StateFlow<BigDecimal>> = _roundedValueFlows
        override val rawTotal: StateFlow<BigDecimal> = _rawTotal
        override val roundedTotal: StateFlow<BigDecimal> = _roundedTotal

        private val transformers =
            mutableMapOf<
                    MutableMap<E, StateFlow<BigDecimal>>,
                        (E, StateFlow<BigDecimal>) -> Flow<BigDecimal>>()

        internal fun add(element: E) {
            if (element !in mElements) {
                mElements.add(element)
                mElementScopes[element] = newScope().apply {
                    launch {
                        val rawValueFlow = elementFlowSelector(element).map { newRawValue ->
                            val oldRawValue = mRawValues.getOrDefault(element, BigDecimal.ZERO)

                            mRawValues[element] = newRawValue
                            _rawValues.value = mRawValues.toMap()

                            _rawTotal.value += newRawValue - oldRawValue

                            newRawValue
                        }.stateIn(this, SharingStarted.Eagerly, BigDecimal.ZERO)

                        _rawValueFlows[element] = rawValueFlow

                        if (roundingModeFlow != null) {
                            val roundedValueFlow = combine(
                                rawValueFlow,
                                currencyFlow,
                                roundingModeFlow
                            ) { newRawValue, currency, roundingMode ->
                                val oldRoundedValue =
                                    mRoundedValues.getOrDefault(element, BigDecimal.ZERO)

                                val newRoundedValue = newRawValue.setScale(
                                    currency.defaultFractionDigits,
                                    roundingMode
                                )

                                mRoundedValues[element] = newRoundedValue
                                _roundedValues.value = mRoundedValues.toMap()

                                _roundedTotal.value += newRoundedValue - oldRoundedValue

                                newRoundedValue
                            }.stateIn(this, SharingStarted.Eagerly, BigDecimal.ZERO)

                            _roundedValueFlows[element] = roundedValueFlow
                        }

                        transformers.forEach { (map, transformFunc) ->
                            val transformedFlow = transformFunc(element, rawValueFlow)
                            map[element] = if (transformedFlow is StateFlow) {
                                transformedFlow
                            } else {
                                transformedFlow.stateIn(
                                    this,
                                    SharingStarted.WhileSubscribed(),
                                    BigDecimal.ZERO
                                )
                            }
                        }
                    }
                }

                _elements.value = mElements.toSet()
            }
        }

        internal fun remove(element: E) {
            mElementScopes.remove(element)?.also { scope ->
                scope.cancel()

                mElements.remove(element)

                _rawValueFlows.remove(element)
                mRawValues.remove(element)?.also { value ->
                    _rawValues.value = mRawValues.toMap()
                    _rawTotal.value -= value
                }

                if (roundingModeFlow != null) {
                    _roundedValueFlows.remove(element)
                    mRoundedValues.remove(element)?.also { value ->
                        _roundedValues.value = mRoundedValues.toMap()
                        _roundedTotal.value -= value
                    }
                }

                _elements.value = mElements.toSet()

                transformers.forEach { (map, _) ->
                    map.remove(element)
                }
            }
        }

        fun transformRawValuesIntoMap(
            transformFunc: (E, StateFlow<BigDecimal>) -> Flow<BigDecimal>
        ): Map<E, StateFlow<BigDecimal>> =
            mutableMapOf<E, StateFlow<BigDecimal>>().also {
                transformers[it] = transformFunc
            }
    }

    inner class CachedDoubleEntityMap<T, S>(
        private val roundingModeFlow: StateFlow<RoundingMode>,
        private val elementFlowSelector: (Pair<T, S>) -> Flow<BigDecimal>
    ): CachedEntityMapInterface {
        private val mReferenceCounter = mutableMapOf<T, Int>()
        private val mElements = mutableMapOf<Pair<T, S>, CoroutineScope>()
        private val mRawValues = mutableMapOf<Pair<T, S>, BigDecimal>()
        private val mRawValuesByT = mutableMapOf<T, BigDecimal>()
        private val mRoundedValuesByT = mutableMapOf<T, BigDecimal>()

        private val _firstTypeElements = MutableStateFlow(mReferenceCounter.keys.toSet())
        private val _rawTotal = MutableStateFlow(BigDecimal.ZERO)
        private val _roundedTotal = MutableStateFlow(BigDecimal.ZERO)

        val firstTypeElements: StateFlow<Set<T>> = _firstTypeElements
        override val rawTotal: StateFlow<BigDecimal> = _rawTotal
        override val roundedTotal: StateFlow<BigDecimal> = _roundedTotal

        internal fun add(element: Pair<T, S>) {
            if (element !in mElements.keys) {
                val t = element.first
                val referenceCount = mReferenceCounter.getOrDefault(t, 0) + 1
                mReferenceCounter[t] = referenceCount
                if (referenceCount > 1) {
                    _firstTypeElements.value = mReferenceCounter.keys.toSet()
                }

                mElements[element] = newScope().apply {
                    launch {
                        val rawValueByTFlow = elementFlowSelector(element).map { newRawValue ->
                            val oldRawValue = mRawValues.getOrDefault(element, BigDecimal.ZERO)
                            mRawValues[element] = newRawValue
                            val deltaRawValue = newRawValue - oldRawValue

                            val newRawValueByT = deltaRawValue +
                                    mRawValuesByT.getOrDefault(t, BigDecimal.ZERO)
                            mRawValuesByT[t] = newRawValueByT

                            _rawTotal.value += deltaRawValue
                            newRawValueByT
                        }.distinctUntilChanged()

                        combine(
                            rawValueByTFlow,
                            currencyFlow,
                            roundingModeFlow
                        ) { newRawValueByT, currency, roundingMode ->
                            val oldRoundedValue = mRoundedValuesByT.getOrDefault(t, BigDecimal.ZERO)
                            val newRoundedValue = newRawValueByT.setScale(
                                currency.defaultFractionDigits,
                                roundingMode
                            )

                            mRoundedValuesByT[t] = newRoundedValue
                            _roundedTotal.value += newRoundedValue - oldRoundedValue
                        }.collect()
                    }
                }
            }
        }

        internal fun remove(element: Pair<T, S>) {
            mElements.remove(element)?.also { scope ->
                scope.cancel()

                val t = element.first
                mReferenceCounter[t] = mReferenceCounter[t]!! - 1

                mRawValues.remove(element)!!.also { rawValue ->
                    val oldRoundedValueByT = mRoundedValuesByT[t]!!

                    if (mReferenceCounter[t] == 0) {
                        // No additional references to t exist (operation is equivalent to removal)
                        mReferenceCounter.remove(t)
                        _firstTypeElements.value = mReferenceCounter.keys.toSet()

                        mRawValuesByT.remove(t)
                        mRoundedValuesByT.remove(t)

                        _rawTotal.value -= rawValue
                        _roundedTotal.value -= oldRoundedValueByT
                    } else {
                        // Additional references to t exist so subtract out values
                        mRawValuesByT[t] = mRawValuesByT[t]!! - rawValue

                        val newRoundedValueByT = mRawValuesByT[t]!!.setScale(
                            currencyFractionDigits,
                            roundingModeFlow.value
                        )
                        mRoundedValuesByT[t] = newRoundedValueByT

                        _rawTotal.value -= rawValue
                        _roundedTotal.value -= oldRoundedValueByT - newRoundedValueByT
                    }
                }
            }
        }
    }

    inner class CachedEntityMapTotaler(
        roundingModeFlow: Flow<RoundingMode>,
        adjustment: Flow<BigDecimal>?,
        cachedEntityMaps: Array<CachedEntityMapInterface>,
        transformer: (Array<BigDecimal>) -> BigDecimal
    ) {

        val raw = combine(cachedEntityMaps.map { it.rawTotal }) { transformer(it) }
        val rounded = combine(cachedEntityMaps.map { it.roundedTotal }) { transformer(it) }
        val displayed = raw.let{
            if (adjustment == null) it else it.plus(adjustment)
        }.asRounded(roundingModeFlow)
        val displayedCorrection = displayed.minus(rounded)
    }
}