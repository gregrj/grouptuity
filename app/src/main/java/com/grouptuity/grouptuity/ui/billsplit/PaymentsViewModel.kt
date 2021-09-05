package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Payment
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class PaymentsViewModel(app: Application): UIViewModel(app) {

    companion object {
        const val DEFAULT = 0
        const val SELECTING_METHOD = 1
        const val SHOWING_SURROGATE_INSTRUCTIONS = 2
        const val CANDIDATE_SURROGATE = 3
        const val INELIGIBLE_SURROGATE = 4
    }

    private val activePaymentAndMethod = MutableStateFlow<Pair<Payment?, PaymentMethod?>>(Pair(null, null))

    val diners = repository.diners.asLiveData()
    val payments: LiveData<List<Pair<Payment, Int>>> = combine(repository.payments, activePaymentAndMethod){ payments, active ->
        if (active.first == null) {
            Log.e("combine", "no payment")
            payments.map { Pair(it, DEFAULT) }
        } else {
            val activePaymentStableId = repository.getPaymentStableId(active.first)
            if (active.second == null) {
                Log.e("combine", active.first!!.payer.name +" "+ active.first!!.payee.name)
                payments.map {
                    Pair(
                        it,
                        if (repository.getPaymentStableId(it) == activePaymentStableId) {
                            SELECTING_METHOD
                        } else {
                            DEFAULT
                        }
                    )
                }
            } else {
                Log.e("combine", active.first!!.payer.name +" "+ active.first!!.payer.name +" "+active.second)
                payments.map {
                    Pair(it, when {
                        repository.getPaymentStableId(it) == activePaymentStableId -> {
                            SHOWING_SURROGATE_INSTRUCTIONS
                        }
                        cannotActAsSurrogate(it.payer, payments) -> { INELIGIBLE_SURROGATE }
                        else -> { CANDIDATE_SURROGATE }
                    })
                }.filter { it.first.payee.isRestaurant() }
            }
        }
    }.asLiveData()

    private fun cannotActAsSurrogate(diner: Diner, payments: List<Payment>) =
        payments.any {
            it.payerId == diner.id && it.payee.isRestaurant() && (it.surrogate != null && !it.surrogate!!.isCashPool())
        }

    fun getPaymentStableId(payment: Payment?) = repository.getPaymentStableId(payment)

    fun setActivePayment(payment: Payment?) {
        Log.e("set activePaymentAndMethod", payment?.payer?.name ?: "f")
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