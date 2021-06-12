package com.grouptuity.grouptuity.data

import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.pow


class TransactionMap(private val allParticipants: Collection<Long>, initialMap: Map<Pair<Long, Long>, Double>? = null) {
    private val pairMap: MutableMap<Pair<Long, Long>, Double> = initialMap?.toMutableMap() ?: mutableMapOf()
    val amountsPaid: Map<Long, Double>
        get() = allParticipants.associateWith { payerId -> pairMap.filterKeys { it.first == payerId }.values.sum() }
    val amountsReceived: Map<Long, Double>
        get() = allParticipants.associateWith { payeeId -> pairMap.filterKeys { it.second == payeeId }.values.sum() }
    val netCashFlows: Map<Long, Double>
        get() {
            val cashFlows = allParticipants.associateWith { 0.0 }.toMutableMap()

            pairMap.forEach {
                cashFlows.merge(it.key.first, -it.value) { oldValue, newValue -> oldValue - newValue}
                cashFlows.merge(it.key.second, it.value) { oldValue, newValue -> oldValue + newValue}
            }

            return cashFlows
        }

    internal fun addTransaction(payerId: Long, payeeId: Long, amount: Double) {
        pairMap.merge(Pair(payerId, payeeId), amount) { oldValue, newValue -> oldValue + newValue }
    }

    internal fun removeTransaction(payerId: Long, payeeId: Long): Double? = pairMap.remove(Pair(payerId, payeeId))

    internal fun plus(newTransactionMap: TransactionMap): TransactionMap {
        val combinedTransactionMap = TransactionMap(allParticipants.union(newTransactionMap.allParticipants), initialMap = pairMap)
        newTransactionMap.pairMap.forEach { combinedTransactionMap.pairMap.merge(it.key, it.value) { oldValue, newValue -> oldValue + newValue } }
        return combinedTransactionMap
    }

    internal fun getPaymentsToPayee(payeeId: Long): Map<Long, Double> = pairMap.filterKeys { it.second == payeeId }.mapKeys { it.key.first }

    internal fun removePaymentsFromPayer(payerId: Long): Map<Long, Double> {
        val filteredMap = pairMap.filterKeys { it.first == payerId }
        pairMap.keys.removeAll(filteredMap.keys)
        return filteredMap.mapKeys { it.key.second }
    }

    internal fun consolidate(): TransactionMap {
        for (payerId in allParticipants) {
            for (payeeId in allParticipants) {
                val forwardKey = Pair(payerId, payeeId)
                val forwardValue = pairMap[forwardKey]

                val reverseKey = Pair(payeeId, payerId)
                val reverseValue = pairMap[reverseKey]

                when {
                    forwardValue == null || reverseValue == null -> continue
                    forwardValue > reverseValue -> {
                        pairMap[forwardKey] = forwardValue - reverseValue
                        pairMap.remove(reverseKey)
                    }
                    reverseValue > forwardValue -> {
                        pairMap[reverseKey] = reverseValue - forwardValue
                        pairMap.remove(forwardKey)
                    }
                    else -> {
                        pairMap.remove(forwardKey)
                        pairMap.remove(reverseKey)
                    }
                }
            }
        }
        return this
    }

    internal fun dropBelowMinimum(minimum: Double): TransactionMap {
        pairMap.filter { it.value >= minimum }
        return this
    }

    internal fun toList() = pairMap.map { Triple(it.key.first, it.key.second, it.value) }
}

