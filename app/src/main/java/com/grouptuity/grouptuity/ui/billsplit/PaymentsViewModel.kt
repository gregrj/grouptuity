package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.text.NumberFormat

data class PaymentData(val payment: Payment,
                       val stableId: Long?,
                       val payerString: String,
                       val payInstructionsString: String,
                       val amountString: String,
                       val iconContact: Contact,
                       val isPaymentIconClickable: Boolean,
                       val allowSurrogatePaymentMethods: Boolean,
                       val displayState: Int)

class PaymentsViewModel(app: Application): UIViewModel(app) {

    companion object {
        const val DEFAULT_STATE = 0
        const val SELECTING_METHOD_STATE = 1
        const val SHOWING_INSTRUCTIONS_STATE = 2
        const val CANDIDATE_STATE = 3
        const val INELIGIBLE_STATE = 4

        val INITIAL_STABLE_ID_AND_STATE: Pair<Long?, Int> = Pair(null, DEFAULT_STATE)
    }

    private val activePaymentAndMethod = MutableStateFlow<Pair<Payment?, PaymentMethod?>>(Pair(null, null))
    private val paymentsWithStableIds = repository.payments.mapLatest { payments ->
        payments.map { Pair(it, repository.getPaymentStableId(it)) }
    }

    val diners = repository.diners.asLiveData()

    // List elements are Triples of the payment, its stable id, and its display data
    val paymentsData: LiveData<Pair<List<PaymentData>, Pair<Long?, Int>>> =
        combine(paymentsWithStableIds, activePaymentAndMethod) { paymentsAndStableIds, active ->
            val surrogates = paymentsAndStableIds.mapNotNull { (payment, _) ->
                if (payment.payee.isRestaurant() && payment.surrogate?.isCashPool() == false) {
                    payment.surrogate
                } else {
                    null
                }
            }.toSet()

            if (active.first == null) {
                // All items in default state
                Pair(
                    paymentsAndStableIds.mapNotNull {
                        if (it.first.amount > PRECISION) {
                            createPaymentData(
                                it.first,
                                it.second,
                                surrogates.contains(it.first.payer),
                                DEFAULT_STATE
                            )
                        } else {
                            null
                        }
                    },
                    INITIAL_STABLE_ID_AND_STATE
                )
            } else {
                val activePaymentStableId = repository.getPaymentStableId(active.first)
                if (active.second == null) {
                    // Selecting payment method on one of the items
                    Pair(
                        paymentsAndStableIds.mapNotNull {
                            if (it.first.amount > PRECISION) {
                                createPaymentData(
                                    it.first,
                                    it.second,
                                    surrogates.contains(it.first.payer),
                                    if (it.second == activePaymentStableId) {
                                        SELECTING_METHOD_STATE
                                    } else {
                                        DEFAULT_STATE
                                    }
                                )
                            } else {
                                null
                            }
                        },
                        Pair(activePaymentStableId, SELECTING_METHOD_STATE)
                    )
                } else {
                    val ineligibleSurrogates = paymentsAndStableIds.mapNotNull { (payment, _) ->
                        if (payment.payee.isRestaurant() && payment.surrogate?.isCashPool() == false) {
                            payment.payer
                        } else {
                            null
                        }
                    }.toSet()

                    Pair(
                        paymentsAndStableIds.map {
                            createPaymentData(
                                it.first,
                                it.second,
                                surrogates.contains(it.first.payer),
                                when {
                                    (it.second == activePaymentStableId) -> {
                                        SHOWING_INSTRUCTIONS_STATE
                                    }
                                    ineligibleSurrogates.contains(it.first.payer) -> {
                                        INELIGIBLE_STATE
                                    }
                                    else -> { CANDIDATE_STATE }
                                }
                            )
                        }.filter { it.payment.payee.isRestaurant() },
                        Pair(activePaymentStableId, SHOWING_INSTRUCTIONS_STATE)
                    )
                }
            }
        }.asLiveData()

