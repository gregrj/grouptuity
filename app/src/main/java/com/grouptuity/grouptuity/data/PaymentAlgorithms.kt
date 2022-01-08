package com.grouptuity.grouptuity.data
//
//import com.grouptuity.grouptuity.data.entities.Diner
//import java.math.BigDecimal
//import java.math.RoundingMode
//import java.util.*
//
//
//class BillCalculation(private val bill: Bill,
//                      private val cashPool: Diner,
//                      private val restaurant: Diner,
//                      private val diners: Collection<Diner>,
//                      private val items: Collection<Item>,
//                      private val debts: Collection<Debt>,
//                      private val discounts: Collection<Discount>,
//                      oldPayments: Collection<Payment>) {
//
//    private val allParticipants = diners.plus(cashPool).plus(restaurant)
//
//    val dinerRoundingAdjustments = diners.associateWith { BigDecimal.ZERO }.toMutableMap()
//    var newPaymentsMap: Map<Diner, List<Payment>> = emptyMap()
//    var sortedPayments: List<Payment> = emptyList()
//
//    var discountValues: Map<Discount, BigDecimal> = emptyMap()
//
//    var groupSubtotal: BigDecimal = BigDecimal.ZERO
//    var groupDiscountAmount: BigDecimal = BigDecimal.ZERO
//    var groupDiscardedDiscounts: BigDecimal = BigDecimal.ZERO
//    var groupSubtotalWithDiscounts: BigDecimal = BigDecimal.ZERO
//    var groupTaxAmount: BigDecimal = BigDecimal.ZERO
//    var groupTaxPercent: BigDecimal = BigDecimal.ZERO
//    var groupSubtotalWithDiscountsAndTax: BigDecimal = BigDecimal.ZERO
//    var groupTipAmount: BigDecimal = BigDecimal.ZERO
//    var groupTipPercent: BigDecimal = BigDecimal.ZERO
//    var groupTotal: BigDecimal = BigDecimal.ZERO
//
//    var individualSubtotals: Map<Diner, BigDecimal> = emptyMap()
//    var individualDiscountShares: Map<Diner, Map<Discount, BigDecimal>> = emptyMap()
//    var individualDiscountTotals: Map<Diner, BigDecimal> = emptyMap()
//    var individualExcessDiscountsReleased: Map<Diner, BigDecimal> = emptyMap()
//    var individualExcessDiscountsAcquired: Map<Diner, BigDecimal> = emptyMap()
//    var individualSubtotalsWithDiscounts: Map<Diner, BigDecimal> = emptyMap()
//    var individualReimbursementDebts: Map<Diner, Map<Discount, BigDecimal>> = emptyMap()
//    var individualReimbursementCredits: Map<Diner, Map<Discount, BigDecimal>> = emptyMap()
//    var individualDebtOwed: Map<Diner, Map<Debt, BigDecimal>> = emptyMap()
//    var individualDebtHeld: Map<Diner, Map<Debt, BigDecimal>> = emptyMap()
//    var individualTax: Map<Diner, BigDecimal> = emptyMap()
//    var individualSubtotalWithDiscountsAndTax: Map<Diner, BigDecimal> = emptyMap()
//    var individualTip: Map<Diner, BigDecimal> = emptyMap()
//    var individualTotal: Map<Diner, BigDecimal> = emptyMap()
//
//    init {
//
//
//        newPaymentsMap = generateNewPayments(netTransactionMap)
//
//        sortedPayments = (committedPayments + newPaymentsMap.flatMap { it.value }).sortedWith { a, b ->
//            val firstDiner = if (a.payer == cashPool) a.payee else a.payer
//            val secondDiner = if (b.payer == cashPool) b.payee else b.payer
//
//            when {
//                firstDiner != secondDiner -> {
//                    diners.indexOf(firstDiner) - diners.indexOf(secondDiner)
//                }
//                a.payee == restaurant && b.payee != restaurant -> {
//                    -1
//                }
//                a.payee != restaurant && b.payee == restaurant -> {
//                    1
//                }
//                a.payee == cashPool && b.payee != cashPool -> {
//                    -1
//                }
//                a.payee != cashPool && b.payee == cashPool -> {
//                    1
//                }
//                else -> {
//                    diners.indexOf(a.payee) - diners.indexOf(b.payee)
//                }
//            }
//        }
//    }
//
//
//    private fun processDiscounts(): Pair<TransactionMap, TransactionMap> {
//        // Calculate discount amounts
//        val discountTransactionMap = TransactionMap(allParticipants)
//        val reimbursementTransactionMap = TransactionMap(diners)
//        val mutableDiscountValues = mutableMapOf<Discount, BigDecimal>()
//        val mutableIndividualDiscountShares = mutableMapOf<Diner, MutableMap<Discount, BigDecimal>>()
//        val mutableIndividualReimbursementDebts = mutableMapOf<Diner, MutableMap<Discount, BigDecimal>>()
//        val mutableIndividualReimbursementCredits = mutableMapOf<Diner, MutableMap<Discount, BigDecimal>>()
//
//        discounts.forEach { discount ->
//            mutableDiscountValues[discount] = getDiscountCurrencyValue(discount, individualSubtotals)
//
//            val discountRecipientShares = getDiscountRecipientShares(discount, individualSubtotals)
//
//            if(BigDecimal(discount.cost).compareTo(BigDecimal.ZERO) == 0) {
//                discountRecipientShares.forEach { (recipient, recipientShare) ->
//                    mutableIndividualDiscountShares.getOrPut(recipient, { mutableMapOf() })[discount] = recipientShare
//                    discountTransactionMap.addTransaction(restaurant, recipient, recipientShare)
//                }
//            } else {
//                val normalizedReimbursementCost = BigDecimal(discount.cost).divide(mutableDiscountValues[discount]!!, mathContext)
//
//                discountRecipientShares.forEach { (recipient, recipientShare) ->
//                    mutableIndividualDiscountShares.getOrPut(recipient, { mutableMapOf() })[discount] = recipientShare
//                    discountTransactionMap.addTransaction(restaurant, recipient, recipientShare)
//
//                    mutableIndividualReimbursementDebts.getOrPut(recipient, { mutableMapOf() })[discount] =
//                        normalizedReimbursementCost.multiply(recipientShare, mathContext)
//                    val reimbursementShare = normalizedReimbursementCost.multiply(
//                        recipientShare.divide(
//                            BigDecimal(discount.purchasers.size),
//                            mathContext
//                        ),
//                        mathContext
//                    )
//                    discount.purchasers.forEach { purchaser ->
//                        reimbursementTransactionMap.addTransaction(recipient, purchaser, reimbursementShare)
//                    }
//                }
//
//                discount.purchasers.forEach { purchaser ->
//                    mutableIndividualReimbursementCredits.getOrPut(purchaser, { mutableMapOf() })[discount] =
//                        BigDecimal(discount.cost).divide(BigDecimal(discount.purchasers.size), mathContext)
//                }
//            }
//        }
//
//        discountValues = mutableDiscountValues
//
//        var discountValuesSum = BigDecimal.ZERO
//        discountValues.values.forEach { discountValuesSum += it }
//        groupDiscountAmount = discountValuesSum
//
//        individualDiscountShares = mutableIndividualDiscountShares
//        individualDiscountTotals = individualDiscountShares.mapValues {
//            var discountSum = BigDecimal.ZERO
//            it.value.values.forEach { discountSum += it }
//            discountSum
//        }
//        individualReimbursementDebts = mutableIndividualReimbursementDebts
//        individualReimbursementCredits = mutableIndividualReimbursementCredits
//        groupDiscardedDiscounts = BigDecimal.ZERO.max(groupDiscountAmount - groupSubtotal)
//        groupSubtotalWithDiscounts = BigDecimal.ZERO.max(groupSubtotal - groupDiscountAmount)
//
//        return Pair(discountTransactionMap, reimbursementTransactionMap)
//    }
//
//    private fun processDebts(): TransactionMap {
//        // Settle external debts between diners. Discount purchase reimbursements handled separately.
//
//        val debtTransactionMap = TransactionMap(diners)
//        val mutableIndividualDebtOwed = mutableMapOf<Diner, MutableMap<Debt, BigDecimal>>()
//        val mutableIndividualDebtHeld = mutableMapOf<Diner, MutableMap<Debt, BigDecimal>>()
//
//        debts.forEach { debt ->
//            val debtorShare = BigDecimal(debt.amount).divide(BigDecimal(debt.debtors.size), mathContext)
//            val creditorShare = BigDecimal(debt.amount).divide(BigDecimal(debt.creditors.size), mathContext)
//            val debtorCreditorTransactionAmount = debtorShare.divide(BigDecimal(debt.creditors.size), mathContext)
//
//            debt.debtors.forEach { debtor ->
//                mutableIndividualDebtOwed.getOrPut(debtor, { mutableMapOf() })[debt] = debtorShare
//
//                debt.creditors.forEach { creditor ->
//                    debtTransactionMap.addTransaction(debtor, creditor, debtorCreditorTransactionAmount)
//                }
//            }
//
//            debt.creditors.forEach { creditor ->
//                mutableIndividualDebtHeld.getOrPut(creditor, { mutableMapOf() })[debt] = creditorShare
//            }
//        }
//
//        individualDebtOwed = mutableIndividualDebtOwed
//        individualDebtHeld = mutableIndividualDebtHeld
//
//        return debtTransactionMap
//    }
//
//
//
//
//}
//
//fun roundAmount(amount: Double): BigDecimal {
//    // TODO dynamic currency
//    return BigDecimal(amount.toString()).setScale(
//        Currency.getInstance(Locale.getDefault()).defaultFractionDigits, RoundingMode.HALF_EVEN)
//}
//
//fun getDiscountedItemPrices(discountValue: BigDecimal,
//                            onItems: Boolean,
//                            asPercent: Boolean,
//                            discountedItems: Collection<Item>): Map<Item, BigDecimal> {
//    return if (onItems) {
//        if (asPercent) {
//            val factor = BigDecimal.ONE - discountValue.movePointLeft(2)
//            discountedItems.associateWith { factor.multiply(BigDecimal(it.price), mathContext) }
//        } else {
//            var discountedItemsTotal = BigDecimal.ZERO
//            discountedItems.forEach { item ->
//                discountedItemsTotal += BigDecimal(item.price)
//            }
//
//            discountedItems.associateWith {
//                val itemPrice = BigDecimal(it.price)
//                itemPrice - (discountValue.multiply(itemPrice.divide(discountedItemsTotal, mathContext), mathContext))
//            }
//        }
//    } else {
//        emptyMap()
//    }
//}
//
//fun getDiscountCurrencyValue(discount: Discount, dinerSubtotals: Map<Diner, BigDecimal>): BigDecimal {
//    return if (discount.asPercent) {
//        if (discount.onItems) {
//            getDiscountCurrencyOnItemsPercent(BigDecimal(discount.value), discount.items)
//        } else {
//            getDiscountCurrencyOnDinersPercent(BigDecimal(discount.value), discount.recipients, dinerSubtotals)
//        }
//    } else {
//        BigDecimal(discount.value).setScale(scale, discountRoundingMode)
//    }
//}

