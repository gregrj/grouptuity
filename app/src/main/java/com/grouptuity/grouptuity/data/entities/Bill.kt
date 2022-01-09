package com.grouptuity.grouptuity.data.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.grouptuity.grouptuity.data.StoredPreference
import com.grouptuity.grouptuity.data.TransactionProcessor
import com.grouptuity.grouptuity.data.dao.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import java.util.Collections.sort

val billIdMap = mutableMapOf<String, WeakReference<Bill>>()


/**
 * A Bill object represents the calculation of payments associated with a meal at a restaurant.
 *
 * Bill objects contain metadata relevant to the payment calculation, such as a timestamp for the
 * meal and rules for handling tax and tip. The {@code id} field is referenced in the other
 * datastore entities so that SQL queries can retrieve objects associated with this Bill.
 */
@Entity(tableName = "bill_table")
class Bill(
    @PrimaryKey val id: String,
    var title: String,
    val timeCreated: Long
): BaseEntity() {
    init {
        billIdMap[id] = WeakReference(this)
    }

    var currencyCode: String = StoredPreference.defaultCurrencyCode.value
        set(value) {
            field = value
            // TODO reject changes if defaultFractionDigits changes?
            _currencyFlow.value = Currency.getInstance(value)
        }
    var taxInput: String = StoredPreference.defaultTaxPercent.value
        set(value) {
            field = value
            taxInputFlow.value = BigDecimal(value)
        }
    var taxAsPercent: Boolean = true
        set(value) {
            field = value
            taxAsPercentFlow.value = value
        }
    var tipInput: String = StoredPreference.defaultTipPercent.value
        set(value) {
            field = value
            tipInputFlow.value = BigDecimal(value)
        }
    var tipAsPercent: Boolean = true
        set(value) {
            field = value
            tipAsPercentFlow.value = value
        }
    var isTaxTipped: Boolean = StoredPreference.taxIsTipped.value
        set(value) {
            field = value
            _isTaxTippedFlow.value = value
        }
    var discountsReduceTip: Boolean = StoredPreference.discountsReduceTip.value
        set(value) {
            field = value
            _discountsReduceTipFlow.value = value
        }
    var itemRoundingModeInput: String = StoredPreference.discountRoundingMode.value
        set(value) {
            field = value
            _itemRoundingModeFlow.value = RoundingMode.valueOf(value)
        }
    var discountRoundingModeInput: String = StoredPreference.discountRoundingMode.value
        set(value) {
            field = value
            _discountRoundingModeFlow.value = RoundingMode.valueOf(value)
        }
    var taxRoundingModeInput: String = StoredPreference.taxRoundingMode.value
        set(value) {
            field = value
            _taxRoundingModeFlow.value = RoundingMode.valueOf(value)
        }
    var tipRoundingModeInput: String = StoredPreference.tipRoundingMode.value
        set(value) {
            field = value
            _tipRoundingModeFlow.value = RoundingMode.valueOf(value)
        }

    @Ignore private val _currencyFlow = MutableStateFlow(Currency.getInstance(currencyCode))
    @Ignore private val _isTaxTippedFlow = MutableStateFlow(isTaxTipped)
    @Ignore private val _discountsReduceTipFlow = MutableStateFlow(discountsReduceTip)
    @Ignore private val _itemRoundingModeFlow = MutableStateFlow(RoundingMode.valueOf(itemRoundingModeInput))
    @Ignore private val _discountRoundingModeFlow = MutableStateFlow(RoundingMode.valueOf(discountRoundingModeInput))
    @Ignore private val _taxRoundingModeFlow = MutableStateFlow(RoundingMode.valueOf(taxRoundingModeInput))
    @Ignore private val _tipRoundingModeFlow = MutableStateFlow(RoundingMode.valueOf(tipRoundingModeInput))
    @Ignore private val taxInputFlow = MutableStateFlow(BigDecimal(taxInput))
    @Ignore private val taxAsPercentFlow = MutableStateFlow(taxAsPercent)
    @Ignore private val tipInputFlow = MutableStateFlow(BigDecimal(tipInput))
    @Ignore private val tipAsPercentFlow = MutableStateFlow(tipAsPercent)

    @Ignore public override val currencyFlow: StateFlow<Currency> = _currencyFlow
    @Ignore public override val isTaxTippedFlow: StateFlow<Boolean> = _isTaxTippedFlow
    @Ignore public override val discountsReduceTipFlow: StateFlow<Boolean> = _discountsReduceTipFlow
    @Ignore public override val itemRoundingModeFlow: StateFlow<RoundingMode> = _itemRoundingModeFlow
    @Ignore public override val discountRoundingModeFlow: StateFlow<RoundingMode> = _discountRoundingModeFlow
    @Ignore public override val taxRoundingModeFlow: StateFlow<RoundingMode> = _taxRoundingModeFlow
    @Ignore public override val tipRoundingModeFlow: StateFlow<RoundingMode> = _tipRoundingModeFlow

    @Ignore private var nextDinerListIndex = 0L
        get() = ++field
    @Ignore private var nextItemListIndex = 0L
        get() = ++field
    @Ignore private var nextDiscountListIndex = 0L
        get() = ++field
    @Ignore private var nextDebtListIndex = 0L
        get() = ++field
    @Ignore private var nextPaymentStableId = 0L
        get() = ++field
    @Ignore private val paymentStableIdMap = mutableMapOf<String, Long>()

    @Ignore private val mDinerLookupKeys = mutableSetOf<String>()
    @Ignore private val mDiners = CachedEntityMap<Diner> {
        it.roundedRestaurantTotal
    }
    @Ignore val diners: StateFlow<Set<Diner>> = mDiners.elements
    @Ignore private val mSurplusDiscountsReleased = CachedEntityMap<Diner> {
        it.rawSurplusDiscountReleased
    }
    @Ignore private val mDinersUnadjustedGrandTotals = CachedEntityMap<Diner> {
        it.roundedGrandTotal
    }

    @Ignore private val mItems = CachedEntityMap<Item>(itemRoundingModeFlow) {
        it.price
    }
    @Ignore private val mDiscounts = CachedEntityMap<Discount>(discountRoundingModeFlow) {
        it.currencyValue
    }
    @Ignore private val mDebts = mutableSetOf<Debt>()
    @Ignore private val mCommittedPayments = mutableSetOf<Payment>()

    @Ignore private val _debts = MutableStateFlow(mDebts.toSet())
    @Ignore private val _committedPayments = MutableStateFlow(mCommittedPayments.toSet())

    @Ignore val items: StateFlow<Set<Item>> = mItems.elements
    @Ignore val discounts: StateFlow<Set<Discount>> = mDiscounts.elements
    @Ignore val debts: StateFlow<Set<Debt>> = _debts
    @Ignore val committedPayments: StateFlow<Set<Payment>> = _committedPayments

    @Ignore val groupSubtotal: StateFlow<BigDecimal> = mItems.roundedTotal

    @Ignore val groupDiscountAmount: StateFlow<BigDecimal> = mDiscounts.roundedTotal
    @Ignore val groupDiscardedDiscountAmount: StateFlow<BigDecimal> =
        combine(groupSubtotal, groupDiscountAmount) { subtotal, discountAmount ->
            BigDecimal.ZERO.max(discountAmount - subtotal)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore private val _discountRedistributionAcquisitions: MutableStateFlow<Map<Diner, BigDecimal>> =
        MutableStateFlow(emptyMap())
    @Ignore val discountRedistributionAcquisitions: StateFlow<Map<Diner, BigDecimal>> = _discountRedistributionAcquisitions

    @Ignore val groupSubtotalWithDiscounts: StateFlow<BigDecimal> =
    combine(groupSubtotal, groupDiscountAmount) { subtotal, discountAmount ->
        BigDecimal.ZERO.max(subtotal - discountAmount)
    }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore val groupTaxAmount: StateFlow<BigDecimal> =
        combine(
            groupSubtotalWithDiscounts,
            taxInputFlow,
            taxAsPercentFlow,
            currencyFlow,
            taxRoundingModeFlow
        ) { subtotalWithDiscounts, taxInput, asPercent, currency, roundingMode ->
            if(asPercent) {
                subtotalWithDiscounts
                    .multiply(taxInput.movePointLeft(2), mathContext)
                    .setScale(currency.defaultFractionDigits, roundingMode)
            } else {
                taxInput
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)
    @Ignore val groupTaxPercent: StateFlow<BigDecimal> =
        combine(
            groupSubtotalWithDiscounts,
            taxInputFlow,
            taxAsPercentFlow
        ) { subtotalWithDiscounts, taxInput, asPercent ->
            if(asPercent) {
                taxInput
            } else {
                taxInput.divide(subtotalWithDiscounts, mathContext).movePointRight(2)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore val groupSubtotalWithDiscountsAndTax: StateFlow<BigDecimal> = groupSubtotalWithDiscounts.plus(groupTaxAmount)

    @Ignore private val basisForTip: StateFlow<BigDecimal> =
        combine(
            groupSubtotal,
            groupSubtotalWithDiscounts,
            groupTaxAmount,
            isTaxTippedFlow,
            discountsReduceTipFlow
        ) { subtotal, subtotalWithDiscounts, tax, taxTipped, reducedByDiscounts ->
            if (reducedByDiscounts) {
                if (taxTipped) {
                    subtotalWithDiscounts + tax
                } else {
                    subtotalWithDiscounts
                }
            } else {
                if (taxTipped) {
                    subtotal + tax
                } else {
                    subtotal
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore val groupTipAmount: StateFlow<BigDecimal> =
        combine(
            basisForTip,
            tipInputFlow,
            tipAsPercentFlow,
            currencyFlow,
            tipRoundingModeFlow,
        ) { basis, tip, asPercent, currency, roundingMode ->
            if(asPercent) {
                basis
                    .multiply(tip.movePointLeft(2), mathContext)
                    .setScale(currency.defaultFractionDigits, roundingMode)
            } else {
                tip
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)
    @Ignore val groupTipPercent: StateFlow<BigDecimal> =
        combine(
            basisForTip,
            tipInputFlow,
            tipAsPercentFlow
        ) { basis, tipInput, asPercent ->
            if(asPercent) {
                tipInput
            } else {
                tipInput.divide(basis, mathContext).movePointRight(2)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore val groupTotal: StateFlow<BigDecimal> = groupSubtotalWithDiscountsAndTax.plus(groupTipAmount)

    @Ignore val restaurantTotalErrors = diners.assemble(scope) { it.roundedRestaurantTotalError }

    @Ignore val mRestaurantRoundingAdjustments = mutableMapOf<Diner, BigDecimal>()
    @Ignore private val _restaurantRoundingAdjustments: MutableStateFlow<Map<Diner, BigDecimal>> =
        MutableStateFlow(mRestaurantRoundingAdjustments.toMap())
    @Ignore val restaurantRoundingAdjustments: StateFlow<Map<Diner, BigDecimal>> =
        _restaurantRoundingAdjustments

    @Ignore val mIntraDinerRoundingAdjustments = mutableMapOf<Diner, BigDecimal>()
    @Ignore private val _intraDinerRoundingAdjustments: MutableStateFlow<Map<Diner, BigDecimal>> =
        MutableStateFlow(mIntraDinerRoundingAdjustments.toMap())
    @Ignore val intraDinerRoundingAdjustments: StateFlow<Map<Diner, BigDecimal>> =
        _intraDinerRoundingAdjustments

    @Ignore var cashPool: Diner = Diner.newCashPool(id).also { mDinerLookupKeys.add(it.lookupKey) }
        private set(value) {
            mDinerLookupKeys.remove(field.lookupKey)
            field = value
            mDinerLookupKeys.add(value.lookupKey)
        }
    @Ignore var restaurant: Diner = Diner.newRestaurant(id).also { mDinerLookupKeys.add(it.lookupKey) }
        private set(value) {
            mDinerLookupKeys.remove(field.lookupKey)
            field = value
            mDinerLookupKeys.add(value.lookupKey)
        }
    @Ignore var userDiner: Diner? = null
        private set(value) {
            field = value
            _isUserOnBill.value = value != null
        }
    @Ignore private val _isUserOnBill = MutableStateFlow(false)
    @Ignore val isUserOnBill: StateFlow<Boolean> = _isUserOnBill

    @Ignore private val transactionProcessor = TransactionProcessor(
        scope,
        id,
        cashPool,
        restaurant,
        diners,
        committedPayments,
        restaurantTotalErrors
    )
    @Ignore val newPaymentsMap: StateFlow<Map<Diner, List<Payment>>> =
        transactionProcessor.newPayments

    @Ignore private val uncommittedPayments: StateFlow<List<Payment>> = newPaymentsMap.map {
        it.flatMap { (_, dinerPayments) ->
            dinerPayments.also { payments ->
                payments.forEach { payment ->
                    if (payment.committed) {
                        if (!paymentStableIdMap.containsKey(payment.id)) {
                            payment.stableId = nextPaymentStableId
                            paymentStableIdMap[payment.id] = payment.stableId
                        }
                    } else {
                        if (!paymentStableIdMap.containsKey(payment.payerId + payment.payeeId)) {
                            payment.stableId = nextPaymentStableId
                            paymentStableIdMap[payment.payerId + payment.payeeId] = payment.stableId
                        }
                    }
                }
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())
    @Ignore val hasUnprocessedPayments: StateFlow<Boolean> = uncommittedPayments.map {
        it.isNotEmpty()
    }.stateIn(scope, SharingStarted.WhileSubscribed(), false)

    @Ignore val allPayments: StateFlow<List<Payment>> =
        combine(
            committedPayments,
            uncommittedPayments
        ) { committed, uncommitted ->
            (committed + uncommitted).sortedWith { a, b ->
                val firstDiner = if (a.payer == cashPool) a.payee else a.payer
                val secondDiner = if (b.payer == cashPool) b.payee else b.payer
                when {
                    firstDiner.listIndex < secondDiner.listIndex -> { -1 }
                    firstDiner.listIndex > secondDiner.listIndex -> { 1 }
                    a.payee == restaurant && b.payee != restaurant -> { -1 }
                    a.payee != restaurant && b.payee == restaurant -> { 1 }
                    a.payee == cashPool && b.payee != cashPool -> { -1 }
                    a.payee != cashPool && b.payee == cashPool -> { 1 }
                    a.payee.listIndex < b.payee.listIndex  -> { -1 }
                    a.payee.listIndex > b.payee.listIndex  -> { 1 }
                    else -> { 0 }
                }
            }

        }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    init {
        // TODO remove accessing of local variables
        scope.launch {
            // TODO needs to be triggered by individual diners swapping released amounts keeping rawTotal the same
            mSurplusDiscountsReleased.rawTotal.map {
                redistributeSurplusDiscount(it)
            }.collect()

            combine(
                groupTotal,
                mDiners.roundedTotal,
                restaurantTotalErrors
            ) { group, individual, errors ->
                _restaurantRoundingAdjustments.value =
                    applyAdjustments(group, individual, errors)
            }.collect()

            combine(
                groupTotal,
                mDinersUnadjustedGrandTotals.rawTotal,
                diners.assemble(this) { it.roundedGrandTotalError }
            ) { group, individual, errors ->
                _intraDinerRoundingAdjustments.value =
                    applyAdjustments(group, individual, errors)
            }.collect()
        }
    }

    override fun onDelete() {
        // TODO
    }

    fun loadEntities(
        cashPoolData: DinerLoadData,
        restaurantData: DinerLoadData,
        dinerData: Map<String, DinerLoadData>,
        itemData: Map<String, ItemLoadData>,
        discountData: Map<String, DiscountLoadData>,
        debtData: Map<String, DebtLoadData>,
        paymentData: Map<String, PaymentLoadData>,
    ) {
        cashPool = cashPoolData.diner
        restaurant = restaurantData.diner

        dinerData.values.map {
            mDiners.add(
                it.diner.also { diner ->
                    mDinerLookupKeys.add(diner.lookupKey)
                    if (diner.isUser) {
                        userDiner = diner
                    }
                }
            )
        }

        itemData.values.map {
            mItems.add(
                it.item.also { item ->
                    item.addDiners(
                        it.dinerIds.mapNotNull { dinerId ->
                            dinerData[dinerId]?.diner
                        }.toSet()
                    )
                }
            )
        }

        discountData.values.map {
            mDiscounts.add(
                it.discount.also { discount ->
                    if (discount.onItems.value) {
                        discount.addItems(
                            it.itemIds.mapNotNull { itemId ->
                                itemData[itemId]?.item
                            }.toSet()
                        )
                    } else {
                        discount.addRecipients(
                            it.recipientIds.mapNotNull { recipientId ->
                                dinerData[recipientId]?.diner
                            }.toSet()
                        )
                    }

                    discount.addPurchasers(
                        it.purchaserIds.mapNotNull { purchaserId ->
                            dinerData[purchaserId]?.diner
                        }.toSet()
                    )
                }
            )
        }

        mDebts.addAll(debtData.values.map {
            it.debt.also { debt ->
                debt.setCreditors( it.creditorIds.mapNotNull { creditorId ->
                    dinerData[creditorId]?.diner
                }.toSet())
                debt.setDebtors( it.debtorIds.mapNotNull { debtorId ->
                    dinerData[debtorId]?.diner
                }.toSet())
            }
        })

        val dinerIdMap = dinerData.mapValues { it.value.diner }
        mCommittedPayments.addAll(paymentData.values.map {
            it.payment.attachDiners(dinerIdMap)
            it.payment.stableId = nextPaymentStableId
            paymentStableIdMap[it.payment.id] = it.payment.stableId
            it.payment
        })

        _debts.value = mDebts.toSet()
        _committedPayments.value = mCommittedPayments.toSet()

        nextDinerListIndex =
            with(diners.value) { if (isEmpty()) 0 else this.maxOf { it.listIndex } }
        nextItemListIndex =
            with(items.value) { if (isEmpty()) 0 else this.maxOf { it.listIndex } }
        nextDiscountListIndex =
            with(discounts.value) { if (isEmpty()) 0 else this.maxOf { it.listIndex } }
        nextDebtListIndex =
            with(debts.value) { if (isEmpty()) 0 else this.maxOf { it.listIndex } }
    }

    fun createDiners(contacts: List<Contact>, includeWithEveryone: Boolean): List<Diner> {

        val existingDiners = diners.value

        val newDiners: List<Diner> = contacts.mapNotNull { contact ->
            if (contact.lookupKey in mDinerLookupKeys) {
                null
            } else {
                mDinerLookupKeys.add(contact.lookupKey)
                Diner(newUUID(), id, nextDinerListIndex, contact).also {
                    mDiners.add(it)
                    mSurplusDiscountsReleased.add(it)

                    if (it.isUser) {
                        userDiner = it
                    }
                }
            }
        }

        if (includeWithEveryone && existingDiners.isNotEmpty()) {
            val newDinersSet = newDiners.toSet()
            items.value.forEach { item ->
                if (item.diners.value == existingDiners) {
                    item.addDiners(newDinersSet)
                }
            }
            discounts.value.forEach { discount ->
                if (!discount.onItems.value && discount.recipients.value == existingDiners) {
                    discount.addRecipients(newDinersSet)
                }
                if (discount.purchasers.value == existingDiners) {
                    discount.addPurchasers(newDinersSet)
                }
            }
            mDebts.forEach { debt ->
                if (debt.debtors.value == existingDiners) {
                    debt.addDebtors(newDinersSet)
                }
                if (debt.creditors.value == existingDiners) {
                    debt.addCreditors(newDinersSet)
                }
            }
        }

        return newDiners
    }

    fun createItem(
        price: String,
        name: String,
        diners: Set<Diner>,
        applyEntireBillDiscounts: Boolean
    ): Item =
        Item(newUUID(), id, nextItemListIndex, name).also {
            it.priceInput = price
            it.addDiners(diners)

            // TODO apply entire bill discounts

            mItems.add(it)
        }
        
    fun createDiscountForItemsByPercent(
        percent: String,
        cost: String,
        items: Set<Item>,
        purchasers: Set<Diner>
    ): Discount = Discount(newUUID(), id, nextDiscountListIndex).also {
        it.asPercentInput = false
        it.onItemsInput = true
        it.amountInput = percent
        it.costInput = cost
        it.addItems(items)
        it.addPurchasers(purchasers)

        mDiscounts.add(it)
    }
    fun createDiscountForItemsByValue(
        value: String,
        cost: String,
        items: Set<Item>,
        purchasers: Set<Diner>
    ): Discount = Discount(newUUID(), id, nextDiscountListIndex).also {
        it.asPercentInput = false
        it.onItemsInput = true
        it.amountInput = value
        it.costInput = cost
        it.addItems(items)
        it.addPurchasers(purchasers)

        mDiscounts.add(it)
    }
    fun createDiscountForDinersByPercent(
        percent: String,
        cost: String,
        recipients: Set<Diner>,
        purchasers: Set<Diner>
    ): Discount = Discount(newUUID(), id, nextDiscountListIndex).also {
        it.asPercentInput = true
        it.onItemsInput = false
        it.amountInput = percent
        it.costInput = cost
        it.addRecipients(recipients)
        it.addPurchasers(purchasers)

        mDiscounts.add(it)
    }
    fun createDiscountForDinersByValue(
        value: String,
        cost: String,
        recipients: Set<Diner>,
        purchasers: Set<Diner>
    ): Discount = Discount(newUUID(), id, nextDiscountListIndex).also {
        it.asPercentInput = false
        it.onItemsInput = false
        it.amountInput = value
        it.costInput = cost
        it.addRecipients(recipients)
        it.addPurchasers(purchasers)

        mDiscounts.add(it)
    }

    fun createDebt(amount: String, name: String, debtors: Set<Diner>, creditors: Set<Diner>) =
        Debt(newUUID(), id, nextDebtListIndex, name).also {
            it.amountInput = amount
            it.addDebtors(debtors)
            it.addCreditors(creditors)

            mDebts.add(it)
            _debts.value = mDebts.toSet()
        }

    fun removeDiners(diners: Collection<Diner>) {
        diners.forEach { diner ->
            diner.delete()
            mDinerLookupKeys.remove(diner.lookupKey)
            mDiners.remove(diner)
            mSurplusDiscountsReleased.remove(diner)

            if (diner.isUser) {
                userDiner = null
            }
        }
    }
    fun removeItems(items: Collection<Item>) {
        items.forEach { item ->
            item.delete()
            mItems.remove(item)
        }
    }
    fun removeDiscounts(discounts: Collection<Discount>) {
        discounts.forEach { discount ->
            discount.delete()
            mDiscounts.remove(discount)
        }
    }
    fun removeDebts(debts: Collection<Debt>) {
        debts.forEach { debt ->
            debt.delete()
            mDebts.remove(debt)
        }
        _debts.value = mDebts.toSet()
    }

    fun commitPayment(payment: Payment) {
        payment.committed = true

        // Update paymentStableIdMap to use new key
        paymentStableIdMap.remove(payment.payerId + payment.payeeId)
        paymentStableIdMap[payment.id] = payment.stableId

        mCommittedPayments.add(payment)
        _committedPayments.value = mCommittedPayments.toSet()
    }
    fun unCommitPayment(payment: Payment) {
        payment.committed = false

        // Update paymentStableIdMap to use new key
        paymentStableIdMap.remove(payment.id)
        paymentStableIdMap[payment.payerId + payment.payeeId] = payment.stableId

        mCommittedPayments.remove(payment)
        _committedPayments.value = mCommittedPayments.toSet()
    }

    private fun redistributeSurplusDiscount(surplus: BigDecimal) {
        /** Excess discount value is lumped and distributed equally to all diners until the excess
         *  is exhausted or all individual subtotals are zero. Redistribution of discount value
         *  does not transfer liabilities pertaining to discount purchase cost reimbursement and
         *  does not offset inter-diner debts.
         **/

        var totalSurplusDiscount = surplus

        var remainingSubtotals = diners.value.filter {
            it.rawSurplusDiscountReleased.value <= BigDecimal.ZERO
        }.associateWith {
            it.rawSubtotal.value - it.rawIntrinsicDiscountAmount.value
        }

        val discountAcquisitions = mutableMapOf<Diner, BigDecimal>()

        while (totalSurplusDiscount > BigDecimal.ZERO && remainingSubtotals.isNotEmpty()) {
            val share = totalSurplusDiscount.divide(BigDecimal(remainingSubtotals.size), mathContext)

            val newRemainingSubtotals = mutableMapOf<Diner, BigDecimal>()
            remainingSubtotals.forEach { (diner, subtotal) ->
                if(subtotal > share) {
                    totalSurplusDiscount -= share
                    discountAcquisitions.merge(diner, share, BigDecimal::plus)
                    newRemainingSubtotals[diner] = subtotal - share
                }
                else {
                    totalSurplusDiscount -= subtotal
                    discountAcquisitions.merge(diner, subtotal, BigDecimal::plus)
                }
            }

            remainingSubtotals = newRemainingSubtotals
        }

        _discountRedistributionAcquisitions.value = discountAcquisitions
    }

    private fun applyAdjustments(
        groupTotal: BigDecimal,
        individualTotal: BigDecimal,
        individualErrors: Map<Diner, BigDecimal>
    ): Map<Diner, BigDecimal> {
        val netCorrection = groupTotal - individualTotal
        val incrementValue = netCorrection.ulp()
        val numIncrements = netCorrection.divide(incrementValue).toInt()

        val dinerSet = individualErrors.keys
        return when {
            (numIncrements == 0) -> {
                emptyMap()
            }
            dinerSet.size < 2 -> {
                val diner = dinerSet.toList()[0]
                mapOf(diner to incrementValue.multiply(BigDecimal(numIncrements)))
            }
            else -> {
                val errors = individualErrors.toList().toMutableList()

                val comparator =
                    compareBy<Pair<Diner, BigDecimal>> { it.second }.thenBy { it.first.listIndex }

                val adjustments = mutableMapOf<Diner, BigDecimal>()
                if(numIncrements > 0) {
                    for (i in 0 until numIncrements) {
                        sort(errors, comparator)
                        val (diner, error) = errors[0]

                        adjustments[diner] =
                            adjustments.getOrDefault(diner, BigDecimal.ZERO) + incrementValue
                        errors[0] = Pair(diner, error + incrementValue)
                    }
                } else {
                    val lastIndex = errors.size - 1
                    for (i in 0 until -numIncrements) {
                        sort(errors, comparator)
                        val (diner, error) = errors[lastIndex]

                        adjustments[diner] =
                            adjustments.getOrDefault(diner, BigDecimal.ZERO) - incrementValue
                        errors[lastIndex] = Pair(diner, error - incrementValue)
                    }
                }

                adjustments
            }
        }
    }
}