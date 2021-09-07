package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Payment
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

class PaymentsViewModel(app: Application): UIViewModel(app) {

    companion object {
        const val DEFAULT = 0
        const val SELECTING_METHOD = 1
        const val SHOWING_SURROGATE_INSTRUCTIONS = 2
        const val CANDIDATE_SURROGATE = 3
        const val INELIGIBLE_SURROGATE = 4
        val INITIAL_STABLE_ID_AND_STATE: Pair<Long?, Int> = Pair(null, DEFAULT)
    }

    private val activePaymentAndMethod = MutableStateFlow<Pair<Payment?, PaymentMethod?>>(Pair(null, null))
    private val paymentsWithStableIds = repository.payments.mapLatest { payments ->
        payments.map { Pair(it, repository.getPaymentStableId(it)) }
    }

    val diners = repository.diners.asLiveData()
    val paymentsData: LiveData<Pair<List<Triple<Payment, Long?, Int>>, Pair<Long?, Int>>> =
        combine(paymentsWithStableIds, activePaymentAndMethod){ paymentsAndStableIds, active ->

        if (active.first == null) {
            // All items in default state
            Pair(
                paymentsAndStableIds.map { Triple(it.first, it.second, DEFAULT) },
                INITIAL_STABLE_ID_AND_STATE
            )
        } else {
            val activePaymentStableId = repository.getPaymentStableId(active.first)
            if (active.second == null) {
                // Selecting payment method on one of the items
                Pair(
                    paymentsAndStableIds.map {
                        Triple(
                            it.first,
                            it.second,
                            if (it.second == activePaymentStableId) {
                                SELECTING_METHOD
                            } else {
                                DEFAULT
                            }
                        )
                    },
                    Pair(activePaymentStableId, SELECTING_METHOD)
                )
            } else {
                // Selecting surrogate payer for one of the items
                Pair(
                    paymentsAndStableIds.map {
                        Triple(
                            it.first,
                            it.second,
                            when {
                                (it.second == activePaymentStableId) -> {
                                    SHOWING_SURROGATE_INSTRUCTIONS
                                }
                                cannotActAsSurrogate(it.first.payer) -> {
                                    INELIGIBLE_SURROGATE
                                }
                                else -> { CANDIDATE_SURROGATE }
                            }
                        )
                    }.filter { it.first.payee.isRestaurant() },
                    Pair(activePaymentStableId, SHOWING_SURROGATE_INSTRUCTIONS)
                )
            }
        }
    }.asLiveData()

    private fun cannotActAsSurrogate(diner: Diner) =
        diner.paymentsSent.any {
            it.payee.isRestaurant() && it.surrogate?.isCashPool() == false
        }

    fun getPaymentStableId(payment: Payment?) = repository.getPaymentStableId(payment)

    fun setActivePayment(payment: Payment?) {
        activePaymentAndMethod.value = Pair(payment, null) }

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