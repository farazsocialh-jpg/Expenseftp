package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val appSettingDao: AppSettingDao
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    fun getSettingFlow(key: String, defaultValue: String): Flow<String> {
        return appSettingDao.getSettingByKey(key).map { it?.value ?: defaultValue }
    }

    suspend fun getSettingImmediate(key: String, defaultValue: String): String {
        return appSettingDao.getSettingByKeyImmediate(key)?.value ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        appSettingDao.insertSetting(AppSettingEntity(key, value))
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun clearAllTransactions() {
        transactionDao.deleteAllTransactions()
    }

    // Backup & Restore functionality using Moshi
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Define a class specifically for the backup data structure to make it clean and extendable
    data class BackupData(
        val version: Int,
        val transactions: List<TransactionBackupItem>
    )

    data class TransactionBackupItem(
        val amount: Double,
        val type: String,
        val category: String,
        val tag: String,
        val description: String,
        val sender: String,
        val account: String,
        val timestamp: Long
    )

    fun exportBackupToJson(transactions: List<TransactionEntity>): String {
        val backupItems = transactions.map {
            TransactionBackupItem(
                amount = it.amount,
                type = it.type,
                category = it.category,
                tag = it.tag,
                description = it.description,
                sender = it.sender,
                account = it.account,
                timestamp = it.timestamp
            )
        }
        val backupData = BackupData(version = 1, transactions = backupItems)
        val adapter = moshi.adapter(BackupData::class.java)
        return adapter.toJson(backupData)
    }

    suspend fun importBackupFromJson(jsonString: String): Boolean {
        return try {
            val adapter = moshi.adapter(BackupData::class.java)
            val backupData = adapter.fromJson(jsonString) ?: return false
            
            // Clear current transactions if import succeeds
            transactionDao.deleteAllTransactions()

            // Insert imported ones
            for (item in backupData.transactions) {
                transactionDao.insertTransaction(
                    TransactionEntity(
                        amount = item.amount,
                        type = item.type,
                        category = item.category,
                        tag = item.tag,
                        description = item.description,
                        sender = item.sender,
                        account = item.account,
                        timestamp = item.timestamp
                    )
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