//
//fun getDiscountRecipientShares(discount: Discount, dinerSubtotals: Map<Diner, BigDecimal>): Map<Diner, BigDecimal> {
//    return if(discount.onItems) {
//        if(discount.asPercent) {
//            getDiscountRecipientSharesOnItemsPercent(BigDecimal(discount.value), discount.items)
//        } else {
//            getDiscountRecipientSharesOnItemsValue(BigDecimal(discount.value), discount.items)
//        }
//    } else {
//        if (discount.asPercent) {
//            getDiscountRecipientSharesOnDinersPercent(BigDecimal(discount.value), discount.recipients, dinerSubtotals)
//        } else {
//            getDiscountRecipientSharesOnDinersValue(BigDecimal(discount.value), discount.recipients)
//        }
//    }
//}
//fun getDiscountRecipientSharesOnItemsPercent(percent: BigDecimal, items: Collection<Item>): Map<Diner, BigDecimal> {
//    val dinerItemTotals = mutableMapOf<Diner, BigDecimal>()
//
//    items.forEach { item ->
//        val dinerShareOfItem = BigDecimal(item.price).divide(BigDecimal(item.diners.size), mathContext)
//        item.diners.map {
//            dinerItemTotals[it] = dinerItemTotals.getOrDefault(it, BigDecimal.ZERO) + dinerShareOfItem
//        }
//    }
//
//    val fraction = percent.movePointLeft(2)
//
//    return dinerItemTotals.mapValues { fraction * it.value }
//}
//fun getDiscountRecipientSharesOnItemsValue(value: BigDecimal, items: Collection<Item>): Map<Diner, BigDecimal> {
//    var itemTotal = BigDecimal.ZERO
//    val dinerItemTotals = mutableMapOf<Diner, BigDecimal>()
//
//    items.forEach { item ->
//        val itemPrice = BigDecimal(item.price)
//        itemTotal += itemPrice
//        val dinerShareOfItem = itemPrice.divide(BigDecimal(item.diners.size), mathContext)
//        item.diners.map {
//            dinerItemTotals[it] = dinerItemTotals.getOrDefault(it, BigDecimal.ZERO) + dinerShareOfItem
//        }
//    }
//
//    return dinerItemTotals.mapValues {
//        value.multiply(it.value.divide(itemTotal, mathContext), mathContext)
//    }
//}
//fun getDiscountRecipientSharesOnDinersPercent(percent: BigDecimal, recipients: Collection<Diner>, dinerSubtotals: Map<Diner, BigDecimal>): Map<Diner, BigDecimal> {
//    val fraction = percent.movePointLeft(2)
//    return recipients.associateWith { diner ->
//        fraction.multiply(dinerSubtotals[diner] ?: BigDecimal.ZERO, mathContext)
//    }
//}
//fun getDiscountRecipientSharesOnDinersValue(value: BigDecimal, recipients: Collection<Diner>): Map<Diner, BigDecimal> {
//    val share = value.divide(BigDecimal(recipients.size), mathContext)
//    return recipients.associateWith { share }
//}
//
//fun getDiscountReimbursementDebts(cost: BigDecimal, currencyValue: BigDecimal, recipientShares: Map<Diner, BigDecimal>): Map<Diner, BigDecimal> {
//    return recipientShares.mapValues { (_, share) ->
//        cost.multiply(share.divide(currencyValue, mathContext), mathContext)
//    }
//}
//fun getDiscountReimbursementCredits(cost: BigDecimal, purchasers: Collection<Diner>): Map<Diner, BigDecimal> {
//    val credit = cost.divide(BigDecimal(purchasers.size), mathContext)
//    return purchasers.associateWith { credit }
//}
