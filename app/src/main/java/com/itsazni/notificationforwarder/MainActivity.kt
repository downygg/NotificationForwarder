package com.itsazni.notificationforwarder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itsazni.notificationforwarder.data.NotificationRepository
import com.itsazni.notificationforwarder.data.QueueItem
import com.itsazni.notificationforwarder.data.QueueStats
import com.itsazni.notificationforwarder.data.QueueStatus
import com.itsazni.notificationforwarder.network.WebhookClient
import com.itsazni.notificationforwarder.reliability.BackgroundReliabilityIntents
import com.itsazni.notificationforwarder.reliability.BatteryOptimizationState
import com.itsazni.notificationforwarder.reliability.OemGuidance
import com.itsazni.notificationforwarder.reliability.OemGuidanceResolver
import com.itsazni.notificationforwarder.reliability.batteryOptimizationState
import com.itsazni.notificationforwarder.service.AppNotificationListenerService
import com.itsazni.notificationforwarder.service.ListenerHealth
import com.itsazni.notificationforwarder.service.ListenerHealthState
import com.itsazni.notificationforwarder.settings.AppSettings
import com.itsazni.notificationforwarder.settings.AuthMode
import com.itsazni.notificationforwarder.settings.FilterMode
import com.itsazni.notificationforwarder.settings.SettingsStore
import com.itsazni.notificationforwarder.ui.FilterPackagePicker
import com.itsazni.notificationforwarder.ui.theme.AppTheme
import com.itsazni.notificationforwarder.worker.ManualRetryCoordinator
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home), WEBHOOK("Webhook", Icons.Filled.Link),
    FILTER("Filter", Icons.Filled.Tune), QUEUE("Queue", Icons.AutoMirrored.Filled.List)
}

private data class UiSettings(
    val webhookUrl: String, val webhookMethod: String, val forwardingEnabled: Boolean,
    val filterMode: FilterMode, val filterPackagesRaw: String, val authMode: AuthMode,
    val bearerToken: String, val customHeadersRaw: String, val queryParamsRaw: String,
    val payloadTemplateRaw: String, val maxRetriesRaw: String, val batchSizeRaw: String
)

