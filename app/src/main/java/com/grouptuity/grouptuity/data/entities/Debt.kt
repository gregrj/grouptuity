package com.grouptuity.grouptuity.data.entities

import androidx.room.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal


@Entity(tableName = "debt_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Debt(
    @PrimaryKey val id: String,
    override val billId: String,
    val listIndex: Long,
    var name: String
): BillComponent() {
    var amountInput = "0"
        set(value) {
            field = value
            _amount.value = BigDecimal(value)
        }
    @Ignore private val _amount = MutableStateFlow(BigDecimal.ZERO)
    @Ignore val amount: StateFlow<BigDecimal> = _amount

    @Ignore private val mCreditors = mutableSetOf<Diner>()
    @Ignore private val _creditors = MutableStateFlow<Set<Diner>>(mCreditors)
    @Ignore val creditors: StateFlow<Set<Diner>> = _creditors

    @Ignore private val mDebtors = mutableSetOf<Diner>()
    @Ignore private val _debtors = MutableStateFlow<Set<Diner>>(mDebtors)
    @Ignore val debtors: StateFlow<Set<Diner>> = _debtors

    @Ignore val creditorShare = amount.divide(creditors.map { it.size })
    @Ignore val debtorShare = amount.divide(creditors.map { it.size })

    override fun onDelete() {
        setCreditors(emptySet())
        setDebtors(emptySet())
    }

    internal fun addCreditors(diners: Set<Diner>) {
        mCreditors.addAll(diners)
        _creditors.value = mCreditors.toSet()
        diners.forEach {
            it.onHeldDebtAdded(this)
        }
    }

    internal fun addDebtors(diners: Set<Diner>) {
        mDebtors.addAll(diners)
        _debtors.value = mDebtors.toSet()
        diners.forEach {
            it.onOwedDebtAdded(this)
        }
    }

    internal fun removeCreditor(diner: Diner) {
        mCreditors.remove(diner)
        _creditors.value = mCreditors.toSet()
        diner.onHeldDebtRemoved(this)
    }

    internal fun removeDebtor(diner: Diner) {
        mDebtors.remove(diner)
        _debtors.value = mDebtors.toSet()
        diner.onOwedDebtRemoved(this)
    }

    fun setCreditors(diners: Set<Diner>) {
        val addedCreditors = diners - mCreditors
        val removedCreditors = mCreditors - diners

        addedCreditors.forEach {
            it.onHeldDebtAdded(this)
        }
        
        removedCreditors.forEach {
            it.onHeldDebtRemoved(this)
        }
        
        mCreditors.clear()
        mCreditors.addAll(diners)
        
        _creditors.value = mCreditors.toSet()
    }

    fun setDebtors(diners: Set<Diner>) {
        val addedDebtors = diners - mDebtors
        val removedDebtors = mDebtors - diners

        addedDebtors.forEach {
            it.onHeldDebtAdded(this)
        }

        removedDebtors.forEach {
            it.onHeldDebtRemoved(this)
        }

        mDebtors.clear()
        mDebtors.addAll(diners)

        _debtors.value = mDebtors.toSet()
    }
}