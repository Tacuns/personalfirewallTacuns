package com.sentinel.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sentinel.ui.dashboard.DashboardScreen
import androidx.activity.viewModels
import com.sentinel.core.settings.AppPreferencesRepository.AppLockUser
import com.sentinel.ui.lock.AppLockAuth
import com.sentinel.ui.lock.AppLockCrypto
import com.sentinel.ui.lock.AppLockScope
import com.sentinel.ui.lock.AppLockScreen
import com.sentinel.core.logs.LogRetentionWorker
import com.sentinel.core.schedule.ScheduleCheckWorker
import com.sentinel.ui.lock.AppLockSession
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sentinel.ui.theme.AmberMedium
import com.sentinel.ui.lock.AppLockThrottle
import com.sentinel.ui.lock.AppLockViewModel
import com.sentinel.ui.logs.LogScreen
import com.sentinel.ui.rules.AppControlScreen
import com.sentinel.ui.rules.RulesScreen
import com.sentinel.ui.settings.AppLockUsersScreen
import com.sentinel.ui.settings.BlocklistScreen
import com.sentinel.ui.settings.DataSafetyScreen
import com.sentinel.ui.settings.LanguageScreen
import com.sentinel.ui.settings.PermissionInfoScreen
import com.sentinel.ui.settings.PrivacyDashboardScreen
import com.sentinel.ui.settings.ChangeHistoryScreen
import com.sentinel.ui.settings.SecurityChecklistScreen
import com.sentinel.ui.settings.SettingsScreen
import com.sentinel.ui.settings.VpnNetworkPrivacyScreen
import com.sentinel.ui.theme.SentinelTheme
import androidx.compose.ui.unit.dp

import androidx.work.*

