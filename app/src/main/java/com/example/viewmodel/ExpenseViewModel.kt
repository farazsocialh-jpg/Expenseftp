package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TransactionEntity
import com.example.data.TransactionRepository
import com.example.util.GeminiCategorizer
import com.example.util.GeminiSuggestion
import com.example.util.ParsedSmsTransaction
import com.example.util.SmsParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AiSuggestionState {
    object Idle : AiSuggestionState()
    object Loading : AiSuggestionState()
    data class Success(val suggestion: GeminiSuggestion) : AiSuggestionState()
    data class Error(val message: String) : AiSuggestionState()
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = TransactionRepository(
        db.transactionDao(),
        db.appSettingDao()
    )

    // Flow of all transactions
    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allItemsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Selected SMS Sender configuration flow
    val smsSenderSetting: StateFlow<String> = repository.getSettingFlow("selected_sms_sender", "HDFCBank")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "HDFCBank"
        )

    // Filter States
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    // Call Gemini to suggest a category/tag for manual entry or simulated SMS
    fun suggestCategoryAndTagAI(description: String, amount: Double, type: String) {
        viewModelScope.launch {
            _aiSuggestionState.value = AiSuggestionState.Loading
            try {
                // Determine a quick local fallback first
                val dummyParsed = SmsParser.parseMessage("Manual", "$type of $amount for $description")
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
}

// Extension to map repository to ViewModel safely
private fun TransactionRepository.allItemsFlow(): Flow<List<TransactionEntity>> = this.allTransactions
