package com.grouptuity.grouptuity.ui.billsplit.itementry

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.Event
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val closeFragmentEventMutable = MutableLiveData<Event<Boolean>>()
    private val _editingName = MutableStateFlow(false)
    val editingName: StateFlow<Boolean> = _editingName

    // Live Data Output
    val closeFragmentEvent: LiveData<Event<Boolean>> = closeFragmentEventMutable
    val dinerData: LiveData<List<Pair<Diner, String>>> = combine(repository.diners, repository.dinerSubtotals) { diners, subtotals ->
        // Diners paired with their individual subtotals as currency strings
        diners.map { diner ->
            diner to formatter.format(subtotals.getOrDefault(diner.id, 0.0))
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val itemName: LiveData<String> = repository.items.combine(itemNameInput) { items, nameInput ->
        nameInput ?: getApplication<Application>().resources.getString(R.string.itementry_toolbar_item_number) + (items.size + 1)
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val selections: LiveData<Set<Long>> = _selections.withOutputSwitch(isOutputFlowing).asLiveData()
    val toolBarState: LiveData<ToolBarState> = combine(calculator.numberPadVisible, _editingName, itemName.asFlow(), selectionCount, areAllDinersSelected) {
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
    val editNameShimVisible: LiveData<Boolean> = _editingName.withOutputSwitch(isOutputFlowing).asLiveData()

    val formattedPrice: LiveData<String> = calculator.displayValue.withOutputSwitch(isOutputFlowing).asLiveData()
    val numberPadVisible: LiveData<Boolean> = calculator.numberPadVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceBackspaceButtonVisible: LiveData<Boolean> = calculator.backspaceButtonVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceEditButtonVisible: LiveData<Boolean> = calculator.editButtonVisible.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceZeroButtonEnabled: LiveData<Boolean> = calculator.zeroButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceDecimalButtonEnabled: LiveData<Boolean> = calculator.decimalButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()
    val priceAcceptButtonEnabled: LiveData<Boolean> = calculator.acceptButtonEnabled.withOutputSwitch(isOutputFlowing).asLiveData()

    fun initializeForItem(item: Item?) {
        selectionSet.clear()
        _editingName.value = false

        if(item == null) {
            // New item
            calculator.initialize(null, false, showNumberPad = true)
            itemNameInput.value = null
            hasUntouchedPriorSelections = false
        } else {
            // Editing existing item
            calculator.initialize(item.price.toString(), false, showNumberPad = false)
            itemNameInput.value = item.name
            selectionSet.addAll(item.diners)
            hasUntouchedPriorSelections = selectionSet.isNotEmpty()
        }

        _selections.value = selectionSet.toSet()
    }

    fun handleOnBackPressed() {
        when {
            isInputLocked.value -> { return }
            numberPadVisible.value == true -> {
                when {
                    _editingName.value -> { stopNameEdit() }
                    calculator.tryRevertToLastValue() -> {  }
                    else -> { closeFragmentEventMutable.value = Event(false) }
                }
            }
            hasUntouchedPriorSelections || selectionSet.isEmpty() -> { closeFragmentEventMutable.value = Event(false) }
            else -> { clearDinerSelections() }
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

    fun startNameEdit() { _editingName.value = true }
    fun stopNameEdit() {
        if(_editingName.value)
            _editingName.value = false
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