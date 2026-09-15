package com.itsazni.notificationforwarder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsazni.notificationforwarder.data.*
import com.itsazni.notificationforwarder.service.ListenerHealthState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DiagnosticsScreen(
    modifier: Modifier,
    events: List<DiagnosticEvent>,
    stats: QueueStats,
    notificationAccessGranted: Boolean,
    listenerHealth: ListenerHealthState
) {
    var filter by remember { mutableStateOf("All") }
    val filtered = remember(events, filter) {
        when (filter) {
            "Listener" -> events.filter { it.category == DiagnosticCategory.LISTENER }
            "Notifications" -> events.filter { it.category == DiagnosticCategory.NOTIFICATION }
            "Queue" -> events.filter { it.category == DiagnosticCategory.QUEUE }
            "Webhook" -> events.filter { it.category == DiagnosticCategory.WEBHOOK }
            "Errors" -> events.filter { it.severity == DiagnosticSeverity.ERROR || it.severity == DiagnosticSeverity.WARNING }
            else -> events
        }
    }
    LazyColumn(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Notification Access: ${if (notificationAccessGranted) "Granted" else "Not granted"}")
                    Text("Listener Status: ${listenerHealth.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    Text("Pending Queue: ${stats.pendingCount}")
                    Text("Failed Queue: ${stats.failedCount}")
                    Text("Persistent event log keeps the newest ${DiagnosticsRepository.MAX_EVENTS} events. Secrets, headers, tokens, payloads, and notification text are not copied into diagnostics.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Listener", "Notifications").forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Queue", "Webhook", "Errors").forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name) }) }
            }
        }
        items(filtered, key = { it.id }) { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(event.type.name, fontWeight = FontWeight.SemiBold)
                        Text(formatDiagnosticTime(event.timestamp), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${event.category.name} · ${event.severity.name}", style = MaterialTheme.typography.labelMedium)
                    event.queueItemId?.let { Text("Queue #$it", style = MaterialTheme.typography.bodySmall) }
                    event.sourcePackage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    event.attemptNumber?.let { Text("Attempt: $it", style = MaterialTheme.typography.bodySmall) }
                    event.httpStatus?.let { Text("HTTP: $it", style = MaterialTheme.typography.bodySmall) }
                    event.failureReason?.let { Text("Reason: $it", style = MaterialTheme.typography.bodySmall) }
                    event.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

fun formatDiagnosticTime(value: Long?): String {
    if (value == null || value <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(value))
}
