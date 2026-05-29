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
                // Seed data on database creation using the raw db object to prevent Room deadlocks!
                try {
                    // Default SMS sender setting
                    db.execSQL("INSERT INTO app_settings (key, value) VALUES ('selected_sms_sender', 'HDFCBank')")

                    // Default Preferred Currency setting
                    db.execSQL("INSERT INTO app_settings (key, value) VALUES ('preferred_currency', 'QAR')")

                    // Seed transaction data
                    val now = System.currentTimeMillis()
                    val oneDayInMs = 24 * 60 * 60 * 1000L

                    // SQLite syntax for INSERT
                    val insertSql = "INSERT INTO transactions (amount, type, category, tag, description, sender, account, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    
                    db.compileStatement(insertSql).use { statement ->
                        // tx 1
                        statement.bindDouble(1, 4200.0)
                        statement.bindString(2, "CREDIT")
                        statement.bindString(3, "Salary")
                        statement.bindString(4, "paycheck")
                        statement.bindString(5, "Monthly Salary Credited")
                        statement.bindString(6, "Manual")
                        statement.bindString(7, "Bank")
                        statement.bindLong(8, now - 5 * oneDayInMs)
                        statement.executeInsert()

                        // tx 2
                        statement.bindDouble(1, 45.50)
                        statement.bindString(2, "DEBIT")
                        statement.bindString(3, "Food")
                        statement.bindString(4, "dining")
                        statement.bindString(5, "Dinner at Olive Garden")
                        statement.bindString(6, "Manual")
                        statement.bindString(7, "Credit Card")
                        statement.bindLong(8, now - 4 * oneDayInMs)
                        statement.executeInsert()

                        // tx 3
                        statement.bindDouble(1, 15.75)
                        statement.bindString(2, "DEBIT")
                        statement.bindString(3, "Transport")
                        statement.bindString(4, "rideshare")
                        statement.bindString(5, "Uber Ride to office")
                        statement.bindString(6, "Manual")
                        statement.bindString(7, "Credit Card")
                        statement.bindLong(8, now - 3 * oneDayInMs)
                        statement.executeInsert()

                        // tx 4
                        statement.bindDouble(1, 120.00)
                        statement.bindString(2, "DEBIT")
                        statement.bindString(3, "Shopping")
                        statement.bindString(4, "groceries")
                        statement.bindString(5, "Weekly Grocery shopping at Walmart")
                        statement.bindString(6, "Manual")
                        statement.bindString(7, "Cash")
                        statement.bindLong(8, now - 2 * oneDayInMs)
                        statement.executeInsert()

                        // tx 5
                        statement.bindDouble(1, 350.00)
                        statement.bindString(2, "TRANSFER")
                        statement.bindString(3, "Rent/Bills")
                        statement.bindString(4, "bank_transfer")
                        statement.bindString(5, "Sent money to savings account")
                        statement.bindString(6, "Manual")
                        statement.bindString(7, "Bank")
                        statement.bindLong(8, now - 1 * oneDayInMs)
                        statement.executeInsert()

                        // tx 6
                        statement.bindDouble(1, 8.40)
                        statement.bindString(2, "DEBIT")
                        statement.bindString(3, "Food")
                        statement.bindString(4, "coffee")
                        statement.bindString(5, "Starbucks latte")
                        statement.bindString(6, "Manual")
                        statement.bindString(7, "Cash")
                        statement.bindLong(8, now - 12 * 60 * 60 * 1000L)
                        statement.executeInsert()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