import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sentinel.core.vpn.BlocklistSyncWorker
import com.sentinel.core.vpn.DailySecuritySummaryWorker
import com.sentinel.core.vpn.SentinelVpnService
import com.sentinel.core.vpn.vpnRunning
import com.sentinel.core.settings.AppPreferencesRepository
import com.sentinel.ui.onboarding.OnboardingScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.tacu.nsfwzerotrust.R
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SentinelActivity : AppCompatActivity() {

    // App Lock state. UI-process only — SentinelVpnService (:vpn process) and
    // BootReceiver never read this, so the firewall keeps running while locked.
    // Held in a ViewModel so rotation does not re-lock the app.
    private val lockVm: AppLockViewModel by viewModels()

    // The system VPN-consent dialog is a separate activity, so it fires onStop().
    // Without this guard the user would be re-locked every time they tapped the
    // VPN toggle and returned.
    private var skipRelockOnce = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    // Android 13+ hides every notification until the user allows them. Without asking,
    // scam alerts and the daily summary never appeared on real phones.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.i("SentinelActivity", "Notification permission granted=$granted")
    }

    /** Asks once, right after protection is switched on. A "no" is respected and never repeated. */
    private fun askNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("permission_prompts", MODE_PRIVATE)
        if (prefs.getBoolean("notifications_asked", false)) return
        prefs.edit().putBoolean("notifications_asked", true).apply()
        skipRelockOnce = true   // the system dialog pauses this screen; do not re-lock for it
        notificationPermissionLauncher.launch(permission)
    }

    override fun onStart() {
        super.onStart()
        com.sentinel.core.utils.AppVisibility.set(true)
        restoreProtectionIfStopped()
        refreshProtectionNotification()
    }

    /** Re-posts the protection notification, e.g. after the user allowed notifications again. */
    private fun refreshProtectionNotification() {
        if (!isVpnActive()) return
        try {
            startService(Intent(this, SentinelVpnService::class.java).apply {
                action = SentinelVpnService.ACTION_REFRESH_NOTIFICATION
            })
        } catch (e: Exception) {
            android.util.Log.w("SentinelActivity", "Notification refresh request failed: ${e.message}")
        }
    }

    /**
     * Android kills the app when its notifications are turned off and does not restart the
     * VPN (emulator: "Destroy ServiceRecord … SentinelVpnService"). If the user had protection
     * on and never stopped it, turn it back on now. Opening the app is an allowed moment to
     * start a foreground service. Skipped if VPN consent is gone (another VPN took over).
     */
    private fun restoreProtectionIfStopped() {
        if (!com.sentinel.core.vpn.ProtectionIntent.isWanted(this)) return
        if (isVpnActive()) return
        // Starting now would leave the phone without internet (see PrivateDnsGuard).
        com.sentinel.core.vpn.PrivateDnsGuard.strictHostname(this)?.let { privateDnsBlocker.value = it; return }
        if (VpnService.prepare(this) != null) return
        android.util.Log.i("SentinelActivity", "Protection was wanted but not running; turning it back on")
        startVpnService()
        android.widget.Toast.makeText(this, R.string.protection_restored, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onStop() {
        super.onStop()
        com.sentinel.core.utils.AppVisibility.set(false)
        if (skipRelockOnce) skipRelockOnce = false
        else if (!isChangingConfigurations) lockVm.unlocked = false
    }

    // Set when the activity is opened from an alert notification; the main screen then
    // shows the Alerts list (after App Lock, if it is on) and clears it.
    private val openAlertsRequest = mutableStateOf(false)
    // Set when protection cannot start because Private DNS is set to a provider.
    private val privateDnsBlocker = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(com.sentinel.core.vpn.SecurityAlertNotifier.EXTRA_OPEN_ALERTS, false)) {
            openAlertsRequest.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The whole app is kept out of screenshots, screen recording, screen sharing and the
        // recent-apps preview (the owner's choice). FLAG_SECURE: "treat the content of the window
        // as secure, preventing it from appearing in screenshots or from being viewed on
        // non-secure displays" (WindowManager.LayoutParams). Compose dialogs inherit it.
        // Controlled by the build setting BLOCK_ALL_SCREENSHOTS (see app/build.gradle.kts).
        if (com.tacu.nsfwzerotrust.BuildConfig.BLOCK_ALL_SCREENSHOTS) window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        if (intent?.getBooleanExtra(com.sentinel.core.vpn.SecurityAlertNotifier.EXTRA_OPEN_ALERTS, false) == true) {
            openAlertsRequest.value = true
        }
        
        cancelRetiredRuleSync()
        BlocklistSyncWorker.schedule(applicationContext)
        LogRetentionWorker.schedule(applicationContext)
        DailySecuritySummaryWorker.schedule(applicationContext)
        // Makes schedule rules match the saved schedule (and clears any leftovers).
        ScheduleCheckWorker.runOnce(applicationContext)
        // An older version let the firewall block itself; clear any such saved rule.
        // The rule engine already ignores it, so this only keeps the stored rules honest.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dao = com.sentinel.core.rules.RuleEngine.getInstance(applicationContext).db.ruleDao()
                com.sentinel.core.rules.SelfProtection.removeStoredSelfBlock(applicationContext, dao)
            } catch (e: Exception) {
                android.util.Log.w("SentinelActivity", "self-block cleanup failed: ${e.javaClass.simpleName}")
            }
        }

        setContent {
            SentinelTheme {
                val repo = remember { AppPreferencesRepository(applicationContext) }
                // Combined into ONE flow so both values resolve together. Collecting them
                // separately leaves a frame where onboarding has loaded but the lock hash
                // has not — which would flash the main UI before the lock screen appears.
                val gate by remember {
                    combine(repo.onboardingDone, repo.appLockConfig) { done, cfg -> done to cfg }
                }.collectAsState(initial = null)
                val scope = rememberCoroutineScope()

                val state = gate
                // Set by a recovery reset: the account it creates can always make changes.
                var justReset = false
                when {
                    state == null   -> Box(Modifier.fillMaxSize().background(Color(0xFF060912)))
                    !state.first    -> OnboardingScreen(onGetStarted = {
                        scope.launch { repo.setOnboardingDone() }
                    })
                    state.second.isLocked && !lockVm.unlocked ->
                        AppLockScreen(
                            config     = state.second,
                            onAttempt  = { ok ->
                                AppLockScope.scope.launch {
                                    try { repo.recordAppLockAttempt(ok, AppLockThrottle::waitAfter) }
                                    catch (_: Exception) { }
                                }
                            },
                            onUnlocked = { matchedName ->
                                lockVm.unlocked = true
                                AppLockSession.readOnly = !justReset &&
                                    AppLockAuth.isReadOnly(state.second, matchedName)
                                // A pre-multi-user install stores one record with no
                                // username. This is the only moment the username is known,
                                // so migrate it into the account list now.
                                val cfg = state.second
                                val legacy = cfg.legacyHash
                                if (cfg.users.isEmpty() && legacy != null) {
                                    AppLockScope.scope.launch {
                                        repo.setAppLockUsers(listOf(AppLockUser(matchedName, legacy)))
                                    }
                                }
                            },
                            onSaveCredentials = { u, p, q, a, code ->
                                justReset = true
                                // Runs in the process-lifetime scope: this survives the
                                // lock screen leaving composition partway through hashing.
                                AppLockScope.scope.launch {
                                    val user   = AppLockAuth.newUser(u, p)
                                    val answer = AppLockCrypto.create(AppLockCrypto.normalizeAnswer(a))
                                    val rec    = AppLockCrypto.create(AppLockCrypto.normalizeCode(code))
                                    // A reset replaces every account — the user proved
                                    // ownership by recovery, not by knowing any password.
                                    repo.setAppLockUsers(listOf(user))
                                    repo.setAppLockRecovery(q, answer, rec)
                                    repo.setAppLockEnabled(true)
                                }
                            }
                        )
                    else -> {
                        // No active lock means nobody signed in with a view-only account.
                        if (!state.second.isLocked) AppLockSession.readOnly = false
                        SentinelMainScreen(onToggleVpn = { toggleVpn() }, viewOnly = AppLockSession.readOnly,
                            openAlertsRequest = openAlertsRequest)
                        privateDnsBlocker.value?.let { provider ->
                            com.sentinel.ui.components.PrivateDnsDialog(
                                provider  = provider,
                                onOpenSettings = {
                                    privateDnsBlocker.value = null
                                    com.sentinel.ui.components.openNetworkSettings(this@SentinelActivity)
                                },
                                onDismiss = { privateDnsBlocker.value = null }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isVpnActive(): Boolean = vpnRunning()

    private fun toggleVpn() {
        // A view-only account may turn protection on, never off.
        if (isVpnActive() && AppLockSession.blockChange(this)) return
        // Private DNS set to a provider and this firewall cannot run together: the phone
        // would lose its internet. Explain instead of starting (see PrivateDnsGuard).
        if (!isVpnActive()) {
            com.sentinel.core.vpn.PrivateDnsGuard.strictHostname(this)?.let { privateDnsBlocker.value = it; return }
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            skipRelockOnce = true
            vpnPermissionLauncher.launch(intent)
        } else {
            if (isVpnActive()) {
                stopVpnService()
            } else {
                startVpnService()
            }
        }
    }

    private fun startVpnService() {
        ContextCompat.startForegroundService(this, Intent(this, SentinelVpnService::class.java))
        askNotificationPermissionOnce()
    }

    private fun stopVpnService() {
        val intent = Intent(this, SentinelVpnService::class.java).apply {
            action = SentinelVpnService.ACTION_STOP
        }
        startService(intent)
    }

    // "sentinel_rule_sync" used to run every 24 h and do nothing at all. The worker is gone now,
    // but phones that ran an older build still have the periodic work saved, and WorkManager would
    // keep waking up for a class it can no longer create. Cancelling by name clears it; on a fresh
    // install there is nothing to cancel and this call simply does nothing.
    private fun cancelRetiredRuleSync() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork("sentinel_rule_sync")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelMainScreen(
    onToggleVpn: () -> Unit,
    viewOnly: Boolean = false,
    openAlertsRequest: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val unreadAlerts by remember {
        com.sentinel.core.alerts.AlertDatabase.getInstance(context).alertDao().unreadCountFlow()
    }.collectAsState(initial = 0)
    LaunchedEffect(openAlertsRequest.value) {
        if (openAlertsRequest.value) {
            openAlertsRequest.value = false
            navController.navigate("alerts") { launchSingleTop = true }
        }
    }

    val items  = listOf("dashboard", "rules",    "appblock",       "logs",         "security")
    val labels = listOf(
        stringResource(R.string.nav_home),
        stringResource(R.string.nav_rules),
        stringResource(R.string.nav_appblock),
        stringResource(R.string.nav_logs),
        stringResource(R.string.nav_security)
    )
    val icons  = listOf(
        Icons.Default.Home,
        Icons.Default.Security,
        Icons.Default.PhoneAndroid,
        Icons.AutoMirrored.Filled.List,
        Icons.Default.VerifiedUser
    )

    // Derive selection purely from the live back-stack route.
    // Returns -1 when on a non-nav-bar screen (Settings), so no tab is highlighted.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val selectedItem = items.indexOf(currentRoute ?: "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE0E6F0)
                    )
                },
                actions = {
                    // Watch-only turns website blocking off, so it stays visible on every tab.
                    val firewallOptions by com.sentinel.core.rules.FirewallOptionsStore
                        .flow(androidx.compose.ui.platform.LocalContext.current).collectAsState()
                    if (firewallOptions.watchOnly) {
                        Text(
                            stringResource(R.string.watch_only_badge),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberMedium,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberMedium.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (viewOnly) {
                        Text(
                            stringResource(R.string.app_lock_view_only_badge),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberMedium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberMedium.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(onClick = { navController.navigate("alerts") { launchSingleTop = true } }) {
                        BadgedBox(badge = {
                            if (unreadAlerts > 0) Badge { Text(if (unreadAlerts > 99) "99+" else "$unreadAlerts") }
                        }) {
                            Icon(Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.cd_alerts, unreadAlerts),
                                tint = Color(0xFFE0E6F0))
                        }
                    }
                    IconButton(onClick = {
                        navController.navigate("settings") {
                            // Always land on [dashboard → settings] so any tab click
                            // from Settings can pop settings and navigate cleanly.
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings),
                            tint = Color(0xFFE0E6F0))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF060912)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp
            ) {
                items.forEachIndexed { index, route ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = labels[index]) },
                        // One line, no extra letter spacing: longer names (Tamil, Russian) broke mid-word.
                        label = { Text(labels[index], fontSize = 11.sp, letterSpacing = 0.sp,
                            maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        selected = selectedItem == index,
                        onClick = {
                            // Screens that are not tabs (Alerts from the bell or a notification,
                            // Settings and its pages) never belong to a tab's saved history, so
                            // close them first WITHOUT saving. Otherwise popUpTo(saveState) saved
                            // e.g. [Activity, Alerts], and restoreState ("restores any previously
                            // saved state for the destination" — official multiple-back-stacks
                            // guide) brought Alerts back every time that tab was opened
                            // (reproduced: Activity → bell → Protect → Activity showed Alerts).
                            // This replaces the earlier Home-only fix, which left every other tab
                            // open to the same bug.
                            while (navController.currentDestination?.route !in items &&
                                   navController.popBackStack()) { /* drop the non-tab screen */ }
                            navController.navigate(route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onToggleVpn    = onToggleVpn,
                    // Same navigation as tapping the Security tab in the bottom bar.
                    onOpenSecurity = {
                        navController.navigate("security") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
            composable("rules")     { RulesScreen() }
            composable("appblock")  { AppControlScreen() }
            composable("logs")      { LogScreen() }
            // The map lives inside Activity (List | Map); this tab took its place.
            composable("security")  {
                com.sentinel.ui.security.SecurityScreen(
                    onToggleVpn      = onToggleVpn,
                    onOpenSettings   = {
                        navController.navigate("settings") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenBlocklists = { navController.navigate("blocklists") { launchSingleTop = true } },
                    onOpenAlerts     = { navController.navigate("alerts") { launchSingleTop = true } }
                )
            }
            composable("settings")  {
                SettingsScreen(
                    onNavigateToBlocklists = { navController.navigate("blocklists") },
                    onNavigateToPermissions = { navController.navigate("permissions") },
                    onNavigateToVpnNetworkPrivacy = { navController.navigate("vpn_network_privacy") },
                    onNavigateToDataSafety = { navController.navigate("data_safety") },
                    onNavigateToPrivacyDashboard = { navController.navigate("privacy_dashboard") },
                    onNavigateToSecurityChecklist = { navController.navigate("security_checklist") },
                    onNavigateToChangeHistory = { navController.navigate("change_history") },
                    onNavigateToAlerts = { navController.navigate("alerts") },
                    onNavigateToDns = { navController.navigate("dns") },
                    onNavigateToLanguage = { navController.navigate("language") },
                    onNavigateToAppLockUsers = { navController.navigate("applock_users") }
                )
            }
            composable("applock_users") { AppLockUsersScreen(onBack = { navController.popBackStack() }) }
            composable("blocklists") { BlocklistScreen(onBack = { navController.popBackStack() }) }
            composable("permissions") { PermissionInfoScreen(onBack = { navController.popBackStack() }) }
            composable("vpn_network_privacy") { VpnNetworkPrivacyScreen(onBack = { navController.popBackStack() }) }
            composable("data_safety") { DataSafetyScreen(onBack = { navController.popBackStack() }) }
            composable("privacy_dashboard") { PrivacyDashboardScreen(onBack = { navController.popBackStack() }) }
            composable("security_checklist") { SecurityChecklistScreen(onBack = { navController.popBackStack() }) }
            composable("change_history") { ChangeHistoryScreen(onBack = { navController.popBackStack() }) }
            composable("alerts") {
                com.sentinel.ui.alerts.AlertsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { dest ->
                        val route = when (dest) {
                            com.sentinel.ui.alerts.AlertDestination.HOME       -> "dashboard"
                            com.sentinel.ui.alerts.AlertDestination.BLOCKLISTS -> "blocklists"
                            com.sentinel.ui.alerts.AlertDestination.BACKUP     -> "rules"
                            com.sentinel.ui.alerts.AlertDestination.NETWORK_SETTINGS -> {
                                com.sentinel.ui.components.openNetworkSettings(context); return@AlertsScreen
                            }
                        }
                        navController.navigate(route) {
                            if (route != "blocklists") popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("dns") { com.sentinel.ui.settings.DnsSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("language") { LanguageScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

