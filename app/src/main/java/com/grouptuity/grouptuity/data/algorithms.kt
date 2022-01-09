package com.grouptuity.grouptuity.data

import com.grouptuity.grouptuity.data.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import java.util.Collections.sort


class TransactionMap(
    val allParticipants: Set<Diner>,
    initialMap: Map<Pair<Diner, Diner>, BigDecimal>? = null
) {
    private val pairMap: MutableMap<Pair<Diner, Diner>, BigDecimal> =
        initialMap?.toMutableMap() ?: mutableMapOf()
    val amountsPaid: Map<Diner, BigDecimal>
        get() = allParticipants.associateWith { payer ->
            var sum = BigDecimal.ZERO
            pairMap.filterKeys { it.first === payer }.values.forEach { sum += it }
            sum
        }
    val amountsReceived: Map<Diner, BigDecimal>
        get() = allParticipants.associateWith { payee ->
            var sum = BigDecimal.ZERO
            pairMap.filterKeys { it.second === payee }.values.forEach { sum += it }
            sum
        }
    val netCashFlows: Map<Diner, BigDecimal>
        get() {
            val cashFlows = allParticipants.associateWith { BigDecimal.ZERO }.toMutableMap()

            pairMap.forEach {
                cashFlows.merge(it.key.first, -it.value, BigDecimal::minus)
                cashFlows.merge(it.key.second, it.value, BigDecimal::minus)
            }

            return cashFlows
        }

    fun addTransaction(payer: Diner, payee: Diner, amount: BigDecimal) {
        pairMap.merge(Pair(payer, payee), amount, BigDecimal::add)
    }

    fun removeTransaction(payer: Diner, payee: Diner): BigDecimal? =
        pairMap.remove(Pair(payer, payee))

    fun merge(newTransactionMap: TransactionMap): TransactionMap {
        val combinedTransactionMap =
            TransactionMap(
                allParticipants.union(newTransactionMap.allParticipants),
                initialMap = pairMap
            )
        newTransactionMap.pairMap.forEach {
            combinedTransactionMap.pairMap.merge(it.key, it.value, BigDecimal::plus)
        }
        return combinedTransactionMap
    }

    fun getTransactionAmount(payer: Diner, payee: Diner): BigDecimal =
        pairMap.getOrDefault(Pair(payer, payee), BigDecimal.ZERO)

    fun getPaymentsFromPayer(payer: Diner): Map<Diner, BigDecimal> =
        pairMap.filterKeys { it.first === payer }.mapKeys { it.key.second }

    fun getPaymentsToPayee(payee: Diner): Map<Diner, BigDecimal> =
        pairMap.filterKeys { it.second === payee }.mapKeys { it.key.first }

    fun removePaymentsFromPayer(payer: Diner): Map<Diner, BigDecimal> {
        val filteredMap = pairMap.filterKeys { it.first === payer }
        pairMap.keys.removeAll(filteredMap.keys)
        return filteredMap.mapKeys { it.key.second }
    }

    fun consolidate(): TransactionMap {
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
        pairMap.entries.removeIf { it.value.compareTo(BigDecimal.ZERO) == 0 }
        return this
    }

    // TODO structural equality override
}


