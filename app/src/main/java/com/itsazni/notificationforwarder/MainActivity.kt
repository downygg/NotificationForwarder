package com.itsazni.notificationforwarder

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itsazni.notificationforwarder.data.*
import com.itsazni.notificationforwarder.network.WebhookClient
import com.itsazni.notificationforwarder.reliability.*
import com.itsazni.notificationforwarder.service.ListenerHealth
import com.itsazni.notificationforwarder.service.ListenerHealthState
import com.itsazni.notificationforwarder.settings.*
import com.itsazni.notificationforwarder.ui.DiagnosticsScreen
import com.itsazni.notificationforwarder.ui.FilterPackagePicker
import com.itsazni.notificationforwarder.ui.formatDiagnosticTime
import com.itsazni.notificationforwarder.ui.theme.AppTheme
import com.itsazni.notificationforwarder.worker.ManualRetryCoordinator
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val label:String,val icon:ImageVector){HOME("Home",Icons.Filled.Home),WEBHOOK("Webhook",Icons.Filled.Link),FILTER("Filter",Icons.Filled.Tune),QUEUE("Queue",Icons.AutoMirrored.Filled.List),DIAGNOSTICS("Diagnostics",Icons.Filled.Info)}
private data class UiSettings(val webhookUrl:String,val webhookMethod:String,val forwardingEnabled:Boolean,val filterMode:FilterMode,val filterPackagesRaw:String,val authMode:AuthMode,val bearerToken:String,val customHeadersRaw:String,val queryParamsRaw:String,val payloadTemplateRaw:String,val maxRetriesRaw:String,val batchSizeRaw:String)
private fun AppSettings.toUiSettings()=UiSettings(webhookUrl,webhookMethod,forwardingEnabled,filterMode,filterPackages.joinToString("\n"),authMode,bearerToken,customHeadersRaw,queryParamsRaw,payloadTemplateRaw,maxRetries.toString(),batchSize.toString())

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val settings=SettingsStore(this);setContent{AppTheme{MainScreen(settings)}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MainScreen(settingsStore:SettingsStore){
 val context=LocalContext.current;val lifecycleOwner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
 val repository=remember{NotificationRepository(context)};val diagnostics=remember{DiagnosticsRepository(context)};val retries=remember{ManualRetryCoordinator(context)};val snackbar=remember{SnackbarHostState()}
 var selectedTab by remember{mutableStateOf(AppTab.HOME)};var ui by remember{mutableStateOf(settingsStore.readAll().toUiSettings())};var access by remember{mutableStateOf(isNotificationListenerEnabled(context))};var battery by remember{mutableStateOf(batteryOptimizationState(isBatteryUnrestricted(context)))}
 val listener by ListenerHealth.state.collectAsState();val guidance=remember{OemGuidanceResolver.resolve(Build.MANUFACTURER.orEmpty())};val stats by repository.observeStats().collectAsState(initial=QueueStats(0,0,0,0));val recent by repository.observeRecent(30).collectAsState(initial=emptyList());val events by diagnostics.observeRecent().collectAsState(initial=emptyList())
 DisposableEffect(lifecycleOwner,context){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){access=isNotificationListenerEnabled(context);battery=batteryOptimizationState(isBatteryUnrestricted(context))}};lifecycleOwner.lifecycle.addObserver(observer);onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}}
 Scaffold(topBar={TopAppBar(title={Text("Notification Forwarder")})},snackbarHost={SnackbarHost(snackbar)},bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=0.dp){AppTab.entries.forEach{tab->NavigationBarItem(selectedTab==tab,{selectedTab=tab},{Icon(tab.icon,tab.label)},label={Text(tab.label)})}}}){padding->
  val modifier=Modifier.fillMaxSize().padding(padding)
  when(selectedTab){
   AppTab.HOME->HomeScreen(modifier,stats,access,listener,battery,guidance,{runCatching{context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}},{requestBatteryUnrestricted(context)},{openBatterySettings(context)},{openAppSettings(context)},{WorkerScheduler.enqueueImmediate(context)})
   AppTab.WEBHOOK->WebhookScreen(modifier,ui,{ui=it},{saveSettings(settingsStore,ui);WorkerScheduler.enqueueImmediate(context);scope.launch{snackbar.showSnackbar("Webhook settings saved")}}){scope.launch{val result=withContext(Dispatchers.IO){WebhookClient().send(ui.webhookUrl,ui.webhookMethod,buildHeadersPreview(ui.authMode,ui.bearerToken,ui.customHeadersRaw),parseKeyValuePairs(ui.queryParamsRaw),ui.payloadTemplateRaw,QueueItem(packageName="com.test.package",appName="Webhook Test",title="Test Notification",text="This is a test payload",postedAt=System.currentTimeMillis(),notificationKey="test-${System.currentTimeMillis()}"),"test-device")};snackbar.showSnackbar(if(result.success)"Webhook test success" else "Webhook test failed: ${result.message}")}}
   AppTab.FILTER->FilterScreen(modifier,ui,{ui=it}){saveSettings(settingsStore,ui);scope.launch{snackbar.showSnackbar("Filter & retry settings saved")}}
   AppTab.QUEUE->QueueScreen(modifier,recent,stats,{scope.launch{val count=retries.retryNow();snackbar.showSnackbar(if(count>0)"Retry requested for $count deliveries" else "No pending or failed deliveries")}},{id->scope.launch{val ok=retries.retryItemNow(id);snackbar.showSnackbar(if(ok)"Retry requested" else "Item is no longer retryable")}},{id->scope.launch{repository.deleteQueueItem(id);snackbar.showSnackbar("Queue item deleted")}},{scope.launch{repository.clearQueue();snackbar.showSnackbar("Queue cleared")}})
   AppTab.DIAGNOSTICS->DiagnosticsScreen(modifier,events,stats,access,listener)
  }
 }
 LaunchedEffect(Unit){WorkerScheduler.ensurePeriodic(context)}
}

