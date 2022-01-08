package com.grouptuity.grouptuity.ui.billsplit.debtentry

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.text.NumberFormat


class DebtEntryViewModel(app: Application): UIViewModel(app) {
    companion object {
        data class ToolBarState(val title: String, val navIconVisible: Boolean, val nameEditVisible: Boolean, val tertiaryBackground: Boolean)
        data class DinerDatapoint(val diner: Diner, val isDebtor: Boolean, val isCreditor: Boolean, val message: String?)
    }

    private val currencyFormatter = NumberFormat.getCurrencyInstance()
    private val calculator = CalculatorData(CalculationType.DEBT_AMOUNT)
    var launchingDiner: Diner? = null
        private set

    // Debt Name
    private val debtNameInput = MutableStateFlow<String?>(null)
    val hasDebtNameInput get() = debtNameInput.value != null

    // Debtor/Creditor selection
    private val diners: StateFlow<List<Diner>> = repository.diners.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyList())
    private val creditorSelectionSet = mutableSetOf<Diner>()
    private val _creditorSelections = MutableStateFlow(creditorSelectionSet.toSet())
    private val creditorSelectionCount: Flow<Int> = _creditorSelections.mapLatest { it.size }
    private val debtorSelectionSet = mutableSetOf<Diner>()
    private val _debtorSelections = MutableStateFlow(debtorSelectionSet.toSet())
    private val debtorSelectionCount: Flow<Int> = _debtorSelections.mapLatest { it.size }

    // UI State
    private val editingName = MutableStateFlow(false)
    private var hasEditedCreditorSelections = false
    private var hasEditedDebtorSelections = false
    private var lastEditWasCreditor = true
    private val areAllCreditorsSelected: Flow<Boolean> = combine(creditorSelectionCount, repository.diners) { selectionCount, diners -> selectionCount == diners.size }
    private val areAllDebtorsSelected: Flow<Boolean> = combine(debtorSelectionCount, repository.diners) { selectionCount, diners -> selectionCount == diners.size }
    val isLaunchingDinerPresent: Boolean get() = launchingDiner == null || creditorSelectionSet.contains(launchingDiner) || debtorSelectionSet.contains(launchingDiner)

    // Live Data Output
    val dinerData: LiveData<List<DinerDatapoint>> = combine(repository.diners, _creditorSelections, _debtorSelections, calcData.numericalValue) { diners, creditors, debtors, amount ->
        val debtShare = amount.divide(BigDecimal(debtors.size))
        val creditShare = amount.divide(BigDecimal(creditors.size))

        diners.map { diner ->
            val isCreditor = creditors.contains(diner)
            val isDebtor = debtors.contains(diner)

            DinerDatapoint(diner, isDebtor, isCreditor, when {
                isCreditor && isDebtor -> {
                    when {
                        debtors.size > creditors.size -> {
                            context.getString(R.string.debtentry_creditor_message, currencyFormatter.format(creditShare - debtShare))
                        }
                        debtors.size < creditors.size -> {
                            context.getString(R.string.debtentry_debtor_message, currencyFormatter.format(debtShare - creditShare))
                        }
                        else -> {
                            context.getString(R.string.debtentry_dual_nothing_message)
                        }
                    }
                }
                isCreditor -> {
                    context.getString(R.string.debtentry_creditor_message, currencyFormatter.format(creditShare))
                }
                isDebtor -> {
                    context.getString(R.string.debtentry_debtor_message, currencyFormatter.format(debtShare))
                }
                else -> {
                    null
                }
            })
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val debtName: LiveData<String> = repository.debts.combine(debtNameInput) { debts, nameInput ->
        nameInput ?: getApplication<Application>().resources.getString(R.string.debtentry_toolbar_debt_number) + (debts.size + 1)
    }.asLiveData(isOutputLocked)
    val areDebtInputsValid: LiveData<Boolean> = combine(creditorSelectionCount, debtorSelectionCount) { creditorCount, debtorCount ->
        creditorCount > 0 && debtorCount > 0
    }.asLiveData(isOutputLocked)

    val toolBarState: LiveData<ToolBarState> = combine(
        calcData.isNumberPadVisible,
        editingName,
        debtName.asFlow(),
        debtorSelectionCount,
        creditorSelectionCount
    ) { calcVisible, editing, name, debtorCount, creditorCount ->
        when {
            calcVisible -> {
                ToolBarState(
                    title = name,
                    navIconVisible = !editing,
                    nameEditVisible = calcVisible,
                    tertiaryBackground = false)
            }
            debtorCount == 0 && creditorCount == 0 -> {
                ToolBarState(
                    title = getApplication<Application>().resources.getString(R.string.debtentry_toolbar_select_both),
                    navIconVisible = true,
                    nameEditVisible = false,
                    tertiaryBackground = false
                )
            }
            debtorCount > 0 && creditorCount == 0 -> {
                ToolBarState(
                    title = getApplication<Application>().resources.getString(R.string.debtentry_toolbar_select_creditor),
                    navIconVisible = true,
                    nameEditVisible = false,
                    tertiaryBackground = false
                )
            }
            debtorCount == 0 && creditorCount > 0 -> {
                ToolBarState(
                    title = getApplication<Application>().resources.getString(R.string.debtentry_toolbar_select_debtor),
                    navIconVisible = true,
                    nameEditVisible = false,
                    tertiaryBackground = false
                )
            }
            else -> {
                ToolBarState(
                    title = when {
                        debtorCount == 1 && creditorCount == 1 -> {
                            val debtorName = debtorSelectionSet.toList().getOrNull(0)?.name ?: getApplication<Application>().resources.getString(R.string.debtentry_1_diner_paying)
                            val creditorName = creditorSelectionSet.toList().getOrNull(0)?.name ?: getApplication<Application>().resources.getString(R.string.debtentry_paying_1_diner)
                            getApplication<Application>().resources.getString(R.string.debtentry_toolbar_single_paying_single, debtorName, creditorName)
                        }
                        debtorCount == 1 && creditorCount > 1 -> {
                            val debtorName = debtorSelectionSet.toList().getOrNull(0)?.name ?: getApplication<Application>().resources.getString(R.string.debtentry_1_diner_paying)
                            getApplication<Application>().resources.getQuantityString(R.plurals.debtentry_toolbar_single_paying_multiple, creditorCount, debtorName, creditorCount)
                        }
                        debtorCount > 1 && creditorCount == 1 -> {
                            val creditorName = creditorSelectionSet.toList().getOrNull(0)?.name ?: getApplication<Application>().resources.getString(R.string.debtentry_paying_1_diner)
                            getApplication<Application>().resources.getQuantityString(R.plurals.debtentry_toolbar_multiple_paying_single, debtorCount, debtorCount, creditorName)
                        }
                        else -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.debtentry_toolbar_multiple_paying,
                                debtorCount,
                                debtorCount) + " " +
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.debtentry_toolbar_paying_multiple,
                                creditorCount,
                                creditorCount)
                        }
                    },
                    navIconVisible = true,
                    nameEditVisible = false,
                    tertiaryBackground = true
                )
            }
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val editNameShimVisible: LiveData<Boolean> = editingName.withOutputSwitch(isOutputFlowing).asLiveData()

    val formattedPrice: LiveData<String> = calculator.displayValue.withOutputSwitch(isOutputFlowing).asLiveData()
    val numberPadVisible: LiveData<Boolean> = calculator.isNumberPadVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceBackspaceButtonVisible: LiveData<Boolean> = calculator.backspaceButtonVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceEditButtonVisible: LiveData<Boolean> = calculator.editButtonVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceZeroButtonEnabled: LiveData<Boolean> = calculator.zeroButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceDecimalButtonEnabled: LiveData<Boolean> = calculator.decimalButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceAcceptButtonEnabled: LiveData<Boolean> = calculator.acceptButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()

    fun initializeForDiner(diner: Diner?) {
        unFreezeOutput()

        launchingDiner = diner

        creditorSelectionSet.clear()
        debtorSelectionSet.clear()

        if(input != null) {
            debtorSelectionSet.add(input)
        }

        editingName.value = false
        hasEditedCreditorSelections = false
        hasEditedDebtorSelections = false
        lastEditWasCreditor = false

        calculator.reset(CalculationType.ITEM_PRICE, null, showNumberPad = true)
        debtNameInput.value = null

        _creditorSelections.value = creditorSelectionSet.toSet()
        _debtorSelections.value = debtorSelectionSet.toSet()
    }

    fun handleOnBackPressed(): Boolean = when {
        isInputLocked.value -> { false }
        numberPadVisible.value == true -> {
            when {
                editingName.value -> {
                    stopNameEdit()
                    false
                }
                calculator.tryRevertToLastValue() -> { false }
                else -> { true }
            }
        }
        !hasEditedCreditorSelections && debtorSelectionSet.isEmpty() -> {
            true
        }
        !hasEditedDebtorSelections && creditorSelectionSet.isEmpty() -> {
            true
        }
        lastEditWasCreditor -> {
            if (creditorSelectionSet.isEmpty()) {
                if (debtorSelectionSet.isEmpty()) {
                    true
                } else {
                    clearDebtorSelections()
                    false
                }
            } else {
                clearCreditorSelections()
                false
            }
        }
        else -> {
            if (debtorSelectionSet.isEmpty()) {
                if (creditorSelectionSet.isEmpty()) {
                    true
                } else {
                    clearCreditorSelections()
                    false
                }
            } else {
                clearDebtorSelections()
                false
            }
        }
    }

    fun openCalculator() {
        calculator.clearValue()
        calculator.showNumberPad()
    }
    fun addDigitToPrice(digit: Char) = calculator.addDigit(digit)
    fun addDecimalToPrice() = calculator.addDecimal()
    fun removeDigitFromPrice() = calculator.removeDigit()
    fun resetPrice() = calculator.clearValue()
    fun acceptPrice() = calculator.tryAcceptValue()

    fun startNameEdit() { editingName.value = true }
    fun stopNameEdit() {
        if(editingName.value)
            editingName.value = false
    }

    fun acceptDebtNameInput(name: String) {
        debtNameInput.value = name
        stopNameEdit()
    }

    fun toggleCreditorSelection(diner: Diner) {
        hasEditedCreditorSelections = true
        lastEditWasCreditor = true

        if(creditorSelectionSet.contains(diner)) {
            creditorSelectionSet.remove(diner)
        } else {
            creditorSelectionSet.add(diner)
        }
        _creditorSelections.value = creditorSelectionSet.toSet()
    }
    fun selectAllCreditors() {
        hasEditedCreditorSelections = true
        lastEditWasCreditor = true

        diners.value.apply {
            creditorSelectionSet.clear()
            creditorSelectionSet.addAll(this)
            _creditorSelections.value = creditorSelectionSet.toSet()
        }
    }
    fun clearCreditorSelections() {
        hasEditedCreditorSelections = true
        lastEditWasCreditor = true

        creditorSelectionSet.clear()
        _creditorSelections.value = creditorSelectionSet.toSet()
    }

    fun toggleDebtorSelection(diner: Diner) {
        hasEditedDebtorSelections = true
        lastEditWasCreditor = false

        if(debtorSelectionSet.contains(diner)) {
            debtorSelectionSet.remove(diner)
        } else {
            debtorSelectionSet.add(diner)
        }
        _debtorSelections.value = debtorSelectionSet.toSet()
    }
    fun selectAllDebtors() {
        hasEditedDebtorSelections = true
        lastEditWasCreditor = false

        diners.value.apply {
            debtorSelectionSet.clear()
            debtorSelectionSet.addAll(this)
            _debtorSelections.value = debtorSelectionSet.toSet()
        }
    }
    fun clearDebtorSelections() {
        hasEditedDebtorSelections = true
        lastEditWasCreditor = false

        debtorSelectionSet.clear()
        _debtorSelections.value = debtorSelectionSet.toSet()
    }

    fun addDebtToBill() {
        repository.createNewDebt(
            calcData.numericalValue.value.toString(),
            debtName.value ?: "Debt",
            debtorSelectionSet,
            creditorSelectionSet)
        }
}
