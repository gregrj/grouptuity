package com.grouptuity.grouptuity.ui.billsplit.discountentry

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.max


class DiscountEntryViewModel(app: Application): UIViewModel<Discount?, Discount?>(app) {
    companion object {
        const val DISCOUNT_SAVED = 0
        const val INVALID_PRICE = 1
        const val MISSING_ITEMS = 2
        const val MISSING_RECIPIENTS = 3
        const val MISSING_PURCHASERS = 4
        const val INVALID_COST = 5
    }

    val topViewPagerInputLocked = MutableStateFlow(false).also { addInputLock(it) }
    val discountBasisInputLocked = MutableStateFlow(false).also { addInputLock(it) }

    private val startTransitionEventMutable = MutableLiveData<Event<Boolean>>()
    private val loadDinerListFragmentEventMutable = MutableLiveData<Event<Boolean>>()
    private val loadDinerRecyclerViewEventMutable = MutableLiveData<Event<Boolean>>()
    private val loadItemListFragmentEventMutable = MutableLiveData<Event<Boolean>>()
    private val loadItemRecyclerViewEventMutable = MutableLiveData<Event<Boolean>>()
    private val loadReimbursementFragmentEventMutable = MutableLiveData<Event<Boolean>>()
    val startTransitionEvent: LiveData<Event<Boolean>> = startTransitionEventMutable
    val loadDinerListFragmentEvent: LiveData<Event<Boolean>> = loadDinerListFragmentEventMutable
    val loadDinerRecyclerViewEvent: LiveData<Event<Boolean>> = loadDinerRecyclerViewEventMutable
    val loadItemListFragmentEvent: LiveData<Event<Boolean>> = loadItemListFragmentEventMutable
    val loadItemRecyclerViewEvent: LiveData<Event<Boolean>> = loadItemRecyclerViewEventMutable
    val loadReimbursementFragmentEvent: LiveData<Event<Boolean>> = loadReimbursementFragmentEventMutable

    val priceCalcData = CalculatorData(CalculationType.ITEMIZED_DISCOUNT_AMOUNT)
    val priceCalculator = CalculatorImpl(this, priceCalcData)

    private val costCalcData = CalculatorData(CalculationType.REIMBURSEMENT_AMOUNT)
    val costCalculator = CalculatorImpl(this, costCalcData)

    private val _isHandlingReimbursements = MutableStateFlow(false)
    private val _isDiscountOnItems = MutableStateFlow(true)

    // Bill Entities and Selections
    val loadedDiscount = MutableStateFlow<Discount?>(null)
    private val itemSelectionSet = mutableSetOf<Item>()
    private val dinerSelectionSet = mutableSetOf<Diner>()
    private val reimburseeSelectionSet = mutableSetOf<Diner>()
    private val _itemSelections = MutableStateFlow(itemSelectionSet.toSet())
    private val _dinerSelections = MutableStateFlow(dinerSelectionSet.toSet())
    private val _reimburseeSelections = MutableStateFlow(reimburseeSelectionSet.toSet())

    // Edit tracking (null == not set, false == prior value unchanged, true == new value set)
    private val editedPrice: Boolean? get() = priceCalcData.editedValue
    private val editedCost: Boolean? get() = costCalcData.editedValue
    private var editedRecipientSelections: Boolean? = null
    private var editedReimbursementSelections: Boolean? = null
    private var hasSubstantiveEdits: StateFlow<Boolean> = combine(
        loadedDiscount,
        priceCalcData.numericalValue,
        costCalcData.numericalValue,
        priceCalcData.isInPercent,
        _itemSelections,
        _dinerSelections,
        _reimburseeSelections) {

        it[0]?.run {
            val discount = this as Discount
            val price = it[1] as Double?
            val cost = it[2] as Double?
            val inPercent = it[3] as Boolean
            val items = it[4] as Set<Item>
            val diners = it[5] as Set<Diner>
            val reimbursees = it[6] as Set<Diner>

            discount.value != price ||
            discount.cost != cost ||
            discount.asPercent != inPercent ||
            discount.itemIds.toSet() != items ||
            discount.recipientIds.toSet() != diners ||
            discount.purchaserIds.toSet() != reimbursees
        } ?: false
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, false)

