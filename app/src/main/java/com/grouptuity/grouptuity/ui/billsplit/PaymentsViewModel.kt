package com.grouptuity.grouptuity.ui.billsplit

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import com.grouptuity.grouptuity.data.entities.Contact
import com.grouptuity.grouptuity.data.entities.Diner
import com.grouptuity.grouptuity.data.entities.Payment
import com.grouptuity.grouptuity.data.entities.PaymentMethod
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat

data class PaymentData(val payment: Payment,
                       val payerString: String,
                       val payInstructionsString: String,
                       val amountString: String,
                       val iconContact: Contact,
                       val isPaymentIconClickable: Boolean,
                       val allowSurrogatePaymentMethods: Boolean,
                       val displayState: Int)


class PaymentsViewModel(app: GrouptuityApplication): BaseUIViewModel(app) {

    companion object {
        const val DEFAULT_STATE = 0
        const val SELECTING_METHOD_STATE = 1
        const val SHOWING_INSTRUCTIONS_STATE = 2
        const val CANDIDATE_STATE = 3
        const val INELIGIBLE_STATE = 4

        const val SELECTING_PAYER_ALIAS = 0
        const val SELECTING_PAYEE_ALIAS = 1
        const val SELECTING_SURROGATE_ALIAS = 2

        val INITIAL_STABLE_ID_AND_STATE: Pair<Long?, Int> = Pair(null, DEFAULT_STATE)
    }

    private val showSetAddressDialogEventMutable = MutableLiveData<Event<Triple<Int, Diner, PaymentMethod>>>()
    val showSetAddressDialogEvent: LiveData<Event<Triple<Int, Diner, PaymentMethod>>> = showSetAddressDialogEventMutable

    val processPaymentsEvent: LiveData<Event<Boolean>> = repository.requestProcessPaymentsEvent

    private val activePaymentAndMethod = repository.activePaymentAndMethod
    private var cachedMethod: PaymentMethod? = null
    private var cachedPayerAddress: String? = null
    private var cachedPayeeAddress: String? = null
    private var cachedSurrogate: Diner? = null
    private var cachedSurrogateAddress: String? = null

