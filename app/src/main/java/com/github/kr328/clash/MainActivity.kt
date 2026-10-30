package com.github.kr328.clash

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.kr328.clash.remote.Remote
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.presentation.AppViewModel
import com.github.kr328.clash.presentation.screens.*
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.AppUpdate
import com.github.kr328.clash.design.settings.UpdateChannel
import com.github.kr328.clash.design.compose.GlonetXTheme
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode

class MainActivity : ComponentActivity() {
    internal var pendingRoute by mutableStateOf<String?>(null)
        private set

    companion object { const val EXTRA_NAV_ROUTE = Intents.EXTRA_NAV_ROUTE }

    private fun acceptNavigation(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_NAV_ROUTE) ?: return
        val validProfile = route.startsWith("edit/") && runCatching {
            java.util.UUID.fromString(route.removePrefix("edit/"))
        }.isSuccess
        if (validProfile || route == "log/live") pendingRoute = route
        intent.removeExtra(EXTRA_NAV_ROUTE)
    }

    internal fun consumeNavigation() { pendingRoute = null }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptNavigation(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptNavigation(intent)
        setContent { ApplicationScreen(this) }
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) { Remote.service.remote.get().settings().getControlToken() }
            setupShortcuts(token)
        }
    }
    private fun setupShortcuts(token: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION
        val shortcuts = listOf(
            Triple("toggle_clash", Intents.ACTION_TOGGLE_CLASH, R.drawable.ic_toggle_all),
            Triple("start_clash", Intents.ACTION_START_CLASH, R.drawable.ic_toggle_on),
            Triple("stop_clash", Intents.ACTION_STOP_CLASH, R.drawable.ic_toggle_off),
        ).mapIndexed { rank, (id, action, icon) ->
            ShortcutInfoCompat.Builder(this, id).setShortLabel(id.substringBefore('_').replaceFirstChar { it.uppercase() })
                .setIcon(IconCompat.createWithResource(this, icon))
                .setIntent(Intent(action).setClass(this, ExternalControlActivity::class.java)
                    .putExtra(ExternalControlActivity.EXTRA_CONTROL_TOKEN, token).addFlags(flags))
                .setRank(rank).build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ApplicationScreen(activity: MainActivity, model: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    LaunchedEffect(activity.pendingRoute) {
        activity.pendingRoute?.let { route ->
            nav.navigate(route)
            activity.consumeNavigation()
        }
    }
    val home by model.home.collectAsStateWithLifecycle()
    val snapshot by model.settings.collectAsStateWithLifecycle()
    val prefs = snapshot?.ui ?: com.github.kr328.clash.service.settings.UiPreferences()
    LaunchedEffect(snapshot?.ui?.hideFromRecents) {
        snapshot?.ui?.let { ui -> runCatching {
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).appTasks.forEach { it.setExcludeFromRecents(ui.hideFromRecents) }
        }.onFailure { model.message(it.message ?: "Unable to change recent tasks") } }
    }
    LaunchedEffect(snapshot?.ui?.hideAppIcon) {
        snapshot?.ui?.let { ui -> runCatching {
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.packageName, "com.github.kr328.clash.MainActivityAlias"),
                if (ui.hideAppIcon) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { model.message(it.message ?: "Unable to change the launcher icon") } }
    }
    val snackbar = remember { SnackbarHostState() }
    var release by remember { mutableStateOf<AppUpdate.Release?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    fun checkUpdate(notify: Boolean) {
        model.launchAction {
            updateBusy = true
            try {
                val channel = if (prefs.appUpdateChannel == "Prerelease") UpdateChannel.Prerelease else UpdateChannel.Stable
                release = AppUpdate.checkUpdate(activity, channel)
                if (release == null && notify) model.message("No update available")
            } finally { updateBusy = false }
        }
    }
    LaunchedEffect(snapshot?.ui?.appUpdateAutoCheck) {
        if (snapshot?.ui?.appUpdateAutoCheck == true && AppUpdate.isAutoCheckDue(activity)) {
            AppUpdate.markChecked(activity)
            checkUpdate(false)
        }
    }
    release?.let { candidate ->
        AlertDialog(onDismissRequest = { release = null }, title = { Text("Update ${candidate.versionName}") },
            text = { Text(candidate.notes.take(800)) }, confirmButton = {
                TextButton(enabled = !updateBusy, onClick = {
                    release = null
                    model.launchAction {
                        val apk = AppUpdate.downloadApk(activity, candidate) { }
                        if (apk == null || !AppUpdate.installApk(activity, apk)) model.message("Unable to install update")
                    }
                }) { Text(text("Download", "下载")) }
            }, dismissButton = { TextButton(onClick = { release = null }) { Text(text("Cancel", "取消")) } })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.setVisible(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.setVisible(false) }
    LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notification.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] != true) model.message("Location permission is required to identify Wi-Fi networks")
    }
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            activity.startClashService()
            requestNotification()
        } else model.message("VPN permission denied")
    }
    val scan = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        if (result is QRResult.QRSuccess) {
            val url = result.content.rawValue
            if (!url.isNullOrBlank()) model.launchAction {
                val id = model.repository.create(Profile.Type.Url, "Subscription", url)
                nav.navigate("edit/$id")
            }
        } else if (result is QRResult.QRError) model.message(result.exception.message ?: "Unable to scan QR code")
    }
    val external = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) model.launchAction {
            val id = model.repository.create(Profile.Type.External, result.data?.getStringExtra(Intents.EXTRA_NAME) ?: "Subscription", uri.toString())
            nav.navigate("edit/$id")
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.launchAction {
            val id = model.repository.create(Profile.Type.File, "Local configuration")
            model.repository.replaceFile("$id/config.yaml", uri)
            nav.navigate("edit/$id")
        }
    }
    fun toggle() {
        if (home.connected) activity.stopClashService()
        else if (home.active?.imported != true) model.message("Select a valid subscription first")
        else runCatching {
            val request = activity.startClashService()
            if (request != null) vpn.launch(request) else requestNotification()
        }.onFailure { model.message(it.message ?: "Unable to start VPN") }
    }
    fun navigate(route: String) { if (route == "reconnect") { if(home.connected) activity.stopClashService(); model.message("Reconnect to apply settings") } else nav.navigate(route) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    LaunchedEffect(route) { model.setHomeVisible(route == "home") }
    val root = route in listOf("home", "proxy", "settings")
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val systemConfiguration = LocalConfiguration.current
    val currentContext = LocalContext.current
    val density = LocalDensity.current
    val localizedConfiguration = remember(systemConfiguration, prefs.appLanguage) {
        Configuration(systemConfiguration).apply {
            if (prefs.appLanguage.isNotBlank()) setLocale(java.util.Locale.forLanguageTag(prefs.appLanguage))
        }
    }
    val localizedContext = remember(currentContext, localizedConfiguration) { currentContext.createConfigurationContext(localizedConfiguration) }
    fun selectDestination(target: String) { nav.navigate(target) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
        LocalDensity provides Density(density.density, density.fontScale * prefs.textScale / 100f),
    ) { GlonetXTheme(mode = prefs.darkMode, dynamic = prefs.dynamicColor) {
        val destinations = listOf(Triple("home", Icons.Default.Home, text("Home", "首页")), Triple("proxy", Icons.Default.Public, text("Proxies", "代理")), Triple("settings", Icons.Default.Settings, text("Settings", "设置")))
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            TopAppBar(title = { Text(when(route.substringBefore('/')) { "home" -> "GlonetX"; "proxy" -> text("Proxies", "代理"); "settings" -> text("Settings", "设置"); else -> route.substringBefore('/').replace('-', ' ').replaceFirstChar { it.uppercase() } }) }, navigationIcon = {
                if(!root) IconButton(onClick = { activity.onBackPressedDispatcher.onBackPressed() }) { Icon(Icons.Default.ArrowBack, text("Back", "返回")) }
            })
        }, bottomBar = { if(root && !wide) NavigationBar {
            destinations.forEach { (target, icon, label) ->
                NavigationBarItem(selected = route == target, onClick = { selectDestination(target) }, icon = { Icon(icon, null) }, label = { Text(label) })
            }
        } }) { padding ->
            Row(Modifier.padding(padding)) {
            if (wide) NavigationRail {
                destinations.forEach { (target, icon, label) ->
                    NavigationRailItem(selected = route == target, onClick = { selectDestination(target) }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
            NavHost(nav, startDestination = "home", Modifier.weight(1f)) {
                composable("home") { HomeScreen(home, prefs, snapshot, ::toggle, model::setMode, ::navigate, model::refreshIp, model::updateUi) }
                composable("proxy") { ProxyScreen(prefs, model::updateUi) }
                listOf("settings", "app-settings", "network-settings").forEach { page -> composable(page) {
                    SettingsScreen(page, snapshot, model::updateRuntime, model::updateUi, ::navigate,
                        onCheckUpdate = { checkUpdate(true) },
                        onBatterySettings = { runCatching { activity.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.onFailure { model.message(it.message ?: "Unable to open battery settings") } },
                        onPermission = { if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) })
                } }
                composable("profiles") { ProfilesScreen({ nav.navigate("edit/$it") }, { nav.navigate("new-profile") }, prefs, model::updateUi) }
                composable("new-profile") { NewProfileScreen({ nav.navigate("edit/$it") }, { scan.launch(null) }, { file.launch(arrayOf("*/*")) }, { runCatching { external.launch(Intent(Intents.ACTION_PROVIDE_URL)) }.onFailure { model.message(it.message ?: "No profile providers installed") } }) }
                composable("edit/{id}") { e -> ProfileEditorScreen(e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, { nav.navigate("files/$it") }) }
                composable("files/{id}") { e -> FilesScreen(e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }) }
                composable("providers") { ProvidersScreen() }
                composable("connections") { ConnectionsScreen(prefs, model::updateUi) }
                composable("quality") { QualityScreen() }
                composable("latency") { LatencyScreen(prefs, model::updateUi) }
                composable("backup") { BackupScreen() }
                composable("access") { snapshot?.let { AccessControlScreen(it, model::updateRuntime, model::updateUi) } }
                composable("override") { OverrideScreen(false, { nav.popBackStack() }) }
                composable("meta-feature") { OverrideScreen(true, { nav.popBackStack() }) }
                composable("logs") { LogsScreen(onOpen = { name -> nav.navigate("log/${Uri.encode(name)}") }) }
                composable("log/{name}") { e -> LogViewerScreen(e.arguments?.getString("name").orEmpty(), { nav.popBackStack() }) }
                composable("help") { HelpScreen { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
                composable("about") { AboutScreen("${activity.packageManager.getPackageInfo(activity.packageName, 0).versionName}\n${Bridge.nativeCoreVersion()}") { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
            }
            }
        }
    }
    }
}