    private val recipients: Flow<Set<Diner>> = combine(
        _isDiscountOnItems,
        _dinerSelections,
        _itemSelections,
        repository.items) { onItems, dinerSelections, itemSelections, _ ->

        if(onItems) {
            itemSelections.flatMap { it.diners }.toSet()
        } else {
            dinerSelections
        }
    }
    private val currencyValue: Flow<Double?> = combine(
        _isDiscountOnItems,
        priceCalcData.isInPercent,
        priceCalcData.numericalValue,
        _itemSelections,
        _dinerSelections,
        repository.individualSubtotals) {
        val onItems = it[0] as Boolean
        val asPercent  = it[1] as Boolean
        val rawValue = it[2] as Double?
        val items = it[3] as Set<Item>
        val recipients = it[4] as Set<Diner>
        val subtotals = it[5] as Map<Diner, Double>

        if(rawValue == null) {
            null
        } else if(asPercent) {
            if(onItems) {
                getDiscountCurrencyOnItemsPercent(rawValue, items)
            } else {
                getDiscountCurrencyOnDinersPercent(rawValue, recipients, subtotals)
            }
        } else {
            rawValue
        }
    }
    private val recipientShares: Flow<Map<Diner, Double>> = combine(
        _isDiscountOnItems,
        priceCalcData.isInPercent,
        priceCalcData.numericalValue,
        _itemSelections,
        repository.items,
        recipients,
        repository.individualSubtotals) {

        val onItems = it[0] as Boolean
        val asPercent = it[1] as Boolean
        val value = it[2] as Double?
        val itemSelections = it[3] as Set<Item>
        val recipients = it[5] as Set<Diner>
        val subtotals = it[6] as Map<Diner, Double>

        if(value != null && value > 0.0) {
            if(onItems) {
                if(asPercent) {
                    getDiscountRecipientSharesOnItemsPercent(value, itemSelections)
                } else {
                    getDiscountRecipientSharesOnItemsValue(value, itemSelections)
                }
            } else {
                if (asPercent) {
                    getDiscountRecipientSharesOnDinersPercent(value, recipients, subtotals)
                } else {
                    getDiscountRecipientSharesOnDinersValue(value, recipients)
                }
            }
        } else {
            emptyMap()
        }
    }
    private val discountedSubtotals = combine(repository.individualSubtotals, recipientShares) { subtotals, shares ->
        shares.mapValues { subtotals.getOrDefault(it.key, 0.0) - it.value }
    }

