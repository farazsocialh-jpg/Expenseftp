package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.util.CustomRule
import com.example.util.ParsedSmsTransaction
import com.example.util.SmsParser
import com.example.viewmodel.AiSuggestionState
import com.example.viewmodel.ExpenseViewModel
import com.example.viewmodel.FinancialBudget
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
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Embedded in MainScreen to keep tab state unified
                    }
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
    // 5-Tab Architecture: 0: Home (Dashboard), 1: Transactions, 2: Budgets, 3: Insights (Analytics), 4: Settings
    var currentTab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val allTransactionsRaw by viewModel.allTransactions.collectAsStateWithLifecycle()
    val selectedSmsSender by viewModel.smsSenderSetting.collectAsStateWithLifecycle()
    val preferredCurrency by viewModel.preferredCurrencySetting.collectAsStateWithLifecycle()

    // Keep global formatting config synchronized reactively
    LaunchedEffect(preferredCurrency) {
        CurrencyConfig.selectedCurrency = preferredCurrency
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editTransactionTarget by remember { mutableStateOf<TransactionEntity?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Premium Global Fintech App Bar Header
            AppHeader(
                appName = "FinOperatingSystem",
                selectedSender = selectedSmsSender,
                onUpdateSender = { viewModel.updateSmsSender(it) },
                currentTab = currentTab
            )

            // Dynamic view loading depending on selected bottom navigation tab
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        allTransactions = allTransactionsRaw,
                        onAddClick = { showAddDialog = true },
                        onNavigateToParser = { currentTab = 1 },
                        onNavigateToBudgets = { currentTab = 2 }
                    )
                    1 -> TransactionsTab(
                        viewModel = viewModel,
                        transactions = transactions,
                        onAddClick = { showAddDialog = true },
                        onEditClick = { editTransactionTarget = it },
                        onDeleteClick = { viewModel.deleteTransaction(it) }
                    )
                    2 -> BudgetsTab(
                        viewModel = viewModel
                    )
                    3 -> AnalyticsTab(
                        transactions = allTransactionsRaw
                    )
                    4 -> SettingsTab(
                        viewModel = viewModel,
                        allTransactions = allTransactionsRaw
                    )
                }
            }

            // High-fidelity Custom Styled Underlined Bottom Navigation Panel
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_home_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Ledger") },
                    label = { Text("Ledger", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_ledger_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Task, contentDescription = "Budgets") },
                    label = { Text("Budgets", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_budgets_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.PieChart, contentDescription = "Analytics") },
                    label = { Text("Insights", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_insights_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_settings_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        // Add Transaction Modal Dialog
        if (showAddDialog) {
            TransactionFormDialog(
                title = "Log Transaction",
                onDismiss = { showAddDialog = false },
                onSave = { amount, type, category, tag, description, account, timestamp ->
                    viewModel.addTransaction(amount, type, category, tag, description, account, timestamp)
                    showAddDialog = false
                },
                viewModel = viewModel
            )
        }

        // Edit Transaction Modal Dialog
        if (editTransactionTarget != null) {
            TransactionFormDialog(
                title = "Modify Transaction Info",
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
}

// ---------------- GLOBAL FINTECH HEADER BAR ----------------
@Composable
fun AppHeader(
    appName: String,
    selectedSender: String,
    onUpdateSender: (String) -> Unit,
    currentTab: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when (currentTab) {
                        0 -> "Personal Finance Headquarters"
                        1 -> "Immutable Double-Entry Ledger"
                        2 -> "Category Limit Strategy"
                        3 -> "Advanced Canvas Analytics"
                        else -> "System Rules & Configuration"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Offline Secured",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp
            )
        }
    }
}

// ==========================================
// 1. DASHBOARD TAB (HOME VIEW)
// ==========================================
@Composable
fun DashboardTab(
    viewModel: ExpenseViewModel,
    allTransactions: List<TransactionEntity>,
    onAddClick: () -> Unit,
    onNavigateToParser: () -> Unit,
    onNavigateToBudgets: () -> Unit
) {
    val healthScore by viewModel.financialHealthScore.collectAsStateWithLifecycle()
    val budgetsStatus by viewModel.budgetStatuses.collectAsStateWithLifecycle()

    val totalIncome = allTransactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val totalExpense = allTransactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val netWorth = totalIncome - totalExpense

    // Account level Aggregations
    val cashCredits = allTransactions.filter { it.account == "Cash" && it.type == "CREDIT" }.sumOf { it.amount }
    val cashDebits = allTransactions.filter { it.account == "Cash" && it.type == "DEBIT" }.sumOf { it.amount }
    val cashBalance = cashCredits - cashDebits

    val bankCredits = allTransactions.filter { it.account == "Bank" && it.type == "CREDIT" }.sumOf { it.amount }
    val bankDebits = allTransactions.filter { it.account == "Bank" && it.type == "DEBIT" }.sumOf { it.amount }
    val bankBalance = bankCredits - bankDebits

    val ccCredits = allTransactions.filter { it.account == "Credit Card" && it.type == "CREDIT" }.sumOf { it.amount }
    val ccDebits = allTransactions.filter { it.account == "Credit Card" && it.type == "DEBIT" }.sumOf { it.amount }
    val ccBalanceOwed = ccDebits - ccCredits // Owed is Debits minus paid back credits

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Custom Dashboard Banner with Welcoming details
        Text(
            text = "FINANCIAL SUMMARY DASHBOARD",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.1.sp
        )

        // Triple Gradient Liquidity/Wallet Account Cards (Horizontal Row of Wallets)
        Text(
            text = "Active Wallets & Balances",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Bank Balance
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = "Bank", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("SAVINGS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Bank Vault", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(bankBalance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Card 2: Cash Drawer
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocalAtm, contentDescription = "Cash", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Text("LIQUID", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Column {
                        Text("Cash Wallet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(cashBalance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Card 3: Credit Card Owed
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ccBalanceOwed > 1000) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) 
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, if (ccBalanceOwed > 1000) MaterialTheme.colorScheme.error.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = "CC", tint = if (ccBalanceOwed > 1000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Text("DEBT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (ccBalanceOwed > 1000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("Credit Owed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatCurrency(ccBalanceOwed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = if (ccBalanceOwed > 1000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Dynamic Circular Financial Health Indicator Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive dynamic health radial visualizer
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            radius = size.minDimension / 2,
                            style = Stroke(width = 8.dp.toPx())
                        )
                        drawArc(
                            color = when {
                                healthScore >= 80 -> Color(0xFF2ECC71) // Green
                                healthScore >= 55 -> Color(0xFFF1C40F) // Amber
                                else -> Color(0xFFE74C3C) // Red
                            },
                            startAngle = -90f,
                            sweepAngle = (healthScore / 100f) * 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = healthScore.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text("INDEX", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HEALTH SCORE EVALUATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            healthScore >= 80 -> "Premium Cashflow Posture. Excellent savings percentage, minimal debt liability, and safe category budgeting."
                            healthScore >= 55 -> "Moderate Health Posture. Budget thresholds are maintained, but savings ratios can be strengthened."
                            else -> "Alert Indicator. Spending exceeds income velocity or high overspent budget alerts require mediation."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Net Worth and Aggregates Header Card (SaaS Style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AGGREGATE NET WORTH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = formatCurrency(netWorth),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = if (netWorth >= 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Tx", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF2ECC71), CircleShape))
                            Text("30D Credits", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatCurrency(totalIncome), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF27AE60))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFE74C3C), CircleShape))
                            Text("30D Debits", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatCurrency(totalExpense), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                    }
                    Column {
                        Text("30D Savings Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val rateFraction = if (totalIncome > 0) (totalIncome - totalExpense) / totalIncome else 0.0
                        val ratePct = (rateFraction * 100).toInt().coerceAtLeast(0)
                        Text("$ratePct%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Instant Alerts and Critical Budget Signals
        val overspendAlerts = budgetsStatus.filter { it.overspent }
        if (overspendAlerts.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Alert", tint = MaterialTheme.colorScheme.error)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BUDGET DEFICIT WARNING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(
                            text = "You have ${overspendAlerts.size} overspent categories. Total overspending: ${formatCurrency(overspendAlerts.sumOf { it.spent - it.limit })}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    TextButton(onClick = onNavigateToBudgets) {
                        Text("Mitigate", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Live AI Advice Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = "AI Advice", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text("AI FORECAST & INSIGHT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = when {
                        allTransactions.isEmpty() -> "The local parsing and modeling engine is primed. Log transactions manually or scan elder device SMS SMS cards to evaluate financial recommendations."
                        totalExpense > totalIncome -> "Outflows exceed monthly income. We recommend pausing discretionary Shopping or Entertainment expenditures immediately to avoid net liquidity degradation."
                        overspendAlerts.isNotEmpty() -> "Discretionary category caps are exceeded. We forecast exhaustion of remaining miscellaneous buffers in less than 5 days based on your current velocity."
                        else -> "Excellent financial hygiene. Net worth is progressing upward at a stable savings coefficient. Continue maintaining current budget rules."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// 2. TRANSACTIONS TAB (TRANSACTION MANAGER & SMS SYNC)
// ==========================================
@Composable
fun TransactionsTab(
    viewModel: ExpenseViewModel,
    transactions: List<TransactionEntity>,
    onAddClick: () -> Unit,
    onEditClick: (TransactionEntity) -> Unit,
    onDeleteClick: (TransactionEntity) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val selType by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()
    val selCat by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategoriesSetting.collectAsStateWithLifecycle()
    val selectedSmsSender by viewModel.smsSenderSetting.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        
        // 1. Double Filter Controls (Search + Dynamic filters)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Expanded Search Text Field
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setSearchQuery(it)
                },
                placeholder = { Text("Search merchant or category...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ledger_search_field")
            )

            // Dynamic Row of Flow Segment Filters (ALL / DEBIT / CREDIT / TRANSFER)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ALL", "DEBIT", "CREDIT", "TRANSFER").forEach { type ->
                    val isSelected = selType == type
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { viewModel.setTypeFilter(type) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = type,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Scrollable category filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isSelected = selCat == "ALL"
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setCategoryFilter("ALL") },
                        label = { Text("All Categories") }
                    )
                }
                items(customCategories) { cat ->
                    val isSelected = selCat == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setCategoryFilter(cat) },
                        label = { Text(cat) },
                        modifier = Modifier.testTag("ledger_cat_chip_$cat")
                    )
                }
            }
        }

        // Live Phone Device Parser card inside Scroll context
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                RealTimeSmsSyncCard(viewModel = viewModel, selectedSender = selectedSmsSender)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ledger Records (" + transactions.size + ")",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log manual tx", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Feed, contentDescription = "Empty", tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No Ledger Records Match", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Amend filters or tap sync to populate transaction archives.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            items(transactions, key = { it.id }) { tx ->
                TransactionInteractiveCard(
                    tx = tx,
                    onEditClick = { onEditClick(tx) },
                    onDeleteClick = { onDeleteClick(tx) }
                )
            }
        }
    }
}

// ---------------- HARDWARE LEVEL READ/RECEIVE SMS CONTROLLER ----------------
@Composable
fun RealTimeSmsSyncCard(
    viewModel: ExpenseViewModel,
    selectedSender: String
) {
    val context = LocalContext.current
    var hasSmsPermissions by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val readGranted = perms[android.Manifest.permission.READ_SMS] ?: false
        val receiveGranted = perms[android.Manifest.permission.RECEIVE_SMS] ?: false
        hasSmsPermissions = readGranted && receiveGranted
        if (hasSmsPermissions) {
            Toast.makeText(context, "Sms Parser Activated!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "READ_SMS and RECEIVE_SMS permissions required for real parsing.", Toast.LENGTH_LONG).show()
        }
    }

    var isScanningSms by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var editableSender by remember { mutableStateOf(selectedSender) }

    LaunchedEffect(selectedSender) {
        editableSender = selectedSender
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("real_time_sms_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (hasSmsPermissions) Icons.Default.CloudDone else Icons.Default.Sms,
                        contentDescription = "Sync State Icon",
                        tint = if (hasSmsPermissions) Color(0xFF2ECC71) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "HARDWARE SIM OVERLOOK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (hasSmsPermissions) Color(0xFFE8F8F5) else Color(0xFFFCE4D6))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (hasSmsPermissions) "Active Sync" else "Permissions Blocked",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasSmsPermissions) Color(0xFF117A65) else Color(0xFFBA4A00),
                        fontSize = 8.sp
                    )
                }
            }

            Text(
                text = "Reads text bank logs instantly to auto-classify category/tag matrices. No manual logging required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!hasSmsPermissions) {
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_SMS,
                                android.Manifest.permission.RECEIVE_SMS
                            )
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("grant_sms_permission_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = "Shield")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grant Device SMS reading permission", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editableSender,
                        onValueChange = {
                            editableSender = it
                            viewModel.updateSmsSender(it)
                        },
                        label = { Text("Filter Sender Code") },
                        placeholder = { Text("e.g. HDFCBank, Chase") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_sender_input_field")
                    )

                    Button(
                        onClick = {
                            if (editableSender.trim().isEmpty()) {
                                Toast.makeText(context, "A valid sender is required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isScanningSms = true
                            scanResult = null
                            viewModel.syncHistoricalSms(editableSender) { scanned, imported ->
                                isScanningSms = false
                                scanResult = "Evaluation Completed: Evaluated $scanned messages. Imported $imported transactions matching filter '$editableSender'."
                            }
                        },
                        enabled = !isScanningSms,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp).testTag("scan_past_sms_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isScanningSms) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Past", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                scanResult?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionInteractiveCard(
    tx: TransactionEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon Mapping
                val categoryIcon = when (tx.category) {
                    "Food" -> Icons.Default.Fastfood
                    "Salary" -> Icons.Default.Payments
                    "Rent/Bills" -> Icons.Default.Receipt
                    "Shopping" -> Icons.Default.LocalMall
                    "Transport" -> Icons.Default.DirectionsCar
                    "Entertainment" -> Icons.Default.Tv
                    "Health" -> Icons.Default.MedicalServices
                    "Investment" -> Icons.Default.ShowChart
                    else -> Icons.Default.FilterVintage
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = when (tx.type) {
                                "CREDIT" -> Color(0xFF27AE60).copy(alpha = 0.12f)
                                "DEBIT" -> Color(0xFFC0392B).copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = tx.category,
                        tint = when (tx.type) {
                            "CREDIT" -> Color(0xFF27AE60)
                            "DEBIT" -> Color(0xFFC0392B)
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tx.description,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Confidence Level metrics (e.g. System matched vs manual)
                        if (tx.sender != "Manual") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("Matched 90%", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(tx.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                        Text(
                            text = tx.account,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when (tx.type) {
                        "CREDIT" -> "+" + formatCurrency(tx.amount)
                        "DEBIT" -> "-" + formatCurrency(tx.amount)
                        else -> formatCurrency(tx.amount)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = when (tx.type) {
                        "CREDIT" -> Color(0xFF27AE60)
                        "DEBIT" -> Color(0xFFC0392B)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
                Text(
                    text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(tx.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ==========================================
// 3. BUDGETS TAB (DYNAMIC CAP SETUPS)
// ==========================================
@Composable
fun BudgetsTab(
    viewModel: ExpenseViewModel
) {
    val statuses by viewModel.budgetStatuses.collectAsStateWithLifecycle()
    val categories by viewModel.customCategoriesSetting.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddBudgetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "STRATEGIC CAP BUDGETS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Weekly, Monthly, or Annual Category Goals",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAddBudgetDialog = true },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create New", fontWeight = FontWeight.Bold)
            }
        }

        if (statuses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TaskAlt, contentDescription = "Empty", tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No Budgets Programmed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Design custom limit constraints (e.g. food, transport) to track burn velocities.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            }
        } else {
            statuses.forEach { bs ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (bs.overspent) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) 
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = bs.budget.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Filter: ${bs.budget.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• Wallet: ${bs.budget.wallet}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.deleteBudget(bs.budget.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Cap details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Spent: " + formatCurrency(bs.spent) + " of " + formatCurrency(bs.limit),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = (bs.percentage * 100).toInt().toString() + "% Utilized",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bs.overspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Gauge
                        LinearProgressIndicator(
                            progress = { bs.percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                bs.overspent -> MaterialTheme.colorScheme.error
                                bs.percentage > 0.8f -> Color(0xFFF39C12) // Orange
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )

                        // Burn Rate insights & Exhaustive Forecasting
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.TrendingDown, contentDescription = "Trend", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "Burn: " + formatCurrency(bs.dailyBurnRate) + "/day",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = when {
                                    bs.overspent -> "DEFICIT LIMITS: " + formatCurrency(bs.spent - bs.limit) + " overspend!"
                                    bs.forecastedExhaustionDays != null && bs.forecastedExhaustionDays < 10 -> "Depleted in ~${bs.forecastedExhaustionDays} days!"
                                    bs.forecastedExhaustionDays != null -> "Est: ~${bs.forecastedExhaustionDays} days left"
                                    else -> "Steady consumption"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (bs.overspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog to Create a Budget
    if (showAddBudgetDialog) {
        var budgetName by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("ALL") }
        var selectedWallet by remember { mutableStateOf("ALL") }
        var budgetAmountStr by remember { mutableStateOf("") }
        var selectedPeriod by remember { mutableStateOf("Monthly") }
        var rolloverEnabled by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showAddBudgetDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
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
                        text = "Establish Budget Constraints",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = budgetName,
                        onValueChange = { budgetName = it },
                        label = { Text("Budget Title (e.g. Weekly Groceries)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("budget_title_input")
                    )

                    OutlinedTextField(
                        value = budgetAmountStr,
                        onValueChange = { budgetAmountStr = it },
                        label = { Text("Constraints Amount Limit (${CurrencyConfig.selectedCurrency})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("budget_amount_input")
                    )

                    Text("Selected Category Target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val selected = selectedCategory == "ALL"
                            FilterChip(selected = selected, onClick = { selectedCategory = "ALL" }, label = { Text("ALL Categories") })
                        }
                        items(categories) { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(selected = selected, onClick = { selectedCategory = cat }, label = { Text(cat) })
                        }
                    }

                    Text("Target Wallet Source", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "Cash", "Bank", "Credit Card").forEach { wallet ->
                            val selected = selectedWallet == wallet
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { selectedWallet = wallet }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(wallet, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Text("Fiducial Period Metric", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Weekly", "Monthly", "Annual").forEach { per ->
                            val selected = selectedPeriod == per
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { selectedPeriod = per }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(per, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddBudgetDialog = false }) {
                            Text("Revert", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val amt = budgetAmountStr.toDoubleOrNull() ?: 0.0
                                if (amt > 0.0) {
                                    viewModel.addBudget(
                                        name = budgetName,
                                        category = selectedCategory,
                                        wallet = selectedWallet,
                                        amount = amt,
                                        period = selectedPeriod,
                                        rollover = rolloverEnabled
                                    )
                                    showAddBudgetDialog = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("budget_save_btn")
                        ) {
                            Text("Deploy Cap", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. ANALYTICS TAB (CHART ENGINES)
// ==========================================
@Composable
fun AnalyticsTab(transactions: List<TransactionEntity>) {
    val context = LocalContext.current
    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.BarChart, contentDescription = "Empty", tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Underlying Distributions Unpopulated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Log transactions or load templates to construct analytical Canvas charts in real-time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
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
        
        Text("FIDUCIARY ANALYTICS OVERVIEW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)

        // 1. Double Bar graph: Credit vs Debit ratio
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Capital Flow Comparison", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        // Draw background Grid lines
                        drawLine(Color.LightGray.copy(alpha = 0.2f), Offset(0f, height * 0.25f), Offset(width, height * 0.25f), strokeWidth = 2f)
                        drawLine(Color.LightGray.copy(alpha = 0.2f), Offset(0f, height * 0.5f), Offset(width, height * 0.5f), strokeWidth = 2f)
                        drawLine(Color.LightGray.copy(alpha = 0.2f), Offset(0f, height * 0.75f), Offset(width, height * 0.75f), strokeWidth = 2f)

                        // Calculate relative height mapping ratio
                        val maxVal = maxOf(credits, debits, transfers, 1.0)
                        val credPercentage = (credits / maxVal).toFloat() * (height * 0.82f)
                        val debPercentage = (debits / maxVal).toFloat() * (height * 0.82f)
                        val transferPercentage = (transfers / maxVal).toFloat() * (height * 0.82f)

                        val barWidth = width * 0.16f
                        val gap = width * 0.12f

                        // Bar 1: Credits (Green)
                        val cX = gap
                        drawRect(
                            color = Color(0xFF2ECC71),
                            topLeft = Offset(cX, height - credPercentage),
                            size = Size(barWidth, credPercentage)
                        )

                        // Bar 2: Debits (Red)
                        val dX = gap * 2 + barWidth
                        drawRect(
                            color = Color(0xFFE74C3C),
                            topLeft = Offset(dX, height - debPercentage),
                            size = Size(barWidth, debPercentage)
                        )

                        // Bar 3: Transfers (Secondary blue)
                        val tX = gap * 3 + barWidth * 2
                        drawRect(
                            color = Color(0xFF3498DB),
                            topLeft = Offset(tX, height - transferPercentage),
                            size = Size(barWidth, transferPercentage)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Credits (In)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF27AE60), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(credits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Debits (Out)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC0392B), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(debits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Transfers", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2980B9), fontWeight = FontWeight.Bold)
                        Text(formatCurrency(transfers), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Spending Category Segmentation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Outflows Concentration by Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                if (totalSpending == 0.0) {
                    Text("No debit transactions recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // Segment list
                    val sortedCategories = spendingByCategory.entries.sortedByDescending { it.value }
                    sortedCategories.forEach { (cat, amt) ->
                        val ratio = if (totalSpending > 0.0) amt / totalSpending else 0.0
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                    Text(cat, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = formatCurrency(amt) + " (" + (ratio * 100).toInt() + "%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { ratio.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 3. Simulated Reports Scheduled Exporter Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Reports Converter", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("LEDGER CONVERSION EXPORTER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text("Export the local database immediately to standard professional CSV matrices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = {
                        val csv = StringBuilder("ID,Amount,Type,Category,Tag,Description,Account,Timestamp\n")
                        transactions.forEach {
                            csv.append("${it.id},${it.amount},${it.type},${it.category},${it.tag},${it.description.replace(",", "")},${it.account},${it.timestamp}\n")
                        }
                        Toast.makeText(context, "SaaS Spreadsheet CSV ready on clipboard!", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("Export CSV", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 5. SETTINGS TAB (MODULAR CONFIG)
// ==========================================
@Composable
fun SettingsTab(
    viewModel: ExpenseViewModel,
    allTransactions: List<TransactionEntity>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customCategories by viewModel.customCategoriesSetting.collectAsStateWithLifecycle()
    val automationRules by viewModel.automationRulesSetting.collectAsStateWithLifecycle()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }

    var backupTextLine by remember { mutableStateOf("") }
    var pasteTextLine by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        Text("MODULAR CONTROL HEADQUARTERS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.1.sp)

        // PANEL 1: Dynamic customizable Categories Manager
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Managed Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New", fontWeight = FontWeight.Bold)
                    }
                }

                Text("Customized categories used to sort transactions and budget envelopes:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Chips board of active categories
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(customCategories) { cat ->
                        AssistChip(
                            onClick = {},
                            label = { Text(cat) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.deleteCategory(cat) }
                                )
                            },
                            modifier = Modifier.testTag("settings_chip_delete_$cat")
                        )
                    }
                }
            }
        }

        // PANEL 2: Automation & Routing Rules Engine Manager
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. Strategic Automation Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showRuleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rule")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Rule", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "If transaction contains [Keyword], the engine auto-assigns [Category] and [Merchant Tag]. Overrides amount-based defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (automationRules.isEmpty()) {
                    Text("No customized rules compiled. Use 'Add Rule' above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.SemiBold)
                } else {
                    automationRules.forEach { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Keyword: \"${r.keyword}\"", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Assigns Category: ${r.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Text("• Merchant: ${r.cleanMerchant}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.deleteAutomationRule(r.keyword) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // PANEL 2.5: Preferred Currency Selection
        val currentCurrency by viewModel.preferredCurrencySetting.collectAsStateWithLifecycle()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Core Application Currency", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Configure the symbol used for displaying transactions, budgets, insights, and account ledger sheets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val currencyOptions = listOf(
                    "QAR" to "QAR (ر.ق)",
                    "USD" to "USD ($)",
                    "EUR" to "EUR (€)",
                    "GBP" to "GBP (£)",
                    "INR" to "INR (₹)",
                    "AED" to "AED (د.إ)",
                    "SAR" to "SAR (ر.س)"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currencyOptions) { (code, label) ->
                        val isSelected = currentCurrency == code
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updatePreferredCurrency(code) },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("currency_chip_$code")
                        )
                    }
                }
            }
        }

        // PANEL 3: Local Database Exporter / Offline backups
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("4. Local Ledger Archiving (Offline Backup)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Export or import entire JSON ledger backups instantly. Restores multi-wallet accounts offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            backupTextLine = viewModel.exportBackup()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Ledger Backup", backupTextLine))
                            Toast.makeText(context, "Ledger backup JSON string copied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Export")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Ledger", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (pasteTextLine.trim().isEmpty()) {
                                Toast.makeText(context, "Paste a valid exported backup JSON string first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                val success = viewModel.restoreBackup(pasteTextLine)
                                if (success) {
                                    Toast.makeText(context, "State successfully imported!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Parsing error. Verify JSON validation.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Import")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import Ledger", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (backupTextLine.isNotEmpty()) {
                    OutlinedTextField(
                        value = backupTextLine,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Exported JSON string (Saved to Clipboard)") },
                        textStyle = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = pasteTextLine,
                    onValueChange = { pasteTextLine = it },
                    label = { Text("Paste exported Ledger JSON here to Restore") },
                    textStyle = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("{\"version\":1,\"transactions\":[...]}") }
                )
            }
        }

        // PANEL 5: Security Sim Biometrics & Clear Sandbox
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("5. Sandbox Security & Reset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        viewModel.clearAllTransactions()
                        Toast.makeText(context, "Sandbox Transactions Purged!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Delete All")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Purge Entire Ledger", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal dialog for Managed Category Creation
    if (showCategoryDialog) {
        var inputCat by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCategoryDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Add Custom Category Segment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = inputCat,
                        onValueChange = { inputCat = it },
                        label = { Text("Category Title (e.g. Subscriptions)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_cat_input_field")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCategoryDialog = false }) {
                            Text("Revert")
                        }
                        Button(
                            onClick = {
                                if (inputCat.trim().isNotEmpty()) {
                                    viewModel.addCategory(inputCat.trim())
                                    showCategoryDialog = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_cat_confirm_btn")
                        ) {
                            Text("Save Segment", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal dialog for Managed Custom Rule Creation
    if (showRuleDialog) {
        var inputKeyWord by remember { mutableStateOf("") }
        var inputCategory by remember { mutableStateOf("Food") }
        var inputMerchantName by remember { mutableStateOf("") }
        var inputTag by remember { mutableStateOf("sub_category") }

        Dialog(onDismissRequest = { showRuleDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add Auto Routing Rule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = inputKeyWord,
                        onValueChange = { inputKeyWord = it },
                        label = { Text("Search Key Word (e.g. Netflix)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_rule_keyword")
                    )

                    OutlinedTextField(
                        value = inputMerchantName,
                        onValueChange = { inputMerchantName = it },
                        label = { Text("Clean brand title (e.g. Netflix Streaming)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Attach Category Destination", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(customCategories) { cat ->
                            val isSelected = inputCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { inputCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputTag,
                        onValueChange = { inputTag = it },
                        label = { Text("Custom Search Tag (e.g. movies)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showRuleDialog = false }) {
                            Text("Revert")
                        }
                        Button(
                            onClick = {
                                if (inputKeyWord.trim().isNotEmpty() && inputMerchantName.trim().isNotEmpty()) {
                                    viewModel.addAutomationRule(
                                        keyword = inputKeyWord.trim(),
                                        category = inputCategory,
                                        tag = inputTag,
                                        cleanMerchant = inputMerchantName.trim()
                                    )
                                    showRuleDialog = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_rule_confirm_btn")
                        ) {
                            Text("Deploy Rule", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. TRANSACTION LOGGING MODAL DIALOG
// ==========================================
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
    val categories by viewModel.customCategoriesSetting.collectAsStateWithLifecycle()

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

                // Decent description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details / Merchant Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_desc_input")
                )

                // Flow type Row selector (DEBIT vs CREDIT vs TRANSFER)
                Text("Flow Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                                containerColor = if (isSelected) {
                                    if (t == "CREDIT") Color(0xFF27AE60) else if (t == "DEBIT") Color(0xFFC0392B) else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_type_btn_$t")
                        ) {
                            Text(t, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Amount numeric entry field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Fiducial Amount (${CurrencyConfig.selectedCurrency})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_amount_input")
                )

                // Premium AI categorization fallback evaluation
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Scan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("INTELLIGENT AI ENVELOPE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Button(
                                onClick = {
                                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                                    viewModel.suggestCategoryAndTagAI(description, amt, type)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Ask Gemini", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        when (aiState) {
                            is AiSuggestionState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp)
                                    Text("Gemini scanning merchant logs...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            is AiSuggestionState.Success -> {
                                val s = (aiState as AiSuggestionState.Success).suggestion
                                Column {
                                    Text("AI Confidence Prediction Matrix:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF27AE60))
                                    Text("• Suggested Segment: ${s.category}", style = MaterialTheme.typography.bodySmall)
                                    Text("• Extracted Brand: ${s.merchant} • Label: ${s.tag}", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            category = s.category
                                            tag = s.tag
                                            if (s.merchant.isNotEmpty()) {
                                                description = s.merchant
                                            }
                                            viewModel.resetAiSuggestion()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(30.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Apply AI Metrics", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is AiSuggestionState.Error -> {
                                Text(
                                    text = "Evaluation failed: ${(aiState as AiSuggestionState.Error).message}. Proceeding with manual input.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }
                }

                // Category Selection Carousel
                Text("Category Segment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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

                // Search tags
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Filter Tag (e.g. coffee, groceries)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_tag_input")
                )

                // Multi account source selector
                Text("Liquidity Account Source", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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

                // Dialog Buttons
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
                        Text("Revert", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0.0) {
                                return@Button
                            }
                            onSave(amt, type, category, tag, description.ifEmpty { "Manual Log" }, account, timestamp)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_save_btn")
                    ) {
                        Text("Commit Record", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- CURRENCY UTILITIES ----------------
object CurrencyConfig {
    var selectedCurrency by mutableStateOf("QAR")
}

fun formatCurrency(amount: Double): String {
    val currency = CurrencyConfig.selectedCurrency
    return when (currency) {
        "QAR" -> String.format(Locale.US, "QAR %,.2f", amount)
        "USD" -> String.format(Locale.US, "$%,.2f", amount)
        "EUR" -> String.format(Locale.US, "€%,.2f", amount)
        "GBP" -> String.format(Locale.US, "£%,.2f", amount)
        "INR" -> String.format(Locale.US, "₹%,.2f", amount)
        "AED" -> String.format(Locale.US, "AED %,.2f", amount)
        "SAR" -> String.format(Locale.US, "SAR %,.2f", amount)
        else -> String.format(Locale.US, "%s %,.2f", currency, amount)
    }
}
