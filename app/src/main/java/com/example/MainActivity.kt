package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TransactionEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ParsedSmsTransaction
import com.example.util.SmsParser
import com.example.viewmodel.AiSuggestionState
import com.example.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(0) } // 0: Ledger, 1: Parser SIM, 2: Analytics, 3: Settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val allTransactionsRaw by viewModel.allTransactions.collectAsStateWithLifecycle()
    val selectedSmsSender by viewModel.smsSenderSetting.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editTransactionTarget by remember { mutableStateOf<TransactionEntity?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // "Sleek Interface" Top App Header
            AppHeader(
                appName = "Expenses",
                selectedSender = selectedSmsSender,
                onUpdateSender = { viewModel.updateSmsSender(it) }
            )

            // Content body switching based on Selected Bottom Navigation Tab
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    0 -> LedgerTab(
                        transactions = transactions,
                        onAddClick = { showAddDialog = true },
                        onEditClick = { editTransactionTarget = it },
                        onDeleteClick = { viewModel.deleteTransaction(it) },
                        onTrySimClick = { currentTab = 1 },
                        viewModel = viewModel
                    )
                    1 -> SmsSimulatorTab(
                        selectedSender = selectedSmsSender,
                        onSaveTransaction = { amount, type, cat, tag, desc, acc ->
                            viewModel.addTransaction(amount, type, cat, tag, desc, acc)
                        },
                        viewModel = viewModel
                    )
                    2 -> AnalyticsTab(transactions = allTransactionsRaw)
                    3 -> BackupSettingsTab(
                        viewModel = viewModel,
                        onClearAll = { viewModel.clearAllTransactions() },
                        allTransactions = allTransactionsRaw
                    )
                }
            }

            // Custom styled Sleek M3 Navigation Bar at the Bottom
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Ledger") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_ledger_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Sms, contentDescription = "SMS Parser") },
                    label = { Text("SMS Sim", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_sms_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.PieChart, contentDescription = "Analytics") },
                    label = { Text("Insights", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_analytics_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Backup") },
                    label = { Text("Banks & Config", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_backup_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }

    // Modal Dialog to Add manually
    if (showAddDialog) {
        TransactionFormDialog(
            title = "Add Transaction",
            onDismiss = { showAddDialog = false },
            onSave = { amount, type, category, tag, description, account, timestamp ->
                viewModel.addTransaction(amount, type, category, tag, description, account, timestamp)
                showAddDialog = false
            },
            viewModel = viewModel
        )
    }

    // Modal Dialog to Edit
    if (editTransactionTarget != null) {
        TransactionFormDialog(
            title = "Edit Transaction",
            transaction = editTransactionTarget,
            onDismiss = { editTransactionTarget = null },
            onSave = { amount, type, category, tag, description, account, timestamp ->
                viewModel.updateTransaction(
                    editTransactionTarget!!.id,
                    amount, type, category, tag, description, account, timestamp
                )
                editTransactionTarget = null
            },
            viewModel = viewModel
        )
    }
}

// ---------------- APP HEADER ----------------

@Composable
fun AppHeader(
    appName: String,
    selectedSender: String,
    onUpdateSender: (String) -> Unit
) {
    var showSenderDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elegant circular budget logo mimicking the HTML mockup design
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF2ECC71), CircleShape)
                    )
                    Text(
                        text = "Local DB • Synced",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Action controls (SMS active shortcode filter)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showSenderDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Sender Filter",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "SMS: $selectedSender",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = { /* Visual sync trigger */ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = "Cloud Synced",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showSenderDialog) {
        var tempSender by remember { mutableStateOf(selectedSender) }

        AlertDialog(
            onDismissRequest = { showSenderDialog = false },
            title = { Text("Selected SMS Sender") },
            text = {
                Column {
                    Text(
                        text = "The application will parse incoming simulated transaction alerts that match this sender code.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempSender,
                        onValueChange = { tempSender = it },
                        label = { Text("Sender ID / Shortcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sms_sender_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSender(tempSender)
                        showSenderDialog = false
                    },
                    modifier = Modifier.testTag("save_sender_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSenderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------- TAB 1: LEDGER ----------------

@Composable
fun LedgerTab(
    transactions: List<TransactionEntity>,
    onAddClick: () -> Unit,
    onEditClick: (TransactionEntity) -> Unit,
    onDeleteClick: (TransactionEntity) -> Unit,
    onTrySimClick: () -> Unit,
    viewModel: ExpenseViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    val totalIncome = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpense

    val selectedSmsSender by viewModel.smsSenderSetting.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant primary calculation board
            SummaryCard(netBalance, totalIncome, totalExpense)

            // SMS Parser Active Alert Banner based on mock theme
            SmartSmsAlert(selectedSmsSender, onTrySimClick)

            // Beautifully integrated minimal search text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                placeholder = { Text("Search description, category, tags...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("transaction_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                )
            )

            // Dynamic filter options row spacing
            FilterChipsRow(
                selectedType = selectedType,
                onTypeSelect = { viewModel.setTypeFilter(it) },
                selectedCategory = selectedCategory,
                onCategorySelect = { viewModel.setCategoryFilter(it) }
            )

            // List or empty state
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = "No transactions",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Transactions Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add a manual entry or paste a mock transaction in the SMS Parser tab to populate your dashboard.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        TransactionRowItem(
                            transaction = tx,
                            onClick = { onEditClick(tx) },
                            onDelete = { onDeleteClick(tx) }
                        )
                    }
                }
            }
        }

        // Premium design compliant floating action button
        LargeFloatingActionButton(
            onClick = { onAddClick() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_transaction_fab"),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Receipt", modifier = Modifier.size(28.dp))
        }
    }
}

// ---------------- SMART SMS ALERT BANNER ----------------

@Composable
fun SmartSmsAlert(selectedSender: String, onTrySimClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Sms,
                    contentDescription = "SMS Alert icon",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New SMS from BANK-$selectedSender",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Debited: $45.00 @ Starbucks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
            Button(
                onClick = onTrySimClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.secondary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Review", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------- BALANCE GRAPH CARD ----------------

@Composable
fun SummaryCard(
    balance: Double,
    income: Double,
    expenses: Double
) {
    val isDark = isSystemInDarkTheme()
    // Solid navy backgrounds for the primary board block matching "Sleek Interface" requirements perfectly
    val containerColor = if (!isDark) Color(0xFF001D35) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (!isDark) Color.White else MaterialTheme.colorScheme.onSurface

    val currentMonthYear = remember {
        val sdf = SimpleDateFormat("MMM yyyy", Locale.US)
        sdf.format(Date()).uppercase()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL BALANCE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .background(textColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentMonthYear,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatCurrency(balance),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Light,
                color = textColor,
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Inflow panel matching mockup
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(textColor.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "INFLOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+" + formatCurrency(income),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4ADE80)
                    )
                }

                // Outflow panel matching mockup
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(textColor.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "OUTFLOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "-" + formatCurrency(expenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }
        }
    }
}

// ---------------- CHIP FILTERS ROW ----------------

@Composable
fun FilterChipsRow(
    selectedType: String,
    onTypeSelect: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    val types = listOf("ALL", "DEBIT", "CREDIT", "TRANSFER")
    val categories = listOf("ALL", "Food", "Salary", "Rent/Bills", "Shopping", "Transport", "Entertainment", "Health", "Investment", "Other")

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        // Types Selector
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(types) { t ->
                val isSelected = selectedType == t
                FilterChip(
                    selected = isSelected,
                    onClick = { onTypeSelect(t) },
                    label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        selectedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("filter_type_$t")
                )
            }
        }

        // Categories Selector
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(categories) { c ->
                val isSelected = selectedCategory == c
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(c) },
                    label = { Text(c, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        selectedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("filter_category_$c")
                )
            }
        }
    }
}