    val diners = repository.diners.asLiveData()
    val paymentsData: LiveData<Pair<List<PaymentData>, Pair<Long?, Int>>> =
        combine(
            repository.payments,
            activePaymentAndMethod,
            repository.processingPayments
        ) { payments, active, processing ->
            val surrogates = payments.mapNotNull { payment ->
                if (payment.payee.isRestaurant && payment.surrogate?.isCashPool == false) {
                    payment.surrogate
                } else {
                    null
                }
            }.toSet()

            val eligibleSurrogates = getEligibleSurrogates(payments)

            val activePayment = active.first

            if (activePayment == null) {
                if (processing) {
                    // Only display unprocessed payments that are handled through the app
                    Pair(
                        payments.mapNotNull { payment ->
                            if (payment.hasNonZeroAmount && payment.isUnprocessed) {
                                createPaymentData(
                                    payment,
                                    surrogates.contains(payment.payer),
                                    PROCESSING_STATE,
                                    eligibleSurrogates
                                )
                            } else {
                                null
                            }
                        },
                        Pair(null, PROCESSING_STATE)
                    )
                } else {
                    // All items in default state
                    Pair(
                        payments.mapNotNull { payment ->
                            if (payment.hasNonZeroAmount) {
                                createPaymentData(
                                    payment,
                                    surrogates.contains(payment.payer),
                                    DEFAULT_STATE,
                                    eligibleSurrogates
                                )
                            } else {
                                null
                            }
                        },
                        INITIAL_STABLE_ID_AND_STATE
                    )
                }
            } else {
                val activePaymentStableId = activePayment.stableId
                if (active.second == null) {
                    // Selecting payment method on one of the items
                    Pair(
                        payments.mapNotNull { payment ->
                            if (payment.hasNonZeroAmount) {
                                createPaymentData(
                                    payment,
                                    surrogates.contains(payment.payer),
                                    if (payment.stableId == activePaymentStableId) {
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
                    val ineligibleSurrogates = getIneligibleSurrogates(payments)

                    Pair(
                        payments.map { payment ->
                            createPaymentData(
                                payment,
                                surrogates.contains(payment.payer),
                                when {
                                    (payment.stableId == activePaymentStableId) -> {
                                        SHOWING_INSTRUCTIONS_STATE
                                    }
                                    ineligibleSurrogates.contains(payment.payer) -> {
                                        INELIGIBLE_STATE
                                    }
                                    else -> { CANDIDATE_STATE }
                                }
                            )
                        }.filter { it.payment.payee.isRestaurant },
                        Pair(activePaymentStableId, SHOWING_INSTRUCTIONS_STATE)
                    )
                }
            }
        }.asLiveData()

    private fun createPaymentData(payment: Payment, actingAsSurrogate: Boolean, displayState: Int, eligibleSurrogates: Set<Diner>) =
        when {
            payment.payer.isCashPool -> {
                PaymentData(
                    payment,
                    payment.payee.name,
                    getApplication<Application>().resources.getString(R.string.payments_instruction_from_cash_pool),
                    NumberFormat.getCurrencyInstance().format(payment.amount),
                    payment.payee.asContact(),
                    isPaymentIconClickable = false,
                    allowSurrogatePaymentMethods = false,
                    displayState
                )
            }
            payment.payee.isCashPool -> {
                PaymentData(
                    payment,
                    payment.payer.name,
                    getApplication<Application>().resources.getString(R.string.payments_instruction_into_cash_pool),
                    NumberFormat.getCurrencyInstance().format(payment.amount),
                    payment.payer.asContact(),
                    isPaymentIconClickable=false,
                    allowSurrogatePaymentMethods=false,
                    displayState
                )
            }
            payment.payee.isRestaurant -> {
                val surrogate = payment.surrogate

                PaymentData(
                    payment,
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
                        surrogate.isCashPool -> {
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
                    payment.payer.asContact(),
                    isPaymentIconClickable = true,
                    allowSurrogatePaymentMethods = !actingAsSurrogate,
                    displayState
                )
            }
            else -> {
                PaymentData(
                    payment,
                    payment.payer.name,
                    getApplication<Application>().resources.getString(payment.method.paymentInstructionStringId, payment.payee.name),
                    NumberFormat.getCurrencyInstance().format(payment.amount),
                    payment.payer.asContact(),
                    isPaymentIconClickable = true,
                    allowSurrogatePaymentMethods = true,
                    displayState
                )
            }
        }

    private fun getEligibleSurrogates(payments: List<Payment>): Set<Diner> =
        repository.diners.value.toSet() - getIneligibleSurrogates(payments)

    private fun getIneligibleSurrogates(payments: List<Payment>): Set<Diner> =
        payments.mapNotNull { payment ->
            if (payment.payee.isRestaurant && payment.surrogate?.isCashPool == false) {
                payment.payer
            } else {
                null
            }
        }.toSet()

    override fun handleOnBackPressed() {
        when {
            repository.processingPayments.value -> {
                repository.processingPayments.value = false
            }
            activePaymentAndMethod.value.first != null -> {
                setActivePayment(null)
            }
            else -> {
                finishFragment(null)
            }
        }
    }

    fun commitPayment() {
        paymentInProcessing?.apply {
            repository.commitPayment(this)
            paymentInProcessing = null
        }
    }

    fun setActivePayment(payment: Payment?) {
        cachedMethod = null
        cachedPayerAddress = null
        cachedPayeeAddress = null
        cachedSurrogate = null
        cachedSurrogateAddress = null
        activePaymentAndMethod.value = Pair(payment, null)
    }

    fun setPaymentMethod(method: PaymentMethod) {
        cachedMethod = method
        activePaymentAndMethod.value.first?.apply {
            when {
                payee.isRestaurant -> {
                    if (method.acceptedByRestaurant) {
                        // Payment will be made directly to the restaurant
                        if (method.processedWithinApp) {
                            val template = payer.getPaymentTemplate(payee)
                            if (template?.method == method) {
                                // Existing template has same method so no further action required
                                setActivePayment(null)
                            } else {
                                cachedPayerAddress = payer.getAddressForMethod(method)
                                if (cachedPayerAddress == null) {
                                    // Need to input payer alias before setting payment method
                                    showSetAddressDialogEventMutable.value = Event(Triple(
                                        SELECTING_PAYER_ALIAS, payer, method))
                                } else {
                                    // Use default alias of the payer
                                    commitPaymentTemplate()
                                }
                            }
                        } else {
                            commitPaymentTemplate()
                        }
                    } else {
                        // Payment to restaurant will be routed through a surrogate
                        if (method.processedWithinApp) {
                            val template = payer.getPaymentTemplate(payee)
                            if (template?.method == method) {
                                // Existing template has same method so proceed to surrogate selection
                                cachedPayerAddress = template.payerAddress
                                activePaymentAndMethod.value = Pair(this, method)
                            } else {
                                cachedPayerAddress = payer.getAddressForMethod(method)
                                if (cachedPayerAddress == null) {
                                    // Need payer alias before setting payment method
                                    showSetAddressDialogEventMutable.value = Event(Triple(
                                        SELECTING_PAYER_ALIAS, payer, method))
                                } else {
                                    // Use default alias of the payer and go to surrogate selection
                                    activePaymentAndMethod.value = Pair(this, method)
                                }
                            }
                        } else {
                            // No aliases needed for payment method so proceed to surrogate selection
                            cachedMethod = method
                            activePaymentAndMethod.value = Pair(this, method)
                        }
                    }
                }
                payee.isCashPool || payer.isCashPool -> {
                    /* These transactions represent payments to/from the cash pool due to a credit card
                       split being under/over the splitting diner's payment due to the restaurant. The
                       user cannot change the payment method. */
                }
                else -> {
                    // All peer-to-peer transactions
                    if (method.processedWithinApp) {
                        val template = payer.getPaymentTemplate(payee)
                        if (template?.method == method) {
                            // Existing template has same method so no further action required
                            setActivePayment(null)
                        } else {
                            cachedPayerAddress = payer.getAddressForMethod(method)
                            if (cachedPayerAddress == null) {
                                // Need payer alias before setting payment method
                                showSetAddressDialogEventMutable.value = Event(Triple(
                                    SELECTING_PAYER_ALIAS, payer, method))
                            } else {
                                cachedPayeeAddress = payee.getAddressForMethod(method)
                                if (cachedPayeeAddress == null) {
                                    // Need payee alias before setting payment method
                                    showSetAddressDialogEventMutable.value = Event(Triple(
                                        SELECTING_PAYEE_ALIAS, payee, method))
                                } else {
                                    // Use default alias of the payer and payee
                                    commitPaymentTemplate()
                                }
                            }
                        }
                    } else {
                        // No aliases needed for payment method so commit payment template
                        commitPaymentTemplate()
                    }
                }
            }
        }
    }

    fun setPayerAddress(payerAddress: String) {
        cachedPayerAddress = payerAddress
        activePaymentAndMethod.value.first?.apply {
            repository.saveAddressForDiner(payer, cachedMethod!!, payerAddress)

            when {
                payee.isRestaurant -> {
                    if (cachedMethod!!.acceptedByRestaurant) {
                        commitPaymentTemplate()
                    } else {
                        // Go to surrogate selection
                        activePaymentAndMethod.value = Pair(this, cachedMethod)
                    }
                }
                else -> {
                    // Peer-to-peer transaction also requires a payeeAddress
                    cachedPayeeAddress = payee.getAddressForMethod(cachedMethod!!)
                    if (cachedPayeeAddress == null) {
                        // Need payee alias before setting payment method
                        showSetAddressDialogEventMutable.value = Event(Triple(
                            SELECTING_PAYEE_ALIAS, payee, cachedMethod!!))
                    } else {
                        // Use default aliases of the payer and payee
                        commitPaymentTemplate()
                    }
                }
            }
        }
    }

    fun setPayeeAddress(payeeAddress: String) {
        cachedPayeeAddress = payeeAddress

        activePaymentAndMethod.value.first?.apply {
            repository.saveAddressForDiner(payee, cachedMethod!!, payeeAddress)
        }

        commitPaymentTemplate()
    }

    fun setSurrogate(surrogate: Diner) {
        cachedSurrogate = surrogate
        activePaymentAndMethod.value.first?.apply {
            val template = payer.getPaymentTemplate(payee)
            if (template?.method == cachedMethod && template?.surrogateId == surrogate.id) {
                // Existing template has same method and surrogate so no further action required
                setActivePayment(null)
            } else {
                cachedSurrogateAddress = surrogate.getAddressForMethod(cachedMethod!!)
                if (cachedSurrogateAddress == null) {
                    // Need surrogate alias before committing
                    showSetAddressDialogEventMutable.value = Event(Triple(
                        SELECTING_SURROGATE_ALIAS, surrogate, cachedMethod!!))
                } else {
                    // Use default surrogate alias and commit
                    commitPaymentTemplate()
                }
            }
        }
    }

    fun setSurrogateAddress(surrogateAddress: String) {
        cachedSurrogateAddress = surrogateAddress
        repository.saveAddressForDiner(cachedSurrogate!!, cachedMethod!!, surrogateAddress)
        commitPaymentTemplate()
    }

    private fun commitPaymentTemplate() {
        activePaymentAndMethod.value.also { (payment, _) ->
            if (payment != null && cachedMethod != null) {
                repository.setPaymentTemplate(
                    cachedMethod!!,
                    payment.payer,
                    payment.payee,
                    cachedSurrogate,
                    cachedPayerAddress,
                    cachedPayeeAddress,
                    cachedSurrogateAddress)
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
