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
    val merchant: String = "Unknown",
    val confidence: Int = 50, // Confidence rating (out of 100)
    val matchedRule: String = "UncategorizedFallback",
    val timestamp: Long = System.currentTimeMillis()
)

data class CustomRule(
    val keyword: String,
    val category: String,
    val tag: String,
    val cleanMerchant: String
)

data class DictionaryMatchItem(
    val keyword: String,
    val category: String,
    val tag: String,
    val cleanMerchant: String
)

object SmsParser {
    // Advanced Regex for amounts supporting multi-currency symbols and comma layouts
    private val amountRegex = Pattern.compile(
        "(?:Rs\\.?|INR|USD|\\$|EUR|GBP|£|AED|SAR|QAR|OMR|BHD|KWD)\\s*([0-9,]+(?:\\.[0-9]{2})?)|([0-9,]+(?:\\.[0-9]{2})?)\\s*(?:USD|INR|EUR|GBP|Rs|bucks|dollars|QAR|AED|SAR|OMR|BHD|KWD)",
        Pattern.CASE_INSENSITIVE
    )
    
    private val backupAmountRegex = Pattern.compile(
        "(?:spent|debited|credited|charged|amount of|paid|recvd|received|trf|transfer of|used for)\\s*(?:Rs\\.?|INR|\\$|EUR|QAR|AED|SAR)?\\s*([0-9,]+(?:\\.[0-9]{2})?)",
        Pattern.CASE_INSENSITIVE
    )

    // Robust Merchant Clean Dictionary with associated Categories and tags
    // This allows conversion of dirty bank shortcode SMS indicators into beautiful clean brands
    private val merchantDictionary = listOf(
        // Ride Shares
        DictionaryMatchItem("uber", "Transport", "rideshare", "Uber"),
        DictionaryMatchItem("lyft", "Transport", "rideshare", "Lyft"),
        DictionaryMatchItem("ola ride", "Transport", "rideshare", "Ola"),
        DictionaryMatchItem("grab", "Transport", "rideshare", "Grab"),
        DictionaryMatchItem("didichuxing", "Transport", "rideshare", "Didi"),
        
        // Food, Cafes, and Deliveries
        DictionaryMatchItem("starbucks", "Food", "coffee", "Starbucks"),
        DictionaryMatchItem("mcdonald", "Food", "dining", "McDonald's"),
        DictionaryMatchItem("kfc", "Food", "dining", "KFC"),
        DictionaryMatchItem("burger king", "Food", "dining", "Burger King"),
        DictionaryMatchItem("pizza hut", "Food", "dining", "Pizza Hut"),
        DictionaryMatchItem("domino", "Food", "dining", "Domino's"),
        DictionaryMatchItem("swiggy", "Food", "dining", "Swiggy Delivery"),
        DictionaryMatchItem("zomato", "Food", "dining", "Zomato Delivery"),
        DictionaryMatchItem("talabat", "Food", "dining", "Talabat Delivery"),
        DictionaryMatchItem("ubereats", "Food", "dining", "UberEats"),
        DictionaryMatchItem("doordash", "Food", "dining", "DoorDash"),
        DictionaryMatchItem("subway", "Food", "dining", "Subway"),
        DictionaryMatchItem("dunkin", "Food", "coffee", "Dunkin' Donuts"),
        
        // Gas Stations & Fuel
        DictionaryMatchItem("shell", "Transport", "fuel", "Shell Fuel"),
        DictionaryMatchItem("exxon", "Transport", "fuel", "Exxon"),
        DictionaryMatchItem("chevron", "Transport", "fuel", "Chevron"),
        DictionaryMatchItem("mobil", "Transport", "fuel", "Mobil"),
        DictionaryMatchItem("gasoline", "Transport", "fuel", "Gas Station"),
        DictionaryMatchItem("petrol", "Transport", "fuel", "Petrol Pump"),
        DictionaryMatchItem("woqod", "Transport", "fuel", "Woqod Petrol Station"),
        
        // Entertainment & Streaming
        DictionaryMatchItem("netflix", "Entertainment", "subscription", "Netflix"),
        DictionaryMatchItem("spotify", "Entertainment", "subscription", "Spotify"),
        DictionaryMatchItem("hulu", "Entertainment", "subscription", "Hulu"),
        DictionaryMatchItem("disneyplus", "Entertainment", "subscription", "Disney+"),
        DictionaryMatchItem("youtube premium", "Entertainment", "subscription", "YouTube Premium"),
        DictionaryMatchItem("prime video", "Entertainment", "subscription", "Amazon Prime"),
        DictionaryMatchItem("hbo max", "Entertainment", "subscription", "HBO Max"),
        DictionaryMatchItem("cinema", "Entertainment", "entertainment", "Movie Theatre"),
        
        // E-Commerce, Retail & Grocery Supermarkets (including Middle Eastern retailers)
        DictionaryMatchItem("amazon", "Shopping", "shopping", "Amazon Store"),
        DictionaryMatchItem("walmart", "Shopping", "groceries", "Walmart"),
        DictionaryMatchItem("target", "Shopping", "shopping", "Target"),
        DictionaryMatchItem("ebay", "Shopping", "shopping", "eBay"),
        DictionaryMatchItem("bestbuy", "Shopping", "electronics", "Best Buy"),
        DictionaryMatchItem("costco", "Shopping", "groceries", "Costco Warehouse"),
        DictionaryMatchItem("kroger", "Shopping", "groceries", "Kroger"),
        DictionaryMatchItem("wholefoods", "Shopping", "groceries", "Whole Foods Market"),
        DictionaryMatchItem("tesco", "Shopping", "groceries", "Tesco"),
        DictionaryMatchItem("lulu", "Shopping", "groceries", "LuLu Hypermarket"),
        DictionaryMatchItem("carrefour", "Shopping", "groceries", "Carrefour"),
        DictionaryMatchItem("al meera", "Shopping", "groceries", "Al Meera"),
        DictionaryMatchItem("taif", "Shopping", "groceries", "Taif Hypermarket"),
        DictionaryMatchItem("safari", "Shopping", "groceries", "Safari Mall"),
        DictionaryMatchItem("monoprix", "Shopping", "groceries", "Monoprix"),
        DictionaryMatchItem("spar", "Shopping", "groceries", "SPAR"),
        DictionaryMatchItem("sidra", "Shopping", "groceries", "Sidra Convenience"),
        
        // Professional Income Sources
        DictionaryMatchItem("salary", "Salary", "salary", "Employer Direct Account"),
        DictionaryMatchItem("payroll", "Salary", "salary", "Employer Payroll"),
        DictionaryMatchItem("dividend", "Investment", "dividend", "Investment Dividend"),
        DictionaryMatchItem("interest payment", "Investment", "interest", "Savings Ledger Interest"),
        
        // Health and Medical Services
        DictionaryMatchItem("cvs", "Health", "medical", "CVS Pharmacy"),
        DictionaryMatchItem("walgreens", "Health", "medical", "Walgreens Pharmacy"),
        DictionaryMatchItem("hospital", "Health", "medical", "Healthcare Center"),
        DictionaryMatchItem("clinic", "Health", "medical", "Doctor Clinic"),
        DictionaryMatchItem("dental", "Health", "medical", "Dental Practice")
    )

