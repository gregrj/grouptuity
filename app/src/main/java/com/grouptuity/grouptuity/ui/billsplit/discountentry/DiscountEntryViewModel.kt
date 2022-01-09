package com.grouptuity.grouptuity.ui.billsplit.discountentry

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import com.grouptuity.grouptuity.data.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*


class DiscountEntryViewModel(app: Application): UIViewModel<String?, Discount?>(app) {
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

    private val priceCalcData = CalculatorData(CalculationType.ITEMIZED_DISCOUNT_AMOUNT)
    val priceCalculator = CalculatorImpl(this, priceCalcData)

    private val costCalcData = CalculatorData(CalculationType.REIMBURSEMENT_AMOUNT)
    val costCalculator = CalculatorImpl(this, costCalcData)

    private val _isHandlingReimbursements = MutableStateFlow(false)
    private val _isDiscountOnItems = MutableStateFlow(true)

    // Bill Entities and Selections
    val loadedDiscount = MutableStateFlow<Discount?>(null)
    private val itemSelectionSet = mutableSetOf<Item>()
    private val recipientSelectionSet = mutableSetOf<Diner>()
    private val reimburseeSelectionSet = mutableSetOf<Diner>()
    private val _itemSelections = MutableStateFlow(itemSelectionSet.toSet())
    private val _recipientSelections = MutableStateFlow(recipientSelectionSet.toSet())
    private val _reimburseeSelections = MutableStateFlow(reimburseeSelectionSet.toSet())

    // Edit tracking (null == not set, false == prior value unchanged, true == new value set)
    private val editedCost: Boolean? get() = costCalcData.editedValue
    private var editedRecipientSelections: Boolean? = null
    private var editedReimbursementSelections: Boolean? = null
    private var hasSubstantiveEdits: StateFlow<Boolean> = combine(
        loadedDiscount,
        priceCalcData.numericalValue,
        costCalcData.numericalValue,
        priceCalcData.isInPercent,
        _itemSelections,
        _recipientSelections,
        _reimburseeSelections) {

        it[0]?.run {
            val discount = this as Discount
            val price = it[1] as BigDecimal
            val cost = it[2] as BigDecimal
            val inPercent = it[3] as Boolean
            val items = it[4] as Set<Item>
            val diners = it[5] as Set<Diner>
            val reimbursees = it[6] as Set<Diner>

            discount.amount.value.compareTo(price) != 0 ||
            discount.cost.value.compareTo(cost) != 0 ||
            discount.asPercentInput != inPercent ||
            discount.items.value != items ||
            discount.recipients.value != diners ||
            discount.purchasers.value != reimbursees
        } ?: false
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, false)

    private val itemPriceSum: Flow<BigDecimal> = _itemSelections.map { items ->
        items.sumOf { it.price.value }
    }
    private val recipientSubtotalSum: Flow<BigDecimal> = _recipientSelections.map { recipients ->
        recipients.sumOf { it.rawSubtotal.value }
    }
    private val currencyValue: Flow<BigDecimal> = combine(
        priceCalcData.isInPercent,
        _isDiscountOnItems,
        priceCalcData.numericalValue,
        itemPriceSum,
        recipientSubtotalSum,
        repository.currencyDigits,
        repository.discountRoundingMode
    ) {
        val asPercent = it[0] as Boolean
        val onItems = it[1] as Boolean
        val amount = it[2] as BigDecimal
        val itemPricesSum = it[3] as BigDecimal
        val recipientSubtotalsSum = it[4] as BigDecimal
        val currencyDigits = (it[5] as Currency).defaultFractionDigits
        val roundingMode = it[6] as RoundingMode

        if (asPercent) {
            amount.movePointLeft(2)
                .multiply(
                    if (onItems) { itemPricesSum } else { recipientSubtotalsSum },
                    mathContext
                )
                .setScale(currencyDigits, roundingMode)
        } else {
            amount
        }
    }

