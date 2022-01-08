package com.grouptuity.grouptuity.data.entities

import androidx.room.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal


@Entity(tableName = "item_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Item(
    @PrimaryKey val id: String,
    override val billId: String,
    val listIndex: Long,
    var name: String
): BillComponent() {
    var priceInput = "0"
        set(value) {
            field = value
            _price.value = BigDecimal(value)
        }
    @Ignore private val _price = MutableStateFlow(BigDecimal.ZERO)
    @Ignore val price: StateFlow<BigDecimal> = _price

    @Ignore private val mDiners = mutableSetOf<Diner>()
    @Ignore private val _diners = MutableStateFlow<Set<Diner>>(mDiners)
    @Ignore val diners: StateFlow<Set<Diner>> = _diners
    @Ignore val numDiners: StateFlow<Int> = diners.size()
    @Ignore val dinerPriceShare = price.divide(numDiners)

    @Ignore private val mItemDiscountShares =
        CachedEntityMap<Discount>(bill.discountRoundingModeFlow) {
            it.itemDiscountShares[this]!!
        }
    @Ignore private val mDinerItemDiscountShares =
        mItemDiscountShares.transformRawValuesIntoMap { _, itemDiscountShare ->
            itemDiscountShare.divide(numDiners)
        }
    @Ignore private val mDinerItemDiscountReimbursementDebtShares =
        mItemDiscountShares.transformRawValuesIntoMap { discount, itemDiscountShare ->
            combine(
                discount.costNormalizedByValue,
                itemDiscountShare,
                numDiners
            ) { normalizedCost, itemShare, num ->
                normalizedCost.multiply(itemShare).divideWithZeroBypass(num)
            }
        }

    @Ignore val dinerDiscountShares: Map<Discount, StateFlow<BigDecimal>> =
        mDinerItemDiscountShares
    @Ignore val dinerDiscountReimbursementDebtShares: Map<Discount, StateFlow<BigDecimal>> =
        mDinerItemDiscountReimbursementDebtShares

    @Ignore val discounts = mItemDiscountShares.elements

    override fun onDelete() {
        removeDiners(mDiners)
        val itemAsSet = setOf(this)
        discounts.value.forEach { discount ->
            discount.removeItems(itemAsSet)
        }
    }

    fun addDiners(dinersToAdd: Set<Diner>) {
        val newDiners = dinersToAdd - mDiners
        if (newDiners.isNotEmpty()) {
            mDiners.addAll(newDiners)
            _diners.value = mDiners.toSet()
            newDiners.forEach { it.onItemAdded(this) }
        }
    }
    fun removeDiners(dinersToRemove: Set<Diner>) {
        val removedDiners = mDiners intersect dinersToRemove
        if (removedDiners.isNotEmpty()) {
            mDiners.removeAll(removedDiners)
            _diners.value = mDiners.toSet()
            removedDiners.forEach { it.onItemRemoved(this) }
        }
    }
    fun setDiners(dinersToSet: Set<Diner>) {
        removeDiners(mDiners - dinersToSet)
        addDiners(dinersToSet - mDiners)
    }

    fun onDiscountAdded(discount: Discount) {
        mItemDiscountShares.add(discount)
        mDiners.forEach {
            it.onItemDiscountAdded(discount, this)
        }
    }
    fun onDiscountRemoved(discount: Discount) {
        mItemDiscountShares.remove(discount)
        mDiners.forEach {
            it.onItemDiscountRemoved(discount, this)
        }
    }
}