class TransactionProcessor(
    val scope: CoroutineScope,
    val billId: String,
    val cashPool: Diner,
    val restaurant: Diner,
    diners: Flow<Set<Diner>>,
    committedPayments: Flow<Set<Payment>>,
    restaurantTotalErrors: Flow<Map<Diner, BigDecimal>>
) {
    private val allParticipants = diners.map {
        it.plus(cashPool).plus(restaurant)
    }.stateIn(scope, SharingStarted.WhileSubscribed(), emptySet())

    private val invertedCommittedPayments: StateFlow<TransactionMap> =
        combine(allParticipants, committedPayments) { participants, payments ->
            TransactionMap(participants).also { transactionMap ->
                payments.forEach { payment ->
                    transactionMap.addTransaction(
                        payment.payee,
                        payment.payer,
                        payment.amount)
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), TransactionMap(emptySet()))

    private val restaurantTransactionMap: StateFlow<TransactionMap> = diners.assemble(scope) {
        it.displayedRestaurantTotal
    }.map { restaurantTotals ->
        TransactionMap(restaurantTotals.keys.plus(cashPool).plus(restaurant)).also {
            restaurantTotals.forEach { (payer, amount) ->
                it.addTransaction(payer, restaurant, amount)
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), TransactionMap(emptySet()))

    private val intraDinerTransactionMap: StateFlow<TransactionMap> = diners.assemble(scope) {
        it.netIntraDinerAmount
    }.map { intraDinerAmounts ->
        TransactionMap(intraDinerAmounts.keys.plus(cashPool)).also { transactionMap ->
            intraDinerAmounts.forEach { (diner, amount) ->
                if (amount > BigDecimal.ZERO) {
                    transactionMap.addTransaction(
                        diner,
                        cashPool,
                        amount
                    )
                } else if (amount < BigDecimal.ZERO) {
                    transactionMap.addTransaction(
                        cashPool,
                        diner,
                        amount
                    )
                }
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), TransactionMap(emptySet()))

    private val netTransactions = combine(
        invertedCommittedPayments,
        restaurantTransactionMap,
        intraDinerTransactionMap
    ) { committed, toRestaurant, intraDiner ->
        committed.merge(toRestaurant).merge(intraDiner)
    }.stateIn(scope, SharingStarted.WhileSubscribed(), TransactionMap(emptySet()))

    val newPayments: StateFlow<Map<Diner, List<Payment>>> =
        combine(
            netTransactions,
            diners.assemble(scope) { it.paymentTemplatesFlow },
            restaurantTotalErrors
        ) { transactionMap, templates, restaurantTotalErrors ->

            val dinerSet = transactionMap.allParticipants.minus(cashPool).minus(restaurant)
            val restaurantTemplates = dinerSet.associateWith { templates[it]?.get(restaurant.id) }

            val paymentsMap = dinerSet.associateWith { mutableListOf<Payment>() }.toMutableMap()
            val dinersWithoutBills = dinerSet.toMutableSet()

            fun createPayment(
                amount: BigDecimal,
                method: PaymentMethod,
                payer: Diner,
                payee: Diner,
                surrogate: Diner?
            ) {
                if(payee == restaurant) {
                    dinersWithoutBills.remove(payer)
                }

                // Remove balance from transactionMap using inverse transaction
                transactionMap.addTransaction(payee, payer, amount)

                paymentsMap[payer]!!.add(
                    Payment(
                        newUUID(),
                        billId,
                        amount.toString(),
                        method,
                        false,
                        payer.id,
                        payee.id,
                        surrogate?.id
                    ).also {
                        it.payer = payer
                        it.payee = payee
                        it.surrogate = surrogate
                    }
                )
            }

            // Insert surrogate transactions for indirect payments to restaurant via peer-to-peer
            transactionMap.getPaymentsToPayee(restaurant).forEach { (payer, amount) ->
                restaurantTemplates[payer]?.also { template ->
                    if (template.surrogateId != null) {
                        dinerSet.find { it.id == template.surrogateId }?.also { surrogate ->
                            createPayment(amount, template.method, payer, restaurant, surrogate)
                            transactionMap.addTransaction(surrogate, restaurant, amount)
                        }
                    }
                }
            }

            transactionMap.consolidate()

            // Determine if a credit card pool exists
            val poolCreditCardUsers = mutableListOf<Diner>()
            val poolCashContributors = mutableSetOf<Diner>()
            val nonPoolCreditCardUsers = mutableSetOf<Diner>()
            var poolAmount = BigDecimal.ZERO
            dinerSet.forEach { payer ->
                restaurantTemplates[payer].also { template ->
                    when {
                        (template?.method == null || template.method == PaymentMethod.CASH) -> {
                            // Diner is paying restaurant with cash and may pay into a cash pool if
                            // any diners are splitting by credit card
                            poolCashContributors.add(payer)
                            poolAmount += transactionMap.getTransactionAmount(payer, restaurant)
                        }
                        (template.method == PaymentMethod.CREDIT_CARD_SPLIT) -> {
                            // Diner is contributing to the group split with their credit card.
                            // Diner may not necessarily have an amount due to the restaurant
                            // separate from this pooling.
                            poolCreditCardUsers.add(payer)
                            poolAmount += transactionMap.getTransactionAmount(payer, restaurant)
                        }
                        (template.method == PaymentMethod.CREDIT_CARD_INDIVIDUAL) -> {
                            // Diner is paying by credit card, but only for their own amount due.
                            // This payment will be handled independently from the pool.
                            nonPoolCreditCardUsers.add(payer)
                        }
                        else -> { /* Currently no other methods exist for paying restaurant */ }
                    }
                }
            }

            // Create payments for restaurant
            if (poolCreditCardUsers.isEmpty()) {
                // Each diner pays restaurant individually
                transactionMap.getPaymentsToPayee(restaurant).forEach { (payer, amount) ->
                    restaurantTemplates[payer].also { template ->
                        createPayment(
                            amount,
                            template?.method ?: PaymentMethod.CASH,
                            payer,
                            restaurant,
                            null
                        )
                    }
                }
            } else {
                /* Payment to restaurant is split evenly over a set of credit cards with cash-paying
                   diners paying into a pool that is distributed to the credit card-paying diners */
                val poolCreditCardShare = poolAmount
                    .divideWithZeroBypass(poolCreditCardUsers.size)
                    .setScale(poolAmount.scale(), RoundingMode.FLOOR)

                val netCorrection = poolAmount - poolCreditCardShare
                    .multiply(BigDecimal(poolCreditCardUsers.size))
                val incrementValue = poolAmount.ulp()
                var incrementCount = netCorrection.divide(incrementValue).toInt()

                val poolCreditCardUsersAndErrors = poolCreditCardUsers.map {
                    Pair(it, restaurantTotalErrors[it] ?: BigDecimal.ZERO)
                }

                /* Sort credit card-paying diners by their restaurant total errors such that the
                   largest error appears first. By applying correction increments to the diners with
                   the largest error first, the amount each credit-card paying diner takes from the
                   cash pool is the same to within 1 ulp. */
                sort(poolCreditCardUsersAndErrors, compareBy<Pair<Diner, BigDecimal>> {
                    -it.second
                }.thenBy { -it.first.listIndex })

                poolCreditCardUsersAndErrors.forEach { (payer, _) ->

                    val creditCardCharge = if (incrementCount > 0){
                        incrementCount--
                        poolCreditCardShare + incrementValue
                    } else {
                        poolCreditCardShare
                    }

                    createPayment(
                        creditCardCharge,
                        PaymentMethod.CREDIT_CARD_SPLIT,
                        payer,
                        restaurant,
                        null)

                    // Settle remaining balance by taking or contributing to the cash pool
                    transactionMap.removeTransaction(payer, restaurant)?.also { remainingBalance ->
                        if (remainingBalance > BigDecimal.ZERO) {
                            transactionMap.addTransaction(payer, cashPool, remainingBalance)
                        } else if (remainingBalance < BigDecimal.ZERO) {
                            transactionMap.addTransaction(cashPool, payer, remainingBalance)
                        }
                    }
                }

                poolCashContributors.forEach { payer->
                    transactionMap.removeTransaction(payer, restaurant)!!.also {
                        transactionMap.addTransaction(payer, cashPool, it)
                    }
                }

                nonPoolCreditCardUsers.forEach { payer->
                    createPayment(
                        transactionMap.getTransactionAmount(payer, restaurant),
                        PaymentMethod.CREDIT_CARD_INDIVIDUAL,
                        payer,
                        restaurant,
                        null)
                }
            }

            // Create payments for peer-to-peer transactions
            // TODO
//            dinerSet.forEach { payer ->
//                transactionMap.getPaymentsFromPayer(payer).forEach { (payee, amount) ->
//                    if (payee != restaurant) {
//                        createPayment(
//                            amount,
//                            payer.getPaymentTemplate(payee)?.method ?: PaymentMethod.CASH,
//                            payer,
//                            payee,
//                            null)
//                    }
//                }
//            }

            /* Insert zero amount payments to restaurant for diners without bills. This is needed for
            the diner to appear as a candidate for acting as a surrogate. These transactions should not
            appear when the PaymentFragment is in the default state. */
            for (diner in dinersWithoutBills.toSet()) {
                createPayment(
                    BigDecimal.ZERO,
                    PaymentMethod.CASH,
                    diner,
                    restaurant,
                    null)
            }

            paymentsMap
        }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyMap())
}


