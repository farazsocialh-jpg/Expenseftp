package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettingEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionRepository
import com.example.util.CustomRule
import com.example.util.GeminiCategorizer
import com.example.util.GeminiSuggestion
import com.example.util.ParsedSmsTransaction
import com.example.util.SmsParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AiSuggestionState {
    object Idle : AiSuggestionState()
    object Loading : AiSuggestionState()
    data class Success(val suggestion: GeminiSuggestion) : AiSuggestionState()
    data class Error(val message: String) : AiSuggestionState()
}

// Full Budget Representation
data class FinancialBudget(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String, // "ALL" or specific e.g. "Food"
    val wallet: String = "ALL", // "ALL" or "Cash", "Bank", "Credit Card"
    val amount: Double,
    val period: String, // "Weekly", "Monthly", "Annual"
    val rollover: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class BudgetStatus(
    val budget: FinancialBudget,
    val limit: Double,
    val spent: Double,
    val remaining: Double,
    val overspent: Boolean,
    val percentage: Float, // 0.0f to 1.0f
    val daysRemaining: Int,
    val dailyBurnRate: Double,
    val forecastedExhaustionDays: Int? // Number of days until fully exhausted based on spend rate
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = TransactionRepository(
        db.transactionDao(),
        db.appSettingDao()
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Flow of all transactions from database
    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allItemsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Selected SMS Sender configuration flow
    val smsSenderSetting: StateFlow<String> = repository.getSettingFlow("selected_sms_sender", "Cb SMS")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Cb SMS"
        )

    // Preferred Currency flow, defaulting to QAR for Qatar
    val preferredCurrencySetting: StateFlow<String> = repository.getSettingFlow("preferred_currency", "QAR")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "QAR"
        )

    // Filter States for Transaction List
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("ALL")
    val selectedTypeFilter = _selectedTypeFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("ALL")
    val selectedCategoryFilter = _selectedCategoryFilter.asStateFlow()

    // Filtered transaction flow combining filters
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        _searchQuery,
        _selectedTypeFilter,
        _selectedCategoryFilter
    ) { txs, query, type, category ->
        txs.filter { tx ->
            val matchesQuery = tx.description.contains(query, ignoreCase = true) ||
                    tx.tag.contains(query, ignoreCase = true) ||
                    tx.category.contains(query, ignoreCase = true)
            
            val matchesType = type == "ALL" || tx.type.equals(type, ignoreCase = true)
            
            val matchesCategory = category == "ALL" || tx.category.equals(category, ignoreCase = true)

            matchesQuery && matchesType && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- PREMIUM EXTENSIONS ----------------
    
    // Custom Categories Flow
    private val defaultCategories = listOf("Food", "Salary", "Rent/Bills", "Shopping", "Transport", "Entertainment", "Health", "Investment", "Other")
    val customCategoriesSetting: StateFlow<List<String>> = repository.getSettingFlow("custom_categories", "")
        .map { serialized ->
            if (serialized.isEmpty()) {
                defaultCategories
            } else {
                try {
                    val type = Types.newParameterizedType(List::class.java, String::class.java)
                    val adapter = moshi.adapter<List<String>>(type)
                    adapter.fromJson(serialized) ?: defaultCategories
                } catch (e: Exception) {
                    defaultCategories
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultCategories)

    // Custom Automation Rules Flow
    val automationRulesSetting: StateFlow<List<CustomRule>> = repository.getSettingFlow("automation_rules", "")
        .map { serialized ->
            if (serialized.isEmpty()) {
                emptyList()
            } else {
                try {
                    val type = Types.newParameterizedType(List::class.java, CustomRule::class.java)
                    val adapter = moshi.adapter<List<CustomRule>>(type)
                    adapter.fromJson(serialized) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Budgets Flow (Monthly, Weekly, Category limits)
    val budgetsSetting: StateFlow<List<FinancialBudget>> = repository.getSettingFlow("financial_budgets", "")
        .map { serialized ->
            if (serialized.isEmpty()) {
                // Return default seed budgets on first launch
                listOf(
                    FinancialBudget(name = "Food Budget (Monthly)", category = "Food", amount = 1000.0, period = "Monthly"),
                    FinancialBudget(name = "Transport Limits (Weekly)", category = "Transport", amount = 150.0, period = "Weekly"),
                    FinancialBudget(name = "Shopping Cap (Monthly)", category = "Shopping", amount = 500.0, period = "Monthly"),
                    FinancialBudget(name = "Base Monthly Cap (All Categories)", category = "ALL", amount = 3000.0, period = "Monthly")
                )
            } else {
                try {
                    val type = Types.newParameterizedType(List::class.java, FinancialBudget::class.java)
                    val adapter = moshi.adapter<List<FinancialBudget>>(type)
                    adapter.fromJson(serialized) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Budget Status Calculations
    val budgetStatuses: StateFlow<List<BudgetStatus>> = combine(allTransactions, budgetsSetting) { txs, budgets ->
        val now = System.currentTimeMillis()
        budgets.map { b ->
            // Filter transactions of this budget's category and wallet inside period days
            val periodMs = when (b.period) {
                "Weekly" -> 7 * 24 * 60 * 60 * 1000L
                "Monthly" -> 30 * 24 * 60 * 60 * 1000L
                "Annual" -> 365 * 24 * 60 * 60 * 1000L
                else -> 30 * 24 * 60 * 60 * 1000L
            }
            val filterStart = now - periodMs
            val filteredTxs = txs.filter { tx ->
                tx.type == "DEBIT" &&
                tx.timestamp >= filterStart &&
                (b.category == "ALL" || tx.category.equals(b.category, ignoreCase = true)) &&
                (b.wallet == "ALL" || tx.account.equals(b.wallet, ignoreCase = true))
            }

            val spent = filteredTxs.sumOf { it.amount }
            val remaining = maxOf(0.0, b.amount - spent)
            val overspent = spent > b.amount
            val percentage = if (b.amount > 0) (spent / b.amount).toFloat().coerceIn(0.0f, 1.0f) else 1.0f
            
            // Days computation
            val days = (periodMs / (24 * 60 * 60 * 1000L)).toInt()
            val elapsedSecs = (now - filterStart).coerceAtLeast(1)
            val elapsedDays = elapsedSecs / (24.0 * 60 * 60 * 1000L)
            
            val burn = if (elapsedDays > 0.1) spent / elapsedDays else spent
            val exhaust = if (burn > 0) (remaining / burn).toInt() else null

            BudgetStatus(
                budget = b,
                limit = b.amount,
                spent = spent,
                remaining = remaining,
                overspent = overspent,
                percentage = percentage,
                daysRemaining = days,
                dailyBurnRate = burn,
                forecastedExhaustionDays = exhaust
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Health Index Score calculation
    val financialHealthScore: StateFlow<Int> = combine(allTransactions, budgetStatuses) { txs, statuses ->
        if (txs.isEmpty()) return@combine 70

        val now = System.currentTimeMillis()
        val oneMonthAgo = now - 30 * 24 * 60 * 60 * 1000L
        val monthTxs = txs.filter { it.timestamp >= oneMonthAgo }

        val debits = monthTxs.filter { it.type == "DEBIT" }.sumOf { it.amount }
        val credits = monthTxs.filter { it.type == "CREDIT" }.sumOf { it.amount }

        var score = 50

        // Ratio weight savings factor (Max 30 points)
        if (credits > 0) {
            val savingsRate = (credits - debits) / credits
            when {
                savingsRate >= 0.40 -> score += 30
                savingsRate >= 0.20 -> score += 20
                savingsRate >= 0.05 -> score += 10
                savingsRate < 0.0 -> score -= 15
            }
        } else if (debits > 0) {
            score -= 10
        }

        // Account risk factor: checks Credit Card usage over cash-bank ratios (Max 25 points)
        val ccDebits = monthTxs.filter { it.type == "DEBIT" && it.account == "Credit Card" }.sumOf { it.amount }
        if (debits > 0) {
            val ccRatio = ccDebits / debits
            if (ccRatio < 0.3) score += 25
            else if (ccRatio < 0.6) score += 15
            else if (ccRatio >= 0.9) score -= 10
        } else {
            score += 25
        }

        // Transaction count frequency index (Max 25 points)
        val count = monthTxs.size
        when {
            count >= 20 -> score += 25
            count >= 10 -> score += 15
            count >= 5 -> score += 10
        }

        // Overspending deduction
        val overspentBudgets = statuses.count { it.overspent }
        score -= (overspentBudgets * 8)

        score.coerceIn(0, 100)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 70)

    // AI Classification Suggestion State
    private val _aiSuggestionState = MutableStateFlow<AiSuggestionState>(AiSuggestionState.Idle)
    val aiSuggestionState = _aiSuggestionState.asStateFlow()

    // Database CRUD actions wrapped in ViewModel functions
    fun addTransaction(
        amount: Double,
        type: String,
        category: String,
        tag: String,
        description: String,
        account: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                TransactionEntity(
                    amount = amount,
                    type = type,
                    category = category,
                    tag = tag,
                    description = description,
                    account = account,
                    timestamp = timestamp
                )
            )
        }
    }

    fun updateTransaction(
        id: Int,
        amount: Double,
        type: String,
        category: String,
        tag: String,
        description: String,
        account: String,
        timestamp: Long
    ) {
        viewModelScope.launch {
            repository.updateTransaction(
                TransactionEntity(
                    id = id,
                    amount = amount,
                    type = type,
                    category = category,
                    tag = tag,
                    description = description,
                    account = account,
                    timestamp = timestamp
                )
            )
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            repository.clearAllTransactions()
        }
    }

    fun updateSmsSender(sender: String) {
        viewModelScope.launch {
            repository.saveSetting("selected_sms_sender", sender)
        }
    }

    fun updatePreferredCurrency(currencyCode: String) {
        viewModelScope.launch {
            repository.saveSetting("preferred_currency", currencyCode)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    // ---------------- MANAGE CUSTOM CATEGORIES ----------------
    fun addCategory(cat: String) {
        viewModelScope.launch {
            val current = customCategoriesSetting.value.toMutableList()
            if (!current.contains(cat) && cat.trim().isNotEmpty()) {
                current.add(cat)
                val type = Types.newParameterizedType(List::class.java, String::class.java)
                val adapter = moshi.adapter<List<String>>(type)
                val serialized = adapter.toJson(current)
                repository.saveSetting("custom_categories", serialized)
            }
        }
    }

    fun deleteCategory(cat: String) {
        viewModelScope.launch {
            val current = customCategoriesSetting.value.toMutableList()
            if (current.contains(cat)) {
                current.remove(cat)
                val type = Types.newParameterizedType(List::class.java, String::class.java)
                val adapter = moshi.adapter<List<String>>(type)
                val serialized = adapter.toJson(current)
                repository.saveSetting("custom_categories", serialized)
            }
        }
    }

    // ---------------- MANAGE FINANCIAL BUDGETS ----------------
    fun saveBudgetsList(list: List<FinancialBudget>) {
        viewModelScope.launch {
            val type = Types.newParameterizedType(List::class.java, FinancialBudget::class.java)
            val adapter = moshi.adapter<List<FinancialBudget>>(type)
            val serialized = adapter.toJson(list)
            repository.saveSetting("financial_budgets", serialized)
        }
    }

    fun addBudget(name: String, category: String, wallet: String, amount: Double, period: String, rollover: Boolean) {
        val newBudget = FinancialBudget(
            name = name.ifEmpty { "$category Limit" },
            category = category,
            wallet = wallet,
            amount = amount,
            period = period,
            rollover = rollover
        )
        val updated = budgetsSetting.value.toMutableList().apply { add(newBudget) }
        saveBudgetsList(updated)
    }

    fun deleteBudget(id: String) {
        val updated = budgetsSetting.value.filter { it.id != id }
        saveBudgetsList(updated)
    }

    // ---------------- MANAGE AUTOMATION RULES ----------------
    fun saveAutomationRulesList(list: List<CustomRule>) {
        viewModelScope.launch {
            val type = Types.newParameterizedType(List::class.java, CustomRule::class.java)
            val adapter = moshi.adapter<List<CustomRule>>(type)
            val serialized = adapter.toJson(list)
            repository.saveSetting("automation_rules", serialized)
        }
    }

    fun addAutomationRule(keyword: String, category: String, tag: String, cleanMerchant: String) {
        val newRule = CustomRule(
            keyword = keyword,
            category = category,
            tag = tag,
            cleanMerchant = cleanMerchant
        )
        val updated = automationRulesSetting.value.toMutableList().apply { add(newRule) }
        saveAutomationRulesList(updated)
    }

    fun deleteAutomationRule(keyword: String) {
        val updated = automationRulesSetting.value.filter { it.keyword != keyword }
        saveAutomationRulesList(updated)
    }


    // Call Gemini to suggest a category/tag for manual entry or simulated SMS
    fun suggestCategoryAndTagAI(description: String, amount: Double, type: String) {
        viewModelScope.launch {
            _aiSuggestionState.value = AiSuggestionState.Loading
            try {
                // Determine a quick local fallback first based on rules
                val dummyParsed = SmsParser.parseMessage("Manual", "$type of $amount for $description", automationRulesSetting.value)
                val fallbackCategory = dummyParsed?.category ?: "Other"
                val fallbackTag = dummyParsed?.tag ?: "uncategorized"

                val suggestion = GeminiCategorizer.suggestCategoryAndTag(
                    description = description,
                    amount = amount,
                    type = type,
                    fallbackCategory = fallbackCategory,
                    fallbackTag = fallbackTag
                )
                _aiSuggestionState.value = AiSuggestionState.Success(suggestion)
            } catch (e: Exception) {
                _aiSuggestionState.value = AiSuggestionState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun resetAiSuggestion() {
        _aiSuggestionState.value = AiSuggestionState.Idle
    }

    // Backup & Restore operations
    fun exportBackup(): String {
        return repository.exportBackupToJson(allTransactions.value)
    }

    suspend fun restoreBackup(jsonString: String): Boolean {
        return repository.importBackupFromJson(jsonString)
    }

    // Historical Device SMS Syncing with Automation rules injection
    fun syncHistoricalSms(sender: String, onComplete: (scanned: Int, imported: Int) -> Unit) {
        viewModelScope.launch {
            var scannedCount = 0
            var importedCount = 0
            try {
                val context = getApplication<Application>()
                
                // Double check runtime permission at service layer
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.READ_SMS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (!hasPermission) {
                    onComplete(0, 0)
                    return@launch
                }

                val uri = android.net.Uri.parse("content://sms/inbox")
                val projection = arrayOf("_id", "address", "body", "date")
                
                context.contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
                    val addressIdx = cursor.getColumnIndex("address")
                    val bodyIdx = cursor.getColumnIndex("body")
                    val dateIdx = cursor.getColumnIndex("date")
                    
                    // Fetch existing descriptions inside database to prevent duplicate inserts
                    val existingDescriptions = allTransactions.value.map { it.description }.toSet()
                    
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(addressIdx) ?: ""
                        val body = cursor.getString(bodyIdx) ?: ""
                        val date = cursor.getLong(dateIdx)
                        
                        scannedCount++
                        
                        // Check if the sender matches configured filter using robust helper
                        if (SmsParser.isSameSender(address, sender)) {
                            // Inject automation rules into the live parsing pipeline!
                            val parsed = SmsParser.parseMessage(address, body, automationRulesSetting.value)
                            if (parsed != null && parsed.amount > 0.0) {
                                val finalDesc = "SMS Alert: ${parsed.description}"
                                
                                // Prevent saving if duplicates exist
                                if (!existingDescriptions.contains(finalDesc)) {
                                    repository.insertTransaction(
                                        TransactionEntity(
                                            amount = parsed.amount,
                                            type = parsed.type,
                                            category = parsed.category,
                                            tag = parsed.tag,
                                            description = finalDesc,
                                            sender = address,
                                            account = parsed.account,
                                            timestamp = date
                                        )
                                    )
                                    importedCount++
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onComplete(scannedCount, importedCount)
        }
    }
}

// Extension to map repository to ViewModel safely
private fun TransactionRepository.allItemsFlow(): Flow<List<TransactionEntity>> = this.allTransactions
