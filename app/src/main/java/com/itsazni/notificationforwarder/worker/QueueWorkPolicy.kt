package com.itsazni.notificationforwarder.worker

internal data class QueueWorkSpec(
    val uniqueWorkName: String,
    val backoffSeconds: Long
)

internal object QueueWorkPolicy {
    const val BACKOFF_SECONDS = 30L
    private const val RETRY_ITEM_PREFIX = "queue_retry_item_"

    val automatic = QueueWorkSpec(
        uniqueWorkName = "queue_sync_work",
        backoffSeconds = BACKOFF_SECONDS
    )

    val manual = QueueWorkSpec(
        uniqueWorkName = "queue_manual_retry_work",
        backoffSeconds = BACKOFF_SECONDS
    )

    fun retryItem(queueItemId: Long) = QueueWorkSpec(
        uniqueWorkName = "$RETRY_ITEM_PREFIX$queueItemId",
        backoffSeconds = BACKOFF_SECONDS
    )
}
