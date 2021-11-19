package com.grouptuity.grouptuity.data

import android.util.Log
import kotlin.math.max

const val PRECISION = 1e-6


class TransactionMap(private val allParticipants: Collection<Diner>, initialMap: Map<Pair<Diner, Diner>, Double>? = null) {
    private val pairMap: MutableMap<Pair<Diner, Diner>, Double> = initialMap?.toMutableMap() ?: mutableMapOf()
    val amountsPaid: Map<Diner, Double>
        get() = allParticipants.associateWith { payer -> pairMap.filterKeys { it.first === payer }.values.sum() }
    val amountsReceived: Map<Diner, Double>
        get() = allParticipants.associateWith { payee -> pairMap.filterKeys { it.second === payee }.values.sum() }
    val netCashFlows: Map<Diner, Double>
        get() {
            val cashFlows = allParticipants.associateWith { 0.0 }.toMutableMap()

            pairMap.forEach {
                cashFlows.merge(it.key.first, -it.value) { oldValue, newValue -> oldValue - newValue}
                cashFlows.merge(it.key.second, it.value) { oldValue, newValue -> oldValue + newValue}
            }

            return cashFlows
        }

    internal fun addTransaction(payer: Diner, payee: Diner, amount: Double) {
        pairMap.merge(Pair(payer, payee), amount) { oldValue, newValue -> oldValue + newValue }
    }

    internal fun removeTransaction(payer: Diner, payee: Diner): Double? = pairMap.remove(Pair(payer, payee))

    internal fun plus(newTransactionMap: TransactionMap): TransactionMap {
        val combinedTransactionMap = TransactionMap(allParticipants.union(newTransactionMap.allParticipants), initialMap = pairMap)
        newTransactionMap.pairMap.forEach { combinedTransactionMap.pairMap.merge(it.key, it.value) { oldValue, newValue -> oldValue + newValue } }
        return combinedTransactionMap
    }

    internal fun getTransactionAmount(payer: Diner, payee: Diner): Double = pairMap.getOrDefault(Pair(payer, payee), 0.0)

    internal fun getPaymentsFromPayer(payer: Diner): Map<Diner, Double> = pairMap.filterKeys { it.first === payer }.mapKeys { it.key.second }

    internal fun getPaymentsToPayee(payee: Diner): Map<Diner, Double> = pairMap.filterKeys { it.second === payee }.mapKeys { it.key.first }

    internal fun removePaymentsFromPayer(payer: Diner): Map<Diner, Double> {
        val filteredMap = pairMap.filterKeys { it.first === payer }
        pairMap.keys.removeAll(filteredMap.keys)
        return filteredMap.mapKeys { it.key.second }
    }

