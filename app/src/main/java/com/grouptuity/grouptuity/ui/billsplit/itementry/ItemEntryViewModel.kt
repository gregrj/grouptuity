package com.grouptuity.grouptuity.ui.billsplit.itementry

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.NumberFormat


class ItemEntryViewModel(app: Application): UIViewModel<Item?, Item?>(app) {
    private val formatter = NumberFormat.getCurrencyInstance()

    private val calculatorData = CalculatorData(CalculationType.ITEM_PRICE) {
        hasUntouchedPriorSelections = false
    }
    val calculator = CalculatorImpl(this, calculatorData)

    // Item Name
    private val itemNameInput = MutableStateFlow<String?>(null)
    val hasItemNameInput get() = itemNameInput.value != null

    // Diner selection
    private val diners: StateFlow<List<Diner>> = repository.diners.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyList())
    private val selectionSet = mutableSetOf<Diner>()
    private val _selections = MutableStateFlow(selectionSet.toSet())
    private val selectionCount: Flow<Int> = _selections.map { it.size }
    private val areAllDinersSelected: Flow<Boolean> = combine(selectionCount, repository.diners) { selectionCount, diners -> selectionCount == diners.size }
    private var hasUntouchedPriorSelections = false

    // UI State
    private var loadedItem = MutableStateFlow<Item?>(null)
    private val pauseDinerRefresh: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val editingName = MutableStateFlow(false)

    // Live Data Output
    val voiceInput: LiveData<Event<String>> = repository.voiceInputMutable
    val dinerData: LiveData<List<Pair<Diner, String>>> = combineTransform(repository.diners, repository.individualSubtotals, pauseDinerRefresh) { diners, subtotals, pause ->
        if(!pause) {
            // Diners paired with their individual subtotals as currency strings
            emit(diners.map { diner ->
                diner to formatter.format(subtotals.getOrDefault(diner, 0.0))
            })
        }
    }.asLiveData(isOutputLocked)
    val itemName: LiveData<String> = repository.items.combine(itemNameInput) { items, nameInput ->
        nameInput ?: getApplication<Application>().resources.getString(R.string.itementry_toolbar_item_number) + (items.size + 1)
    }.asLiveData(isOutputLocked)
    val selections: LiveData<Set<Diner>> = _selections.asLiveData(isOutputLocked)
    val toolBarNavIconVisible: LiveData<Boolean> = combine(
        calculatorData.isNumberPadVisible,
        editingName,
        selectionCount,
    ) { calcVisible, editing, count ->
        when {
            calcVisible -> {
                // Calculator is visible
                !editing
            }
            count == 0 -> {
                // Selecting diners but no diners are selected
                !editing
            }
            else -> {
                // Selecting diners and has at least one selection
                true
            }
        }
    }.asLiveData(isOutputLocked)
    val toolBarEditButtonVisible: LiveData<Boolean> = combine(
        calculatorData.isNumberPadVisible,
        selectionCount,
    ) { calcVisible, count ->
        calcVisible || count == 0
    }.asLiveData(isOutputLocked)
    val toolBarTitle: LiveData<String> = combine(
        calculatorData.isNumberPadVisible,
        itemName.asFlow(),
        selectionCount,
        areAllDinersSelected
    ) { calcVisible, name, count, allSelected ->
        when {
            calcVisible || count == 0 -> {
                // Calculator is visible
                name
            }
            allSelected -> {
                getApplication<Application>().resources.getQuantityString(R.plurals.itementry_num_diners_selected_everyone, count, count)
            }
            else -> {
                getApplication<Application>().resources.getQuantityString(R.plurals.itementry_num_diners_selected, count, count)
            }
        }
    }.asLiveData(isOutputLocked)
    val toolBarInTertiaryState: LiveData<Boolean> = combine(
        calculatorData.isNumberPadVisible,
        selectionCount,
        calculatorData.acceptButtonEnabled
    ) { calcVisible, count, acceptButtonEnabled ->
        when {
            calcVisible -> {
                // Calculator is visible
                acceptButtonEnabled
            }
            count == 0 -> {
                // Selecting diners but no diners are selected
                false
            }
            else -> {
                // Selecting diners and has at least one selection
                true
            }
        }
    }.asLiveData(isOutputLocked)
    val selectAllButtonDisabled: LiveData<Boolean> = areAllDinersSelected.asLiveData(isOutputLocked)
    val editNameShimVisible: LiveData<Boolean> = editingName.asLiveData(isOutputLocked)

    override fun notifyTransitionFinished() {
        super.notifyTransitionFinished()

        // For better performance, allow the diners to update only after transition finishes
        if(loadedItem.value == null) {
            pauseDinerRefresh.value = false
        }
    }

    override fun onInitialize(input: Item?) {
        selectionSet.clear()
        editingName.value = false

        if(input == null) {
            // New item
            loadedItem.value = null
            calculatorData.reset(CalculationType.ITEM_PRICE, null, showNumberPad = true)
            itemNameInput.value = null
            hasUntouchedPriorSelections = false
        } else {
            // Editing existing item
            loadedItem.value = input
            pauseDinerRefresh.value = false
            calculatorData.reset(CalculationType.ITEM_PRICE, input.price, showNumberPad = false)
            itemNameInput.value = input.name
            selectionSet.addAll(input.diners)
            hasUntouchedPriorSelections = selectionSet.isNotEmpty()
        }

        _selections.value = selectionSet.toSet()
    }

    override fun handleOnBackPressed() {
        when {
            calculatorData.isNumberPadVisible.value -> {
                when {
                    editingName.value -> { stopNameEdit() }
                    !calculatorData.tryRevertToLastValue() -> { finishFragment(null) }
                }
            }
            hasUntouchedPriorSelections || selectionSet.isEmpty() -> {
                finishFragment(null)
            }
            else -> { clearDinerSelections() }
        }
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

    fun trySavingItem() {
        val dinerSelections = selections.value

        if (dinerSelections.isNullOrEmpty()) {
            /* No diner selections so cannot proceed */
        } else {
            val editedItem = loadedItem.value
            finishFragment(
                if (editedItem == null) {
                    // Creating a new item
                    repository.createNewItem(
                        calculatorData.numericalValue.value ?: 0.0,
                        itemName.value ?: "Item",
                        diners.value.filter { diner -> dinerSelections.contains(diner) }
                    )
                } else {
                    // Editing an existing item
                    repository.editItem(
                        editedItem,
                        calculatorData.numericalValue.value ?: 0.0,
                        itemName.value ?: "Item",
                        diners.value.filter { diner -> dinerSelections.contains(diner) })

                    editedItem
                }
            )
        }
    }
}