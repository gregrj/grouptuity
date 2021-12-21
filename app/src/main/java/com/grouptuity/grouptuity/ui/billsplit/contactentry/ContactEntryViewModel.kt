package com.grouptuity.grouptuity.ui.billsplit.contactentry

import android.app.Application
import androidx.lifecycle.LiveData
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.data.UIViewModel
import com.grouptuity.grouptuity.data.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map


class ContactEntryViewModel(app: Application): UIViewModel<Unit?, Diner?>(app) {

    companion object {
        const val NONE = 0
        const val CANCEL = 1
        const val FINISH = 2

        const val NAME = 0
        const val EMAIL = 1
        const val VENMO = 2
        const val CASH_APP = 3
        const val ALGORAND = 4
    }

    private val activeField = MutableStateFlow<Int?>(null)
    private val nameInput = MutableStateFlow<String?>(null)
    private val emailInput = MutableStateFlow<String?>(null)
    private val venmoInput = MutableStateFlow<String?>(null)
    private val cashAppInput = MutableStateFlow<String?>(null)
    private val algorandInput = MutableStateFlow<String?>(null)

    val name: StateFlow<String?> = nameInput

    val toolbarButtonState: LiveData<Int> = combine(activeField, nameInput, emailInput, venmoInput, cashAppInput, algorandInput) {
        when (it[0] as Int?) {
            NAME -> if (it[1] == null) CANCEL else FINISH
            EMAIL -> if (it[2] == null) CANCEL else FINISH
            VENMO -> if (it[3] == null) CANCEL else FINISH
            CASH_APP -> if (it[4] == null) CANCEL else FINISH
            ALGORAND -> if (it[5] == null) CANCEL else FINISH
            else -> NONE
        }
    }.asLiveData(isOutputLocked)

    val enableCreateContact: LiveData<Boolean> = name.map {
        it != null
    }.asLiveData(isOutputLocked)

    override fun onInitialize(input: Unit?) {
        activeField.value = NAME
        nameInput.value = null
        emailInput.value = null
        venmoInput.value = null
        cashAppInput.value = null
        algorandInput.value = null
    }

    fun activateField(fieldNumber: Int) {
        activeField.value = fieldNumber
    }

    fun deactivateField(fieldNumber: Int) {
        if (activeField.value == fieldNumber)
            activeField.value = null
    }

    fun setNameInput(input: String?) {
        nameInput.value = if (input.isNullOrBlank()) null else input.trim()
    }

    fun setEmailInput(input: String?) {
        emailInput.value = if (input.isNullOrBlank()) null else input.trim()
    }

    fun setVenmoInput(input: String?) {
        venmoInput.value = if (input.isNullOrBlank()) null else input.trim()
    }

    fun setCashAppInput(input: String?) {
        cashAppInput.value = if (input.isNullOrBlank()) null else input.trim()
    }

    fun setAlgorandInput(input: String?) {
        algorandInput.value = if (input.isNullOrBlank()) null else input.trim()
    }

    fun addContactToBill(
        name: String,
        emailAddress: String?,
        venmoAddress: String?,
        cashAppAddress: String?,
        algorandAddress: String?) {

        if (enableCreateContact.value != true) {
            return
        }

        val paymentAddresses = mutableMapOf<PaymentMethod, String>()
        if (emailAddress?.isNotBlank() == true) {
            paymentAddresses[PaymentMethod.PAYBACK_LATER] = emailAddress
        }
        if (venmoAddress?.isNotBlank() == true) {
            paymentAddresses[PaymentMethod.VENMO] = venmoAddress
        }
        if (cashAppAddress?.isNotBlank() == true) {
            paymentAddresses[PaymentMethod.CASH_APP] = cashAppAddress
        }
        if (algorandAddress?.isNotBlank() == true) {
            paymentAddresses[PaymentMethod.ALGO] = algorandAddress
        }

        finishFragment(repository.createNewDiner(name, paymentAddresses))
    }

    override fun handleOnBackPressed() { finishFragment(null) }
}