@Composable private fun HomeScreen(modifier:Modifier,stats:QueueStats,notificationAccessGranted:Boolean,listenerHealth:ListenerHealthState,batteryState:BatteryOptimizationState,oemGuidance:OemGuidance,onOpenNotificationAccess:()->Unit,onRequestUnrestricted:()->Unit,onOpenBatterySettings:()->Unit,onOpenAppSettings:()->Unit,onSyncQueue:()->Unit){LazyColumn(modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Background Reliability",fontWeight=FontWeight.SemiBold);StatusRow("Notification Access",if(notificationAccessGranted)"Granted" else "Not granted",notificationAccessGranted);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Notification Listener");when(listenerHealth){ListenerHealthState.CONNECTED->StatusBadge("Connected",true);ListenerHealthState.DISCONNECTED->StatusBadge("Disconnected",false);ListenerHealthState.UNKNOWN->NeutralBadge("Unknown")}};StatusRow("Battery",if(batteryState==BatteryOptimizationState.UNRESTRICTED)"Unrestricted" else "Optimized",batteryState==BatteryOptimizationState.UNRESTRICTED);StatusRow("Boot Recovery","Enabled",true);if(!notificationAccessGranted)Button(onOpenNotificationAccess,Modifier.fillMaxWidth()){Text("Grant Notification Access")}else Button(onOpenNotificationAccess,Modifier.fillMaxWidth()){Text("Open Notification Access Settings")};if(batteryState==BatteryOptimizationState.OPTIMIZED)Button(onRequestUnrestricted,Modifier.fillMaxWidth()){Text("Request Unrestricted")};Button(onOpenBatterySettings,Modifier.fillMaxWidth()){Text("Open Battery Settings")};Text("${oemGuidance.manufacturerLabel} guidance",fontWeight=FontWeight.SemiBold);Text(oemGuidance.summary,style=MaterialTheme.typography.bodySmall);Button(onOpenAppSettings,Modifier.fillMaxWidth()){Text("Open App Settings")};Button(onSyncQueue,Modifier.fillMaxWidth()){Text("Sync Queue")}}}}
 item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Queue Summary",fontWeight=FontWeight.SemiBold);Text("Pending ${stats.pendingCount} · Sending ${stats.sendingCount} · Sent ${stats.sentCount} · Failed ${stats.failedCount}")}}}
}}
@Composable private fun StatusRow(label:String,value:String,ok:Boolean){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);StatusBadge(value,ok)}}