    internal fun consolidate(): TransactionMap {
        for (payer in allParticipants) {
            for (payee in allParticipants) {
                val forwardKey = Pair(payer, payee)
                val forwardValue = pairMap[forwardKey]

                val reverseKey = Pair(payee, payer)
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
        pairMap.entries.removeIf { it.value == 0.0 }
        return this
    }

    internal fun toList() = pairMap.map { Triple(it.key.first, it.key.second, it.value) }
}

// TODO need to operate only on immutable data across entire calculation


class BillCalculation(private val bill: Bill,
                      private val cashPool: Diner,
                      private val restaurant: Diner,
                      private val diners: Collection<Diner>,
                      private val items: Collection<Item>,
                      private val debts: Collection<Debt>,
                      private val discounts: Collection<Discount>,
                      private val oldPayments: Collection<Payment>) {

    private val allParticipants = diners.plus(cashPool).plus(restaurant)

    //    val dinerRoundingAdjustments: Map<String, Double>? = null // TODO
    var newPaymentsMap: Map<Diner, List<Payment>> = emptyMap()
    var sortedPayments: List<Payment> = emptyList()

    var discountValues: Map<Discount, Double> = emptyMap()

    var groupSubtotal: Double = 0.0
    var groupDiscountAmount: Double = 0.0
    var groupDiscardedDiscounts: Double = 0.0
    var groupSubtotalWithDiscounts: Double = 0.0
    var groupTaxAmount: Double = 0.0
    var groupTaxPercent: Double = 0.0
    var groupSubtotalWithDiscountsAndTax: Double = 0.0
    var groupTipAmount: Double = 0.0
    var groupTipPercent: Double = 0.0
    var groupTotal: Double = 0.0

    var individualSubtotals: Map<Diner, Double> = emptyMap()
    var individualDiscountShares: Map<Diner, Map<Discount, Double>> = emptyMap()
    var individualDiscountTotals: Map<Diner, Double> = emptyMap()
    var individualExcessDiscountsReleased: Map<Diner, Double> = emptyMap()
    var individualExcessDiscountsAcquired: Map<Diner, Double> = emptyMap()
    var individualSubtotalsWithDiscounts: Map<Diner, Double> = emptyMap()
    var individualReimbursementDebts: Map<Diner, Map<Discount, Double>> = emptyMap()
    var individualReimbursementCredits: Map<Diner, Map<Discount, Double>> = emptyMap()
    var individualDebtOwed: Map<Diner, Map<Debt, Double>> = emptyMap()
    var individualDebtHeld: Map<Diner, Map<Debt, Double>> = emptyMap()
    var individualTax: Map<Diner, Double> = emptyMap()
    var individualSubtotalWithDiscountsAndTax: Map<Diner, Double> = emptyMap()
    var individualTip: Map<Diner, Double> = emptyMap()
    var individualTotal: Map<Diner, Double> = emptyMap()

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
        val committedPayments = oldPayments.filter{ it.committed }

        committedPayments.map { payment ->
            netTransactionMap.addTransaction(payment.payee, payment.payer, payment.amount)
        }

        newPaymentsMap = generateNewPayments(netTransactionMap)



        sortedPayments = (committedPayments + newPaymentsMap.flatMap { it.value }).sortedWith { a, b ->
            val firstDiner = if (a.payer == cashPool) a.payee else a.payer
            val secondDiner = if (b.payer == cashPool) b.payee else b.payer

            when {
                firstDiner != secondDiner -> {
                    diners.indexOf(firstDiner) - diners.indexOf(secondDiner)
                }
                a.payee == restaurant && b.payee != restaurant -> {
                    -1
                }
                a.payee != restaurant && b.payee == restaurant -> {
                    1
                }
                a.payee == cashPool && b.payee != cashPool -> {
                    -1
                }
                a.payee != cashPool && b.payee == cashPool -> {
                    1
                }
                else -> {
                    diners.indexOf(a.payee) - diners.indexOf(b.payee)
                }
            }
        }
    }

    private fun processItems(): TransactionMap {
        // Calculate subtotals
        val itemTransactionMap = TransactionMap(allParticipants)
        items.forEach { item ->
            val priceShare: Double = item.price / item.diners.size
            item.diners.forEach { diner ->
                itemTransactionMap.addTransaction(diner, restaurant, priceShare)
            }
        }
        individualSubtotals = itemTransactionMap.amountsPaid
        groupSubtotal = itemTransactionMap.amountsReceived.getOrDefault(restaurant, 0.0)

        return itemTransactionMap
    }

    private fun processDiscounts(): Pair<TransactionMap, TransactionMap> {
        // Calculate discount amounts
        val discountTransactionMap = TransactionMap(allParticipants)
        val reimbursementTransactionMap = TransactionMap(diners)
        val mutableDiscountValues = mutableMapOf<Discount, Double>()
        val mutableIndividualDiscountShares = mutableMapOf<Diner, MutableMap<Discount, Double>>()
        val mutableIndividualReimbursementDebts = mutableMapOf<Diner, MutableMap<Discount, Double>>()
        val mutableIndividualReimbursementCredits = mutableMapOf<Diner, MutableMap<Discount, Double>>()

        discounts.forEach { discount ->
            mutableDiscountValues[discount] = getDiscountCurrencyValue(discount, individualSubtotals)

            val discountRecipientShares = getDiscountRecipientShares(discount, individualSubtotals)

            if(discount.cost == null) {
                discountRecipientShares.forEach { (recipient, recipientShare) ->
                    mutableIndividualDiscountShares.getOrPut(recipient, { mutableMapOf() })[discount] = recipientShare
                    discountTransactionMap.addTransaction(restaurant, recipient, recipientShare)
                }
            } else {
                val normalizedReimbursementCost = discount.cost / mutableDiscountValues[discount]!!

                discountRecipientShares.forEach { (recipient, recipientShare) ->
                    mutableIndividualDiscountShares.getOrPut(recipient, { mutableMapOf() })[discount] = recipientShare
                    discountTransactionMap.addTransaction(restaurant, recipient, recipientShare)

                    mutableIndividualReimbursementDebts.getOrPut(recipient, { mutableMapOf() })[discount] = normalizedReimbursementCost * recipientShare
                    val reimbursementShare = normalizedReimbursementCost * recipientShare / discount.purchasers.size
                    discount.purchasers.forEach { purchaser -> reimbursementTransactionMap.addTransaction(recipient, purchaser, reimbursementShare) }
                }

                discount.purchasers.forEach { purchaser ->
                    mutableIndividualReimbursementCredits.getOrPut(purchaser, { mutableMapOf() })[discount] = discount.cost / discount.purchasers.size
                }
            }
        }

        discountValues = mutableDiscountValues
        groupDiscountAmount = discountValues.values.sum()
        individualDiscountShares = mutableIndividualDiscountShares
        individualDiscountTotals = individualDiscountShares.mapValues { it.value.values.sum() }
        individualReimbursementDebts = mutableIndividualReimbursementDebts
        individualReimbursementCredits = mutableIndividualReimbursementCredits
        groupDiscardedDiscounts = max(0.0, groupDiscountAmount - groupSubtotal)
        groupSubtotalWithDiscounts = max(0.0, groupSubtotal - groupDiscountAmount)

        return Pair(discountTransactionMap, reimbursementTransactionMap)
    }

    private fun processDebts(): TransactionMap {
        // Settle external debts between diners. Discount purchase reimbursements handled separately.

        val debtTransactionMap = TransactionMap(diners)
        val mutableIndividualDebtOwed = mutableMapOf<Diner, MutableMap<Debt, Double>>()
        val mutableIndividualDebtHeld = mutableMapOf<Diner, MutableMap<Debt, Double>>()

        debts.forEach { debt ->
            val debtorShare = debt.amount / debt.debtors.size
            val creditorShare = debt.amount / debt.creditors.size
            val debtorCreditorTransactionAmount = debtorShare / debt.creditors.size

            debt.debtors.forEach { debtor ->
                mutableIndividualDebtOwed.getOrPut(debtor, { mutableMapOf() })[debt] = debtorShare

                debt.creditors.forEach { creditor ->
                    debtTransactionMap.addTransaction(debtor, creditor, debtorCreditorTransactionAmount)
                }
            }

            debt.creditors.forEach { creditor ->
                mutableIndividualDebtHeld.getOrPut(creditor, { mutableMapOf() })[debt] = creditorShare
            }
        }

        individualDebtOwed = mutableIndividualDebtOwed
        individualDebtHeld = mutableIndividualDebtHeld

        return debtTransactionMap
    }

    private fun processGroupTaxAndTip() {
        if(bill.taxAsPercent) {
            groupTaxPercent = bill.tax
            groupTaxAmount = groupSubtotalWithDiscounts * bill.tax / 100.0
        } else {
            groupTaxAmount = bill.tax
            groupTaxPercent = bill.tax / groupSubtotalWithDiscounts * 100.0
        }
        groupSubtotalWithDiscountsAndTax = groupSubtotalWithDiscounts + groupTaxAmount

        val baseValueToTip = (if(bill.discountsReduceTip) groupSubtotalWithDiscounts else groupSubtotal) + (if(bill.isTaxTipped) groupTaxAmount else 0.0)
        if(bill.tipAsPercent) {
            groupTipPercent = bill.tip
            groupTipAmount = baseValueToTip * bill.tip / 100.0
        } else {
            groupTipAmount = bill.tip
            groupTipPercent = bill.tip / baseValueToTip * 100.0
        }

        groupTotal = groupSubtotalWithDiscountsAndTax + groupTipAmount
    }

    private fun redistributeExcessDiscounts(transactionMap: TransactionMap): TransactionMap {
        /** Excess discount value is lumped and distributed equally to all diners until the excess
         *  is exhausted or all individual subtotals are zero. Redistribution of discount value
         *  does not transfer liabilities pertaining to discount purchase cost reimbursement and
         *  does not offset inter-diner debts.
         **/

        transactionMap.consolidate()

        individualExcessDiscountsReleased = transactionMap.removePaymentsFromPayer(restaurant)
        var totalExcessDiscount = individualExcessDiscountsReleased.values.sum()
        var remainingSubtotals = transactionMap.getPaymentsToPayee(restaurant).toMutableMap()
        val discountsAcquired = mutableMapOf<Diner, Double>()

        while (totalExcessDiscount > PRECISION && remainingSubtotals.isNotEmpty()) {
            val share = totalExcessDiscount / remainingSubtotals.size

            val newRemainingSubtotals = mutableMapOf<Diner, Double>()
            remainingSubtotals.forEach { (diner, subtotal) ->
                if(subtotal > share) {
                    totalExcessDiscount -= share
                    discountsAcquired.merge(diner, share) { oldValue, newValue -> oldValue + newValue }
                    newRemainingSubtotals[diner] = subtotal - share
                }
                else {
                    totalExcessDiscount -= subtotal
                    discountsAcquired.merge(diner, subtotal) { oldValue, newValue -> oldValue + newValue }
                }
            }

            remainingSubtotals = newRemainingSubtotals
        }

        individualExcessDiscountsAcquired = discountsAcquired
        individualSubtotalsWithDiscounts = individualSubtotals.mapValues { (diner, subtotal) ->
            max(0.0, subtotal - (individualDiscountTotals[diner] ?: 0.0) - (individualExcessDiscountsAcquired[diner] ?: 0.0))
        }

        discountsAcquired.forEach { (payee, amount) -> transactionMap.addTransaction(restaurant, payee, amount) }

        return transactionMap
    }

    private fun processIndividualTaxAndTip(transactionMap: TransactionMap): TransactionMap {
        individualTax = individualSubtotalsWithDiscounts.mapValues { (diner, discountedSubtotal) ->
            val tax = discountedSubtotal * groupTaxPercent / 100.0
            transactionMap.addTransaction(diner, restaurant, tax)
            tax
        }

        individualSubtotalWithDiscountsAndTax = individualSubtotalsWithDiscounts.mapValues { it.value + individualTax[it.key]!! }

        individualTip = (if(bill.discountsReduceTip) individualSubtotalsWithDiscounts else individualSubtotals).mapValues { (diner, baseValueToTip) ->
            val tip = (baseValueToTip + (if(bill.isTaxTipped) individualTax[diner]!! else 0.0)) * groupTipPercent / 100.0
            transactionMap.addTransaction(diner, restaurant, tip)
            tip
        }

        individualTotal = individualSubtotalWithDiscountsAndTax.mapValues { it.value + individualTip[it.key]!! }

        return transactionMap
    }

    private fun generateNewPayments(transactionMap: TransactionMap): Map<Diner, List<Payment>> {
        val paymentsMap = diners.associateWith { mutableListOf<Payment>() }.toMutableMap()
        val dinersWithoutBills = diners.toMutableSet()

        // Insert surrogate transactions for indirect payments to restaurant via peer-to-peer
        transactionMap.getPaymentsToPayee(restaurant).forEach { (payer, amount) ->
            payer.getPaymentTemplate(restaurant).also { template ->
                if (template?.surrogateId != null) {
                    diners.find { it.id == template.surrogateId }?.also { surrogate ->
                        transactionMap.removeTransaction(payer, restaurant)
                        dinersWithoutBills.remove(payer)
                        paymentsMap[payer]!!.add(createPayment(amount, template.method, payer, restaurant, surrogate))

                        transactionMap.addTransaction(surrogate, restaurant, amount)
                    }
                }
            }
        }

        transactionMap.consolidate()

        // Determine if a credit card pool exists
        val poolCreditCardUsers = mutableListOf<Diner>()
        val poolCashContributors = mutableListOf<Diner>()
        val nonPoolCreditCardUsers = mutableListOf<Diner>()
        var poolAmount = 0.0
        diners.forEach { payer ->
            payer.getPaymentTemplate(restaurant).also { template ->
                if (template?.method == PaymentMethod.CREDIT_CARD_SPLIT) {
                    poolCreditCardUsers.add(payer)
                    poolAmount += transactionMap.getTransactionAmount(payer, restaurant)
                } else if (transactionMap.getTransactionAmount(payer, restaurant) > 0.0)  {
                    when (template?.method) {
                        null, PaymentMethod.CASH -> {
                            poolCashContributors.add(payer)
                            poolAmount += transactionMap.getTransactionAmount(payer, restaurant)
                        }
                        PaymentMethod.CREDIT_CARD_INDIVIDUAL -> {
                            nonPoolCreditCardUsers.add(payer)
                        }
                        else -> { }
                    }
                }
            }
        }

        // Create payments for restaurant
        if (poolCreditCardUsers.isNotEmpty()) {
            val poolCreditCardAmount = poolAmount / poolCreditCardUsers.size
            poolCreditCardUsers.forEach { payer ->
                dinersWithoutBills.remove(payer)
                paymentsMap[payer]!!.add(createPayment(poolCreditCardAmount, PaymentMethod.CREDIT_CARD_SPLIT, payer, restaurant, null))

                val remainingBalance = transactionMap.getTransactionAmount(payer, restaurant) - poolCreditCardAmount
                if (remainingBalance > 0) {
                    paymentsMap[payer]!!.add(createPayment(remainingBalance, PaymentMethod.CASH, payer, cashPool, null))
                } else if (remainingBalance < 0) {
                    paymentsMap[payer]!!.add(createPayment(-remainingBalance, PaymentMethod.CASH, cashPool, payer, null))
                }
            }

            poolCashContributors.forEach { payer->
                dinersWithoutBills.remove(payer)
                paymentsMap[payer]!!.add(
                    createPayment(
                        transactionMap.getTransactionAmount(payer, restaurant),
                        PaymentMethod.CASH,
                        payer,
                        restaurant,
                        cashPool)
                )
            }

            nonPoolCreditCardUsers.forEach { payer->
                dinersWithoutBills.remove(payer)
                paymentsMap[payer]!!.add(
                    createPayment(
                        transactionMap.getTransactionAmount(payer, restaurant),
                        PaymentMethod.CREDIT_CARD_INDIVIDUAL,
                        payer,
                        restaurant,
                        null)
                )
            }
        } else {
            transactionMap.getPaymentsToPayee(restaurant).forEach { (payer, amount) ->
                payer.getPaymentTemplate(restaurant).also { template ->
                    dinersWithoutBills.remove(payer)
                    paymentsMap[payer]!!.add(createPayment(amount, template?.method ?: PaymentMethod.CASH, payer, restaurant, null))
                }
            }
        }

        // Create payments for peer-to-peer transactions
        diners.forEach { payer ->
            transactionMap.getPaymentsFromPayer(payer).forEach { (payee, amount) ->
                if (payee != restaurant) {
                    paymentsMap[payer]!!.add(
                        createPayment(
                            amount,
                            payer.getPaymentTemplate(payee)?.method ?: PaymentMethod.CASH,
                            payer,
                            payee,
                            null)
                    )
                }
            }
        }

        // Reconcile rounding errors TODO

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

        /* Insert zero amount payments to restaurant for diners without bills. This is needed for
        the diner to appear as a candidate for acting as a surrogate. These transactions should not
        appear when the PaymentFragment is in the default state. */
        for (diner in dinersWithoutBills) {
            paymentsMap[diner]!!.add(
                createPayment(
                    0.0,
                    PaymentMethod.CASH,
                    diner,
                    restaurant,
                    null)
            )
        }

        return paymentsMap
    }

    private fun createPayment(amount: Double, method: PaymentMethod, payer: Diner, payee: Diner, surrogate: Diner?) =
        Payment(newUUID(), bill.id, amount, method, false, payer.id, payee.id, surrogate?.id).also {
            it.payer = payer
            it.payee = payee
            it.surrogate = surrogate
        }
}

fun getDiscountedItemPrices(discountValue: Double,
                            onItems: Boolean,
                            asPercent: Boolean,
                            discountedItems: Collection<Item>): Map<Item, Double> =
    if(onItems) {
        if(asPercent) {
            val factor = 1.0 - (0.01 * discountValue)
            discountedItems.associateWith { factor * it.price }
        } else {
            val discountedItemsTotal = discountedItems.sumOf { it.price }
            discountedItems.associateWith {
                it.price - (it.price / discountedItemsTotal * discountValue)
            }
        }
    } else {
        emptyMap()
    }

fun getDiscountCurrencyValue(discount: Discount, dinerSubtotals: Map<Diner, Double>): Double {
    return if (discount.asPercent) {
        if (discount.onItems) {
            getDiscountCurrencyOnItemsPercent(discount.value, discount.items)
        } else {
            getDiscountCurrencyOnDinersPercent(discount.value, discount.recipients, dinerSubtotals)
        }
    } else {
        discount.value
    }
}
fun getDiscountCurrencyOnItemsPercent(percent: Double, items: Collection<Item>) =
    0.01 * percent * items.sumOf { it.price }
fun getDiscountCurrencyOnDinersPercent(percent: Double, recipients: Collection<Diner>, dinerSubtotals: Map<Diner, Double>) =
    0.01 * percent * recipients.sumOf { dinerSubtotals[it] ?: 0.0 }

fun getDiscountRecipientShares(discount: Discount, dinerSubtotals: Map<Diner, Double>): Map<Diner, Double> {
    return if(discount.onItems) {
        if(discount.asPercent) {
            getDiscountRecipientSharesOnItemsPercent(discount.value, discount.items)
        } else {
            getDiscountRecipientSharesOnItemsValue(discount.value, discount.items)
        }
    } else {
        if (discount.asPercent) {
            getDiscountRecipientSharesOnDinersPercent(discount.value, discount.recipients, dinerSubtotals)
        } else {
            getDiscountRecipientSharesOnDinersValue(discount.value, discount.recipients)
        }
    }
}
fun getDiscountRecipientSharesOnItemsPercent(percent: Double, items: Collection<Item>): Map<Diner, Double> {
    val dinerItemTotals = mutableMapOf<Diner, Double>()

    items.forEach { item ->
        val itemShare = item.price / item.diners.size
        item.diners.map { dinerItemTotals[it] = dinerItemTotals.getOrDefault(it, 0.0) + itemShare }
    }

    return dinerItemTotals.mapValues { 0.01 * percent * it.value }
}
fun getDiscountRecipientSharesOnItemsValue(value: Double, items: Collection<Item>): Map<Diner, Double> {
    var itemTotal = 0.0
    val dinerItemTotals = mutableMapOf<Diner, Double>()

    items.forEach { item ->
        itemTotal += item.price
        val itemShare = item.price / item.diners.size
        item.diners.map { dinerItemTotals[it] = dinerItemTotals.getOrDefault(it, 0.0) + itemShare }
    }

    return dinerItemTotals.mapValues { value * it.value / itemTotal }
}
fun getDiscountRecipientSharesOnDinersPercent(percent: Double, recipients: Collection<Diner>, dinerSubtotals: Map<Diner, Double>): Map<Diner, Double> =
    recipients.associateWith { 0.01 * percent * (dinerSubtotals[it] ?: 0.0) }
fun getDiscountRecipientSharesOnDinersValue(value: Double, recipients: Collection<Diner>): Map<Diner, Double> =
    recipients.associateWith { value / recipients.size }

fun getDiscountReimbursementDebts(cost: Double, currencyValue: Double, recipientShares: Map<Diner, Double>): Map<Diner, Double> =
    recipientShares.mapValues { cost * (recipientShares[it.key] ?: 0.0) / currencyValue }
fun getDiscountReimbursementCredits(cost: Double, purchasers: Collection<Diner>): Map<Diner, Double> =
    purchasers.associateWith { cost / purchasers.size }