package com.example.util

import com.example.data.TransactionEntity
import java.util.regex.Pattern

data class ParsedSmsTransaction(
    val amount: Double,
    val type: String, // DEBIT, CREDIT, TRANSFER
    val category: String,
    val tag: String,
    val description: String,
    val sender: String,
    val account: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SmsParser {
    // Regex to capture amounts like Rs. 500, Rs 500.00, $45.50, INR 12,00.00, 1500 USD, etc.
    private val amountRegex = Pattern.compile(
        "(?:Rs\\.?|INR|USD|\\$|EUR|GBP|£)\\s*([0-9,]+(?:\\.[0-9]{2})?)|([0-9,]+(?:\\.[0-9]{2})?)\\s*(?:USD|INR|EUR|GBP|Rs|bucks|dollars)",
        Pattern.CASE_INSENSITIVE
    )
    
    // Backup regex for any decimal or integer amount preceded by keywords
    private val backupAmountRegex = Pattern.compile(
        "(?:spent|debited|credited|charged|amount of|paid|recvd|received|trf|transfer of)\\s*(?:Rs\\.?|INR|\\$|EUR)?\\s*([0-9,]+(?:\\.[0-9]{2})?)",
        Pattern.CASE_INSENSITIVE
    )

    fun parseMessage(sender: String, messageText: String): ParsedSmsTransaction? {
        val text = messageText.trim()
        if (text.isEmpty()) return null

        // 1. Extract Amount
        var amount = 0.0
        var matcher = amountRegex.matcher(text)
        if (matcher.find()) {
            val amountStr = matcher.group(1) ?: matcher.group(2)
            if (amountStr != null) {
                amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
            }
        }
        
        if (amount == 0.0) {
            matcher = backupAmountRegex.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                if (amountStr != null) {
                    amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                }
            }
        }

        // 2. Classify Transaction Type
        val type = determineType(text)

        // 3. Classify Category and tag based on local offline logic
        val (category, tag) = determineCategoryAndTag(text, type)

        // 4. Determine Account
        val account = when {
            text.contains("credit card", ignoreCase = true) || text.contains("cc", ignoreCase = true) -> "Credit Card"
            text.contains("debit card", ignoreCase = true) || text.contains("dc", ignoreCase = true) || text.contains("a/c", ignoreCase = true) -> "Bank"
            else -> "Cash"
        }

        // 5. Build Description
        val description = truncateDescription(text)

        return ParsedSmsTransaction(
            amount = amount,
            type = type,
            category = category,
            tag = tag,
            description = description,
            sender = sender,
            account = account
        )
    }

    private fun determineType(text: String): String {
        val lowercase = text.lowercase()
        return when {
            // Transfers
            lowercase.contains("transfer") || lowercase.contains("transferred") || 
            lowercase.contains("trf") || lowercase.contains("sent to a/c") ||
            lowercase.contains("sent to") || lowercase.contains("remit") || 
            lowercase.contains("remitted") -> "TRANSFER"

            // Credits
            lowercase.contains("credit") || lowercase.contains("credited") || 
            lowercase.contains("received") || lowercase.contains("recvd") || 
            lowercase.contains("deposited") || lowercase.contains("refund") || 
            lowercase.contains("refunded") || lowercase.contains("added") -> "CREDIT"

            // Debits (Default for typical transactions)
            lowercase.contains("debit") || lowercase.contains("debited") || 
            lowercase.contains("spent") || lowercase.contains("withdrawn") || 
            lowercase.contains("charged") || lowercase.contains("paid") || 
            lowercase.contains("payment") || lowercase.contains("purchase") -> "DEBIT"

            else -> "DEBIT" // Standard fallback
        }
    }

    private fun determineCategoryAndTag(text: String, type: String): Pair<String, String> {
        val lowercase = text.lowercase()
        
        if (type == "CREDIT") {
            return when {
                lowercase.contains("salary") || lowercase.contains("paycheck") || lowercase.contains("payroll") -> "Salary" to "salary"
                lowercase.contains("refund") || lowercase.contains("reimbursement") -> "Refunds" to "refund"
                lowercase.contains("interest") || lowercase.contains("dividend") -> "Investment" to "interest"
                else -> "Income" to "misc_credit"
            }
        }

        return when {
            lowercase.contains("starbucks") || lowercase.contains("coffee") || lowercase.contains("cafe") -> "Food" to "coffee"
            lowercase.contains("mcdonald") || lowercase.contains("burger") || lowercase.contains("pizza") || lowercase.contains("swiggy") || lowercase.contains("zomato") || lowercase.contains("dining") || lowercase.contains("restaurant") || lowercase.contains("food") -> "Food" to "dining"
            
            lowercase.contains("uber") || lowercase.contains("lyft") || lowercase.contains("ola") || lowercase.contains("taxi") || lowercase.contains("cab") -> "Transport" to "rideshare"
            lowercase.contains("fuel") || lowercase.contains("gas") || lowercase.contains("petrol") || lowercase.contains("diesel") -> "Transport" to "fuel"
            lowercase.contains("train") || lowercase.contains("metro") || lowercase.contains("subway") || lowercase.contains("flight") || lowercase.contains("airline") -> "Transport" to "travel"
            
            lowercase.contains("netflix") || lowercase.contains("spotify") || lowercase.contains("hulu") || lowercase.contains("youtube") || lowercase.contains("movie") || lowercase.contains("cinema") || lowercase.contains("ticket") -> "Entertainment" to "subscription"
            
            lowercase.contains("rent") -> "Rent/Bills" to "rent"
            lowercase.contains("electricity") || lowercase.contains("power") || lowercase.contains("water") || lowercase.contains("internet") || lowercase.contains("broadband") || lowercase.contains("mobile") || lowercase.contains("recharge") || lowercase.contains("bill") -> "Rent/Bills" to "utility"
            
            lowercase.contains("amazon") || lowercase.contains("walmart") || lowercase.contains("target") || lowercase.contains("ebay") || lowercase.contains("flipkart") || lowercase.contains("store") || lowercase.contains("grocery") || lowercase.contains("groceries") || lowercase.contains("supermarket") -> "Shopping" to "groceries"
            
            lowercase.contains("medical") || lowercase.contains("hospital") || lowercase.contains("pharmacy") || lowercase.contains("doctor") || lowercase.contains("health") || lowercase.contains("pill") -> "Health" to "medical"
            
            else -> "Other" to "uncategorized"
        }
    }

    private fun truncateDescription(text: String): String {
        // Just extract a nice clean part or return the whole text capped at 80 chars
        val clean = text.replace(Regex("\\s+"), " ")
        return if (clean.length > 80) {
            clean.take(77) + "..."
        } else {
            clean
        }
    }
}
