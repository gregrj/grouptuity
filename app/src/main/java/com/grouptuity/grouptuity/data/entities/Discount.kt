package com.grouptuity.grouptuity.data.entities

import androidx.room.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*


@Entity(tableName = "discount_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Discount(
    @PrimaryKey val id: String,
    override val billId: String,
    val listIndex: Long
): BillComponent() {

    var asPercentInput: Boolean = true
        set(value) {
            field = value
            _asPercent.value = value
        }
    var onItemsInput: Boolean = true
        set(value) {
            field = value
            _onItems.value = value
        }
    var amountInput: String = "0"
        set(value) {
            field = value
            _amount.value = BigDecimal(value)
        }
    var costInput:String  = "0"
        set(value) {
            field = value
            _cost.value = BigDecimal(value)
        }

    @Ignore private val _asPercent = MutableStateFlow(asPercentInput)
    @Ignore private val _onItems = MutableStateFlow(onItemsInput)
    @Ignore private val _amount = MutableStateFlow(BigDecimal.ZERO)
    @Ignore private val _cost = MutableStateFlow(BigDecimal.ZERO)
    @Ignore val asPercent: StateFlow<Boolean> = _asPercent
    @Ignore val onItems: StateFlow<Boolean> = _onItems
    @Ignore val amount: StateFlow<BigDecimal> = _amount
    @Ignore val cost: StateFlow<BigDecimal> = _cost

    @Ignore private val mItemPrices = CachedEntityMap<Item> {
        it.price
    }
    @Ignore val items: StateFlow<Set<Item>> = mItemPrices.elements

    @Ignore private val mRecipientSubtotals = CachedEntityMap<Diner> {
        it.rawSubtotal
    }
    @Ignore val recipients: StateFlow<Set<Diner>> = mRecipientSubtotals.elements
    @Ignore private val numRecipients: StateFlow<Int> = recipients.size()

    @Ignore private val mPurchasers = mutableSetOf<Diner>()
    @Ignore private val _purchasers = MutableStateFlow<Set<Diner>>(mPurchasers)
    @Ignore val purchasers: StateFlow<Set<Diner>> = _purchasers

    @Ignore val currencyValue: StateFlow<BigDecimal> =
        combine(
            asPercent,
            onItems,
            amount,
            mItemPrices.rawTotal,
            mRecipientSubtotals.rawTotal,
            bill.currencyFlow,
            bill.discountRoundingModeFlow
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
        }.stateIn(scope, SharingStarted.WhileSubscribed(), BigDecimal.ZERO)

    @Ignore val costNormalizedByValue: StateFlow<BigDecimal> =
        cost.divide(currencyValue)

    @Ignore val reimbursementCreditorShare: StateFlow<BigDecimal> =
        cost.divide(_purchasers.map { it.size })

    @Ignore val itemDiscountShares: Map<Item, StateFlow<BigDecimal>> =
        mItemPrices.transformRawValuesIntoMap { _, itemPrice ->
            combine(
                itemPrice,
                currencyValue,
                mItemPrices.rawTotal
            ) { price, discountValue, priceSum ->
                price.multiply(discountValue.divideWithZeroBypass(priceSum),mathContext)
            }
        }

    @Ignore val recipientDiscountShares: Map<Diner, StateFlow<BigDecimal>> =
        mRecipientSubtotals.transformRawValuesIntoMap { _, recipientSubtotal ->
            combine(
                asPercent,
                currencyValue,
                recipientSubtotal,
                mRecipientSubtotals.rawTotal,
                numRecipients
            ) { asPercent, discountValue, subtotal, subtotalSum, numRecipients ->
                if (asPercent) {
                    discountValue.multiply(subtotal.divideWithZeroBypass(subtotalSum), mathContext)
                } else {
                    discountValue.divideWithZeroBypass(numRecipients)
                }
            }
        }

    override fun onDelete() {
        removeItems(items.value)
        removeRecipients(recipients.value)
        removePurchasers(purchasers.value)
    }

    fun addItems(itemsToAdd: Set<Item>) {
        (itemsToAdd - items.value).forEach { newItem ->
            mItemPrices.add(newItem)
            newItem.onDiscountAdded(this)
        }
    }
    fun removeItems(itemsToRemove: Set<Item>) {
        (itemsToRemove intersect items.value).forEach { removedItem ->
            mItemPrices.remove(removedItem)
        }
    }
    fun setItems(itemsToSet: Set<Item>) {
        val existingItems = items.value
        removeItems(existingItems - itemsToSet)
        addItems(itemsToSet - existingItems)
    }

    fun addRecipients(recipientsToAdd: Set<Diner>) {
        (recipientsToAdd - recipients.value).forEach { newRecipient ->
            mRecipientSubtotals.add(newRecipient)
            newRecipient.onDinerDiscountAdded(this)
        }
    }
    fun removeRecipients(recipientsToRemove: Set<Diner>) {
        (recipientsToRemove intersect recipients.value).forEach { removedRecipient ->
            mRecipientSubtotals.remove(removedRecipient)
        }
    }
    fun setRecipients(recipientsToSet: Set<Diner>) {
        val existingRecipients = recipients.value
        removeRecipients(existingRecipients - recipientsToSet)
        addRecipients(recipientsToSet - existingRecipients)
    }
    
    fun addPurchasers(purchasersToAdd: Set<Diner>) {
        val newPurchasers = purchasersToAdd - mPurchasers
        if (newPurchasers.isNotEmpty()) {
            mPurchasers.addAll(newPurchasers)
            _purchasers.value = mPurchasers.toSet()
            newPurchasers.forEach { it.onPurchasedDiscountAdded(this) }
        }
    }
    fun removePurchasers(purchasersToRemove: Set<Diner>) {
        val removedPurchasers = mPurchasers intersect purchasersToRemove
        if (removedPurchasers.isNotEmpty()) {
            mPurchasers.removeAll(removedPurchasers)
            _purchasers.value = mPurchasers.toSet()
            removedPurchasers.forEach { it.onPurchasedDiscountRemoved(this) }
        }
    }
    fun setPurchasers(purchasersToSet: Set<Diner>) {
        removePurchasers(mPurchasers - purchasersToSet)
        addPurchasers(purchasersToSet - mPurchasers)
    }
}


fun getDiscountCurrencyValueOnItemsPercent(
    percent: BigDecimal,
    items: Collection<Item>,
    currencyDigits: Int,
    discountRoundingMode: RoundingMode
): BigDecimal {
    var itemSum = BigDecimal.ZERO
    items.forEach { item ->
        itemSum += item.price.value
    }

    return percent.movePointLeft(2)
        .multiply(itemSum, mathContext)
        .setScale(currencyDigits, discountRoundingMode)
}

fun getDiscountCurrencyValueOnDinersPercent(
    percent: BigDecimal,
    recipients: Collection<Diner>,
    currencyDigits: Int,
    discountRoundingMode: RoundingMode
): BigDecimal {
    var dinerSubtotalSum = BigDecimal.ZERO
    recipients.forEach { recipient ->
        dinerSubtotalSum += recipient.rawSubtotal.value
    }

    return percent.movePointLeft(2)
        .multiply(dinerSubtotalSum, mathContext)
        .setScale(currencyDigits, discountRoundingMode)
}