package com.grouptuity.grouptuity.ui.billsplit

import android.accounts.AccountManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.data.BaseUIViewModel
import com.grouptuity.grouptuity.data.StoredPreference
import com.grouptuity.grouptuity.data.entities.Diner
import kotlinx.coroutines.flow.combineTransform
import java.text.NumberFormat


class DinersViewModel(application: GrouptuityApplication): BaseUIViewModel(application) {
    private val formatter = NumberFormat.getCurrencyInstance()

    // Diners paired with their individual subtotals as currency strings
    val dinerData: LiveData<List<Pair<Diner, String>>> =
        combineTransform(repository.diners, repository.individualSubtotals) { diners, subtotals ->
            if (diners.size + 2 == subtotals.size) {
                emit(diners.map { diner ->
                    diner to formatter.format(subtotals.getOrDefault(diner, 0.0))
                })
            }
        }.asLiveData()

    fun removeDiner(diner: Diner) { repository.removeDiner(diner) }

    fun addSelfOrGetAccounts(): List<String>? {
        return if (StoredPreference.userName.isSet == true) {
            //TODO query for boolean value
            repository.addUserAsDiner(false)
            null
        } else {
            AccountManager.get(context).getAccountsByType("com.google").map { it.name }
        }
    }

    fun addSelfToBill(userName: String?) {
        //TODO query for boolean value
        repository.addUserAsDiner(false, userName)
    }
}
