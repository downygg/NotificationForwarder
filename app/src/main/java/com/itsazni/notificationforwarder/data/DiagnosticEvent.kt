package com.itsazni.notificationforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DiagnosticSeverity { INFO, WARNING, ERROR }
enum class DiagnosticCategory { APP, LISTENER, NOTIFICATION, QUEUE, WEBHOOK, WORKER }
enum class DiagnosticEventType {
    APP_STARTED, BOOT_COMPLETED, LISTENER_CONNECTED, LISTENER_DISCONNECTED, LISTENER_REBIND_REQUESTED,
    NOTIFICATION_RECEIVED, FILTER_MATCHED, FILTER_REJECTED, QUEUE_CREATED,
    WEBHOOK_ATTEMPT, WEBHOOK_SUCCESS, WEBHOOK_FAILED, RETRY_SCHEDULED,
    MANUAL_RETRY_REQUESTED, RETRY_ALL_REQUESTED, WORKER_STARTED
}

@Entity(tableName = "diagnostic_events", indices = [Index("timestamp"), Index("queueItemId"), Index("category")])
data class DiagnosticEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: DiagnosticEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    val category: DiagnosticCategory,
    val queueItemId: Long? = null,
    val sourcePackage: String? = null,
    val ruleInfo: String? = null,
    val attemptNumber: Int? = null,
    val httpStatus: Int? = null,
    val failureReason: String? = null,
    val message: String? = null
)