    private val discountedPrices =
        combine(
            _isDiscountOnItems,
            currencyValue,
            _itemSelections,
            itemPriceSum,
            repository.currencyDigits,
            repository.discountRoundingMode
        ) {
            val onItems = it[0] as Boolean
            val discountValue = it[1] as BigDecimal
            val items = it[2] as Set<Item>
            val priceSum = it[3] as BigDecimal
            val digits = it[4] as BigDecimal
            val roundingMode = it[5] as RoundingMode

            emptyMap<Diner, BigDecimal>()
            // TODO
//            items.associateWith { item ->
//                item.price.value - share.setScale(digits, roundingMode)
//            }
        }

    private val recipientShares: Flow<Map<Diner, BigDecimal>> =
        combine(
            currencyValue,
            _isDiscountOnItems,
            _itemSelections,
            _recipientSelections,
            recipientSubtotalSum
        ) { discountValue, onItems, itemSelections, recipients, recipientSubtotal ->

            if(onItems) {
                val dinerItemTotals = mutableMapOf<Diner, BigDecimal>()

                itemSelections.forEach { item ->
                    val itemDiners = item.diners.value
                    val dinerShareOfItem = item.price.value.divideWithZeroBypass(itemDiners.size)
                    itemDiners.forEach {
                        dinerItemTotals.add(it, dinerShareOfItem)
                    }
                }

                val normalizedValue = discountValue.divideWithZeroBypass(dinerItemTotals.values.sumOf { it })
                dinerItemTotals.mapValues { (_, dinerItemTotal) ->
                    dinerItemTotal.multiply(normalizedValue, mathContext)
                }
            } else {
                val normalizedValue = discountValue.divideWithZeroBypass(recipientSubtotal)
                recipients.associateWith { recipient ->
                    recipient.rawSubtotal.value.multiply(normalizedValue, mathContext)
                }
            }
        }
    private val discountedSubtotals =
        combine(
            recipientShares,
            repository.currencyDigits,
            repository.discountRoundingMode
        ) { shares, digits, roundingMode ->

            shares.mapValues { (diner, share) ->
                diner.displayedSubtotal.value - share.setScale(digits, roundingMode)
            }
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
        _recipientSelections,
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
        repository.diners.mapLatest { it.size }) { selectedReimbursees, maxReimbursees ->

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
    private val uiInTertiaryState: Flow<Boolean> = combine(
        _isHandlingReimbursements,
        _isDiscountOnItems,
        priceCalcData.isNumberPadVisible,
        costCalcData.isNumberPadVisible,
        _reimburseeSelections,
        _itemSelections,
        _recipientSelections) {

        val handlingReimbursements = it[0] as Boolean
        val discountOnItems = it[1] as Boolean
        val numberPadVisible = (if(handlingReimbursements) it[3] else it[2]) as Boolean

        when {
            numberPadVisible -> false
            handlingReimbursements -> (it[4] as Set<*>).size > 0
            discountOnItems -> (it[5] as Set<*>).size > 0
            else -> (it[6] as Set<*>).size > 0
        }
    }

    // Live Data Output
    val isHandlingReimbursements: LiveData<Boolean> = _isHandlingReimbursements.asLiveData(isOutputLocked)
    val isDiscountOnItems: LiveData<Boolean> = _isDiscountOnItems.asLiveData(isOutputLocked)
    val tabsVisible: LiveData<Boolean> = combine(priceCalcData.isNumberPadVisible, costCalcData.isNumberPadVisible){ price, cost -> !price && !cost }.asLiveData(isOutputLocked)

    val itemData: LiveData<List<Triple<Item, Boolean, Triple<String, String?, String>>>> = combine(
        repository.diners,
        repository.items,
        _itemSelections,
        _isDiscountOnItems,
        priceCalcData.numericalValue,
        priceCalcData.isInPercent) {

        val diners = it[0] as List<Diner>
        val items = it[1] as List<Item>
        val itemSelections = it[2] as Set<Item>
        val onItems = it[3] as Boolean
        val discountValue = it[4] as BigDecimal
        val asPercent = it[5] as Boolean

        val currencyFormatter = NumberFormat.getCurrencyInstance()

        val discountedPrices = emptyMap<Diner, BigDecimal>()
        //if (onItems) {
//            itemSelections.associateWith {
//                if (asPercent) {
//                    val factor = BigDecimal.ONE - discountValue.movePointLeft(2)
//                    discountedItems.associateWith { factor.multiply(BigDecimal(it.price), mathContext) }
//                } else {
//                    var discountedItemsTotal = BigDecimal.ZERO
//                    discountedItems.forEach { item ->
//                        discountedItemsTotal += BigDecimal(item.price)
//                    }
//
//                    discountedItems.associateWith {
//                        val itemPrice = BigDecimal(it.price)
//                        itemPrice - (discountValue.multiply(itemPrice.divide(discountedItemsTotal, mathContext), mathContext))
//                    }
//                }
//            }
//        } else {
//            emptyMap()
//        }




        items.map { item ->
            Triple(
                item,
                itemSelections.contains(item),
                Triple(
                    currencyFormatter.format(item.price),
                    "NA", //discountedPrices[item]?.let { price -> currencyFormatter.format(price) },
                    when(item.diners.value.size) {
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
        _recipientSelections,
        repository.individualSubtotals,
        recipientShares,
        discountedSubtotals) { diners, dinerSelections, individualSubtotals, recipientShares, discountedSubtotals ->

        val currencyFormatter = NumberFormat.getCurrencyInstance()

        diners.map { diner ->
            val dinerItems = diner.items.value
            Triple(diner, dinerSelections.contains(diner), if(dinerSelections.contains(diner)) {
                when {
                    dinerItems.isEmpty() -> {
                        getApplication<Application>().resources.getString(
                            R.string.discountentry_fordiners_unused,
                            currencyFormatter.format(recipientShares.getOrDefault(diner, BigDecimal.ZERO)))
                    }
                    discountedSubtotals.getOrDefault(diner, BigDecimal.ZERO) >= BigDecimal.ZERO ||
                            currencyFormatter.format(-discountedSubtotals.getOrDefault(diner, BigDecimal.ZERO)) == currencyFormatter.format(0.0) -> {
                        getApplication<Application>().resources.getString(
                            R.string.discountentry_fordiners_fullyused,
                            currencyFormatter.format(BigDecimal.ZERO.max(discountedSubtotals.getOrDefault(diner, BigDecimal.ZERO))),
                            currencyFormatter.format(recipientShares.getOrDefault(diner, BigDecimal.ZERO)))
                    }
                    else -> {
                        getApplication<Application>().resources.getString(
                            R.string.discountentry_fordiners_partiallyused,
                            currencyFormatter.format(BigDecimal.ZERO.max(discountedSubtotals.getOrDefault(diner, BigDecimal.ZERO))),
                            currencyFormatter.format(recipientShares.getOrDefault(diner, BigDecimal.ZERO)))
                    }
                }
            } else {
                when(dinerItems.size) {
                    0 -> {
                        getApplication<Application>().resources.getString(R.string.discountentry_fordiners_zeroitems)
                    }
                    else -> {
                        getApplication<Application>().resources.getQuantityString(
                            R.plurals.discountentry_fordiners_items_with_subtotal,
                            dinerItems.size,
                            dinerItems.size,
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

        val debts: Map<Diner, BigDecimal> = if(cost > BigDecimal.ZERO && value > BigDecimal.ZERO) {
            emptyMap() //getDiscountReimbursementDebts(cost, value, recipientShares)
        }
        else {
            emptyMap()
        }

        val credits: Map<Diner, BigDecimal> = if(cost > BigDecimal.ZERO) {
            emptyMap() //getDiscountReimbursementCredits(cost, selections)
        } else {
            emptyMap()
        }

        val currencyFormatter = NumberFormat.getCurrencyInstance()

        diners.map { diner ->
            val message = if (diner in credits) {
                if(diner in debts) {
                    val netReimbursementNumerical = credits[diner]!! - debts[diner]!!
                    val netReimbursementString = currencyFormatter.format(netReimbursementNumerical.abs())

                    if(netReimbursementString == currencyFormatter.format(0.0)) {
                        getApplication<Application>().resources.getString(R.string.discountentry_reimbursement_neutral)
                    } else {
                        val fullDebtString = currencyFormatter.format(debts[diner])
                        val fullCreditString = currencyFormatter.format(credits[diner])

                        if (netReimbursementNumerical > BigDecimal.ZERO) {
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
    val recipientSelections: LiveData<Set<Diner>> = _recipientSelections.asLiveData(isOutputLocked)
    val reimburseeSelections: LiveData<Set<Diner>> = _reimburseeSelections.asLiveData(isOutputLocked)

    val fabIcon: LiveData<Int?> = combine(
        _isHandlingReimbursements,
        _isDiscountOnItems,
        _itemSelections,
        _recipientSelections,
        _reimburseeSelections) { handlingReimbursements, onItems, items, recipients, reimbursees ->

        if(handlingReimbursements) {
            if(reimbursees.isEmpty()) null else {
                if(onItems) {
                    if (items.isEmpty()) R.drawable.ic_arrow_back_light else  R.drawable.ic_arrow_forward
                } else {
                    if (recipients.isEmpty()) R.drawable.ic_arrow_back_light else  R.drawable.ic_arrow_forward
                }
            }
        } else {
            if(onItems) {
                if (items.isEmpty()) null else  R.drawable.ic_arrow_forward
            } else {
                if (recipients.isEmpty()) null else  R.drawable.ic_arrow_forward
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

    val toolbarInTertiaryState: LiveData<Boolean> = uiInTertiaryState.asLiveData(isOutputLocked)
    val discountBasisButtonState: LiveData<Pair<Boolean, Boolean>> = combine(_isDiscountOnItems, uiInTertiaryState) { onItems, inTertiary ->
        Pair(onItems, inTertiary)
    }.asLiveData(isOutputLocked)
    val clearItemsButtonDeemphasized: LiveData<Boolean> = _itemSelections.mapLatest { it.isEmpty() }.asLiveData(isOutputLocked)
    val clearDinersButtonDeemphasized: LiveData<Boolean> = _recipientSelections.mapLatest { it.isEmpty() }.asLiveData(isOutputLocked)
    val clearReimburseeButtonDeemphasized: LiveData<Boolean> = _reimburseeSelections.mapLatest { it.isEmpty() }.asLiveData(isOutputLocked)

    val priceNumberPadVisible: LiveData<Boolean> = priceCalcData.isNumberPadVisible.asLiveData(isOutputLocked)
    val isPriceInPercent: LiveData<Boolean> = priceCalcData.isInPercent.asLiveData(isOutputLocked)
    val formattedPrice: LiveData<String> = priceCalcData.displayValue.asLiveData(isOutputLocked)
    val priceBackspaceButtonVisible: LiveData<Boolean> = priceCalcData.backspaceButtonVisible.asLiveData(isOutputLocked)
    val priceEditButtonVisible: LiveData<Boolean> = priceCalcData.editButtonVisible.asLiveData(isOutputLocked)
    val priceZeroButtonEnabled: LiveData<Boolean> = priceCalcData.zeroButtonEnabled.asLiveData(isOutputLocked)
    val priceNonZeroButtonsEnabled: LiveData<Boolean> = priceCalcData.nonZeroButtonsEnabled.asLiveData(isOutputLocked)
    val priceDecimalButtonEnabled: LiveData<Boolean> = priceCalcData.decimalButtonEnabled.asLiveData(isOutputLocked)
    val priceAcceptButtonEnabled: LiveData<Boolean> = priceCalcData.acceptButtonEnabled.asLiveData(isOutputLocked)

    val costNumberPadVisible: LiveData<Boolean> = costCalcData.isNumberPadVisible.asLiveData(isOutputLocked)
    val formattedCost: LiveData<String> = costCalcData.displayValue.asLiveData(isOutputLocked)
    val costBackspaceButtonVisible: LiveData<Boolean> = costCalcData.backspaceButtonVisible.asLiveData(isOutputLocked)
    val costEditButtonVisible: LiveData<Boolean> = costCalcData.editButtonVisible.asLiveData(isOutputLocked)
    val costZeroButtonEnabled: LiveData<Boolean> = costCalcData.zeroButtonEnabled.asLiveData(isOutputLocked)
    val costNonZeroButtonsEnabled: LiveData<Boolean> = costCalcData.nonZeroButtonsEnabled.asLiveData(isOutputLocked)
    val costDecimalButtonEnabled: LiveData<Boolean> = costCalcData.decimalButtonEnabled.asLiveData(isOutputLocked)
    val costAcceptButtonEnabled: LiveData<Boolean> = costCalcData.acceptButtonEnabled.asLiveData(isOutputLocked)

    val selectAllItemsButtonDisabled: LiveData<Boolean> = combine(_itemSelections, repository.items) { selections, items ->
        selections.size == items.size
    }.asLiveData(isOutputLocked)
    val selectAllDinersButtonDisabled: LiveData<Boolean> = combine(_recipientSelections, repository.diners) { selections, diners ->
        selections.size == diners.size
    }.asLiveData(isOutputLocked)
    val selectAllReimburseesButtonDisabled: LiveData<Boolean> = combine(_reimburseeSelections, repository.diners) { selections, diners ->
        selections.size == diners.size
    }.asLiveData(isOutputLocked)

    val costAcceptEvents: LiveData<Event<String>> = costCalcData.acceptEvents.asLiveData()
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

    override fun onInitialize(input: String?) {
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
        recipientSelectionSet.clear()
        reimburseeSelectionSet.clear()

        _isHandlingReimbursements.value = false

        val discount = repository.getDiscount(input)

        if(discount == null) {
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
            loadedDiscount.value = discount

            priceCalcData.reset(
                if (discount.asPercentInput) {
                    CalculationType.ITEMIZED_DISCOUNT_PERCENT
                } else {
                    CalculationType.ITEMIZED_DISCOUNT_AMOUNT
                },
                discount.amountInput,
                false
            )
            costCalcData.reset(CalculationType.REIMBURSEMENT_AMOUNT, discount.costInput, false)

            _isDiscountOnItems.value = if (discount.onItemsInput) {
                itemSelectionSet.addAll(discount.items.value)
                editedRecipientSelections = if(itemSelectionSet.isNotEmpty()) false else null
                loadItemListFragmentEventMutable.value = Event(true)
                true
            } else {
                recipientSelectionSet.addAll(discount.recipients.value)
                editedRecipientSelections = if(recipientSelectionSet.isNotEmpty()) false else null
                loadDinerListFragmentEventMutable.value = Event(true)
                false
            }
            reimburseeSelectionSet.addAll(discount.purchasers.value)
            editedReimbursementSelections = if(reimburseeSelectionSet.isNotEmpty()) false else null
        }

        _itemSelections.value = itemSelectionSet.toSet()
        _recipientSelections.value = recipientSelectionSet.toSet()
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
                    recipientSelectionSet.isEmpty() -> {
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
    fun switchPriceToPercent() { priceCalcData.switchToPercent() }
    fun switchPriceToCurrency() { priceCalcData.switchToCurrency() }

    fun editPrice() {
        priceCalcData.clearValue()
        priceCalcData.showNumberPad()
    }
    fun addDigitToPrice(digit: Char) = priceCalcData.addDigit(digit)
    fun addDecimalToPrice() = priceCalcData.addDecimal()
    fun removeDigitFromPrice() = priceCalcData.removeDigit()
    fun resetPrice() = priceCalcData.clearValue()
    private fun revertToLastPrice() = priceCalcData.tryRevertToLastValue()
    fun acceptPrice() = priceCalcData.tryAcceptValue()

    fun editCost() {
        costCalcData.clearValue()
        costCalcData.showNumberPad()
    }
    fun addDigitToCost(digit: Char) = costCalcData.addDigit(digit)
    fun addDecimalToCost() = costCalcData.addDecimal()
    fun removeDigitFromCost() = costCalcData.removeDigit()
    fun resetCost() = costCalcData.clearValue()
    private fun revertToLastCost() = costCalcData.tryRevertToLastValue()
    fun cancelCost() {
        costCalcData.discardEntry()
        clearReimburseeSelections()
        switchToDiscountProperties()
    }
    //fun acceptCost() = costCalcData.tryAcceptValue()

    fun isDinerSelected(diner: Diner) = recipientSelectionSet.contains(diner)
    fun toggleDinerSelection(diner: Diner) {
        editedRecipientSelections = true

        if(recipientSelectionSet.contains(diner)) {
            recipientSelectionSet.remove(diner)
        } else {
            recipientSelectionSet.add(diner)
        }
        _recipientSelections.value = recipientSelectionSet.toSet()
    }
    fun selectAllDiners() {
        editedRecipientSelections = true

        repository.diners.value.apply {
            recipientSelectionSet.clear()
            recipientSelectionSet.addAll(this)
            _recipientSelections.value = recipientSelectionSet.toSet()
        }
    }
    fun clearDinerSelections() {
        editedRecipientSelections = true
        recipientSelectionSet.clear()
        _recipientSelections.value = recipientSelectionSet.toSet()
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
        val items = itemSelectionSet.toSet()
        val recipients = recipientSelectionSet.toSet()
        val cost = costCalcData.numericalValue.value
        val purchasers = reimburseeSelectionSet.toSet()

        when {
            price <= BigDecimal.ZERO -> { return INVALID_PRICE }
            onItems && items.isEmpty() -> { return MISSING_ITEMS }
            !onItems && recipients.isEmpty() -> { return MISSING_RECIPIENTS }
            cost > BigDecimal.ZERO && purchasers.isEmpty() -> { return MISSING_PURCHASERS }
            purchasers.isNotEmpty() && cost > BigDecimal.ZERO -> { return INVALID_COST }
            else -> {
                val editedDiscount = loadedDiscount.value
                finishFragment(
                    if (editedDiscount == null) {
                        // Creating a new discount
                        when {
                            asPercent && onItems -> {
                                repository.createNewDiscountForItemsByPercent(
                                    price,
                                    cost,
                                    items,
                                    purchasers)
                            }
                            asPercent && !onItems -> {
                                repository.createNewDiscountForDinersByPercent(
                                    price,
                                    cost,
                                    recipients,
                                    purchasers)
                            }
                            !asPercent && onItems -> {
                                repository.createNewDiscountForItemsByValue(
                                    price,
                                    cost,
                                    items,
                                    purchasers)
                            }
                            else -> {
                                repository.createNewDiscountForDinersByValue(
                                    price,
                                    cost,
                                    recipients,
                                    purchasers)
                            }
                        }
                    } else {
                        repository.editDiscount(
                            editedDiscount,
                            asPercent,
                            onItems,
                            price,
                            cost,
                            items,
                            recipients,
                            purchasers)
                        null
                    }
                )
                return DISCOUNT_SAVED
            }
        }
    }
}