    /**
     * Parses the SMS message with smart merchant, type classification, custom routing rules, 
     * and confidence indices.
     */
    fun parseMessage(
        sender: String, 
        messageText: String, 
        userRules: List<CustomRule> = emptyList()
    ): ParsedSmsTransaction? {
        val text = messageText.replace(Regex("\\s+"), " ").trim()
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

        // 3. Smart Categorization Pipeline
        var category = "Other"
        var tag = "uncategorized"
        var merchant = "Unknown Merchant"
        var confidence = 50
        var ruleName = "FallbackDefault"

        val lowercase = text.lowercase()

        // Phase A: Check User Custom Defined Rules (Highest Priority 95% Confidence)
        val matchedUserRule = userRules.firstOrNull { rule ->
            lowercase.contains(rule.keyword.lowercase())
        }

        if (matchedUserRule != null) {
            category = matchedUserRule.category
            tag = matchedUserRule.tag
            merchant = matchedUserRule.cleanMerchant
            confidence = 95
            ruleName = "UserDefinedAutomation"
        } else {
            // Phase B: Check Primary Dictionary Match (90% Confidence)
            val dictionaryMatch = merchantDictionary.firstOrNull { item ->
                lowercase.contains(item.keyword.lowercase())
            }

            if (dictionaryMatch != null) {
                category = dictionaryMatch.category
                tag = dictionaryMatch.tag
                merchant = dictionaryMatch.cleanMerchant
                confidence = 90
                ruleName = "SystemMerchantRecognition"
            } else {
                // Phase C: Fallback Keyword Checks (70% Confidence)
                val typeResolvedCatTag = determineCategoryFallback(lowercase, type)
                category = typeResolvedCatTag.first
                tag = typeResolvedCatTag.second
                merchant = extractPotentialMerchant(text)
                confidence = 65
                ruleName = "SemanticKeywordHeuristic"
            }
        }

        // 4. Determine Account
        val account = when {
            lowercase.contains("credit card") || lowercase.contains("cc") || lowercase.contains("creditcard") -> "Credit Card"
            lowercase.contains("debit card") || lowercase.contains("dc") || lowercase.contains("a/c") || lowercase.contains("bank") -> "Bank"
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
            account = account,
            merchant = merchant,
            confidence = confidence,
            matchedRule = ruleName
        )
    }

