package com.grouptuity.grouptuity.ui.billsplit

import android.accounts.AccountManager
import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.combineTransform
import java.text.NumberFormat

class DinersViewModel(application: Application): UIViewModel(application) {
    private val formatter = NumberFormat.getCurrencyInstance()

    // Diners paired with their individual subtotals as currency strings
    val dinerData: LiveData<List<Pair<Diner, String>>> = combineTransform(repository.diners, repository.individualSubtotals) { diners, subtotals ->
        if (diners.size + 2 == subtotals.size) {
            emit(diners.map { diner ->
                diner to formatter.format(subtotals.getOrDefault(diner, 0.0))
            })
        }
    }.asLiveData()

    fun removeDiner(diner: Diner) { repository.removeDiner(diner) }

    fun addSelfOrGetAccounts(): List<String>? {
        return if (repository.prefUserName.isSet == true) {
            repository.addSelfAsDiner()
            null
        } else {
            AccountManager.get(context).getAccountsByType("com.google").map { it.name }
        }
    }

    fun addSelfToBill(userName: String?) {
        if (userName != null) {
            repository.prefUserName.value = userName
            repository.addSelfAsDiner(userName)
        } else {
            repository.addSelfAsDiner()
        }
    }
}