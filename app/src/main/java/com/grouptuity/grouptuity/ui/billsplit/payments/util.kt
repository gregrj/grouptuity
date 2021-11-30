package com.grouptuity.grouptuity.ui.billsplit.payments

import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Payment


enum class PeerToPeerAppUserRole {
    SENDING,
    RECEIVING,
    MEDIATING
}


data class PeerToPeerTransactionData(
    val sender: Diner,
    val senderAddress: String,
    val receiver: Diner,
    val receiverAddress: String,
    val appUserRole: PeerToPeerAppUserRole)


fun extractPeerToPeerData(payment: Payment): PeerToPeerTransactionData? {
    // Retrieve template from the payment's payer
    val template = payment.payer.getPaymentTemplate(payment.payee) ?: return null

    // Payer is the sender for all payee and/or surrogate configurations
    val sender = payment.payer
    val senderAddress = template.payerAddress ?: return null

    val (receiver, receiverAddress) = if (payment.payee.isRestaurant()) {
        // Surrogate has covered the bill to the restaurant and needs to be paid back by the payer
        Pair(payment.surrogate ?: return null, template.surrogateAddress ?: return null)
    } else {
        // Payer is directly paying the payee
        Pair(payment.payee, template.payeeAddress ?: return null)
    }

    // Determine how the app user is involved in this transaction
    val selfRole: PeerToPeerAppUserRole = when {
        sender.isSelf() -> {
            // User owes another diner
            PeerToPeerAppUserRole.SENDING
        }
        receiver.isSelf() -> {
            // Another diner owes the user directly
            PeerToPeerAppUserRole.RECEIVING
        }
        else -> {
            // Mediating between two other diners
            PeerToPeerAppUserRole.MEDIATING
        }
    }

    return PeerToPeerTransactionData(
        sender,
        senderAddress,
        receiver,
        receiverAddress,
        selfRole)
}