    private fun determineType(text: String): String {
        val lowercase = text.lowercase()
        return when {
            lowercase.contains("transfer") || lowercase.contains("transferred") || 
            lowercase.contains("trf") || lowercase.contains("sent to a/c") ||
            lowercase.contains("sent to") || lowercase.contains("remit") || 
            lowercase.contains("remitted") -> "TRANSFER"

            lowercase.contains("credit") || lowercase.contains("credited") || 
            lowercase.contains("received") || lowercase.contains("recvd") || 
            lowercase.contains("deposited") || lowercase.contains("refund") || 
            lowercase.contains("refunded") || lowercase.contains("added") -> "CREDIT"

            lowercase.contains("debit") || lowercase.contains("debited") || 
            lowercase.contains("spent") || lowercase.contains("withdrawn") || 
            lowercase.contains("charged") || lowercase.contains("paid") || 
            lowercase.contains("payment") || lowercase.contains("purchase") ||
            lowercase.contains("used for") || lowercase.contains("used") -> "DEBIT"

            else -> "DEBIT" // Standard default is debit
        }
    }

    private fun determineCategoryFallback(lowercase: String, type: String): Pair<String, String> {
        if (type == "CREDIT") {
            return when {
                lowercase.contains("salary") || lowercase.contains("paycheck") || lowercase.contains("payroll") -> "Salary" to "salary"
                lowercase.contains("refund") || lowercase.contains("reimbursement") -> "Refunds" to "refund"
                lowercase.contains("interest") || lowercase.contains("dividend") || lowercase.contains("yield") -> "Investment" to "interest"
                else -> "Income" to "misc_income"
            }
        }

        return when {
            lowercase.contains("rent") || lowercase.contains("mortgage") || lowercase.contains("lease") -> "Rent/Bills" to "rent"
            lowercase.contains("utility") || lowercase.contains("electricity") || lowercase.contains("power") || lowercase.contains("water") || lowercase.contains("internet") || lowercase.contains("broadband") || lowercase.contains("mobile") || lowercase.contains("recharge") || lowercase.contains("bill") -> "Rent/Bills" to "utility"
            
            lowercase.contains("cafe") || lowercase.contains("coffee") || lowercase.contains("food") || lowercase.contains("restaurant") || lowercase.contains("dinner") || lowercase.contains("breakfast") || lowercase.contains("brunch") || lowercase.contains("lunch") || lowercase.contains("eat") -> "Food" to "dining"
            lowercase.contains("uber") || lowercase.contains("cab") || lowercase.contains("taxi") || lowercase.contains("metro") || lowercase.contains("subway") || lowercase.contains("bus") || lowercase.contains("train") || lowercase.contains("rideshare") -> "Transport" to "commute"
            lowercase.contains("fuel") || lowercase.contains("petrol") || lowercase.contains("gas") || lowercase.contains("diesel") -> "Transport" to "fuel"
            
            lowercase.contains("netflix") || lowercase.contains("spotify") || lowercase.contains("movie") || lowercase.contains("ticket") || lowercase.contains("show") || lowercase.contains("play") -> "Entertainment" to "entertainment"
            
            lowercase.contains("shopping") || lowercase.contains("store") || lowercase.contains("buy") || lowercase.contains("electronics") || lowercase.contains("apparel") || lowercase.contains("clothes") -> "Shopping" to "retail"
            lowercase.contains("grocery") || lowercase.contains("groceries") || lowercase.contains("mart") || lowercase.contains("market") -> "Shopping" to "groceries"
            
            lowercase.contains("medical") || lowercase.contains("hospital") || lowercase.contains("clinic") || lowercase.contains("pill") || lowercase.contains("pharmacy") || lowercase.contains("doctor") || lowercase.contains("health") -> "Health" to "medical"
            
            else -> "Other" to "uncategorized"
        }
    }

    private fun extractPotentialMerchant(text: String): String {
        val keywords = listOf("at", "to", "info", "for", "paid")
        val words = text.split(" ")
        for (i in 0 until words.size - 1) {
            if (keywords.contains(words[i].lowercase()) && words[i + 1].isNotEmpty()) {
                val merchantWords = mutableListOf<String>()
                var j = i + 1
                while (j < words.size && merchantWords.size < 3) {
                    val word = words[j].trim().replace(Regex("[^a-zA-Z0-9]"), "")
                    if (word.isEmpty()) break
                    if (word.all { it.isLowerCase() } && merchantWords.isNotEmpty()) break
                    merchantWords.add(word.replaceFirstChar { it.uppercase() })
                    j++
                }
                if (merchantWords.isNotEmpty()) {
                    return merchantWords.joinToString(" ")
                }
            }
        }
        return "Local Merchant"
    }

    /**
     * Checks if the telecom SMS sender matches the user-configured sender,
     * stripping special characters for maximum compatibility with carriers.
     */
    fun isSameSender(smsSender: String, configSender: String): Boolean {
        val cleanSms = smsSender.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        val cleanConfig = configSender.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return cleanSms.contains(cleanConfig) || cleanConfig.contains(cleanSms)
    }

    private fun truncateDescription(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ")
        return if (clean.length > 80) {
            clean.take(77) + "..."
        } else {
            clean
        }
    }
}
