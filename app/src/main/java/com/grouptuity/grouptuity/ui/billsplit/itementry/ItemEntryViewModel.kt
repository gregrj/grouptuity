package com.grouptuity.grouptuity.ui.billsplit.itementry

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.NumberFormat

class ItemEntryViewModel(app: Application): UIViewModel(app) {
    companion object {
        data class ToolBarState(val title: String, val navIconVisible: Boolean, val nameEditVisible: Boolean, val tertiaryBackground: Boolean)
    }

    private val formatter = NumberFormat.getCurrencyInstance()
    private val calculator = CalculatorData(acceptValueCallback = { hasUntouchedPriorSelections = false })

    // Item Name
    private val itemNameInput = MutableStateFlow<String?>(null)
    val hasItemNameInput get() = itemNameInput.value != null

    // Diner selection
    private val diners: StateFlow<Array<Diner>> = repository.diners.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyArray())
    private val selectionSet = mutableSetOf<Long>()
    private val _selections = MutableStateFlow(selectionSet.toSet())
    private val selectionCount: Flow<Int> = _selections.mapLatest { it.size }
    private val areAllDinersSelected: Flow<Boolean> = combine(selectionCount, repository.diners) { selectionCount, diners -> selectionCount == diners.size }
    private var hasUntouchedPriorSelections = false

    // UI State
    private var creatingNewItem = true
    private val pauseDinerRefresh: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val editingName = MutableStateFlow(false)

    // Live Data Output
    val dinerData: LiveData<List<Pair<Diner, String>>> = combineTransform(repository.diners, repository.dinerSubtotals, pauseDinerRefresh) { diners, subtotals, pause ->
        if(!pause) {
            // Diners paired with their individual subtotals as currency strings
            emit(diners.map { diner ->
                diner to formatter.format(subtotals.getOrDefault(diner.id, 0.0))
            })
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val itemName: LiveData<String> = repository.items.combine(itemNameInput) { items, nameInput ->
        nameInput ?: getApplication<Application>().resources.getString(R.string.itementry_toolbar_item_number) + (items.size + 1)
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val selections: LiveData<Set<Long>> = _selections.withOutputSwitch(isOutputFlowing).asLiveData()
    val toolBarState: LiveData<ToolBarState> = combine(calculator.isNumberPadVisible, editingName, itemName.asFlow(), selectionCount, areAllDinersSelected) {
            calcVisible, editing, name, count, allSelected ->
        if(!calcVisible && count > 0) {
            // Selecting diners and has at least one selection
            ToolBarState(
                title = if(allSelected) {
                    getApplication<Application>().resources.getQuantityString(R.plurals.itementry_num_diners_selected_everyone, count, count)
                } else {
                    getApplication<Application>().resources.getQuantityString(R.plurals.itementry_num_diners_selected, count, count)
                },
                navIconVisible = true,
                nameEditVisible = false,
                tertiaryBackground = true
            )
        } else {
            // Calculator is visible or no diners are selected
            ToolBarState(title = name, navIconVisible = !editing, nameEditVisible = calcVisible, tertiaryBackground = false)
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val selectAllButtonDisabled: LiveData<Boolean> = areAllDinersSelected.withOutputSwitch(isOutputFlowing).asLiveData()
    val editNameShimVisible: LiveData<Boolean> = editingName.withOutputSwitch(isOutputFlowing).asLiveData()

    val formattedPrice: LiveData<String> = calculator.displayValue.withOutputSwitch(isOutputFlowing).asLiveData()
    val numberPadVisible: LiveData<Boolean> = calculator.isNumberPadVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceBackspaceButtonVisible: LiveData<Boolean> = calculator.backspaceButtonVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceEditButtonVisible: LiveData<Boolean> = calculator.editButtonVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceZeroButtonEnabled: LiveData<Boolean> = calculator.zeroButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceDecimalButtonEnabled: LiveData<Boolean> = calculator.decimalButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceAcceptButtonEnabled: LiveData<Boolean> = calculator.acceptButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()

    override fun notifyTransitionFinished() {
        super.notifyTransitionFinished()

        // For better performance, allow the diners to update only after transition finishes
        if(creatingNewItem) {
            pauseDinerRefresh.value = false
        }
    }

    fun initializeForItem(item: Item?) {
        unFreezeOutput()

        selectionSet.clear()
        editingName.value = false

        if(item == null) {
            // New item
            creatingNewItem = true
            calculator.initialize(null, false, showNumberPad = true)
            itemNameInput.value = null
            hasUntouchedPriorSelections = false
        } else {
            // Editing existing item
            creatingNewItem = false
            pauseDinerRefresh.value = false
            calculator.initialize(item.price, false, showNumberPad = false)
            itemNameInput.value = item.name
            selectionSet.addAll(item.diners)
            hasUntouchedPriorSelections = selectionSet.isNotEmpty()
        }

        _selections.value = selectionSet.toSet()
    }

    fun handleOnBackPressed(): Boolean? {
        when {
            isInputLocked.value -> { }
            numberPadVisible.value == true -> {
                when {
                    editingName.value -> { stopNameEdit() }
                    calculator.tryRevertToLastValue() -> {  }
                    else -> { return false }
                }
            }
            hasUntouchedPriorSelections || selectionSet.isEmpty() -> { return false }
            else -> { clearDinerSelections() }
        }
        return null
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

    fun acceptItemNameInput(name: String) {
        itemNameInput.value = name
        stopNameEdit()
    }

    fun isDinerSelected(diner: Diner) = selectionSet.contains(diner.id)
    fun toggleDinerSelection(diner: Diner) {
        hasUntouchedPriorSelections = false

        if(isDinerSelected(diner)) {
            selectionSet.remove(diner.id)
        } else {
            selectionSet.add(diner.id)
        }
        _selections.value = selectionSet.toSet()
    }
    fun selectAllDiners() {
        hasUntouchedPriorSelections = false

        diners.value.apply {
            selectionSet.clear()
            selectionSet.addAll(this.map { it.id })
            _selections.value = selectionSet.toSet()
        }
    }
    fun clearDinerSelections() {
        hasUntouchedPriorSelections = false
        selectionSet.clear()
        _selections.value = selectionSet.toSet()
    }

    fun addItemToBill() {
        //TODO handle re-editing existing items
        selections.value?.apply {
            val price = calculator.numericalValue.value ?: 0.0
            val itemDiners = diners.value.filter { diner -> this.contains(diner.id) }

            repository.createItem(price, itemName.value ?: "Item", itemDiners)
        }
    }
}