@Composable private fun WebhookScreen(modifier:Modifier,ui:UiSettings,onChange:(UiSettings)->Unit,onSave:()->Unit,onTest:()->Unit){LazyColumn(modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Webhook Settings",fontWeight=FontWeight.SemiBold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Enable forwarding");Switch(ui.forwardingEnabled,{onChange(ui.copy(forwardingEnabled=it))})};OutlinedTextField(ui.webhookUrl,{onChange(ui.copy(webhookUrl=it))},Modifier.fillMaxWidth(),label={Text("Webhook URL")},singleLine=true);DropdownSelector("HTTP method",ui.webhookMethod,listOf("GET","POST","PUT","PATCH")){onChange(ui.copy(webhookMethod=it))};DropdownSelector("Auth mode",ui.authMode.name,AuthMode.entries.map{it.name}){onChange(ui.copy(authMode=AuthMode.valueOf(it)))};if(ui.authMode==AuthMode.BEARER)OutlinedTextField(ui.bearerToken,{onChange(ui.copy(bearerToken=it))},Modifier.fillMaxWidth(),label={Text("Bearer token")});OutlinedTextField(ui.customHeadersRaw,{onChange(ui.copy(customHeadersRaw=it))},Modifier.fillMaxWidth().height(120.dp),label={Text("Custom headers (Key: Value per line)")});OutlinedTextField(ui.queryParamsRaw,{onChange(ui.copy(queryParamsRaw=it))},Modifier.fillMaxWidth().height(120.dp),label={Text("Query params (key=value per line)")});OutlinedTextField(ui.payloadTemplateRaw,{onChange(ui.copy(payloadTemplateRaw=it))},Modifier.fillMaxWidth().height(180.dp),label={Text("Payload template (JSON)")});Text("Variables: {deviceId}, {packageName}, {appName}, {title}, {text}, {postedAt}, {notificationKey}",style=MaterialTheme.typography.bodySmall);Button(onSave,Modifier.fillMaxWidth()){Text("Save Webhook Settings")};Button(onTest,Modifier.fillMaxWidth()){Text("Test Webhook")}}}}}}

@Composable private fun FilterScreen(modifier:Modifier,ui:UiSettings,onChange:(UiSettings)->Unit,onSave:()->Unit){LazyColumn(modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Filter & Retry",fontWeight=FontWeight.SemiBold);DropdownSelector("Filter mode",ui.filterMode.name,FilterMode.entries.map{it.name}){onChange(ui.copy(filterMode=FilterMode.valueOf(it)))};if(ui.filterMode!=FilterMode.ALL_APPS)FilterPackagePicker(SettingsStore.parsePackages(ui.filterPackagesRaw)){selected->onChange(ui.copy(filterPackagesRaw=selected.sorted().joinToString("\n")))}else Text("All applications are forwarded. Saved package selections are preserved.");OutlinedTextField(ui.maxRetriesRaw,{onChange(ui.copy(maxRetriesRaw=it.filter(Char::isDigit)))},Modifier.fillMaxWidth(),label={Text("Max retries")});OutlinedTextField(ui.batchSizeRaw,{onChange(ui.copy(batchSizeRaw=it.filter(Char::isDigit)))},Modifier.fillMaxWidth(),label={Text("Batch size")});Button(onSave,Modifier.fillMaxWidth()){Text("Save Filter & Retry")}}}}}}

@Composable private fun QueueScreen(modifier:Modifier,recent:List<QueueItem>,stats:QueueStats,onRetryNow:()->Unit,onRetryItem:(Long)->Unit,onDeleteItem:(Long)->Unit,onClearQueue:()->Unit){LazyColumn(modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Recent Queue",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text("Pending deliveries: ${stats.pendingCount} · Failed: ${stats.failedCount}");Button(onRetryNow,Modifier.fillMaxWidth(),enabled=stats.pendingCount+stats.failedCount>0){Text("Retry pending now")};Button(onClearQueue,Modifier.fillMaxWidth(),enabled=recent.isNotEmpty()){Text("Clear All Queue")}}}}
 items(recent,key={it.id}){item->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(item.appName,fontWeight=FontWeight.SemiBold);QueueStatusBadge(item.status)};Text(item.title.ifBlank{"(no title)"});Text(item.text.ifBlank{"(no text)"});Text(item.packageName,style=MaterialTheme.typography.bodySmall);HorizontalDivider();TimingRow("Captured",item.capturedAt);TimingRow("Queued",item.createdAt);TimingRow("First Attempt",item.firstAttemptAt);TimingRow("Last Attempt",item.lastAttemptAt);TimingRow("Sent",item.sentAt);TimingRow("Next Retry",item.nextRetryAt.takeIf{item.status==QueueStatus.PENDING&&it>0});Text("Attempts: ${item.attemptCount}");if(!item.lastError.isNullOrBlank())Text("Err: ${item.lastError}");if(item.status==QueueStatus.PENDING||item.status==QueueStatus.FAILED)Button({onRetryItem(item.id)},Modifier.fillMaxWidth()){Text("Retry now")}else if(item.status==QueueStatus.SENDING)Button({},Modifier.fillMaxWidth(),enabled=false){Text("Retrying...")};Button({onDeleteItem(item.id)},Modifier.fillMaxWidth()){Text("Delete This Queue")}}}}
 item{Spacer(Modifier.height(20.dp))}
}}
@Composable private fun TimingRow(label:String,value:Long?){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.bodySmall);Text(formatDiagnosticTime(value),style=MaterialTheme.typography.bodySmall)}}

