package com.grouptuity.grouptuity.ui.basictipcalc

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.BaseUIViewModel
import com.grouptuity.grouptuity.data.CalculationType
import com.grouptuity.grouptuity.data.Event
import com.grouptuity.grouptuity.data.StoredPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.NumberFormat
import kotlin.math.max

// TODO residual issues with unusual discount/tax/tip input with bill amount as total

// TODO convert to big decimal

class BasicTipCalcViewModel(app: Application): BaseUIViewModel(app) {
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance()
    private val percentFormatter: NumberFormat = NumberFormat.getPercentInstance().apply {
        this.minimumFractionDigits = 0
        this.maximumFractionDigits = 3
    }

    private val greaterThan100Pct = app.resources.getString(R.string.calculator_gt_100_pct)
    private val warningDiscountExceeds100Pct = app.resources.getString(R.string.calculator_warning_discount_exceeds_100pct)
    private val warningDiscountExceedsSubtotal = app.resources.getString(R.string.calculator_warning_discount_exceeds_subtotal)
    private val warningTaxExceedsAfterTax = app.resources.getString(R.string.calculator_warning_tax_exceeds_aftertax)
    private val warningTipExceedsTotal = app.resources.getString(R.string.calculator_warning_tip_exceeds_total)
    private val warningTipPlusTaxExceedsTotal = app.resources.getString(R.string.calculator_warning_tip_plus_tax_exceeds_total)
    private val warningTaxExceedsTotal = app.resources.getString(R.string.calculator_warning_tax_exceeds_total)

    private val mBillAmountInput = MutableStateFlow(Pair(0.0, CalculationType.SUBTOTAL))
    private val mDiscountInput = MutableStateFlow(Pair(0.0, false))
    private val mTaxInput = MutableStateFlow(Pair(StoredPreference.defaultTaxPercent.value.toDouble(), true))
    private val mTipInput = MutableStateFlow(Pair(StoredPreference.defaultTipPercent.value.toDouble(), true))
    private val mTaxTipped = MutableStateFlow(StoredPreference.taxIsTipped.value)
    private val mDiscountReducesTip = MutableStateFlow(StoredPreference.discountsReduceTip.value)