private fun AppSettings.toUiSettings() = UiSettings(
    webhookUrl, webhookMethod, forwardingEnabled, filterMode, filterPackages.joinToString("\n"),
    authMode, bearerToken, customHeadersRaw, queryParamsRaw, payloadTemplateRaw,
    maxRetries.toString(), batchSize.toString()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = SettingsStore(this)
        setContent { AppTheme { MainScreen(settingsStore) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { NotificationRepository(context) }
    val manualRetryCoordinator = remember { ManualRetryCoordinator(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var uiSettings by remember { mutableStateOf(settingsStore.readAll().toUiSettings()) }
    var notificationAccessGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var batteryState by remember { mutableStateOf(batteryOptimizationState(isBatteryUnrestricted(context))) }
    val listenerHealth by ListenerHealth.state.collectAsState()
    val oemGuidance = remember { OemGuidanceResolver.resolve(Build.MANUFACTURER.orEmpty()) }
    val stats by repository.observeStats().collectAsState(initial = QueueStats(0, 0, 0, 0))
    val recent by repository.observeRecent(30).collectAsState(initial = emptyList())

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted = isNotificationListenerEnabled(context)
                batteryState = batteryOptimizationState(isBatteryUnrestricted(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notification Forwarder") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 0.dp) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(selectedTab == tab, { selectedTab = tab },
                        { Icon(tab.icon, contentDescription = tab.label) }, label = { Text(tab.label) })
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.fillMaxSize().padding(innerPadding)
        when (selectedTab) {
            AppTab.HOME -> HomeScreen(
                modifier = modifier,
                stats = stats,
                notificationAccessGranted = notificationAccessGranted,
                listenerHealth = listenerHealth,
                batteryState = batteryState,
                oemGuidance = oemGuidance,
                onOpenNotificationAccess = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                },
                onRequestUnrestricted = { requestBatteryUnrestricted(context) },
                onOpenBatterySettings = { openBatterySettings(context) },
                onOpenAppSettings = { openAppSettings(context) },
                onSyncQueue = { WorkerScheduler.enqueueImmediate(context) }
            )
            AppTab.WEBHOOK -> WebhookScreen(modifier, uiSettings, { uiSettings = it }, {
                saveSettings(settingsStore, uiSettings)
                WorkerScheduler.enqueueImmediate(context)
                scope.launch { snackbarHostState.showSnackbar("Webhook settings saved") }
            }) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        WebhookClient().send(
                            uiSettings.webhookUrl, uiSettings.webhookMethod,
                            buildHeadersPreview(uiSettings.authMode, uiSettings.bearerToken, uiSettings.customHeadersRaw),
                            parseKeyValuePairs(uiSettings.queryParamsRaw), uiSettings.payloadTemplateRaw,
                            QueueItem(packageName = "com.test.package", appName = "Webhook Test", title = "Test Notification",
                                text = "This is a test payload", postedAt = System.currentTimeMillis(),
                                notificationKey = "test-${System.currentTimeMillis()}"), "test-device"
                        )
                    }
                    snackbarHostState.showSnackbar(if (result.success) "Webhook test success" else "Webhook test failed: ${result.message}")
                }
            }
            AppTab.FILTER -> FilterScreen(modifier, uiSettings, { uiSettings = it }) {
                saveSettings(settingsStore, uiSettings)
                scope.launch { snackbarHostState.showSnackbar("Filter & retry settings saved") }
            }
            AppTab.QUEUE -> QueueScreen(
                modifier, recent, stats,
                onRetryNow = {
                    scope.launch {
                        val count = manualRetryCoordinator.retryNow()
                        snackbarHostState.showSnackbar(if (count > 0) "Retry requested for $count deliveries" else "No pending or failed deliveries")
                    }
                },
                onRetryItem = { itemId ->
                    scope.launch {
                        val scheduled = manualRetryCoordinator.retryItemNow(itemId)
                        snackbarHostState.showSnackbar(if (scheduled) "Retry requested" else "Item is no longer retryable")
                    }
                },
                onDeleteItem = { itemId -> scope.launch { repository.deleteQueueItem(itemId); snackbarHostState.showSnackbar("Queue item deleted") } },
                onClearQueue = { scope.launch { repository.clearQueue(); snackbarHostState.showSnackbar("Queue cleared") } }
            )
        }
    }
    LaunchedEffect(Unit) { WorkerScheduler.ensurePeriodic(context) }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    stats: QueueStats,
    notificationAccessGranted: Boolean,
    listenerHealth: ListenerHealthState,
    batteryState: BatteryOptimizationState,
    oemGuidance: OemGuidance,
    onOpenNotificationAccess: () -> Unit,
    onRequestUnrestricted: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSyncQueue: () -> Unit
) {
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Background Reliability", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Notification Access")
                        StatusBadge(if (notificationAccessGranted) "Granted" else "Not granted", notificationAccessGranted)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Notification Listener")
                        when (listenerHealth) {
                            ListenerHealthState.CONNECTED -> StatusBadge("Connected", true)
                            ListenerHealthState.DISCONNECTED -> StatusBadge("Disconnected", false)
                            ListenerHealthState.UNKNOWN -> NeutralBadge("Unknown")
                        }
                    }
                    if (notificationAccessGranted && listenerHealth == ListenerHealthState.DISCONNECTED) {
                        Text("Android is reconnecting the notification listener.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Battery")
                        StatusBadge(
                            if (batteryState == BatteryOptimizationState.UNRESTRICTED) "Unrestricted" else "Optimized",
                            batteryState == BatteryOptimizationState.UNRESTRICTED
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Boot Recovery")
                        StatusBadge("Enabled", true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Queue Worker")
                        StatusBadge("Configured", true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Device Auto Start")
                        NeutralBadge("Check device settings")
                    }

                    if (!notificationAccessGranted) {
                        Button(onOpenNotificationAccess, Modifier.fillMaxWidth()) { Text("Grant Notification Access") }
                    } else {
                        Button(onOpenNotificationAccess, Modifier.fillMaxWidth()) { Text("Open Notification Access Settings") }
                    }

                    if (batteryState == BatteryOptimizationState.OPTIMIZED) {
                        Text("For reliable notification forwarding, allow unrestricted background battery usage.", style = MaterialTheme.typography.bodySmall)
                        Button(onRequestUnrestricted, Modifier.fillMaxWidth()) { Text("Request Unrestricted") }
                    }
                    Button(onOpenBatterySettings, Modifier.fillMaxWidth()) { Text("Open Battery Settings") }

                    Text("${oemGuidance.manufacturerLabel} guidance", fontWeight = FontWeight.SemiBold)
                    Text(oemGuidance.summary, style = MaterialTheme.typography.bodySmall)
                    Button(onOpenAppSettings, Modifier.fillMaxWidth()) { Text("Open App Settings") }
                    Button(onSyncQueue, Modifier.fillMaxWidth()) { Text("Sync Queue") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Queue Summary", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueStatCard(Modifier.weight(1f), "Pending", stats.pendingCount.toString(), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        QueueStatCard(Modifier.weight(1f), "Sending", stats.sendingCount.toString(), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueStatCard(Modifier.weight(1f), "Sent", stats.sentCount.toString(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        QueueStatCard(Modifier.weight(1f), "Failed", stats.failedCount.toString(), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun WebhookScreen(modifier: Modifier, ui: UiSettings, onChange: (UiSettings) -> Unit, onSave: () -> Unit, onTest: () -> Unit) {
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Webhook Settings", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Enable forwarding"); Switch(ui.forwardingEnabled, { onChange(ui.copy(forwardingEnabled = it)) }) }
                    OutlinedTextField(ui.webhookUrl, { onChange(ui.copy(webhookUrl = it)) }, Modifier.fillMaxWidth(), label = { Text("Webhook URL") }, singleLine = true)
                    DropdownSelector("HTTP method", ui.webhookMethod, listOf("GET", "POST", "PUT", "PATCH")) { onChange(ui.copy(webhookMethod = it)) }
                    DropdownSelector("Auth mode", ui.authMode.name, AuthMode.entries.map { it.name }) { onChange(ui.copy(authMode = AuthMode.valueOf(it))) }
                    if (ui.authMode == AuthMode.BEARER) OutlinedTextField(ui.bearerToken, { onChange(ui.copy(bearerToken = it)) }, Modifier.fillMaxWidth(), label = { Text("Bearer token") })
                    OutlinedTextField(ui.customHeadersRaw, { onChange(ui.copy(customHeadersRaw = it)) }, Modifier.fillMaxWidth().height(140.dp), label = { Text("Custom headers (Key: Value per line)") })
                    OutlinedTextField(ui.queryParamsRaw, { onChange(ui.copy(queryParamsRaw = it)) }, Modifier.fillMaxWidth().height(140.dp), label = { Text("Query params (key=value per line)") })
                    OutlinedTextField(ui.payloadTemplateRaw, { onChange(ui.copy(payloadTemplateRaw = it)) }, Modifier.fillMaxWidth().height(200.dp), label = { Text("Payload template (JSON with {title} {text} etc.)") })
                    Button(onSave, Modifier.fillMaxWidth()) { Text("Save Webhook Settings") }
                    Button(onTest, Modifier.fillMaxWidth()) { Text("Test Webhook") }
                }
            }
        }
    }
}