class BillCalculation(private val bill: Bill,
                      private val diners: Map<Long, Diner> = emptyMap(),
                      private val items: Map<Long, Item> = emptyMap(),
                      private val debts: Map<Long, Debt> = emptyMap(),
                      private val discounts: Map<Long, Discount> = emptyMap(),
                      private val payments: Map<Long, Payment> = emptyMap(),
                      private val restaurant: Diner) {

    private val restaurantId = restaurant.id
    private val allParticipantId = diners.keys.toList().plus(restaurant.id)

    //    val dinerRoundingAdjustments: Map<Long, Double>? = null // TODO
    var newPaymentsMap: Map<Long, List<Payment>> = emptyMap()

    var discountValues: Map<Long, Double> = emptyMap()

    var groupSubtotal: Double = 0.0
    var groupDiscountTotal: Double = 0.0
    var groupDiscardedDiscounts: Double = 0.0
    var groupSubtotalWithDiscounts: Double = 0.0
    var groupTaxValue: Double = 0.0
    var groupTaxPercent: Double = 0.0
    var groupSubtotalWithDiscountsAndTax: Double = 0.0
    var groupTipValue: Double = 0.0
    var groupTipPercent: Double = 0.0
    var groupTotal: Double = 0.0

    var individualSubtotals: Map<Long, Double> = emptyMap()
    var individualDiscountShares: Map<Long, Map<Long, Double>> = emptyMap()
    var individualDiscountTotals: Map<Long, Double> = emptyMap()
    var individualExcessDiscountsReleased: Map<Long, Double> = emptyMap()
    var individualExcessDiscountsAcquired: Map<Long, Double> = emptyMap()
    var individualSubtotalsWithDiscounts: Map<Long, Double> = emptyMap()
    var individualReimbursementDebts: Map<Long, Map<Long, Double>> = emptyMap()
    var individualReimbursementCredits: Map<Long, Map<Long, Double>> = emptyMap()
    var individualDebtOwed: Map<Long, Map<Long, Double>> = emptyMap()
    var individualDebtHeld: Map<Long, Map<Long, Double>> = emptyMap()
    var individualTax: Map<Long, Double> = emptyMap()
    var individualSubtotalWithDiscountsAndTax: Map<Long, Double> = emptyMap()
    var individualTip: Map<Long, Double> = emptyMap()
    var individualTotal: Map<Long, Double> = emptyMap()

    init {
        val itemsTransactionMap = processItems()
        val (discountTransactionMap, reimbursementTransactionMap) = processDiscounts()
        val debtTransactionMap = processDebts()

        processGroupTaxAndTip()

        val netTransactionMap = itemsTransactionMap
            .plus(discountTransactionMap)
            .plus(reimbursementTransactionMap)
            .plus(debtTransactionMap)

        redistributeExcessDiscounts(netTransactionMap)

        processIndividualTaxAndTip(netTransactionMap)

        // Add inverted committed payments
        payments.filter{ it.value.committed }.map { (_, payment) ->
            netTransactionMap.addTransaction(payment.payeeId, payment.payerId, payment.amount)
        }

        newPaymentsMap = generateNewPayments(netTransactionMap)
    }

    private fun processItems(): TransactionMap {
        // Calculate subtotals
        val itemTransactionMap = TransactionMap(allParticipantId)
        items.values.forEach { item ->
            val priceShare: Double = item.price / item.diners.size
            item.diners.forEach { dinerId ->
                itemTransactionMap.addTransaction(dinerId, restaurantId, priceShare)
            }
        }
        individualSubtotals = itemTransactionMap.amountsPaid
        groupSubtotal = itemTransactionMap.amountsReceived.getOrDefault(restaurantId, 0.0)

        return itemTransactionMap
    }

    private fun processDiscounts(): Pair<TransactionMap, TransactionMap> {
        // Calculate discount amounts
        val discountTransactionMap = TransactionMap(allParticipantId)
        val reimbursementTransactionMap = TransactionMap(diners.keys)
        val mutableDiscountValues = mutableMapOf<Long, Double>()
        val mutableIndividualDiscountShares = mutableMapOf<Long, MutableMap<Long, Double>>()
        val mutableIndividualReimbursementDebts = mutableMapOf<Long, MutableMap<Long, Double>>()
        val mutableIndividualReimbursementCredits = mutableMapOf<Long, MutableMap<Long, Double>>()

        discounts.values.forEach { discount ->
            mutableDiscountValues[discount.id] = getDiscountCurrencyValue(discount, items, individualSubtotals)

            val discountRecipientShares = getDiscountRecipientShares(discount, items, individualSubtotals)

            if(discount.cost == null) {
                discountRecipientShares.forEach { (recipientId, recipientShare) ->
                    mutableIndividualDiscountShares.getOrPut(recipientId, { mutableMapOf() })[discount.id] = recipientShare
                    discountTransactionMap.addTransaction(restaurantId, recipientId, recipientShare)
                }
            } else {
                val normalizedReimbursementCost = discount.cost / mutableDiscountValues[discount.id]!!

                discountRecipientShares.forEach { (recipientId, recipientShare) ->
                    mutableIndividualDiscountShares.getOrPut(recipientId, { mutableMapOf() })[discount.id] = recipientShare
                    discountTransactionMap.addTransaction(restaurantId, recipientId, recipientShare)

                    mutableIndividualReimbursementDebts.getOrPut(recipientId, { mutableMapOf() })[discount.id] = normalizedReimbursementCost * recipientShare
                    val reimbursementShare = normalizedReimbursementCost * recipientShare / discount.purchasers.size
                    discount.purchasers.forEach { purchaserId -> reimbursementTransactionMap.addTransaction(recipientId, purchaserId, reimbursementShare) }
                }

                discount.purchasers.forEach { purchaserId -> mutableIndividualReimbursementCredits.getOrPut(purchaserId, { mutableMapOf() })[discount.id] = discount.cost / discount.purchasers.size }
            }
        }

        discountValues = mutableDiscountValues
        groupDiscountTotal = discountValues.values.sum()
        individualDiscountShares = mutableIndividualDiscountShares
        individualDiscountTotals = individualDiscountShares.mapValues { it.value.values.sum() }
        individualReimbursementDebts = mutableIndividualReimbursementDebts
        individualReimbursementCredits = mutableIndividualReimbursementCredits
        groupDiscardedDiscounts = max(0.0, groupDiscountTotal - groupSubtotal)
        groupSubtotalWithDiscounts = max(0.0, groupSubtotal - groupDiscountTotal)

        return Pair(discountTransactionMap, reimbursementTransactionMap)
    }

    private fun processDebts(): TransactionMap {
        // Settle external debts between diners. Discount purchase reimbursements handled separately.

        val debtTransactionMap = TransactionMap(diners.keys)
        val mutableIndividualDebtOwed = mutableMapOf<Long, MutableMap<Long, Double>>()
        val mutableIndividualDebtHeld = mutableMapOf<Long, MutableMap<Long, Double>>()

        debts.values.forEach { debt ->
            val debtorShare = debt.amount / debt.debtors.size
            val creditorShare = debt.amount / debt.creditors.size
            val debtorCreditorTransactionAmount = debtorShare / debt.creditors.size

            debt.debtors.forEach { debtorId ->
                mutableIndividualDebtOwed.getOrPut(debtorId, { mutableMapOf() })[debt.id] = debtorShare

                debt.creditors.forEach { creditorId ->
                    debtTransactionMap.addTransaction(debtorId, creditorId, debtorCreditorTransactionAmount)
                }
            }

            debt.creditors.forEach { creditorId ->
                mutableIndividualDebtHeld.getOrPut(creditorId, { mutableMapOf() })[debt.id] = creditorShare
            }
        }

        individualDebtOwed = mutableIndividualDebtOwed
        individualDebtHeld = mutableIndividualDebtHeld

        return debtTransactionMap
    }

    private fun processGroupTaxAndTip() {
        if(bill.taxAsPercent) {
            groupTaxPercent = bill.tax
            groupTaxValue = groupSubtotalWithDiscounts * bill.tax / 100.0
        } else {
            groupTaxValue = bill.tax
            groupTaxPercent = bill.tax / groupSubtotalWithDiscounts * 100.0
        }
        groupSubtotalWithDiscountsAndTax = groupSubtotalWithDiscounts + groupTaxValue

        val baseValueToTip = (if(bill.discountsReduceTip) groupSubtotalWithDiscounts else groupSubtotal) + (if(bill.isTaxTipped) groupTaxValue else 0.0)
        if(bill.tipAsPercent) {
            groupTipPercent = bill.tip
            groupTipValue = baseValueToTip * bill.tip / 100.0
        } else {
            groupTipValue = bill.tip
            groupTipPercent = bill.tip / baseValueToTip * 100.0
        }

        groupTotal = groupSubtotalWithDiscountsAndTax + groupTipValue
    }

    private fun redistributeExcessDiscounts(transactionMap: TransactionMap): TransactionMap {
        /** Excess discount value is lumped and distributed equally to all diners until the excess
         *  is exhausted or all individual subtotals are zero. Redistribution of discount value
         *  does not transfer liabilities pertaining to discount purchase cost reimbursement and
         *  does not offset inter-diner debts.
         **/

        transactionMap.consolidate()

        individualExcessDiscountsReleased = transactionMap.removePaymentsFromPayer(restaurantId)
        var totalExcessDiscount = individualExcessDiscountsReleased.values.sum()
        var remainingSubtotals = transactionMap.getPaymentsToPayee(restaurantId).toMutableMap()
        val discountsAcquired = mutableMapOf<Long, Double>()

        while (totalExcessDiscount > 0.0 && remainingSubtotals.isNotEmpty()) {
            val share = totalExcessDiscount / remainingSubtotals.size

            val newRemainingSubtotals = mutableMapOf<Long, Double>()
            remainingSubtotals.forEach { (dinerId, subtotal) ->
                if(subtotal > share) {
                    totalExcessDiscount -= share
                    discountsAcquired.merge(dinerId, share) { oldValue, newValue -> oldValue + newValue }
                    newRemainingSubtotals[dinerId] = subtotal - share
                }
                else {
                    totalExcessDiscount -= subtotal
                    discountsAcquired.merge(dinerId, subtotal) { oldValue, newValue -> oldValue + newValue }
                }
            }

            remainingSubtotals = newRemainingSubtotals
        }

        individualExcessDiscountsAcquired = discountsAcquired
        individualSubtotalsWithDiscounts = individualSubtotals.mapValues { (dinerId, subtotal) ->
            max(0.0, subtotal - (individualDiscountTotals[dinerId] ?: 0.0) - (individualExcessDiscountsAcquired[dinerId] ?: 0.0))
        }

        discountsAcquired.forEach { (payeeId, amount) -> transactionMap.addTransaction(restaurantId, payeeId, amount) }

        return transactionMap
    }

    private fun processIndividualTaxAndTip(transactionMap: TransactionMap): TransactionMap {
        individualTax = individualSubtotalsWithDiscounts.mapValues { (dinerId, discountedSubtotal) ->
            val tax = discountedSubtotal * groupTaxPercent / 100.0
            transactionMap.addTransaction(dinerId, restaurantId, tax)
            tax
        }

        individualSubtotalWithDiscountsAndTax = individualSubtotalsWithDiscounts.mapValues { it.value + individualTax[it.key]!! }

        individualTip = (if(bill.discountsReduceTip) individualSubtotalsWithDiscounts else individualSubtotals).mapValues { (dinerId, baseValueToTip) ->
            val tip = (baseValueToTip + (if(bill.isTaxTipped) individualTax[dinerId]!! else 0.0)) * groupTaxPercent / 100.0
            transactionMap.addTransaction(dinerId, restaurantId, tip)
            tip
        }

        individualTotal = individualSubtotalWithDiscountsAndTax.mapValues { it.value + individualTip[it.key]!! }

        return transactionMap
    }

    private fun generateNewPayments(transactionMap: TransactionMap): Map<Long, List<Payment>> {

        val payments = diners.mapValues { mutableListOf<Payment>() }

//        transactionMap.getPaymentsToPayee(restaurantId).forEach { (payerId, amount) ->
//            diners[payerId]!!.paymentPreferences.forRecipient(restaurantId)?.second?.apply {
//                transactionMap.removeTransaction(payerId, restaurantId)
//                transactionMap.addTransaction(payerId, it.second, it.third)
//                transactionMap.addTransaction(it.second, restaurantId, it.third)
//
//            }
//        }
//
//        restaurantPeerToPeerUsers.forEach {
//
//        }

        val numDecimals = NumberFormat.getCurrencyInstance().maximumFractionDigits
        val transactionList = transactionMap.consolidate().dropBelowMinimum(0.9 * 10.0.pow(-numDecimals)).toList()

        transactionList.forEach {
            payments[it.first]!!.add(Payment(0L, billId = bill.id, it.third, PaymentMethod.CASH.name, false, it.first, it.second))
        }

//        val targetNetCashFlows = transactionMap.netCashFlows






        // Identify payment methods for all transactions with the restaurant
        val restaurantCashUsers = mutableListOf<Pair<Long, Double>>()
        val restaurantIndividualsCreditCardUsers = mutableListOf<Pair<Long, Double>>()
        val restaurantSplitCreditCardUsers = mutableListOf<Pair<Long, Double>>()
        val restaurantPeerToPeerUsers = mutableListOf<Triple<Long, Long, Double>>()
        var splitAmount = 0.0
//        transactionList.forEach { (payerId, payeeId, amount) ->
//            if(payeeId == restaurantId) {
//                // Default to paying restaurant with cash if no preference has been defined
//                val (method, surrogateId) = diners[payerId]!!.paymentPreferences.forRecipient(payeeId) ?: Pair<PaymentMethod, Long?>(PaymentMethod.CASH, null)
//
//                when(method) {
//                    PaymentMethod.CASH -> {
//                        restaurantCashUsers.add(Pair(payerId, amount))
//                        splitAmount += 0.0
//                    }
//                    PaymentMethod.CREDIT_CARD_INDIVIDUAL -> { restaurantIndividualsCreditCardUsers.add(Pair(payerId, amount)) }
//                    PaymentMethod.CREDIT_CARD_SPLIT -> {
//                        restaurantSplitCreditCardUsers.add(Pair(payerId, amount))
//                        splitAmount += 0.0
//                    }
//                    else -> {
//                        if(surrogateId == null) {
//                            // Peer-to-peer payment not possible so use cash as fallback
//                            restaurantCashUsers.add(Pair(payerId, amount))
//                        } else {
//                            // Surrogate diner exists so route restaurant payment through them
//                            restaurantPeerToPeerUsers.add(Triple(payerId, surrogateId, amount))
//                        }
//                        splitAmount += 0.0
//                    }
//                }
//            }
//        }

//        if(restaurantSplitCreditCardUsers.isEmpty()) {
//
//        } else {
//            val splitShare = splitAmount / restaurantSplitCreditCardUsers.size
//
//            restaurantPeerToPeerUsers.forEach {
//                transactionMap.removeTransaction(it.first, restaurantId)
//                transactionMap.addTransaction(it.first, it.second, it.third)
//                transactionMap.addTransaction(it.second, restaurantId, it.third)
//            }
//
//
//            restaurantPeerToPeerUsers.forEach {
//                transactionMap.removeTransaction(it.first, restaurantId)
//                transactionMap.addTransaction(it.first, it.second, it.third)
//                transactionMap.addTransaction(it.second, restaurantId, it.third)
//            }
//
//
//            restaurantSplitCreditCardUsers.map { payerId ->
//                payments[payerId]!!.add(Triple(restaurantId, splitAmount, PaymentMethod.CREDIT_CARD_SPLIT))
//            }
//        }

        //        val roundedAmount = BigDecimal(amount.toString()).setScale(numDecimals, RoundingMode.HALF_EVEN).toDouble()
//            val amount = BigDecimal(value.toString()).setScale(numDecimals, RoundingMode.HALF_EVEN).toDouble()
//            if (amount > minQuantity) {
//                payments[payerId]?.add(Payment(0L,
//                    bill.id,
////                    amount,
////                    PaymentMethod.CASH.toString(), // getPreferredPaymentMethod(payerId, payeeId),
////                    false,
////                    payerId,
////                    payeeId))
//            }
//        }

        return payments
    }
}


