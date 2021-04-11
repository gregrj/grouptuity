package com.grouptuity.grouptuity.ui.billsplit.diners

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Repository
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat

class DinersViewModel(application: Application): AndroidViewModel(application) {
    private val repository = Repository.getInstance(application)
    private val formatter = NumberFormat.getCurrencyInstance()

    // Diners paired with their individual subtotals as currency strings
    val dinerData: LiveData<List<Pair<Diner, String>>> = combine(repository.diners, repository.dinerSubtotals) { diners, subtotals ->
        diners.map { diner ->
            diner to formatter.format(subtotals[diner.id])
        }
    }.asLiveData()

    fun removeDiner(diner: Diner) {
        // TODO repository.deleteDiner(diner)
    }

    fun addSelfToBill() {
        // TODO repository.addSelfToBill()
    }
}