@Composable
private fun FilterScreen(modifier: Modifier, ui: UiSettings, onChange: (UiSettings) -> Unit, onSave: () -> Unit) {
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Filter & Retry", fontWeight = FontWeight.SemiBold)
                    DropdownSelector("Filter mode", ui.filterMode.name, FilterMode.entries.map { it.name }) { onChange(ui.copy(filterMode = FilterMode.valueOf(it))) }
                    if (ui.filterMode != FilterMode.ALL_APPS) {
                        FilterPackagePicker(SettingsStore.parsePackages(ui.filterPackagesRaw)) { selected -> onChange(ui.copy(filterPackagesRaw = selected.sorted().joinToString("\n"))) }
                    } else Text("All applications are forwarded. Saved package selections are preserved.")
                    OutlinedTextField(ui.maxRetriesRaw, { onChange(ui.copy(maxRetriesRaw = it.filter(Char::isDigit))) }, Modifier.fillMaxWidth(), label = { Text("Max retries") }, singleLine = true)
                    OutlinedTextField(ui.batchSizeRaw, { onChange(ui.copy(batchSizeRaw = it.filter(Char::isDigit))) }, Modifier.fillMaxWidth(), label = { Text("Batch size") }, singleLine = true)
                    Button(onSave, Modifier.fillMaxWidth()) { Text("Save Filter & Retry") }
                }
            }
        }
    }
}

