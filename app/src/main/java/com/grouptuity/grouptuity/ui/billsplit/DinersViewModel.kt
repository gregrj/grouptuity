package com.grouptuity.grouptuity.ui.billsplit

import android.accounts.AccountManager
import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.BaseUIViewModel
import com.grouptuity.grouptuity.data.StoredPreference
import com.grouptuity.grouptuity.data.entities.Diner
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import java.text.NumberFormat


class DinersViewModel(application: Application): BaseUIViewModel(application) {
    private val formatter = NumberFormat.getCurrencyInstance()

    // Diners paired with their individual subtotals as currency strings
    val dinerData: LiveData<List<Triple<Diner, String, String>>> =
        combine(repository.diners, repository.individualSubtotals) { diners, _ ->
            diners.map { diner ->
                val numItems = diner.items.value.size
                val subtotalString = formatter.format(diner.displayedSubtotal.value)
                val itemCountAndSubtotal = if (numItems == 0) {
                    resources.getString(R.string.diners_zero_items)
                } else {
                    resources.getQuantityString(
                        R.plurals.diners_num_items_with_subtotal,
                        numItems,
                        numItems,
                        subtotalString)
                }

                Triple(diner, subtotalString, itemCountAndSubtotal)
            }
        }.asLiveData()

    fun removeDiner(diner: Diner) { repository.removeDiner(diner) }

    fun addUserOrGetAccounts(): List<String>? {
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