fun getDinerSubtotal(diner: Diner, itemMap: Map<Long, Item>): Double = diner.items.sumOf { itemId -> itemMap[itemId]?.let { it.price / it.diners.size }  ?: 0.0 }

fun getDiscountCurrencyValue(discount: Discount, itemMap: Map<Long, Item>, dinerSubtotals: Map<Long, Double>): Double {
    return if (discount.asPercent) {
        if (discount.onItems) {
            getDiscountCurrencyOnItemsPercent(discount.value, discount.items, itemMap)
        } else {
            getDiscountCurrencyOnDinersPercent(discount.value, discount.recipients, dinerSubtotals)
        }
    } else {
        discount.value
    }
}
fun getDiscountCurrencyOnItemsPercent(percent: Double, itemIds: Collection<Long>, itemMap: Map<Long, Item>) = 0.01 * percent * itemIds.sumOf { itemMap[it]?.price ?: 0.0 }
fun getDiscountCurrencyOnDinersPercent(percent: Double, recipients: Collection<Long>, dinerSubtotals: Map<Long, Double>) = 0.01 * percent * recipients.sumOf { dinerSubtotals[it] ?: 0.0 }

fun getDiscountRecipientShares(discount: Discount, itemMap: Map<Long, Item>, dinerSubtotals: Map<Long, Double>): Map<Long, Double> {
    return if(discount.onItems) {
        if(discount.asPercent) {
            getDiscountRecipientSharesOnItemsPercent(discount.value, discount.items, itemMap)
        } else {
            getDiscountRecipientSharesOnItemsValue(discount.value, discount.items, itemMap)
        }
    } else {
        if (discount.asPercent) {
            getDiscountRecipientSharesOnDinersPercent(discount.value, discount.recipients, dinerSubtotals)
        } else {
            getDiscountRecipientSharesOnDinersValue(discount.value, discount.recipients)
        }
    }
}
fun getDiscountRecipientSharesOnItemsPercent(percent: Double, itemIds: Collection<Long>, itemMap: Map<Long, Item>): Map<Long, Double> {
    val dinerItemTotals = mutableMapOf<Long, Double>()

    for(itemId in itemIds) {
        itemMap[itemId]?.also { item ->
            val itemShare = item.price / item.diners.size
            item.diners.map { dinerItemTotals[it] = dinerItemTotals.getOrDefault(it, 0.0) + itemShare }
        }
    }
    return dinerItemTotals.mapValues { 0.01 * percent * it.value }
}
fun getDiscountRecipientSharesOnItemsValue(value: Double, itemIds: Collection<Long>, itemMap: Map<Long, Item>): Map<Long, Double> {
    var itemTotal = 0.0
    val dinerItemTotals = mutableMapOf<Long, Double>()

    for(itemId in itemIds) {
        itemMap[itemId]?.also { item ->
            itemTotal += item.price
            val itemShare = item.price / item.diners.size
            item.diners.map { dinerItemTotals[it] = dinerItemTotals.getOrDefault(it, 0.0) + itemShare }
        }
    }
    return dinerItemTotals.mapValues { value * it.value / itemTotal }
}
fun getDiscountRecipientSharesOnDinersPercent(percent: Double, recipients: Collection<Long>, dinerSubtotals: Map<Long, Double>) = recipients.associateWith { 0.01 * percent * (dinerSubtotals[it] ?: 0.0) }
fun getDiscountRecipientSharesOnDinersValue(value: Double, recipients: Collection<Long>) = recipients.associateWith { value / recipients.size }

fun getDiscountReimbursementDebts(cost: Double, currencyValue: Double, recipientShares: Map<Long, Double>) = recipientShares.mapValues { cost * (recipientShares[it.key] ?: 0.0) / currencyValue }
fun getDiscountReimbursementCredits(cost: Double, purchasers: Collection<Long>) = purchasers.associateWith { cost / purchasers.size }