@OptIn(ExperimentalMaterial3Api::class) @Composable private fun DropdownSelector(label:String,value:String,options:List<String>,onSelected:(String)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded,{expanded=!expanded}){OutlinedTextField(value,{},Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)});DropdownMenu(expanded,{expanded=false}){options.forEach{option->DropdownMenuItem({Text(option)},{onSelected(option);expanded=false})}}}}
@Composable private fun StatusBadge(text:String,success:Boolean){val c=if(success)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer;val t=if(success)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer;Surface(color=c,contentColor=t,shape=RoundedCornerShape(999.dp)){Text(text,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium)}}
@Composable private fun NeutralBadge(text:String){Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(999.dp)){Text(text,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium)}}
@Composable private fun QueueStatusBadge(status:QueueStatus){val pair=when(status){QueueStatus.PENDING->MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer;QueueStatus.SENDING->MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer;QueueStatus.SENT->MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer;QueueStatus.FAILED->MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer};Surface(color=pair.first,contentColor=pair.second,shape=RoundedCornerShape(999.dp)){Text(status.name,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium)}}
private fun saveSettings(store:SettingsStore,ui:UiSettings){store.webhookUrl=ui.webhookUrl;store.webhookMethod=ui.webhookMethod;store.forwardingEnabled=ui.forwardingEnabled;store.filterMode=ui.filterMode;store.filterPackages=SettingsStore.parsePackages(ui.filterPackagesRaw);store.authMode=ui.authMode;store.bearerToken=ui.bearerToken;store.customHeadersRaw=ui.customHeadersRaw;store.queryParamsRaw=ui.queryParamsRaw;store.payloadTemplateRaw=ui.payloadTemplateRaw;store.maxRetries=ui.maxRetriesRaw.toIntOrNull()?:10;store.batchSize=ui.batchSizeRaw.toIntOrNull()?:20}
private fun isNotificationListenerEnabled(context:Context)=notificationAccessGranted(context.packageName,NotificationManagerCompat.getEnabledListenerPackages(context))
private fun parseKeyValuePairs(raw:String):Map<String,String> = buildMap{raw.lines().forEach{line->val s=line.trim();val i=s.indexOf('=');if(i>0)put(s.substring(0,i).trim(),s.substring(i+1).trim())}}
private fun buildHeadersPreview(mode:AuthMode,token:String,raw:String):Map<String,String> = linkedMapOf<String,String>("Content-Type" to "application/json").apply{if(mode==AuthMode.BEARER&&token.isNotBlank())put("Authorization","Bearer $token");raw.lines().forEach{line->val i=line.indexOf(':');if(i>0)put(line.substring(0,i).trim(),line.substring(i+1).trim())}}
private fun requestBatteryUnrestricted(context:Context)=launchSafely(context,BackgroundReliabilityIntents.requestBatteryExemption(context.packageName),BackgroundReliabilityIntents.batteryOptimizationSettings())
private fun openBatterySettings(context:Context)=launchSafely(context,BackgroundReliabilityIntents.batteryOptimizationSettings(),BackgroundReliabilityIntents.appDetails(context.packageName))
private fun openAppSettings(context:Context){runCatching{context.startActivity(BackgroundReliabilityIntents.appDetails(context.packageName))}}
private fun launchSafely(context:Context,primary:Intent,fallback:Intent){runCatching{context.startActivity(primary)}.onFailure{runCatching{context.startActivity(fallback)}}}
private fun isBatteryUnrestricted(context:Context):Boolean{val pm=context.getSystemService(Context.POWER_SERVICE) as? PowerManager?:return false;return pm.isIgnoringBatteryOptimizations(context.packageName)}