package com.mazidulhaque.autopocket

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mazidulhaque.autopocket.data.Transaction
import com.mazidulhaque.autopocket.security.AppIntegrity
import com.mazidulhaque.autopocket.ui.AboutScreen
import com.mazidulhaque.autopocket.ui.AccountsScreen
import com.mazidulhaque.autopocket.ui.AddFromSmsScreen
import com.mazidulhaque.autopocket.ui.AppTheme
import com.mazidulhaque.autopocket.ui.AutoPocketTheme
import com.mazidulhaque.autopocket.ui.BackupScreen
import com.mazidulhaque.autopocket.ui.BudgetsScreen
import com.mazidulhaque.autopocket.ui.DonateScreen
import com.mazidulhaque.autopocket.ui.Glyphs
import com.mazidulhaque.autopocket.ui.HiddenZoneScreen
import com.mazidulhaque.autopocket.ui.HomeScreen
import com.mazidulhaque.autopocket.ui.InsightsScreen
import com.mazidulhaque.autopocket.ui.MainViewModel
import com.mazidulhaque.autopocket.ui.MoreScreen
import com.mazidulhaque.autopocket.ui.OnboardingScreen
import com.mazidulhaque.autopocket.ui.ReviewScreen
import com.mazidulhaque.autopocket.ui.SettingsScreen
import com.mazidulhaque.autopocket.ui.SmsPermissionHelpScreen
import com.mazidulhaque.autopocket.ui.TransactionDialog
import com.mazidulhaque.autopocket.ui.TransactionsScreen

/** The four tabs of the bottom bar. */
private enum class Tab { HOME, ACCOUNTS, INSIGHTS, MORE }

