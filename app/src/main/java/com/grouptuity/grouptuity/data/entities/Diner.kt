package com.grouptuity.grouptuity.data.entities

import androidx.room.*
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal


@Entity(tableName = "diner_table",
    foreignKeys = [ ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Diner(@PrimaryKey val id: String,
            override val billId: String,
            val listIndex: Long,
            val lookupKey: String,
            var name: String,
            val addresses: MutableMap<PaymentMethod, String> = mutableMapOf(),
            val paymentTemplateMap: MutableMap<String, PaymentTemplate> = mutableMapOf()
): BillComponent() {

    constructor(id: String, billId: String, listIndex: Long, contact: Contact):
            this(id, billId, listIndex, contact.lookupKey, contact.name, contact.addresses) {
        photoUri = contact.photoUri
    }

    companion object {
        fun newCashPool(billId: String) = Diner(newUUID(), billId, -2, Contact.cashPool)

        fun newRestaurant(billId: String) = Diner(newUUID(), billId, -1, Contact.restaurant)
    }

    @Ignore var photoUri: String? = null
    @Ignore var emailAddresses: List<String> = emptyList()
    @Ignore val isCashPool: Boolean = lookupKey == Contact.GROUPTUITY_CASH_POOL_LOOKUPKEY
    @Ignore val isRestaurant: Boolean = lookupKey == Contact.GROUPTUITY_RESTAURANT_LOOKUPKEY
    @Ignore val isUser: Boolean = lookupKey == Contact.GROUPTUITY_USER_CONTACT_LOOKUPKEY

    @Ignore private val _paymentTemplatesFlow = MutableStateFlow(paymentTemplateMap.toMap())
    @Ignore val paymentTemplatesFlow: StateFlow<Map<String, PaymentTemplate>> = _paymentTemplatesFlow

    @Ignore private val mItems = CachedEntityMap<Item>(bill.itemRoundingModeFlow) {
        it.dinerPriceShare
    }
    @Ignore private val mDinerDiscounts = CachedEntityMap<Discount>(bill.discountRoundingModeFlow) {
        it.recipientDiscountShares[this]!!
    }
    @Ignore private val mItemDiscounts =
        CachedDoubleEntityMap<Discount, Item>(bill.discountRoundingModeFlow) { (discount, item) ->
            item.dinerDiscountShares[discount]!!
        }

    @Ignore private val mDinerDiscountReimbursementDebts =
        CachedEntityMap<Discount>(bill.itemRoundingModeFlow) { discount ->
            combine(
                discount.recipientDiscountShares[this]!!,
                discount.costNormalizedByValue
            ) { recipientShare, normalizedCost ->
                recipientShare.multiply(normalizedCost, mathContext)
            }
        }
    @Ignore private val mItemDiscountReimbursementDebts =
        CachedDoubleEntityMap<Discount, Item>(bill.itemRoundingModeFlow) { (discount, item) ->
            item.dinerDiscountReimbursementDebtShares[discount]!!
        }
    @Ignore private val mDiscountReimbursementCredits =
        CachedEntityMap<Discount>(bill.itemRoundingModeFlow) {
            it.reimbursementCreditorShare
    }
    @Ignore private val mDebtsOwed = CachedEntityMap<Debt>(bill.itemRoundingModeFlow) {
        it.debtorShare
    }
    @Ignore private val mDebtsHeld = CachedEntityMap<Debt>(bill.itemRoundingModeFlow) {
        it.creditorShare
    }

    @Ignore private val mPaymentsSent = CachedEntityMap<Payment> {
        it.amountFlow
    }
    @Ignore private val mPaymentsReceived = CachedEntityMap<Payment> {
        it.amountFlow
    }

    @Ignore val items: StateFlow<Set<Item>> = mItems.elements
    @Ignore val dinerDiscountsReceived: StateFlow<Set<Discount>> = mDinerDiscounts.elements
    @Ignore val itemDiscountsReceived: StateFlow<Set<Discount>> = mItemDiscounts.firstTypeElements
    @Ignore val discountsWithDebts: StateFlow<Set<Discount>> =
        combine(
            mDinerDiscountReimbursementDebts.elements,
            mItemDiscountReimbursementDebts.firstTypeElements
        ) { fromDiners, fromItems ->
            fromDiners union fromItems
        }.stateIn(scope, SharingStarted.WhileSubscribed(), emptySet())
    @Ignore val discountsPurchased: StateFlow<Set<Discount>> = mDiscountReimbursementCredits.elements
    @Ignore val debtsOwed: StateFlow<Set<Debt>> = mDebtsOwed.elements
    @Ignore val debtsHeld: StateFlow<Set<Debt>> = mDebtsHeld.elements
    @Ignore val paymentsSent: StateFlow<Set<Payment>> = mPaymentsSent.elements
    @Ignore val paymentsReceived: StateFlow<Set<Payment>> = mPaymentsReceived.elements

    @Ignore internal val rawSubtotal: StateFlow<BigDecimal> = mItems.rawTotal
    @Ignore private val roundedSubtotal: StateFlow<BigDecimal> = mItems.roundedTotal

    @Ignore internal val rawIntrinsicDiscountAmount: StateFlow<BigDecimal> =
        mDinerDiscounts.rawTotal.plus(mItemDiscounts.rawTotal)
    @Ignore private val roundedIntrinsicDiscountAmount: StateFlow<BigDecimal> =
        mDinerDiscounts.roundedTotal.plus(mItemDiscounts.roundedTotal)

    @Ignore internal val rawSurplusDiscountReleased: StateFlow<BigDecimal> =
        combine(rawSubtotal, rawIntrinsicDiscountAmount) { subtotal, discount ->
            BigDecimal.ZERO.max(discount - subtotal)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)
    @Ignore val roundedSurplusDiscountReleased: StateFlow<BigDecimal> =
        combine(
            rawSurplusDiscountReleased,
            roundedIntrinsicDiscountAmount
        ) { released, roundedAmount ->
            if (released > BigDecimal.ZERO) {
                roundedAmount
            } else {
                BigDecimal.ZERO
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore private val rawSurplusDiscountAcquired = MutableStateFlow(BigDecimal.ZERO)
    @Ignore val roundedSurplusDiscountAcquired: StateFlow<BigDecimal> =
        rawSurplusDiscountAcquired.asRounded(discountRoundingModeFlow)

    @Ignore val rawNetDiscount: StateFlow<BigDecimal> =
        combine(
            rawIntrinsicDiscountAmount,
            rawSurplusDiscountReleased,
            rawSurplusDiscountAcquired
        ) { intrinsic, released, acquired ->
            intrinsic - released + acquired
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)
    @Ignore val roundedNetDiscount: StateFlow<BigDecimal> =
        combine(
            roundedIntrinsicDiscountAmount,
            roundedSurplusDiscountReleased,
            roundedSurplusDiscountAcquired
        ) { intrinsic, released, acquired ->
            intrinsic - released + acquired
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore private val rawSubtotalWithDiscounts: StateFlow<BigDecimal> = rawSubtotal.minus(rawNetDiscount)

    @Ignore private val rawTaxAmount: StateFlow<BigDecimal> =
        combine(
            rawSubtotalWithDiscounts,
            bill.groupTaxPercent
        ) { subtotalWithDiscounts, taxPercent ->
            subtotalWithDiscounts.multiply(taxPercent.movePointLeft(2), mathContext)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore private val rawSubtotalWithDiscountsAndTax = rawSubtotalWithDiscounts.plus(rawTaxAmount)

    @Ignore private val basisForTip: StateFlow<BigDecimal> =
        combine(
            rawSubtotal,
            rawSubtotalWithDiscounts,
            rawTaxAmount,
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

    @Ignore private val rawTipAmount: StateFlow<BigDecimal> =
        combine(basisForTip, bill.groupTipPercent) { basis, tipPercent ->
            basis.multiply(tipPercent.movePointLeft(2), mathContext)
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore internal val rawRestaurantTotal = rawSubtotalWithDiscountsAndTax.plus(rawTipAmount)
    @Ignore internal val roundedRestaurantTotal = rawRestaurantTotal.asRounded(itemRoundingModeFlow)
    @Ignore internal val roundedRestaurantTotalError = roundedRestaurantTotal.minus(rawRestaurantTotal)

    @Ignore private val groupTotalCorrection = MutableStateFlow(BigDecimal.ZERO)
    @Ignore val displayedRestaurantTotal = roundedRestaurantTotal.plus(groupTotalCorrection)

    @Ignore val displayedSubtotal = rawSubtotal.asRounded(itemRoundingModeFlow)
    @Ignore val displayedSubtotalRoundingAdjustment = displayedSubtotal.minus(roundedSubtotal)
    @Ignore val displayedDiscountAmount = rawNetDiscount.asRounded(bill.discountRoundingModeFlow)
    @Ignore val displayedDiscountRoundingAdjustment =
        displayedDiscountAmount.minus(roundedNetDiscount)
    @Ignore val displayedSubtotalWithDiscounts = displayedSubtotal.plus(displayedDiscountAmount)
    @Ignore val displayedTaxAmount = rawTaxAmount.asRounded(taxRoundingModeFlow)
    @Ignore val displayedSubtotalWithDiscountsAndTax =
        displayedSubtotalWithDiscounts.plus(displayedTaxAmount)
    @Ignore val displayedTipAmount = rawTipAmount.asRounded(tipRoundingModeFlow)
    @Ignore val displayedSubtotalWithDiscountsTaxAndTip =
        displayedSubtotalWithDiscountsAndTax.plus(displayedTipAmount)
    @Ignore val displayedRestaurantTotalRoundingAdjustment =
        displayedRestaurantTotal.minus(displayedSubtotalWithDiscountsTaxAndTip)

    @Ignore val reimbursementTotal = CachedEntityMapTotaler(
        itemRoundingModeFlow,
        null,
        arrayOf(
            mDinerDiscountReimbursementDebts,
            mItemDiscountReimbursementDebts,
            mDiscountReimbursementCredits)) {
        it[0] + it[1] - it[2]
    }

    @Ignore val standaloneDebtsTotal = CachedEntityMapTotaler(
        itemRoundingModeFlow,
        null,
        arrayOf(mDebtsOwed, mDebtsHeld)) {
        it[0] - it[1]
    }

    @Ignore val rawGrandTotal = combine(
        rawRestaurantTotal,
        reimbursementTotal.raw,
        standaloneDebtsTotal.raw
    ) { restaurant, reimbursement, debt ->
        restaurant + reimbursement + debt
    }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)
    @Ignore internal val roundedGrandTotal = rawGrandTotal.asRounded(itemRoundingModeFlow)
    @Ignore internal val roundedGrandTotalError = roundedGrandTotal.minus(rawGrandTotal)

    @Ignore val roundedSumsGrandTotal = combine(
        displayedRestaurantTotal,
        reimbursementTotal.displayed,
        standaloneDebtsTotal.displayed
    ) { restaurant, reimbursement, debt ->
        restaurant + reimbursement + debt
    }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore private val intraDinerCorrection = MutableStateFlow(BigDecimal.ZERO)
    @Ignore val displayedGrandTotal = roundedGrandTotal.plus(intraDinerCorrection)
    @Ignore val displayedGrandTotalRoundingAdjustment = displayedGrandTotal.minus(roundedSumsGrandTotal)

    @Ignore val netIntraDinerAmount = displayedGrandTotal.minus(displayedRestaurantTotal)

    init {
        scope.launch {
            bill.discountRedistributionAcquisitions.collect {
                it[this@Diner]?.apply {
                    rawSurplusDiscountAcquired.value = this
                }
            }

            bill.restaurantRoundingAdjustments.collect {
                groupTotalCorrection.value = it[this@Diner] ?: BigDecimal.ZERO
            }

            bill.intraDinerRoundingAdjustments.collect {
                intraDinerCorrection.value = it[this@Diner] ?: BigDecimal.ZERO
            }
        }
    }

    override fun onDelete() {
        val dinerAsSet = setOf(this)
        mItems.elements.value.forEach { item ->
            item.removeDiners(dinerAsSet)
        }
        mDinerDiscounts.elements.value.forEach { discount ->
            discount.removeRecipients(dinerAsSet)
        }
        mDiscountReimbursementCredits.elements.value.forEach { discount ->
            discount.removePurchasers(dinerAsSet)
        }
        mDebtsOwed.elements.value.forEach { debt ->
            debt.removeDebtor(this)
        }
        mDebtsHeld.elements.value.forEach { debt ->
            debt.removeCreditor(this)
        }
        mPaymentsSent.elements.value.forEach { payment ->
            TODO()
        }
        mPaymentsReceived.elements.value.forEach { payment ->
            TODO()
        }
    }

    internal fun onItemAdded(item: Item) {
        mItems.add(item)
        item.discounts.value.forEach { discount ->
            onItemDiscountAdded(discount, item)
        }
    }
    internal fun onItemRemoved(item: Item) {
        mItems.remove(item)
        item.discounts.value.forEach { discount ->
            onItemDiscountRemoved(discount, item)
        }
    }

    internal fun onDinerDiscountAdded(discount: Discount) {
        mDinerDiscounts.add(discount)
        mDinerDiscountReimbursementDebts.add(discount)
    }
    internal fun onDinerDiscountRemoved(discount: Discount) {
        mDinerDiscounts.remove(discount)
        mDinerDiscountReimbursementDebts.remove(discount)
    }

    internal fun onItemDiscountAdded(discount: Discount, item: Item) {
        mItemDiscounts.add(Pair(discount, item))
        mItemDiscountReimbursementDebts.add(Pair(discount, item))
    }
    internal fun onItemDiscountRemoved(discount: Discount, item: Item) {
        mItemDiscounts.remove(Pair(discount, item))
        mItemDiscountReimbursementDebts.remove(Pair(discount, item))
    }

    internal fun onPurchasedDiscountAdded(discount: Discount) {
        mDiscountReimbursementCredits.add(discount)
    }
    internal fun onPurchasedDiscountRemoved(discount: Discount) {
        mDiscountReimbursementCredits.remove(discount)
    }

    fun onOwedDebtAdded(debt: Debt) {
        mDebtsOwed.add(debt)
    }
    fun onOwedDebtRemoved(debt: Debt) {
        mDebtsOwed.remove(debt)
    }

    fun onHeldDebtAdded(debt: Debt) {
        mDebtsHeld.add(debt)
    }
    fun onHeldDebtRemoved(debt: Debt) {
        mDebtsHeld.remove(debt)
    }

    fun onPaymentSent(payment: Payment) {
        mPaymentsSent.add(payment)
    }
    fun onPaymentReceived(payment: Payment) {
        mPaymentsReceived.add(payment)
    }

    fun asContact() = Contact(lookupKey, name, Contact.VISIBLE, addresses).also {
        it.photoUri = photoUri
    }

    fun applyContactInfo(info: AddressBook.Companion.ContactInfo) {
        name = info.name
        photoUri = info.photoUri
        emailAddresses = info.emailAddresses
    }

    fun getAddressForMethod(method: PaymentMethod) = addresses[method]

    fun setAddressForMethod(method: PaymentMethod, alias: String?) {
        if (alias == null) {
            addresses.remove(method)
        } else {
            addresses[method] = alias
        }
    }

    fun getPaymentTemplate(payee: Diner) = paymentTemplateMap[payee.id]

    fun setPaymentTemplate(template: PaymentTemplate) {
        paymentTemplateMap[template.payeeId] = template
        _paymentTemplatesFlow.value = paymentTemplateMap.toMap()
    }
}