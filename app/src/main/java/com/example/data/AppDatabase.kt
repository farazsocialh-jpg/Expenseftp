package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TransactionEntity::class, AppSettingEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_manager_database"
                )
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed data on database creation
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val transactionDao = database.transactionDao()
                        val appSettingDao = database.appSettingDao()

                        // Default SMS sender setting
                        appSettingDao.insertSetting(
                            AppSettingEntity("selected_sms_sender", "HDFCBank")
                        )

                        // Seed transaction data
                        val now = System.currentTimeMillis()
                        val oneDayInMs = 24 * 60 * 60 * 1000L

                        val seedTransactions = listOf(
                            TransactionEntity(
                                amount = 4200.0,
                                type = "CREDIT",
                                category = "Salary",
                                tag = "paycheck",
                                description = "Monthly Salary Credited",
                                sender = "Manual",
                                account = "Bank",
                                timestamp = now - 5 * oneDayInMs
                            ),
                            TransactionEntity(
                                amount = 45.50,
                                type = "DEBIT",
                                category = "Food",
                                tag = "dining",
                                description = "Dinner at Olive Garden",
                                sender = "Manual",
                                account = "Credit Card",
                                timestamp = now - 4 * oneDayInMs
                            ),
                            TransactionEntity(
                                amount = 15.75,
                                type = "DEBIT",
                                category = "Transport",
                                tag = "rideshare",
                                description = "Uber Ride to office",
                                sender = "Manual",
                                account = "Credit Card",
                                timestamp = now - 3 * oneDayInMs
                            ),
                            TransactionEntity(
                                amount = 120.00,
                                type = "DEBIT",
                                category = "Shopping",
                                tag = "groceries",
                                description = "Weekly Grocery shopping at Walmart",
                                sender = "Manual",
                                account = "Cash",
                                timestamp = now - 2 * oneDayInMs
                            ),
                            TransactionEntity(
                                amount = 350.00,
                                type = "TRANSFER",
                                category = "Rent/Bills",
                                tag = "bank_transfer",
                                description = "Sent money to savings account",
                                sender = "Manual",
                                account = "Bank",
                                timestamp = now - 1 * oneDayInMs
                            ),
                            TransactionEntity(
                                amount = 8.40,
                                type = "DEBIT",
                                category = "Food",
                                tag = "coffee",
                                description = "Starbucks latte",
                                sender = "Manual",
                                account = "Cash",
                                timestamp = now - 12 * 60 * 60 * 1000L // 12 hours ago
                            )
                        )

                        for (tx in seedTransactions) {
                            transactionDao.insertTransaction(tx)
                        }
                    }
                }
            }
        }
    }
}