    private fun createPaymentData(payment: Payment, stableId: Long?, actingAsSurrogate: Boolean, displayState: Int) =
        when {
            payment.payer.isCashPool() -> {
                PaymentData(
                    payment,
                    stableId,
                    payment.payee.name,
                    getApplication<Application>().resources.getString(R.string.payments_instruction_from_cash_pool),
                    NumberFormat.getCurrencyInstance().format(payment.amount),
                    payment.payee.contact,
                    isPaymentIconClickable = false,
                    allowSurrogatePaymentMethods = false,
                    displayState
                )
            }
            payment.payee.isCashPool() -> {
                PaymentData(
                    payment,
                    stableId,
                    payment.payer.name,
                    getApplication<Application>().resources.getString(R.string.payments_instruction_into_cash_pool),
                    NumberFormat.getCurrencyInstance().format(payment.amount),
                    payment.payer.contact,
                    isPaymentIconClickable=false,
                    allowSurrogatePaymentMethods=false,
                    displayState
                )
            }
            payment.payee.isRestaurant() -> {
                val surrogate = payment.surrogate

                PaymentData(
                    payment,
                    stableId,
                    payment.payer.name,
                    when {
                        surrogate == null -> {
                            getApplication<Application>().resources.getString(
                                payment.method.paymentInstructionStringId,
                                getApplication<Application>().resources.getString(
                                    R.string.payments_instruction_restaurant_payee
                                )
                            )
                        }
                        surrogate.isCashPool() -> {
                            getApplication<Application>().resources.getString(R.string.payments_instruction_into_cash_pool)
                        }
                        else -> {
                            getApplication<Application>().resources.getString(
                                payment.method.paymentInstructionStringId,
                                surrogate.name
                            )
                        }
                    },
                    NumberFormat.getCurrencyInstance().format(payment.amount),
                    payment.payer.contact,
                    isPaymentIconClickable = true,
                    allowSurrogatePaymentMethods = !actingAsSurrogate,
                    displayState
                )
            }
            else -> {
                PaymentData(
                    payment,
                    stableId,
                    payment.payer.name,
                    getApplication<Application>().resources.getString(payment.method.paymentInstructionStringId, payment.payee.name),
                    NumberFormat.getCurrencyInstance().format(payment.amount),
                    payment.payer.contact,
                    isPaymentIconClickable = true,
                    allowSurrogatePaymentMethods = true,
                    displayState
                )
            }
        }

    fun setActivePayment(payment: Payment?) {
        activePaymentAndMethod.value = Pair(payment, null)
    }

    fun setPaymentMethodPreference(payment: Payment, method: PaymentMethod) {
        when {
            payment.payee.isRestaurant() -> {
                if (method.acceptedByRestaurant) {
                    repository.setPaymentPreference(payment, method, null)
                    setActivePayment(null)
                } else {
                    // Payment must be routed through a surrogate
                    activePaymentAndMethod.value = Pair(payment, method)
                }
            }
            payment.payee.isCashPool() || payment.payer.isCashPool() -> {
                /* These transactions represent payments to/from the cash pool due to a credit card
                   split being under/over the splitting diner's payment due to the restaurant. The
                   user cannot change the payment method. */
            }
            else -> {
                // All peer-to-peer transactions
                repository.setPaymentPreference(payment, method, null)
                setActivePayment(null)
            }
        }
    }

    fun setSurrogate(diner: Diner) {
        activePaymentAndMethod.value.also { (payment, method) ->
            if (payment != null && method != null) {
                repository.setPaymentPreference(payment, method, diner)
                setActivePayment(null)
            }
        }
    }

    fun handleOnBackPressed(): Boolean {
        return when {
            activePaymentAndMethod.value.first != null -> {
                setActivePayment(null)
                false
            }
            else -> { true }
        }
    }
}