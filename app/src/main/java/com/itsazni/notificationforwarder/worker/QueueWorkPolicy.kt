package com.itsazni.notificationforwarder.worker

internal data class QueueWorkSpec(
    val uniqueWorkName: String,
    val backoffSeconds: Long
)

internal object QueueWorkPolicy {
    const val BACKOFF_SECONDS = 30L

    val automatic = QueueWorkSpec(
        uniqueWorkName = "queue_sync_work",
        backoffSeconds = BACKOFF_SECONDS
    )

    val manual = QueueWorkSpec(
        uniqueWorkName = "queue_manual_retry_work",
        backoffSeconds = BACKOFF_SECONDS
    )
}