/** Full screens opened on top of the tabs. */
private enum class Page { NONE, WELCOME, ABOUT, DONATE, HIDDEN, BACKUP, BUDGETS, SETTINGS, ADD_SMS, REVIEW, TRANSACTIONS, SMS_HELP }

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    /** Text of an SMS shared to the app from a messaging app. */
    private var sharedText by mutableStateOf<String?>(null)

    private fun readShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(20_000)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }

    /** Re-checked whenever the app comes back (e.g. after turning SMS on in phone settings). */
    private var smsGranted by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        smsGranted = smsAllowed()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (home button, switching apps, screen off) always locks the hidden zone.
        vm.lockHidden()
    }

    private fun smsAllowed() = BuildConfig.AUTO_SMS &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val unofficial = AppIntegrity.status(this) == AppIntegrity.Status.UNOFFICIAL
        val settings = Settings(this)
        if (savedInstanceState == null) readShare(intent)
        smsGranted = smsAllowed()

        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            AutoPocketTheme(themeMode) {
                Surface(color = AppTheme.colors.ground) {
                    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                    var page by rememberSaveable { mutableStateOf(if (prefs.getBoolean("welcomed", false)) Page.NONE else Page.WELCOME) }
                    var category by rememberSaveable { mutableStateOf<String?>(null) }
                    var editing by remember { mutableStateOf<Transaction?>(null) }
                    var adding by remember { mutableStateOf(false) }
                    // Only the Auto edition can read the inbox; the standard edition has no SMS permission.
                    val hasPermission = smsGranted
                    val sources by vm.sources.collectAsState()
                    val importCount by vm.lastImportCount.collectAsState()
                    val snackbar = remember { SnackbarHostState() }
                    val noneFound = stringResource(R.string.no_new_transactions)
                    val importedFormat = stringResource(R.string.imported_n)

                    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        smsGranted = granted
                        // No dialog appeared (Android blocked it as a "restricted setting", or "don't ask again"): show the guide.
                        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) page = Page.SMS_HELP
                    }
                    fun requestSms() = launcher.launch(Manifest.permission.READ_SMS)

                    LaunchedEffect(sharedText) { if (sharedText != null) page = Page.ADD_SMS }
                    // Bring in SMS each time the app opens (and right after permission is given).
                    LaunchedEffect(hasPermission) {
                        if (hasPermission) vm.importSms() else if (!BuildConfig.AUTO_SMS) vm.importFromFolder()
                    }
                    LaunchedEffect(importCount) {
                        importCount?.let {
                            if (it > 0 || page == Page.NONE) snackbar.showSnackbar(if (it == 0) noneFound else importedFormat.format(it))
                            vm.lastImportCount.value = null
                        }
                    }
                    BackHandler(enabled = page != Page.NONE && page != Page.WELCOME || tab != Tab.HOME) {
                        when {
                            page == Page.HIDDEN -> { vm.lockHidden(); page = Page.NONE }
                            page != Page.NONE -> { sharedText = null; page = Page.NONE }
                            else -> tab = Tab.HOME
                        }
                    }
                    val back = { sharedText = null; page = Page.NONE }

                    when (page) {
                        Page.WELCOME -> OnboardingScreen {
                            val firstTime = !prefs.getBoolean("welcomed", false)
                            prefs.edit().putBoolean("welcomed", true).apply()
                            page = Page.NONE
                            if (BuildConfig.AUTO_SMS && firstTime && !hasPermission) requestSms()
                        }
                        Page.ADD_SMS -> AddFromSmsScreen(vm, sharedText, onBack = back)
                        Page.BUDGETS -> BudgetsScreen(vm, onBack = back)
                        Page.SETTINGS -> SettingsScreen(themeMode, onThemeMode = { themeMode = it; settings.themeMode = it }, onBack = back)
                        Page.BACKUP -> BackupScreen(vm, onBack = back)
                        Page.HIDDEN -> HiddenZoneScreen(vm, onBack = back)
                        Page.ABOUT -> AboutScreen(onBack = back, onShowWelcome = { page = Page.WELCOME })
                        Page.DONATE -> DonateScreen(onBack = back)
                        Page.REVIEW -> ReviewScreen(vm, onBack = back)
                        Page.SMS_HELP -> SmsPermissionHelpScreen(hasPermission, onRequest = ::requestSms, onBack = { smsGranted = smsAllowed(); back() })
                        Page.TRANSACTIONS -> TransactionsScreen(
                            vm, category, onClearCategory = { category = null }, onBack = back, onEdit = { editing = it },
                        )
                        Page.NONE -> Scaffold(
                            containerColor = AppTheme.colors.ground,
                            snackbarHost = { SnackbarHost(snackbar) },
                            bottomBar = {
                                BottomBar(tab, onTab = { tab = it }, onAdd = { adding = true })
                            },
                        ) { padding ->
                            Box(Modifier.padding(padding).fillMaxSize()) {
                                when (tab) {
                                    Tab.HOME -> HomeScreen(
                                        vm, hasPermission, unofficial,
                                        onFixPermission = { page = Page.SMS_HELP },
                                        onOpenAddSms = { page = Page.ADD_SMS },
                                        onOpenReview = { page = Page.REVIEW },
                                        onOpenAccounts = { tab = Tab.ACCOUNTS },
                                        onOpenTransactions = { category = null; page = Page.TRANSACTIONS },
                                        onOpenBudgets = { page = Page.BUDGETS },
                                        onEdit = { editing = it },
                                    )
                                    Tab.ACCOUNTS -> AccountsScreen(vm)
                                    Tab.INSIGHTS -> InsightsScreen(
                                        vm, onOpenCategory = { category = it; page = Page.TRANSACTIONS }, onOpenBudgets = { page = Page.BUDGETS },
                                    )
                                    Tab.MORE -> MoreScreen(
                                        hasPermission,
                                        onAddSms = { page = Page.ADD_SMS }, onSmsHelp = { page = Page.SMS_HELP },
                                        onHidden = { page = Page.HIDDEN }, onBudgets = { page = Page.BUDGETS },
                                        onBackup = { page = Page.BACKUP }, onSettings = { page = Page.SETTINGS },
                                        onAbout = { page = Page.ABOUT }, onDonate = { page = Page.DONATE },
                                    )
                                }
                            }
                        }
                    }

                    if (adding) {
                        TransactionDialog(null, sources, onDismiss = { adding = false }, onSave = { t, r -> vm.save(t, r); adding = false }, onDelete = null)
                    }
                    editing?.let { t ->
                        TransactionDialog(
                            t, sources,
                            onDismiss = { editing = null },
                            onSave = { updated, r -> vm.save(updated, r); editing = null },
                            onDelete = { vm.delete(t); editing = null },
                            hideLabel = stringResource(R.string.move_to_hidden_zone),
                            onToggleHidden = { vm.setHidden(t, true); editing = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        unselectedIconColor = AppTheme.colors.muted,
        unselectedTextColor = AppTheme.colors.muted,
    )
    NavigationBar(containerColor = AppTheme.colors.card) {
        NavigationBarItem(tab == Tab.HOME, { onTab(Tab.HOME) }, { Icon(Glyphs.Home, null) }, label = { Text(stringResource(R.string.tab_home)) }, colors = colors)
        NavigationBarItem(tab == Tab.ACCOUNTS, { onTab(Tab.ACCOUNTS) }, { Icon(Glyphs.Card, null) }, label = { Text(stringResource(R.string.tab_accounts)) }, colors = colors)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(
                onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(56.dp),
            ) { Icon(Glyphs.Plus, stringResource(R.string.add_manually)) }
        }
        NavigationBarItem(tab == Tab.INSIGHTS, { onTab(Tab.INSIGHTS) }, { Icon(Glyphs.Chart, null) }, label = { Text(stringResource(R.string.insights)) }, colors = colors)
        NavigationBarItem(tab == Tab.MORE, { onTab(Tab.MORE) }, { Icon(Glyphs.More, null) }, label = { Text(stringResource(R.string.more)) }, colors = colors)
    }
}