    private val calculation = combine(
        mBillAmountInput,
        mDiscountInput,
        mTaxInput,
        mTipInput,
        mTaxTipped,
        mDiscountReducesTip) {

        val (billAmountInput, billAmountType) = it[0] as Pair<Double, CalculationType>
        val (discountInput, discountAsPercent) = it[1] as Pair<Double, Boolean>
        val (taxInput, taxAsPercent) = it[2] as Pair<Double, Boolean>
        val (tipInput, tipAsPercent) = it[3] as Pair<Double, Boolean>
        val taxTipped = it[4] as Boolean
        val discountsReduceTip = it[5] as Boolean

        when(billAmountType) {
            CalculationType.SUBTOTAL -> {
                val discountPct: Double
                val discountAmt: Double
                val taxPct: Double
                val taxAmt: Double
                val tipPct: Double
                val tipAmt: Double

                if (discountAsPercent) {
                    discountPct = discountInput
                    discountAmt = billAmountInput * discountInput / 100.0
                } else {
                    discountPct = rectifiedDivide(discountInput, billAmountInput) * 100.0
                    discountAmt = discountInput
                }

                val discountedSubtotal = max(billAmountInput - discountAmt, 0.0)

                if (taxAsPercent) {
                    taxPct = taxInput
                    taxAmt = discountedSubtotal * taxInput / 100.0
                } else {
                    taxPct = rectifiedDivide(taxInput, discountedSubtotal) * 100.0
                    taxAmt = taxInput
                }

                val taxedSubtotal = discountedSubtotal + taxAmt

                val baseValueToTip =
                    (if (discountsReduceTip) discountedSubtotal else billAmountInput) +
                            (if (taxTipped) taxAmt else 0.0)
                if (tipAsPercent) {
                    tipPct = tipInput
                    tipAmt = baseValueToTip * tipInput / 100.0
                } else {
                    tipPct = rectifiedDivide(tipInput, baseValueToTip) * 100.0
                    tipAmt = tipInput
                }

                subtotal.value = billAmountInput
                discountPercent.value = discountPct
                discountAmount.value = discountAmt
                subtotalWithDiscounts.value = discountedSubtotal
                taxPercent.value = taxPct
                taxAmount.value = taxAmt
                subtotalWithDiscountsAndTax.value = taxedSubtotal
                tipPercent.value = tipPct
                tipAmount.value = tipAmt
                total.value = taxedSubtotal + tipAmt
            }
            CalculationType.AFTER_DISCOUNT -> {
                val rawSubtotal: Double
                val discountPct: Double
                val discountAmt: Double
                val taxPct: Double
                val taxAmt: Double
                val tipPct: Double
                val tipAmt: Double

                if (discountAsPercent) {
                    discountPct = discountInput
                    rawSubtotal = rectifiedDivide(billAmountInput, 1 - discountPct/100.0)
                    discountAmt = rawSubtotal - billAmountInput
                } else {
                    discountAmt = discountInput
                    rawSubtotal = billAmountInput + discountAmt
                    discountPct = rectifiedDivide(discountInput, rawSubtotal) * 100.0
                }

                if (taxAsPercent) {
                    taxPct = taxInput
                    taxAmt = billAmountInput * taxInput / 100.0
                } else {
                    taxPct = rectifiedDivide(taxInput, billAmountInput) * 100.0
                    taxAmt = taxInput
                }

                val taxedSubtotal = billAmountInput + taxAmt

                val baseValueToTip =
                    (if (discountsReduceTip) billAmountInput else rawSubtotal) +
                            (if (taxTipped) taxAmt else 0.0)
                if (tipAsPercent) {
                    tipPct = tipInput
                    tipAmt = baseValueToTip * tipInput / 100.0
                } else {
                    tipPct = rectifiedDivide(tipInput, baseValueToTip) * 100.0
                    tipAmt = tipInput
                }

                subtotal.value = rawSubtotal
                discountPercent.value = discountPct
                discountAmount.value = discountAmt
                subtotalWithDiscounts.value = billAmountInput
                taxPercent.value = taxPct
                taxAmount.value = taxAmt
                subtotalWithDiscountsAndTax.value = taxedSubtotal
                tipPercent.value = tipPct
                tipAmount.value = tipAmt
                total.value = taxedSubtotal + tipAmt
            }
            CalculationType.AFTER_TAX -> {
                val rawSubtotal: Double
                val discountPct: Double
                val discountAmt: Double
                val discountedSubtotal: Double
                val taxPct: Double
                val taxAmt: Double
                val tipPct: Double
                val tipAmt: Double

                if (taxAsPercent) {
                    taxPct = taxInput
                    discountedSubtotal = billAmountInput / (1 + taxPct/100.0)
                    taxAmt = billAmountInput - discountedSubtotal
                } else {
                    taxAmt = taxInput
                    discountedSubtotal = max(billAmountInput - taxAmt, 0.0)
                    taxPct = rectifiedDivide(taxInput, discountedSubtotal) * 100.0
                }

                if (discountAsPercent) {
                    discountPct = discountInput
                    rawSubtotal = rectifiedDivide(discountedSubtotal, 1 - discountPct / 100.0)
                    discountAmt = rawSubtotal - discountedSubtotal
                } else {
                    discountAmt = discountInput
                    rawSubtotal = discountedSubtotal + discountAmt
                    discountPct = rectifiedDivide(discountInput, rawSubtotal) * 100.0
                }

                val baseValueToTip = (if (discountsReduceTip) discountedSubtotal else rawSubtotal) +
                        (if (taxTipped) taxAmt else 0.0)
                if (tipAsPercent) {
                    tipPct = tipInput
                    tipAmt = baseValueToTip * tipInput / 100.0
                } else {
                    tipAmt = tipInput
                    tipPct = rectifiedDivide(tipAmt, baseValueToTip) * 100.0
                }

                subtotal.value = rawSubtotal
                discountPercent.value = discountPct
                discountAmount.value = discountAmt
                subtotalWithDiscounts.value = discountedSubtotal
                taxPercent.value = taxPct
                taxAmount.value = taxAmt
                subtotalWithDiscountsAndTax.value = billAmountInput
                tipPercent.value = tipPct
                tipAmount.value = tipAmt
                total.value = billAmountInput + tipAmt
            }
            CalculationType.TOTAL -> {
                val rawSubtotal: Double
                val discountPct: Double
                val discountAmt: Double
                val discountedSubtotal: Double
                val taxPct: Double
                val taxAmt: Double
                val taxedSubtotal: Double
                val tipPct: Double
                val tipAmt: Double

                when {
                    discountsReduceTip && taxTipped -> {
                        // Tip applied to taxedSubtotal
                        if(tipAsPercent) {
                            tipPct = tipInput
                            taxedSubtotal = billAmountInput / (1 + tipPct/100.0)
                            tipAmt = billAmountInput - taxedSubtotal
                        } else {
                            tipAmt = tipInput
                            taxedSubtotal = max(billAmountInput - tipAmt, 0.0)
                            tipPct = rectifiedDivide(tipAmt, taxedSubtotal) * 100.0
                        }

                        if(taxAsPercent) {
                            taxPct = taxInput
                            discountedSubtotal = taxedSubtotal / (1 + taxPct/100.0)
                            taxAmt = discountedSubtotal * taxPct/100.0
                        } else {
                            taxAmt = taxInput
                            discountedSubtotal = max(taxedSubtotal - taxAmt, 0.0)
                            taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                        }

                        if(discountAsPercent) {
                            discountPct = discountInput
                            rawSubtotal = rectifiedDivide(discountedSubtotal, 1 - discountPct/100.0)
                            discountAmt = rawSubtotal * discountPct/100.0
                        } else {
                            discountAmt = discountInput
                            rawSubtotal = discountedSubtotal + discountAmt
                            discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                        }
                    }
                    discountsReduceTip && !taxTipped -> {
                        // Tip applied to discountedSubtotal
                        if(tipAsPercent){
                            tipPct = tipInput

                            if(taxAsPercent) {
                                taxPct = taxInput
                                discountedSubtotal = billAmountInput / (1 + (taxPct + tipPct)/100.0)
                                tipAmt = discountedSubtotal * tipPct/100.0
                                taxedSubtotal = billAmountInput - tipAmt
                                taxAmt = taxedSubtotal - discountedSubtotal
                            } else {
                                taxAmt = taxInput
                                discountedSubtotal = (billAmountInput - taxAmt) / (1 + tipPct/100.00)
                                taxedSubtotal = discountedSubtotal + taxAmt
                                tipAmt = billAmountInput - taxedSubtotal
                                taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                            }

                            if(discountAsPercent) {
                                discountPct = discountInput
                                rawSubtotal = rectifiedDivide(discountedSubtotal, 1 - discountPct/100.0)
                                discountAmt = rawSubtotal * discountPct/100.0
                            } else {
                                discountAmt = discountInput
                                rawSubtotal = discountedSubtotal + discountAmt
                                discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                            }
                        } else {
                            tipAmt = tipInput
                            taxedSubtotal = max(0.0, billAmountInput - tipAmt)

                            if(taxAsPercent) {
                                taxPct = taxInput
                                discountedSubtotal = taxedSubtotal / (1 + taxPct/100.0)
                                taxAmt = discountedSubtotal * taxPct/100.0
                            } else {
                                taxAmt = taxInput
                                discountedSubtotal = max(0.0, taxedSubtotal - taxAmt)
                                taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                            }

                            tipPct = rectifiedDivide(tipAmt, discountedSubtotal) * 100.0

                            if(discountAsPercent) {
                                discountPct = discountInput
                                rawSubtotal = rectifiedDivide(discountedSubtotal, 1 - discountPct/100.0)
                                discountAmt = rawSubtotal * discountPct/100.0
                            } else {
                                discountAmt = discountInput
                                rawSubtotal = discountedSubtotal + discountAmt
                                discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                            }
                        }

                    }
                    !discountsReduceTip && taxTipped -> {
                        // Tip applied to rawSubtotal + taxAmt
                        if(tipAsPercent) {
                            tipPct = tipInput

                            if(taxAsPercent) {
                                taxPct = taxInput

                                if(discountAsPercent) {
                                    discountPct = discountInput
                                    rawSubtotal = 100.0*rectifiedDivide(billAmountInput, (100.0 - discountPct)*
                                            (1 + taxPct/100.0 + tipPct/100.0 + tipPct/100.0*taxPct/100.0)
                                            - tipPct*discountPct/100.0)
                                    discountAmt = rawSubtotal * discountPct/100.0

                                    discountedSubtotal = if(discountPct == 100.0) {
                                        0.0
                                    } else {
                                        max(0.0, rawSubtotal - discountAmt)
                                    }
                                    taxAmt = discountedSubtotal * taxPct/100.0
                                    taxedSubtotal = discountedSubtotal + taxAmt
                                    tipAmt = (rawSubtotal + taxAmt) * tipPct/100.0
                                } else {
                                    discountAmt = discountInput
                                    discountedSubtotal = (billAmountInput + tipPct/100.0*discountAmt) /
                                            (1+ taxPct/100.0 + tipPct/100.0 + tipPct/100.0*taxPct/100.0)
                                    rawSubtotal = discountedSubtotal + discountAmt
                                    discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                                    taxAmt = discountedSubtotal * taxPct/100.0
                                    taxedSubtotal = discountedSubtotal + taxAmt
                                    tipAmt = (rawSubtotal + taxAmt) * tipPct/100.0
                                }
                            } else {
                                taxAmt = taxInput

                                if(discountAsPercent) {
                                    discountPct = discountInput
                                    rawSubtotal = rectifiedDivide(billAmountInput - taxAmt*(1+tipPct/100.0), 1 - discountPct/100.0 + tipPct*100.0 - 2*tipPct*100.0*discountPct/100.0)
                                    discountAmt = rawSubtotal * discountPct / 100.0
                                    discountedSubtotal = max(0.0, rawSubtotal - discountAmt)
                                    taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                                    taxedSubtotal = discountedSubtotal + taxAmt
                                    tipAmt = 0.01*tipPct * (rawSubtotal + taxAmt)
                                } else {
                                    discountAmt = discountInput
                                    discountedSubtotal = rectifiedDivide(billAmountInput - taxAmt*(1 + tipPct/100.0) + discountAmt*tipPct/100.0, 1 + tipPct/100.0)
                                    rawSubtotal = discountedSubtotal + discountAmt
                                    discountPct = rectifiedDivide(discountAmt, rawSubtotal * 100.0)
                                    taxedSubtotal = discountedSubtotal + taxAmt
                                    taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                                    tipAmt = 0.01*tipPct * (rawSubtotal + taxAmt)
                                }
                            }
                        } else {
                            tipAmt = tipInput
                            taxedSubtotal = max(0.0, billAmountInput - tipAmt)

                            if(taxAsPercent) {
                                taxPct = taxInput
                                discountedSubtotal = taxedSubtotal / (1 + taxPct/100.0)
                                taxAmt = discountedSubtotal * taxPct/100.0
                            } else {
                                taxAmt = taxInput
                                discountedSubtotal = max(0.0, taxedSubtotal - taxAmt)
                                taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                            }

                            if(discountAsPercent) {
                                discountPct = discountInput
                                rawSubtotal = rectifiedDivide(discountedSubtotal, 1 - discountPct/100.0)
                                discountAmt = rawSubtotal * discountPct/100.0
                            } else {
                                discountAmt = discountInput
                                rawSubtotal = discountedSubtotal + discountAmt
                                discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                            }

                            tipPct = rectifiedDivide(tipAmt, rawSubtotal + taxAmt) * 100.0
                        }
                    }
                    else -> {
                        // Tip applied to rawSubtotal
                        if(tipAsPercent) {
                            tipPct = tipInput

                            if(taxAsPercent) {
                                taxPct = taxInput

                                if(discountAsPercent) {
                                    discountPct = discountInput
                                    rawSubtotal = 100.0*rectifiedDivide(billAmountInput, 100.0 - discountPct + taxPct - taxPct*discountPct/100.0 + tipPct)
                                    discountAmt = rawSubtotal * discountPct/100.0
                                } else {
                                    discountAmt = discountInput
                                    rawSubtotal = (billAmountInput + discountAmt * (1 + taxPct/100.0)) / (1 + taxPct/100.0 + tipPct/100.0)
                                    discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                                }

                                discountedSubtotal = max(0.0, rawSubtotal - discountAmt)
                                taxAmt = discountedSubtotal * taxPct/100.0
                                taxedSubtotal = discountedSubtotal + taxAmt
                                tipAmt = max(0.0, billAmountInput - taxedSubtotal)
                            } else {
                                taxAmt = taxInput

                                if(discountAsPercent) {
                                    discountPct = discountInput
                                    rawSubtotal = rectifiedDivide(billAmountInput - taxAmt, 1 + tipPct/100.0 - discountPct/100.0)
                                    discountAmt = discountPct/100.0 * rawSubtotal
                                } else {
                                    discountAmt = discountInput
                                    rawSubtotal = max(0.0, (billAmountInput + discountAmt - taxAmt) / (1 + tipPct/100.0))
                                    discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                                }

                                discountedSubtotal = max(0.0, rawSubtotal - discountAmt)
                                taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                                taxedSubtotal = discountedSubtotal + taxAmt
                                tipAmt = max(0.0, billAmountInput - taxedSubtotal)
                            }
                        } else {
                            tipAmt = tipInput
                            taxedSubtotal = max(0.0, billAmountInput - tipAmt)

                            if(taxAsPercent) {
                                taxPct = taxInput
                                discountedSubtotal = taxedSubtotal / (1 + taxPct/100.0)
                                taxAmt = discountedSubtotal * taxPct/100.0
                            } else {
                                taxAmt = taxInput
                                discountedSubtotal = max(0.0, taxedSubtotal - taxAmt)
                                taxPct = rectifiedDivide(taxAmt, discountedSubtotal) * 100.0
                            }

                            if(discountAsPercent) {
                                discountPct = discountInput
                                rawSubtotal = rectifiedDivide(discountedSubtotal, 1 - discountPct/100.0)
                                discountAmt = rawSubtotal * discountPct/100.0
                            } else {
                                discountAmt = discountInput
                                rawSubtotal = discountedSubtotal + discountAmt
                                discountPct = rectifiedDivide(discountAmt, rawSubtotal) * 100.0
                            }

                            tipPct = rectifiedDivide(tipAmt, rawSubtotal) * 100.0
                        }
                    }
                }

                subtotal.value = max(0.0, rawSubtotal)
                discountPercent.value = max(0.0, discountPct)
                discountAmount.value = max(0.0, discountAmt)
                subtotalWithDiscounts.value = max(0.0, discountedSubtotal)
                taxPercent.value = max(0.0, taxPct)
                taxAmount.value = max(0.0, taxAmt)
                subtotalWithDiscountsAndTax.value = max(0.0, taxedSubtotal)
                tipPercent.value = max(0.0, tipPct)
                tipAmount.value = max(0.0, tipAmt)
                total.value = max(0.0, billAmountInput)
            }
            else -> { /* Other CalculationTypes should not be received */ }
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, Unit)

    private val subtotal = MutableStateFlow(0.0)
    private val discountPercent = MutableStateFlow(0.0)
    private val discountAmount = MutableStateFlow(0.0)
    private val subtotalWithDiscounts = MutableStateFlow(0.0)
    private val taxPercent = MutableStateFlow(StoredPreference.defaultTaxPercent.value.toDouble())
    private val taxAmount = MutableStateFlow(0.0)
    private val subtotalWithDiscountsAndTax = MutableStateFlow(0.0)
    private val tipPercent = MutableStateFlow(StoredPreference.defaultTipPercent.value.toDouble())
    private val tipAmount = MutableStateFlow(0.0)
    private val total = MutableStateFlow(0.0)
    private val warningMessageEventMutable = MutableLiveData<Event<String>>()

    val subtotalString: LiveData<String> = subtotal.map { currencyFormatter.format(it) }.asLiveData()
    val discountPercentString: LiveData<String> = discountPercent.map {
        if (it > 100.0) { greaterThan100Pct } else { percentFormatter.format(0.01*it) }
    }.asLiveData()
    val discountAmountString: LiveData<String> = discountAmount.map { currencyFormatter.format( it) }.asLiveData()
    val afterDiscountString: LiveData<String> = subtotalWithDiscounts.map { currencyFormatter.format( it) }.asLiveData()
    val taxPercentString: LiveData<String> = combine(taxPercent, mTaxInput) { taxPct, taxInput ->
        if (taxPct > 100.0 && !taxInput.second) { greaterThan100Pct } else { percentFormatter.format(0.01*taxPct) }
    }.asLiveData()
    val taxAmountString: LiveData<String> = taxAmount.map { currencyFormatter.format( it) }.asLiveData()
    val afterTaxString: LiveData<String> = subtotalWithDiscountsAndTax.map { currencyFormatter.format(it) }.asLiveData()
    val tipPercentString: LiveData<String> = combine(tipPercent, mTipInput) { tipPct, tipInput ->
        if (tipPct > 100.0 && !tipInput.second) { greaterThan100Pct } else { percentFormatter.format(0.01*tipPct) }
    }.asLiveData()
    val tipAmountString: LiveData<String> = tipAmount.map { currencyFormatter.format( it) }.asLiveData()
    val totalString: LiveData<String> = total.map { currencyFormatter.format( it) }.asLiveData()
    val taxTipped: LiveData<Boolean> = mTaxTipped.asLiveData()
    val discountReducesTip: LiveData<Boolean> = mDiscountReducesTip.asLiveData()
    val warningMessageEvent: LiveData<Event<String>> = warningMessageEventMutable

    private fun rectifiedDivide(dividend: Double, divisor: Double): Double = when {
        dividend <= 0.0 -> 0.0
        divisor <= 0.0 -> Double.POSITIVE_INFINITY
        else -> dividend / divisor
    }

    fun setSubtotal(amount: Double) {
        if (!mDiscountInput.value.second && amount < mDiscountInput.value.first) {
            warningMessageEventMutable.value = Event(warningDiscountExceedsSubtotal)
            setDiscountAmount(amount)
        }
        mBillAmountInput.value = Pair(amount, CalculationType.SUBTOTAL)
    }
    fun setDiscountPercent(percent: Double) {
        if (percent > 100.0) {
            warningMessageEventMutable.value = Event(warningDiscountExceeds100Pct)
            mDiscountInput.value = Pair(100.0, true)
        } else {
            mDiscountInput.value = Pair(percent, true)
        }
    }
    fun setDiscountAmount(amount: Double) {
        if (mBillAmountInput.value.second == CalculationType.SUBTOTAL && amount > mBillAmountInput.value.first) {
            warningMessageEventMutable.value = Event(warningDiscountExceedsSubtotal)
            mDiscountInput.value = Pair(mBillAmountInput.value.first, false)
        } else {
            mDiscountInput.value = Pair(amount, false)
        }
    }
    fun setAfterDiscount(amount: Double) { mBillAmountInput.value = Pair(amount, CalculationType.AFTER_DISCOUNT) }
    fun setTaxPercent(percent: Double) { mTaxInput.value = Pair(percent, true) }
    fun setTaxAmount(amount: Double) {
        if (mBillAmountInput.value.second == CalculationType.AFTER_TAX && amount > mBillAmountInput.value.first) {
            warningMessageEventMutable.value = Event(warningTaxExceedsAfterTax)
            mTaxInput.value = Pair(mBillAmountInput.value.first, false)
        } else if(mBillAmountInput.value.second == CalculationType.TOTAL) {
            if (amount > mBillAmountInput.value.first) {
                // Constrain tax amount to be equal to the total
                warningMessageEventMutable.value = Event(warningTaxExceedsTotal)
                mTaxInput.value = Pair(mBillAmountInput.value.first, false)

                // If tip is a fixed amount, set this to zero (this will also make the subtotal zero)
                if (!mTipInput.value.second) {
                    mTipInput.value = Pair(0.0, false)
                }
            } else if(!mTipInput.value.second && amount > mBillAmountInput.value.first - mTipInput.value.first) {
                // Accept tax input, but constrain the tip amount so that aftertax + tip < total
                warningMessageEventMutable.value = Event(warningTipPlusTaxExceedsTotal)
                mTaxInput.value = Pair(amount, false)
                mTipInput.value = Pair(mBillAmountInput.value.first - amount, false)
            } else if (mTipInput.value.second) {
                if(mTaxTipped.value && amount > mBillAmountInput.value.first/(0.01*mTipInput.value.first + 1.0)) {
                    warningMessageEventMutable.value = Event(warningTipPlusTaxExceedsTotal)
                    mTaxInput.value = Pair(mBillAmountInput.value.first/(0.01*mTipInput.value.first + 1.0), false)
                } else if(!mTaxTipped.value) {
                    mTaxInput.value = Pair(amount, false) // TODO
                } else {
                    mTaxInput.value = Pair(amount, false)
                }
            }
            else {
                mTaxInput.value = Pair(amount, false)
            }
        }
        else {
            mTaxInput.value = Pair(amount, false)
        }
    }
    fun setAfterTax(amount: Double) {
        if (!mTaxInput.value.second && amount < mTaxInput.value.first) {
            warningMessageEventMutable.value = Event(warningTaxExceedsAfterTax)
            setTaxAmount(amount)
        }
        mBillAmountInput.value = Pair(amount, CalculationType.AFTER_TAX)
    }
    fun setTipPercent(percent: Double) { mTipInput.value = Pair(percent, true) }
    fun setTipAmount(amount: Double) {
        if (mBillAmountInput.value.second == CalculationType.TOTAL) {
            if (amount > mBillAmountInput.value.first) {
                // Constrain tip amount to be equal to the total
                warningMessageEventMutable.value = Event(warningTipExceedsTotal)
                mTipInput.value = Pair(mBillAmountInput.value.first, false)

                // If tax is a fixed amount, set this to zero
                if (!mTaxInput.value.second) {
                    mTaxInput.value = Pair(0.0, false)
                }
            } else if(!mTaxInput.value.second && amount > mBillAmountInput.value.first - mTaxInput.value.first) {
                // Accept tip input, but constrain the tax amount so that aftertax + tip < total
                warningMessageEventMutable.value = Event(warningTipPlusTaxExceedsTotal)
                mTipInput.value = Pair(amount, false)
                mTaxInput.value = Pair(mBillAmountInput.value.first - amount, false)
            }
            else {
                mTipInput.value = Pair(amount, false)
            }
        }
        else {
            mTipInput.value = Pair(amount, false)
        }
    }
    fun setTotal(amount: Double) {
        mBillAmountInput.value = Pair(amount, CalculationType.TOTAL)
    }
    fun toggleTipTaxed() { mTaxTipped.value = !mTaxTipped.value }
    fun toggleDiscountsReduceTip() { mDiscountReducesTip.value = !mDiscountReducesTip.value }

    fun reset() {
        mBillAmountInput.value = Pair(0.0, CalculationType.SUBTOTAL)
        mDiscountInput.value = Pair(0.0, false)
        mTaxInput.value = Pair(StoredPreference.defaultTaxPercent.value.toDouble(), true)
        mTipInput.value = Pair(StoredPreference.defaultTipPercent.value.toDouble(), true)
        mTaxTipped.value = StoredPreference.taxIsTipped.value
        mDiscountReducesTip.value = StoredPreference.discountsReduceTip.value
    }
}
