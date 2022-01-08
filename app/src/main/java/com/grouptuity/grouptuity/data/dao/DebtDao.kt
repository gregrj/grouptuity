package com.grouptuity.grouptuity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.grouptuity.grouptuity.data.entities.Debt
import com.grouptuity.grouptuity.data.entities.DebtCreditorJoin
import com.grouptuity.grouptuity.data.entities.DebtDebtorJoin


data class DebtLoadData(val debt: Debt,
                        val creditorIds: Collection<String>,
                        val debtorIds: Collection<String>)


@Dao
abstract class DebtDao: BaseDao<Debt>() {
    @Query("SELECT id FROM debt_table WHERE billId = :billId")
    abstract suspend fun getDebtIdsOnBill(billId: String): List<String>

    @Query("SELECT * FROM debt_table WHERE id = :debtId")
    abstract suspend fun getBaseDebt(debtId: String): Debt?

    @Query("SELECT id FROM diner_table INNER JOIN debt_debtor_join_table ON diner_table.id=debt_debtor_join_table.dinerId WHERE debt_debtor_join_table.debtId=:debtId")
    abstract suspend fun getDebtorIdsForDebt(debtId: String): List<String>

    @Query("SELECT id FROM diner_table INNER JOIN debt_creditor_join_table ON diner_table.id=debt_creditor_join_table.dinerId WHERE debt_creditor_join_table.debtId=:debtId")
    abstract suspend fun getCreditorIdsForDebt(debtId: String): List<String>

    @Insert
    abstract suspend fun addDebtorsForDebt(joins: List<DebtDebtorJoin>)

    @Insert
    abstract suspend fun addCreditorsForDebt(joins: List<DebtCreditorJoin>)

    @Transaction
    open suspend fun save(debt: Debt) {
        delete(debt)
        insert(debt)
        addDebtorsForDebt(debt.debtors.value.map { DebtDebtorJoin(debt.id, it.id) })
        addCreditorsForDebt(debt.creditors.value.map { DebtCreditorJoin(debt.id, it.id) })
    }

    @Transaction
    open suspend fun getDebtLoadDataForBill(billId: String): Map<String, DebtLoadData> =
        getDebtIdsOnBill(billId).mapNotNull { debtId ->
            getBaseDebt(debtId)?.let { debt ->
                DebtLoadData(
                    debt,
                    getDebtorIdsForDebt(debtId),
                    getCreditorIdsForDebt(debtId),
                )
            }
        }.associateBy { it.debt.id }
}