@Composable
private fun QueueScreen(
    modifier: Modifier, recent: List<QueueItem>, stats: QueueStats,
    onRetryNow: () -> Unit, onRetryItem: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit, onClearQueue: () -> Unit
) {
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recent Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Pending deliveries: ${stats.pendingCount} · Failed: ${stats.failedCount}")
                    Button(onRetryNow, Modifier.fillMaxWidth(), enabled = stats.pendingCount + stats.failedCount > 0) { Text("Retry pending now") }
                    Button(onClearQueue, Modifier.fillMaxWidth(), enabled = recent.isNotEmpty()) { Text("Clear All Queue") }
                }
            }
        }
        items(recent, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(item.appName, fontWeight = FontWeight.SemiBold); QueueStatusBadge(item.status) }
                    Text(item.title.ifBlank { "(no title)" }); Text(item.text.ifBlank { "(no text)" }); Text(item.packageName)
                    Text("Attempt: ${item.attemptCount}")
                    if (!item.lastError.isNullOrBlank()) Text("Err: ${item.lastError}")
                    if (item.status == QueueStatus.PENDING || item.status == QueueStatus.FAILED) {
                        Button({ onRetryItem(item.id) }, Modifier.fillMaxWidth()) { Text("Retry now") }
                    } else if (item.status == QueueStatus.SENDING) {
                        Button({}, Modifier.fillMaxWidth(), enabled = false) { Text("Retrying...") }
                    }
                    Button({ onDeleteItem(item.id) }, Modifier.fillMaxWidth()) { Text("Delete This Queue") }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(value, {}, Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem({ Text(option) }, { onSelected(option); expanded = false }) } }
    }
}

@Composable
private fun StatusBadge(text: String, success: Boolean) {
    val container = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(999.dp)) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun NeutralBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(999.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QueueStatusBadge(status: QueueStatus) {
    val (container, content) = when (status) {
        QueueStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        QueueStatus.SENDING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        QueueStatus.SENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        QueueStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(999.dp)) { Text(status.name, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun QueueStatCard(modifier: Modifier, label: String, value: String, containerColor: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color) {
    Surface(modifier, color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
    }
}

private fun saveSettings(store: SettingsStore, ui: UiSettings) {
    store.webhookUrl = ui.webhookUrl; store.webhookMethod = ui.webhookMethod; store.forwardingEnabled = ui.forwardingEnabled
    store.filterMode = ui.filterMode; store.filterPackages = SettingsStore.parsePackages(ui.filterPackagesRaw); store.authMode = ui.authMode
    store.bearerToken = ui.bearerToken; store.customHeadersRaw = ui.customHeadersRaw; store.queryParamsRaw = ui.queryParamsRaw
    store.payloadTemplateRaw = ui.payloadTemplateRaw; store.maxRetries = ui.maxRetriesRaw.toIntOrNull() ?: 10; store.batchSize = ui.batchSizeRaw.toIntOrNull() ?: 20
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.contains(ComponentName(context, AppNotificationListenerService::class.java).flattenToString())
}

private fun parseKeyValuePairs(raw: String): Map<String, String> = buildMap {
    raw.lines().forEach { line ->
        val trimmed = line.trim(); val idx = trimmed.indexOf('=')
        if (idx > 0) { val key = trimmed.substring(0, idx).trim(); if (key.isNotEmpty()) put(key, trimmed.substring(idx + 1).trim()) }
    }
}

private fun buildHeadersPreview(authMode: AuthMode, token: String, raw: String): Map<String, String> = linkedMapOf<String, String>("Content-Type" to "application/json").apply {
    if (authMode == AuthMode.BEARER && token.isNotBlank()) put("Authorization", "Bearer $token")
    raw.lines().forEach { line -> val idx = line.indexOf(':'); if (idx > 0) put(line.substring(0, idx).trim(), line.substring(idx + 1).trim()) }
}

private fun requestBatteryUnrestricted(context: Context) {
    val primary = BackgroundReliabilityIntents.requestBatteryExemption(context.packageName)
    val fallback = BackgroundReliabilityIntents.batteryOptimizationSettings()
    launchSafely(context, primary, fallback)
}

private fun openBatterySettings(context: Context) {
    launchSafely(
        context,
        BackgroundReliabilityIntents.batteryOptimizationSettings(),
        BackgroundReliabilityIntents.appDetails(context.packageName)
    )
}

private fun openAppSettings(context: Context) {
    runCatching { context.startActivity(BackgroundReliabilityIntents.appDetails(context.packageName)) }
}

private fun launchSafely(context: Context, primary: Intent, fallback: Intent) {
    runCatching { context.startActivity(primary) }
        .onFailure { runCatching { context.startActivity(fallback) } }
}

private fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
