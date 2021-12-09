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
    private val calculatorInputLock = MutableStateFlow(false).also { addInputLock(it) }
    val calculatorData = CalculatorData(
        CalculationType.ITEM_PRICE,
        calculatorInputLock,
        isOutputFlowing,
        acceptValueCallback = {
            hasUntouchedPriorSelections = false
        })

    // Item Name
    private val itemNameInput = MutableStateFlow<String?>(null)
    val hasItemNameInput get() = itemNameInput.value != null

    // Diner selection
    private val diners: StateFlow<List<Diner>> = repository.diners.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyList())
    private val selectionSet = mutableSetOf<Diner>()
    private val _selections = MutableStateFlow(selectionSet.toSet())
    private val selectionCount: Flow<Int> = _selections.mapLatest { it.size }
    private val areAllDinersSelected: Flow<Boolean> = combine(selectionCount, repository.diners) { selectionCount, diners -> selectionCount == diners.size }
    private var hasUntouchedPriorSelections = false

    // UI State
    private var loadedItem = MutableStateFlow<Item?>(null)
    private val pauseDinerRefresh: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val editingName = MutableStateFlow(false)

    // Live Data Output
    val dinerData: LiveData<List<Pair<Diner, String>>> = combineTransform(repository.diners, repository.individualSubtotals, pauseDinerRefresh) { diners, subtotals, pause ->
        if(!pause) {
            // Diners paired with their individual subtotals as currency strings
            emit(diners.map { diner ->
                diner to formatter.format(subtotals.getOrDefault(diner, 0.0))
            })
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val itemName: LiveData<String> = repository.items.combine(itemNameInput) { items, nameInput ->
        nameInput ?: getApplication<Application>().resources.getString(R.string.itementry_toolbar_item_number) + (items.size + 1)
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val selections: LiveData<Set<Diner>> = _selections.withOutputSwitch(isOutputFlowing).asLiveData()
    val toolBarState: LiveData<ToolBarState> = combine(
        calculatorData.isNumberPadVisible.asFlow(),
        editingName,
        itemName.asFlow(),
        selectionCount,
        areAllDinersSelected,
        calculatorData.acceptButtonEnabled.asFlow()) {

        val calcVisible = it[0] as Boolean
        val editing = it[1] as Boolean
        val name = it[2] as String
        val count = it[3] as Int
        val allSelected = it[4] as Boolean
        val acceptButtonEnabled = it[5] as Boolean

        when {
            calcVisible -> {
                // Calculator is visible
                ToolBarState(title = name, navIconVisible = !editing, nameEditVisible = calcVisible, tertiaryBackground = acceptButtonEnabled)
            }
            count == 0 -> {
                // Selecting diners but no diners are selected
                ToolBarState(title = name, navIconVisible = !editing, nameEditVisible = calcVisible, tertiaryBackground = false)
            }
            else -> {
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
            }
        }
    }.withOutputSwitch(isOutputFlowing).asLiveData()
    val selectAllButtonDisabled: LiveData<Boolean> = areAllDinersSelected.withOutputSwitch(isOutputFlowing).asLiveData()
    val editNameShimVisible: LiveData<Boolean> = editingName.withOutputSwitch(isOutputFlowing).asLiveData()

    override fun notifyTransitionFinished() {
        super.notifyTransitionFinished()

        // For better performance, allow the diners to update only after transition finishes
        if(loadedItem.value == null) {
            pauseDinerRefresh.value = false
        }
    }

    fun initializeForItem(item: Item?) {
        unFreezeOutput()

        selectionSet.clear()
        editingName.value = false

        if(item == null) {
            // New item
            loadedItem.value = null
            calculatorData.reset(CalculationType.ITEM_PRICE, null, showNumberPad = true)
            itemNameInput.value = null
            hasUntouchedPriorSelections = false
        } else {
            // Editing existing item
            loadedItem.value = item
            pauseDinerRefresh.value = false
            calculatorData.reset(CalculationType.ITEM_PRICE, item.price, showNumberPad = false)
            itemNameInput.value = item.name
            selectionSet.addAll(item.diners)
            hasUntouchedPriorSelections = selectionSet.isNotEmpty()
        }

        _selections.value = selectionSet.toSet()
    }

    fun handleOnBackPressed(): Boolean? {
        when {
            isInputLocked.value -> { }
            calculatorData.isNumberPadVisible.value == true -> {
                when {
                    editingName.value -> { stopNameEdit() }
                    calculatorData.tryRevertToLastValue() -> {  }
                    else -> { return false }
                }
            }
            hasUntouchedPriorSelections || selectionSet.isEmpty() -> { return false }
            else -> { clearDinerSelections() }
        }
        return null
    }

    fun startNameEdit() { editingName.value = true }
    fun stopNameEdit() {
        if(editingName.value)
            editingName.value = false
    }

    fun acceptItemNameInput(name: String) {
        itemNameInput.value = name
        stopNameEdit()
    }

    fun isDinerSelected(diner: Diner) = selectionSet.contains(diner)
    fun toggleDinerSelection(diner: Diner) {
        hasUntouchedPriorSelections = false

        if(isDinerSelected(diner)) {
            selectionSet.remove(diner)
        } else {
            selectionSet.add(diner)
        }
        _selections.value = selectionSet.toSet()
    }
    fun selectAllDiners() {
        hasUntouchedPriorSelections = false

        diners.value.apply {
            selectionSet.clear()
            selectionSet.addAll(this)
            _selections.value = selectionSet.toSet()
        }
    }
    fun clearDinerSelections() {
        hasUntouchedPriorSelections = false
        selectionSet.clear()
        _selections.value = selectionSet.toSet()
    }

    fun addNewItemToBill(): Item? = selections.value?.let {
        repository.createNewItem(
            calculatorData.numericalValue.value ?: 0.0,
            itemName.value ?: "Item",
            diners.value.filter { diner -> it.contains(diner) })
    }

    fun saveItemEdits() {
        loadedItem.value.also { editedItem ->
            selections.value?.apply {
                repository.editItem(
                    editedItem!!,
                    calculatorData.numericalValue.value ?: 0.0,
                    itemName.value ?: "Item",
                    diners.value.filter { diner -> this.contains(diner) })
            }
        }
    }
}