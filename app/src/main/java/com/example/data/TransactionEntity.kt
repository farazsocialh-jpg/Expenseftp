package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // DEBIT, CREDIT, TRANSFER
    val category: String, // Food, Salary, Housing, etc.
    val tag: String, // Suggested/custom tag
    val description: String,
    val sender: String = "Manual", // Selected SMS sender or "Manual"
    val account: String = "Cash", // Cash, Bank, Credit Card
    val timestamp: Long = System.currentTimeMillis()
)