// ---------------- TRANSACTION ITEM CARD ----------------

@Composable
fun TransactionRowItem(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = sdf.format(Date(transaction.timestamp))

    val isDark = isSystemInDarkTheme()

    // Setup beautiful high contrast style colors based on type
    val typeColor: Color
    val prefix: String
    val iconBg: Color
    val iconColor: Color

    when (transaction.type) {
        "CREDIT" -> {
            typeColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
            prefix = "+"
            iconBg = if (isDark) Color(0xFF132F1A) else Color(0xFFE8F5E9)
            iconColor = typeColor
        }
        "DEBIT" -> {
            typeColor = if (isDark) Color(0xFFF87171) else Color(0xFFC62828)
            prefix = "-"
            iconBg = if (isDark) Color(0xFF3B1E1E) else Color(0xFFFFEBEE)
            iconColor = typeColor
        }
        else -> {
            typeColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1565C0)
            prefix = ""
            iconBg = if (isDark) Color(0xFF1E2E3D) else Color(0xFFE3F2FD)
            iconColor = typeColor
        }
    }

    val icon = when (transaction.category) {
        "Food" -> Icons.Default.Fastfood
        "Salary" -> Icons.Default.Work
        "Rent/Bills" -> Icons.Default.Receipt
        "Shopping" -> Icons.Default.ShoppingCart
        "Transport" -> Icons.Default.DirectionsCar
        "Entertainment" -> Icons.Default.Movie
        "Health" -> Icons.Default.Healing
        "Investment" -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Default.Category
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
            .testTag("transaction_item_${transaction.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded Icon Block matching mockup
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = transaction.category,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.wrapContentSize()
                ) {
                    // Type Badge: CREDIT/DEBIT/TRANSFER
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = transaction.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }

                    // Highlight tag pill matching: bg-[#EADDFF] text-[#21005D]/SecondaryContainer style
                    if (transaction.tag.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✨ #${transaction.tag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            )
                        }
                    }

                    Text(
                        text = transaction.account,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            }

            // Amount / action column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$prefix" + formatCurrency(transaction.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    color = typeColor
                )
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .testTag("delete_tx_btn_${transaction.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ---------------- TAB 2: SMS SIMULATOR ----------------

@Composable
fun SmsSimulatorTab(
    selectedSender: String,
    onSaveTransaction: (Double, String, String, String, String, String) -> Unit,
    viewModel: ExpenseViewModel
) {
    val context = LocalContext.current
    var customSmsBody by remember { mutableStateOf("") }
    var detectedSender by remember { mutableStateOf(selectedSender) }
    var parsedTransaction by remember { mutableStateOf<ParsedSmsTransaction?>(null) }
    
    val aiState by viewModel.aiSuggestionState.collectAsStateWithLifecycle()

    val simulatedTemplates = listOf(
        "Bank" to "ALERT: Dear Customer, your Debit Card has been spent with USD 45.80 on 29-May-2026 for dining at STARBUCKS. Ref: 2091.",
        "PayPal" to "You have received USD 180.00 from Upwork Inc directly into your account balance. Confirmation id #92190.",
        "HDFCBank" to "Your account xx1029 has been debited with INR 500.00 at UBER INDIA. Current balance: INR 12,410.",
        "Chase" to "Chase alert: Your Credit Card ending 9823 was charged USD 120.00 at TARGET SUPERMARKET.",
        "Personal" to "Money transferred. Sent USD 350.00 to Savings Account via instant transfer Ref #TXF90218."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SMS SIMULATOR / TESTING SANDBOX",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No real phone or SIM is required! Select a bank SMS template below, or write custom bank text to view how the rules automatically parse transactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick Selector Templates
        Text("Try templates:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(simulatedTemplates) { (sender, templateText) ->
                SuggestionChip(
                    onClick = {
                        detectedSender = sender
                        customSmsBody = templateText
                        parsedTransaction = null
                        viewModel.resetAiSuggestion()
                    },
                    label = { Text("$sender Alert", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("template_$sender")
                )
            }
        }

        // Text area
        OutlinedTextField(
            value = customSmsBody,
            onValueChange = { 
                customSmsBody = it
                parsedTransaction = null
                viewModel.resetAiSuggestion()
            },
            label = { Text("Simulate Incoming SMS Body") },
            placeholder = { Text("Paste any typical transaction alert SMS here...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .testTag("sms_body_textarea"),
            shape = RoundedCornerShape(12.dp),
            maxLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = detectedSender,
                onValueChange = { detectedSender = it },
                label = { Text("Sender Name") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulated_sender_input"),
                singleLine = true
            )

            Button(
                onClick = {
                    if (customSmsBody.trim().isEmpty()) {
                        Toast.makeText(context, "SMS text is empty", Toast.LENGTH_SHORT).show()
                    } else {
                        val parsed = SmsParser.parseMessage(detectedSender, customSmsBody)
                        if (parsed != null && parsed.amount > 0.0) {
                            parsedTransaction = parsed
                            Toast.makeText(context, "Parsed offline: ${parsed.type}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to parse amount from SMS", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .height(56.dp)
                    .testTag("local_parse_btn")
            ) {
                Icon(Icons.Default.OfflineBolt, contentDescription = "Parse")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Parse", fontWeight = FontWeight.Bold)
            }
        }

        // Output Preview Card
        parsedTransaction?.let { tx ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "PARSED RESULT PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Amount extracted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Text(formatCurrency(tx.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Type detected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (tx.type) {
                                            "CREDIT" -> Color(0xFF2E7D32)
                                            "DEBIT" -> Color(0xFFC62828)
                                            else -> Color(0xFF1565C0)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(tx.type, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Machine Learning / Gemini section
                    Text(
                        text = "AI CLASSIFICATION & TAGS (GEMINI ML)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (aiState) {
                        is AiSuggestionState.Idle -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Category: ${tx.category}\nTag: #${tx.tag}\nMerchant: ${tx.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.suggestCategoryAndTagAI(customSmsBody, tx.amount, tx.type)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("ai_suggest_btn")
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = "AI Recommend")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AI Enhance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is AiSuggestionState.Loading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Analyzing semantics with Gemini...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is AiSuggestionState.Success -> {
                            val sug = (aiState as AiSuggestionState.Success).suggestion
                            
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text("AI Recommended Mapping:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("🎯 Category: ${sug.category}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("🏷️ Predicted Tag: #${sug.tag.lowercase()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("🏪 Extracted Merchant: ${sug.merchant}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { viewModel.resetAiSuggestion() }) {
                                        Text("Reset AI Advice")
                                    }
                                }
                            }
                        }
                        is AiSuggestionState.Error -> {
                            Column {
                                Text(
                                    text = "Gemini Call failed: ${(aiState as AiSuggestionState.Error).message}. Using smart offline defaults instead.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { viewModel.resetAiSuggestion() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val activeState = aiState
                            val finalCat = if (activeState is AiSuggestionState.Success) activeState.suggestion.category else tx.category
                            val finalTag = if (activeState is AiSuggestionState.Success) activeState.suggestion.tag else tx.tag
                            val finalDesc = if (activeState is AiSuggestionState.Success) "SMS: " + activeState.suggestion.merchant else "SMS: " + tx.description
 
                            onSaveTransaction(tx.amount, tx.type, finalCat, finalTag, finalDesc, tx.account)
                            parsedTransaction = null
                            customSmsBody = ""
                            viewModel.resetAiSuggestion()
                            Toast.makeText(context, "Saved successfully to DB!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_parsed_tx_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Accept")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Approve & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- TAB 3: ANALYTICS ----------------

@Composable
fun AnalyticsTab(transactions: List<TransactionEntity>) {
    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.BarChart, contentDescription = "Empty", tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Analytics Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Add transactions to view interactive Canvas distributions & ratio matrices.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
        return
    }

    val debits = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val credits = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val transfers = transactions.filter { it.type == "TRANSFER" }.sumOf { it.amount }

    // Grouping spending by Category
    val spendingByCategory = transactions.filter { it.type == "DEBIT" }
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }

    val totalSpending = spendingByCategory.values.sum()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("BUSINESS ANALYTICS OVERVIEW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        // 1. Double Bar graph: Credit vs Debit ratio
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Cash flow distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        // Draw Grid lines
                        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height * 0.25f), Offset(width, height * 0.25f), strokeWidth = 2f)
                        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height * 0.5f), Offset(width, height * 0.5f), strokeWidth = 2f)
                        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height * 0.75f), Offset(width, height * 0.75f), strokeWidth = 2f)

                        // Relative Sizing for bars
                        val maxVal = maxOf(credits, debits, transfers, 1.0)
                        val credHeight = (credits / maxVal).toFloat() * (height * 0.8f)
                        val debHeight = (debits / maxVal).toFloat() * (height * 0.8f)
                        val trfHeight = (transfers / maxVal).toFloat() * (height * 0.8f)

                        val barWidth = width * 0.15f
                        val gap = width * 0.12f

                        // Draw Credits Bar (Green)
                        val xCred = gap
                        drawRect(
                            color = Color(0xFF2E7D32),
                            topLeft = Offset(xCred, height - credHeight),
                            size = Size(barWidth, credHeight)
                        )

                        // Draw Debits Bar (Red)
                        val xDeb = xCred + barWidth + gap
                        drawRect(
                            color = Color(0xFFC62828),
                            topLeft = Offset(xDeb, height - debHeight),
                            size = Size(barWidth, debHeight)
                        )

                        // Draw Transfers Bar (Blue)
                        val xTrf = xDeb + barWidth + gap
                        drawRect(
                            color = Color(0xFF1565C0),
                            topLeft = Offset(xTrf, height - trfHeight),
                            size = Size(barWidth, trfHeight)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF2E7D32), RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Credits", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(formatCurrency(credits), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFC62828), RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Debits", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(formatCurrency(debits), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF1565C0), RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Transfers", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(formatCurrency(transfers), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Spending Pie Chart distribution
        if (totalSpending > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Expenditure by Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Pie Canvas
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                var startAngle = 0f
                                val colorsList = listOf(
                                    Color(0xFFE74C3C), Color(0xFF3498DB), Color(0xFFF1C40F),
                                    Color(0xFF9B59B6), Color(0xFF1ABC9C), Color(0xFFE67E22), Color(0xFF95A5A6), Color(0xFF2ECC71)
                                )

                                var index = 0
                                for ((_, amt) in spendingByCategory) {
                                    val sweep = (amt / totalSpending * 360f).toFloat()
                                    drawArc(
                                        color = colorsList[index % colorsList.size],
                                        startAngle = startAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 30f)
                                    )
                                    startAngle += sweep
                                    index++
                                }
                            }
                        }

                        // Category Labels list
                        Column(
                            modifier = Modifier.weight(1.2f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val colorsList = listOf(
                                Color(0xFFE74C3C), Color(0xFF3498DB), Color(0xFFF1C40F),
                                Color(0xFF9B59B6), Color(0xFF1ABC9C), Color(0xFFE67E22), Color(0xFF95A5A6), Color(0xFF2ECC71)
                            )
                            var idx = 0
                            for ((category, amount) in spendingByCategory.entries.take(5)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(colorsList[idx % colorsList.size], CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$category (${(amount / totalSpending * 100).toInt()}%s)".format("%"),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                idx++
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB 4: SETTINGS & BACKUP ----------------

@Composable
fun BackupSettingsTab(
    viewModel: ExpenseViewModel,
    onClearAll: () -> Unit,
    allTransactions: List<TransactionEntity>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupJsonString by remember { mutableStateOf("") }
    var restoreJsonString by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "BACKUP & RESTORE UTILITIES",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Import or export complete JSON ledger archives locally, offline. No complicated remote logins or servers required to keep your data secure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 1: Export Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "1. EXPORT TRANSACTION LEDGER",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Button(
                    onClick = {
                        backupJsonString = viewModel.exportBackup()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_backup_button")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Export")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate JSON Backup", fontWeight = FontWeight.Bold)
                }

                if (backupJsonString.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = backupJsonString,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ledger_backup", backupJsonString)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied backup JSON to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("copy_backup_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy to Clipboard", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2: Import Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "2. RESTORE / IMPORT LEDGER",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = restoreJsonString,
                    onValueChange = { restoreJsonString = it },
                    label = { Text("Paste JSON Backup String Here") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("import_backup_textarea"),
                    maxLines = 4
                )

                Button(
                    onClick = {
                        if (restoreJsonString.trim().isEmpty()) {
                            Toast.makeText(context, "Please paste valid JSON archive", Toast.LENGTH_SHORT).show()
                        } else {
                            scope.launch {
                                val success = viewModel.restoreBackup(restoreJsonString)
                                if (success) {
                                    restoreJsonString = ""
                                    showSuccessDialog = true
                                } else {
                                    Toast.makeText(context, "Invalid JSON formatting or schema mismatch", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_backup_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Import")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Overwrite and Import Database", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Safe Section 3: Force Clear
        Button(
            onClick = {
                onClearAll()
                Toast.makeText(context, "All records wiped cleanly.", Toast.LENGTH_SHORT).show()
                viewModel.resetAiSuggestion()
                backupJsonString = ""
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("clear_db_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = "Clear database")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Clear Raw Local Database", fontWeight = FontWeight.Bold)
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Imports Complete") },
            text = { Text("Database restored successfully from local JSON archive. Your ledger totals and transaction lists are recalculated.") },
            confirmButton = {
                Button(onClick = { showSuccessDialog = false }, shape = RoundedCornerShape(10.dp)) {
                    Text("Ok")
                }
            }
        )
    }
}

// ---------------- DIALOGS ----------------

@Composable
fun TransactionFormDialog(
    title: String,
    transaction: TransactionEntity? = null,
    onDismiss: () -> Unit,
    onSave: (Double, String, String, String, String, String, Long) -> Unit,
    viewModel: ExpenseViewModel
) {
    var amountStr by remember { mutableStateOf(transaction?.amount?.toString() ?: "") }
    var type by remember { mutableStateOf(transaction?.type ?: "DEBIT") }
    var category by remember { mutableStateOf(transaction?.category ?: "Food") }
    var tag by remember { mutableStateOf(transaction?.tag ?: "") }
    var description by remember { mutableStateOf(transaction?.description ?: "") }
    var account by remember { mutableStateOf(transaction?.account ?: "Cash") }
    val timestamp = transaction?.timestamp ?: System.currentTimeMillis()

    val aiState by viewModel.aiSuggestionState.collectAsStateWithLifecycle()

    val categories = listOf("Food", "Salary", "Rent/Bills", "Shopping", "Transport", "Entertainment", "Health", "Investment", "Other")

    Dialog(onDismissRequest = {
        viewModel.resetAiSuggestion()
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("transaction_dialog_surface"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Merchant / Description") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_desc_input")
                )

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_amount_input"),
                    leadingIcon = { Text("$", fontWeight = FontWeight.Bold) }
                )

                // Type selector row
                Text("Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("DEBIT", "CREDIT", "TRANSFER").forEach { t ->
                        val isSelected = type == t
                        Button(
                            onClick = { type = t },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_type_btn_$t")
                        ) {
                            Text(t, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // AI Categorizer Button with soft purple colors mimicking Sleek interface smart alerts
                Button(
                    onClick = {
                        val amtNum = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.suggestCategoryAndTagAI(description, amtNum, type)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("dialog_ai_assist_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = "AI")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Categorize with Gemini", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // AI Response Status Block inside dialog Form
                AnimatedVisibility(visible = aiState !is AiSuggestionState.Idle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        when (aiState) {
                            is AiSuggestionState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing description with LLM...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is AiSuggestionState.Success -> {
                                val sug = (aiState as AiSuggestionState.Success).suggestion
                                Column {
                                    Text("Gemini predicted mapping:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Category -> ${sug.category}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Tag -> #${sug.tag}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Merchant -> ${sug.merchant}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            category = if (categories.contains(sug.category)) sug.category else "Other"
                                            tag = sug.tag.lowercase()
                                            description = sug.merchant
                                            viewModel.resetAiSuggestion()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Apply AI Suggestions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is AiSuggestionState.Error -> {
                                Text(
                                    text = "Error suggestion: ${(aiState as AiSuggestionState.Error).message}. Please write manually.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }
                }

                // Category selection dropdown/pills
                Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = category == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            modifier = Modifier.testTag("dialog_cat_chip_$cat")
                        )
                    }
                }

                // Custom tags
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Merchant Tag (e.g. uber, groceries)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_tag_input")
                )

                // Accounts selector
                Text("Source Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Cash", "Bank", "Credit Card").forEach { acc ->
                        val isSelected = account == acc
                        Button(
                            onClick = { account = acc },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_account_btn_$acc")
                        ) {
                            Text(acc, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        viewModel.resetAiSuggestion()
                        onDismiss()
                    }) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0.0) {
                                return@Button
                            }
                            onSave(amt, type, category, tag, description.ifEmpty { "Manual Transaction" }, account, timestamp)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_save_btn")
                    ) {
                        Text("Save Record", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- CONVERTERS & HELPERS ----------------

fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}