    private val calculatorToolBarTitle: Flow<String> = combine(_isHandlingReimbursements, priceCalcData.isInPercent) { handlingReimbursements, inPercent ->
        if(handlingReimbursements) {
            getApplication<Application>().resources.getString(R.string.discountentry_toolbar_reimbursemententry)
        } else {
            if(inPercent)
                getApplication<Application>().resources.getString(R.string.discountentry_toolbar_priceentry_percent)
            else
                getApplication<Application>().resources.getString(R.string.discountentry_toolbar_priceentry_value)
        }
    }
    private val itemsToolBarTitle: Flow<String> = combine(
        _itemSelections,
        repository.items,
        priceCalcData.isInPercent) { selectedItems, items, inPercent ->

        when(selectedItems.size) {
            0 -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_zeroitems) }
            1 -> {
                selectedItems.firstOrNull()?.name?.let {
                    getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_oneitem, it)
                } ?: if(inPercent) {
                    getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_someitems_percent, 1, 1)
                } else {
                    getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_someitems_currency, 1, 1)
                }
            }
            items.size -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_allitems) }
            else -> {
                if(inPercent) {
                    getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_someitems_percent, selectedItems.size, selectedItems.size)
                } else {
                    getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_someitems_currency, selectedItems.size, selectedItems.size)
                }
            }
        }
    }
    private val dinersToolBarTitle: Flow<String> = combine(
        _dinerSelections,
        repository.diners,
        priceCalcData.isInPercent) { selectedDiners, diners, inPercent ->

        if(inPercent) {
            when(selectedDiners.size) {
                0 -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_zerodiners) }
                1 -> {
                    selectedDiners.firstOrNull()?.name?.let {
                        getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_onediner, it)
                    } ?: getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_somediners_percent, 1, 1)
                }
                diners.size -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_alldiners_percent) }
                else -> { getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_somediners_percent, selectedDiners.size, selectedDiners.size) }
            }
        } else {
            when(selectedDiners.size) {
                0 -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_zerodiners) }
                1 -> {
                    selectedDiners.firstOrNull()?.name?.let {
                        getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_onediner, it)
                    } ?: getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_somediners_currency, 1, 1)
                }
                diners.size -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_alldiners_currency) }
                else -> {
                    getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_somediners_currency, selectedDiners.size, selectedDiners.size)
                }
            }
        }
    }
    private val reimbursementToolBarTitle: Flow<String> = combine(
        _reimburseeSelections,
        repository.diners.map { it.size }) { selectedReimbursees, maxReimbursees ->

        when(selectedReimbursees.size) {
            0 -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_zeroreimbursees) }
            1 -> {
                selectedReimbursees.firstOrNull()?.name?.let { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_onereimbursee, it) } ?:
                getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_somereimbursees, 1, 1)
            }
            maxReimbursees -> { getApplication<Application>().resources.getString(R.string.discountentry_toolbar_selection_allreimbursees) }
            else -> { getApplication<Application>().resources.getQuantityString(R.plurals.discountentry_toolbar_selection_somereimbursees, selectedReimbursees.size, selectedReimbursees.size) }
        }
    }
    val uiInTertiaryState: LiveData<Boolean> = combine(
        _isHandlingReimbursements,
        _isDiscountOnItems,
        priceCalcData.isNumberPadVisible,
        costCalcData.isNumberPadVisible,
        priceCalcData.acceptButtonEnabled,
        costCalcData.acceptButtonEnabled,
        _reimburseeSelections,
        _itemSelections,
        _dinerSelections) {

        val handlingReimbursements = it[0] as Boolean
        val discountOnItems = it[1] as Boolean
        val numberPadVisible = (if(handlingReimbursements) it[3] else it[2]) as Boolean
        val acceptButtonEnabled = (if(handlingReimbursements) it[5] else it[4]) as Boolean

        when {
            numberPadVisible -> acceptButtonEnabled
            handlingReimbursements -> (it[6] as Set<*>).size > 0
            discountOnItems -> (it[7] as Set<*>).size > 0
            else -> (it[8] as Set<*>).size > 0
        }
    }.asLiveData(isOutputLocked)

    // Live Data Output
    val isHandlingReimbursements: LiveData<Boolean> = _isHandlingReimbursements.asLiveData(isOutputLocked)
    val isDiscountOnItems: LiveData<Boolean> = _isDiscountOnItems.asLiveData(isOutputLocked)
    val tabsVisible: LiveData<Boolean> = combine(
        priceCalcData.isNumberPadVisible,
        costCalcData.isNumberPadVisible
    ) { price, cost ->
        !price && !cost
    }.asLiveData(isOutputLocked)

    val itemData: LiveData<List<Triple<Item, Boolean, Triple<String, String?, String>>>> = combine(
        repository.diners,
        repository.items,
        _itemSelections,
        _isDiscountOnItems,
        priceCalcData.numericalValue.filterNotNull(),
        priceCalcData.isInPercent) {

        val diners = it[0] as List<Diner>
        val items = it[1] as List<Item>
        val itemSelections = it[2] as Set<Item>
        val onItems = it[3] as Boolean
        val discountValue = it[4] as Double
        val asPercent = it[5] as Boolean

        val currencyFormatter = NumberFormat.getCurrencyInstance()

        val discountedPrices = getDiscountedItemPrices(discountValue, onItems, asPercent, itemSelections)

        items.map { item ->
            Triple(
                item,
                itemSelections.contains(item),
                Triple(
                    currencyFormatter.format(item.price),
                    discountedPrices[item]?.let { price -> currencyFormatter.format(price) },
                    when(item.dinerIds.size) {
                        0 -> {
                            getApplication<Application>().resources.getString(R.string.discountentry_foritems_no_diners_warning)
                        }
                        diners.size -> {
                            getApplication<Application>().resources.getString(R.string.discountentry_foritems_shared_by_everyone)
                        }
                        else -> { "" }
                    }
                )
            )
        }
    }.asLiveData(isOutputLocked)

    val recipientData: LiveData<List<Triple<Diner, Boolean, String>>> = combine(
        repository.diners,
        _dinerSelections,
        repository.individualSubtotals,
        recipientShares,
        discountedSubtotals) { diners, dinerSelections, individualSubtotals, recipientShares, discountedSubtotals ->

        val currencyFormatter = NumberFormat.getCurrencyInstance()

        diners.map { diner ->
            Triple(diner, dinerSelections.contains(diner), if(dinerSelections.contains(diner)) {
                when {
                    diner.itemIds.isEmpty() -> {
                        getApplication<Application>().resources.getString(
                            R.string.discountentry_fordiners_unused,
                            currencyFormatter.format(recipientShares.getOrDefault(diner, 0.0)))
                    }
                    discountedSubtotals.getOrDefault(diner, 0.0) >= 0.0 ||
                            currencyFormatter.format(-discountedSubtotals.getOrDefault(diner, 0.0)) == currencyFormatter.format(0.0) -> {
                        getApplication<Application>().resources.getString(
                            R.string.discountentry_fordiners_fullyused,
                            currencyFormatter.format(max(0.0, discountedSubtotals.getOrDefault(diner, 0.0))),
                            currencyFormatter.format(recipientShares.getOrDefault(diner, 0.0)))
                    }
                    else -> {
                        getApplication<Application>().resources.getString(
                            R.string.discountentry_fordiners_partiallyused,
                            currencyFormatter.format(max(0.0, discountedSubtotals.getOrDefault(diner, 0.0))),
                            currencyFormatter.format(recipientShares.getOrDefault(diner, 0.0)))
                    }
                }
            } else {
                when(diner.itemIds.size) {
                    0 -> {
                        getApplication<Application>().resources.getString(R.string.discountentry_fordiners_zeroitems)
                    }
                    else -> {
                        getApplication<Application>().resources.getQuantityString(
                            R.plurals.discountentry_fordiners_items_with_subtotal,
                            diner.itemIds.size,
                            diner.itemIds.size,
                            currencyFormatter.format(individualSubtotals.getOrDefault(diner, 0.0)))
                    }
                }
            })
        }
    }.asLiveData(isOutputLocked)

    val reimbursementData: LiveData<List<Triple<Diner, Boolean, String>>> = combine(
        repository.diners,
        _reimburseeSelections,
        recipientShares,
        currencyValue,
        costCalcData.numericalValue) { diners, selections, recipientShares, value, cost ->

        val debts = if(cost != null && value != null && value > 0.0) {
            getDiscountReimbursementDebts(cost, value, recipientShares)
        }
        else {
            emptyMap()
        }

        val credits = if(cost == null) {
            emptyMap()
        } else {
            getDiscountReimbursementCredits(cost, selections)
        }

        val currencyFormatter = NumberFormat.getCurrencyInstance()

        diners.map { diner ->
            val message = if (diner in credits) {
                if(diner in debts) {
                    val netReimbursementNumerical = credits[diner]!! - debts[diner]!!
                    val netReimbursementString = currencyFormatter.format(abs(netReimbursementNumerical))

                    if(netReimbursementString == currencyFormatter.format(0.0)) {
                        getApplication<Application>().resources.getString(R.string.discountentry_reimbursement_neutral)
                    } else {
                        val fullDebtString = currencyFormatter.format(debts[diner])
                        val fullCreditString = currencyFormatter.format(credits[diner])

                        if (netReimbursementNumerical > 0.0) {
                            getApplication<Application>().resources.getString(
                                R.string.discountentry_reimbursement_receive_reduced,
                                netReimbursementString,
                                fullCreditString,
                                fullDebtString)
                        } else {
                            getApplication<Application>().resources.getString(
                                R.string.discountentry_reimbursement_pay_reduced,
                                netReimbursementString,
                                fullDebtString,
                                fullCreditString)
                        }
                    }
                } else {
                    getApplication<Application>().resources.getString(
                        R.string.discountentry_reimbursement_receive,
                        currencyFormatter.format(credits[diner]))
                }
            } else if(diner in debts) {
                getApplication<Application>().resources.getString(
                    R.string.discountentry_reimbursement_pay,
                    currencyFormatter.format(debts[diner]))
            } else {
                ""
            }

            Triple(diner, selections.contains(diner), message)
        }
    }.asLiveData(isOutputLocked)

    val itemSelections: LiveData<Set<Item>> = _itemSelections.asLiveData(isOutputLocked)
    val dinerSelections: LiveData<Set<Diner>> = _dinerSelections.asLiveData(isOutputLocked)
    val reimburseeSelections: LiveData<Set<Diner>> = _reimburseeSelections.asLiveData(isOutputLocked)

    val fabIcon: LiveData<Int?> = combine(
        _isHandlingReimbursements,
        _isDiscountOnItems,
        _itemSelections,
        _dinerSelections,
        _reimburseeSelections) { handlingReimbursements, onItems, items, diners, reimbursees ->

        if(handlingReimbursements) {
            if(reimbursees.isEmpty()) null else {
                if(onItems) {
                    if (items.isEmpty()) R.drawable.ic_arrow_back_light else  R.drawable.ic_arrow_forward
                } else {
                    if (diners.isEmpty()) R.drawable.ic_arrow_back_light else  R.drawable.ic_arrow_forward
                }
            }
        } else {
            if(onItems) {
                if (items.isEmpty()) null else  R.drawable.ic_arrow_forward
            } else {
                if (diners.isEmpty()) null else  R.drawable.ic_arrow_forward
            }
        }
    }.asLiveData(isOutputLocked)

    val toolbarTitle: LiveData<String> = combine(
        _isHandlingReimbursements,
        _isDiscountOnItems,
        priceCalcData.isNumberPadVisible,
        costCalcData.isNumberPadVisible,
        calculatorToolBarTitle,
        reimbursementToolBarTitle,
        itemsToolBarTitle,
        dinersToolBarTitle) {
        val handlingReimbursements = it[0] as Boolean
        val discountOnItems = it[1] as Boolean
        val numberPadVisible = (if(handlingReimbursements) it[3] else it[2]) as Boolean

        when {
            numberPadVisible -> it[4]
            handlingReimbursements -> it[5]
            discountOnItems -> it[6]
            else -> it[7]
        } as String
    }.asLiveData(isOutputLocked)

    val discountBasisButtonsOnItems: LiveData<Boolean> = _isDiscountOnItems.asLiveData(isOutputLocked)
    val clearItemsButtonDeemphasized: LiveData<Boolean> = _itemSelections.map { it.isEmpty() }.asLiveData(isOutputLocked)
    val clearDinersButtonDeemphasized: LiveData<Boolean> = _dinerSelections.map { it.isEmpty() }.asLiveData(isOutputLocked)
    val clearReimburseeButtonDeemphasized: LiveData<Boolean> = _reimburseeSelections.map { it.isEmpty() }.asLiveData(isOutputLocked)

    val selectAllItemsButtonDisabled: LiveData<Boolean> = combine(_itemSelections, repository.items) { selections, items ->
        selections.size == items.size
    }.asLiveData(isOutputLocked)
    val selectAllDinersButtonDisabled: LiveData<Boolean> = combine(_dinerSelections, repository.diners) { selections, diners ->
        selections.size == diners.size
    }.asLiveData(isOutputLocked)
    val selectAllReimburseesButtonDisabled: LiveData<Boolean> = combine(_reimburseeSelections, repository.diners) { selections, diners ->
        selections.size == diners.size
    }.asLiveData(isOutputLocked)

    private val showUnsavedInvalidEditsAlertEventMutable = MutableLiveData<Event<Boolean>>()
    val showUnsavedInvalidEditsAlertEvent: LiveData<Event<Boolean>> = showUnsavedInvalidEditsAlertEventMutable
    private val showUnsavedValidEditsAlertEventMutable = MutableLiveData<Event<Boolean>>()
    val showUnsavedValidEditsAlertEvent: LiveData<Event<Boolean>> = showUnsavedValidEditsAlertEventMutable
    private val showIncompleteReimbursementAlertEventMutable = MutableLiveData<Event<Boolean>>()
    val showIncompleteReimbursementAlertEvent: LiveData<Event<Boolean>> = showIncompleteReimbursementAlertEventMutable

    override fun notifyTransitionFinished() {
        super.notifyTransitionFinished()
        loadDinerListFragmentEventMutable.value = Event(true)
        loadDinerRecyclerViewEventMutable.value = Event(true)
        loadItemListFragmentEventMutable.value = Event(true)
        loadItemRecyclerViewEventMutable.value = Event(true)
        loadReimbursementFragmentEventMutable.value = Event(true)
    }

    fun startTransition() { startTransitionEventMutable.value = Event(true) }

    override fun onInitialize(input: Discount?) {

        // Invalidate any unconsumed events
        startTransitionEventMutable.value?.consume()
        loadDinerListFragmentEventMutable.value?.consume()
        loadDinerRecyclerViewEventMutable.value?.consume()
        loadItemListFragmentEventMutable.value?.consume()
        loadItemRecyclerViewEventMutable.value?.consume()
        loadReimbursementFragmentEventMutable.value?.consume()

        showIncompleteReimbursementAlertEventMutable.value?.consume()
        showUnsavedInvalidEditsAlertEventMutable.value?.consume()
        showUnsavedValidEditsAlertEventMutable.value?.consume()

        itemSelectionSet.clear()
        dinerSelectionSet.clear()
        reimburseeSelectionSet.clear()

        _isHandlingReimbursements.value = false

        if(input == null) {
            // Creating new discount
            loadedDiscount.value = null

            priceCalcData.reset(CalculationType.ITEMIZED_DISCOUNT_AMOUNT, null, true)
            costCalcData.reset(CalculationType.REIMBURSEMENT_AMOUNT, null, false)

            _isDiscountOnItems.value = true
            editedRecipientSelections = null
            editedReimbursementSelections = null

            startTransitionEventMutable.value = Event(true)
        } else {
            // Editing existing discount
            loadedDiscount.value = input

            priceCalcData.reset(
                if (input.asPercent) CalculationType.ITEMIZED_DISCOUNT_PERCENT else CalculationType.ITEMIZED_DISCOUNT_AMOUNT,
                input.value,
                false
            )
            costCalcData.reset(CalculationType.REIMBURSEMENT_AMOUNT, input.cost, false)

            _isDiscountOnItems.value = if(input.onItems) {
                itemSelectionSet.addAll(input.items)
                editedRecipientSelections = if(itemSelectionSet.isNotEmpty()) false else null
                loadItemListFragmentEventMutable.value = Event(true)
                true
            } else {
                dinerSelectionSet.addAll(input.recipients)
                editedRecipientSelections = if(dinerSelectionSet.isNotEmpty()) false else null
                loadDinerListFragmentEventMutable.value = Event(true)
                false
            }
            reimburseeSelectionSet.addAll(input.purchasers)
            editedReimbursementSelections = if(reimburseeSelectionSet.isNotEmpty()) false else null
        }

        _itemSelections.value = itemSelectionSet.toSet()
        _dinerSelections.value = dinerSelectionSet.toSet()
        _reimburseeSelections.value = reimburseeSelectionSet.toSet()
    }

    override fun handleOnBackPressed() {
        when {
            isInputLocked.value -> { /* Ignore back press while input is locked */ }
            _isHandlingReimbursements.value -> {
                when {
                    costCalcData.isNumberPadVisible.value -> {
                        /* User is editing the reimbursement cost so try to return to the last
                         value. If successful, the number pad will be dismissed. */
                        if(!revertToLastCost()) {
                            // No prior cost exists so abort reimbursement and return to properties
                            switchToDiscountProperties()
                        }
                    }
                    reimburseeSelectionSet.isEmpty() -> {
                        /* Reimbursement cost has been entered, but no reimbursees have been
                         selected. Give user option to stay or discard reimbursement before
                         returning to properties. */
                        showIncompleteReimbursementAlertEventMutable.value = Event(true)
                    }
                    editedReimbursementSelections == true -> {
                        // Reimbursee selections exist and have been edited so clear all
                        clearReimburseeSelections()
                    }
                    else -> {
                        /* Has unedited selections from a previously created item. Keep
                         reimbursement data and return to the properties fragment */
                        switchToDiscountProperties()
                    }
                }
            }
            priceCalcData.isNumberPadVisible.value -> {
                /* User is editing the discount amount so try to return to the last value. If
                 successful, the number pad will be dismissed. */
                if(!revertToLastPrice()) {
                    // No prior discount value exists so close fragment without creating discount
                    finishFragment(null)
                }
            }
            _isDiscountOnItems.value -> {
                when {
                    itemSelectionSet.isEmpty() -> {
                        // Discount amount has been entered, but no items are selected
                        if(loadedDiscount.value != null) {
                            // Alert user of unsaved edits and present option to discard and close fragment
                            showUnsavedInvalidEditsAlertEventMutable.value = Event(true)
                        } else {
                            if(editedRecipientSelections == true || editedCost == true) {
                                // Alert user of unsaved edits and present option to discard and close fragment
                                showUnsavedInvalidEditsAlertEventMutable.value = Event(true)
                            } else {
                                finishFragment(null)
                            }
                        }
                    }
                    editedRecipientSelections == true -> {
                        // Item selections exist and have been edited so clear all
                        clearItemSelections()
                    }
                    else -> {
                        // Has unedited item selections from a previously created discount
                        if(hasSubstantiveEdits.value) {
                            // Alert user of unsaved edits and present option to save and close fragment
                            showUnsavedValidEditsAlertEventMutable.value = Event(true)
                        } else {
                            finishFragment(null)
                        }
                    }
                }
            }
            else -> {
                when {
                    dinerSelectionSet.isEmpty() -> {
                        // Discount amount has been entered, but no diners are selected
                        if(loadedDiscount.value != null) {
                            // Alert user of unsaved edits and present option to discard and close fragment
                            showUnsavedInvalidEditsAlertEventMutable.value = Event(true)
                        } else {
                            if(editedRecipientSelections == true || editedCost == true) {
                                // Alert user of unsaved edits and present option to discard and close fragment
                                showUnsavedInvalidEditsAlertEventMutable.value = Event(true)
                            } else {
                                finishFragment(null)
                            }
                        }
                    }
                    editedRecipientSelections == true -> {
                        // Diner selections exist and have been edited so clear all
                        clearDinerSelections()
                    }
                    else -> {
                        /* Has unedited diner selections from a previously created discount. Close
                         fragment without saving discount. */
                        if(hasSubstantiveEdits.value) {
                            // Alert user of unsaved edits and present option to save and close fragment
                            showUnsavedValidEditsAlertEventMutable.value = Event(true)
                        } else {
                            finishFragment(null)
                        }
                    }
                }
            }
        }
    }

    fun switchToReimbursements() {
        if(costCalcData.rawInputIsBlank.value) {
            editCost()
        }
        _isHandlingReimbursements.value = true
    }
    fun switchToDiscountProperties() {
        if(costCalcData.isNumberPadVisible.value) {
            costCalcData.discardEntry()
        }

        _isHandlingReimbursements.value = false
    }
    fun switchDiscountBasisToItems() { _isDiscountOnItems.value = true }
    fun switchDiscountBasisToDiners() { _isDiscountOnItems.value = false }

    private fun revertToLastPrice() = priceCalcData.tryRevertToLastValue()

    fun editCost() {
        costCalcData.clearValue()
        costCalcData.showNumberPad()
    }
    private fun revertToLastCost() = costCalcData.tryRevertToLastValue()
    fun cancelCost() {
        costCalcData.discardEntry()
        clearReimburseeSelections()
        switchToDiscountProperties()
    }

    fun isDinerSelected(diner: Diner) = dinerSelectionSet.contains(diner)
    fun toggleDinerSelection(diner: Diner) {
        editedRecipientSelections = true

        if(dinerSelectionSet.contains(diner)) {
            dinerSelectionSet.remove(diner)
        } else {
            dinerSelectionSet.add(diner)
        }
        _dinerSelections.value = dinerSelectionSet.toSet()
    }
    fun selectAllDiners() {
        editedRecipientSelections = true

        repository.diners.value.apply {
            dinerSelectionSet.clear()
            dinerSelectionSet.addAll(this)
            _dinerSelections.value = dinerSelectionSet.toSet()
        }
    }
    fun clearDinerSelections() {
        editedRecipientSelections = true
        dinerSelectionSet.clear()
        _dinerSelections.value = dinerSelectionSet.toSet()
    }

    fun isItemSelected(item: Item) = itemSelectionSet.contains(item)
    fun toggleItemSelection(item: Item) {
        editedRecipientSelections = true
        if(itemSelectionSet.contains(item)) {
            itemSelectionSet.remove(item)
        } else {
            itemSelectionSet.add(item)
        }
        _itemSelections.value = itemSelectionSet.toSet()
    }
    fun selectAllItems() {
        editedRecipientSelections = true
        repository.items.value.apply {
            itemSelectionSet.clear()
            itemSelectionSet.addAll(this)
            _itemSelections.value = itemSelectionSet.toSet()
        }
    }
    fun clearItemSelections() {
        editedRecipientSelections = true
        itemSelectionSet.clear()
        _itemSelections.value = itemSelectionSet.toSet()
    }

    fun isReimburseeSelected(reimbursee: Diner) = reimburseeSelectionSet.contains(reimbursee)
    fun toggleReimburseeSelection(reimbursee: Diner) {
        editedReimbursementSelections = true
        if(reimburseeSelectionSet.contains(reimbursee)) {
            reimburseeSelectionSet.remove(reimbursee)
        } else {
            reimburseeSelectionSet.add(reimbursee)
        }
        _reimburseeSelections.value = reimburseeSelectionSet.toSet()
    }
    fun selectAllReimbursees() {
        editedReimbursementSelections = true
        repository.diners.value.apply {
            reimburseeSelectionSet.clear()
            reimburseeSelectionSet.addAll(this)
            _reimburseeSelections.value = reimburseeSelectionSet.toSet()
        }
    }
    fun clearReimburseeSelections() {
        editedReimbursementSelections = true
        reimburseeSelectionSet.clear()
        _reimburseeSelections.value = reimburseeSelectionSet.toSet()
    }

    fun saveDiscount(): Int {
        val price = priceCalcData.numericalValue.value
        val asPercent = priceCalcData.isInPercent.value
        val onItems = _isDiscountOnItems.value
        val items = itemSelectionSet.toList()
        val recipients = dinerSelectionSet.toList()
        val cost = costCalcData.numericalValue.value
        val purchasers = reimburseeSelectionSet.toList()

        when {
            price == null || price == 0.0 -> { return INVALID_PRICE }
            onItems && items.isEmpty() -> { return MISSING_ITEMS }
            !onItems && recipients.isEmpty() -> { return MISSING_RECIPIENTS }
            (cost != null && cost > 0.0) && purchasers.isEmpty() -> { return MISSING_PURCHASERS }
            purchasers.isNotEmpty() && (cost == null || cost == 0.0) -> { return INVALID_COST }
            else -> {
                val editedDiscount = loadedDiscount.value
                finishFragment(
                    if (editedDiscount == null) {
                        // Creating a new discount
                        repository.createNewDiscount(
                            asPercent,
                            onItems,
                            price,
                            cost,
                            items,
                            recipients,
                            purchasers)
                    } else {
                        // Editing an existing discount
                        repository.editDiscount(
                            editedDiscount,
                            asPercent,
                            onItems,
                            price,
                            cost,
                            items,
                            recipients,
                            purchasers)

                        editedDiscount
                    }
                )
                return DISCOUNT_SAVED
            }
        }
    }
}