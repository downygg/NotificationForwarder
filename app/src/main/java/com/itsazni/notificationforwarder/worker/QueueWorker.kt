package com.itsazni.notificationforwarder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.itsazni.notificationforwarder.data.NotificationRepository
import com.itsazni.notificationforwarder.settings.SettingsStore

class QueueWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    private val repository = NotificationRepository(appContext)
    private val settings = SettingsStore(appContext)
    private val processor = QueueItemProcessor(appContext, repository, settings)

    override suspend fun doWork(): Result {
        val config = settings.readAll()
        if (!config.forwardingEnabled || config.webhookUrl.isBlank()) return Result.success()

        repository.recoverStaleSending(STALE_SENDING_MILLIS)

        val targetItemId = inputData.getLong(INPUT_QUEUE_ITEM_ID, NO_ITEM_ID)
        if (targetItemId != NO_ITEM_ID) {
            val item = repository.getById(targetItemId) ?: return Result.success()
            processor.process(item, config)
            return Result.success()
        }

        val retryAll = inputData.getBoolean(INPUT_RETRY_ALL, false)
        var shouldRetry = false

        do {
            val items = repository.getPending(config.batchSize)
            if (items.isEmpty()) break

            items.forEach { item ->
                val result = processor.process(item, config)
                shouldRetry = shouldRetry || result.shouldRetryWorker
            }
        } while (retryAll)

        return if (shouldRetry) Result.retry() else Result.success()
    }

    companion object {
        const val INPUT_QUEUE_ITEM_ID = "queue_item_id"
        const val INPUT_RETRY_ALL = "retry_all"
        const val NO_ITEM_ID = -1L
        const val STALE_SENDING_MILLIS = 5 * 60 * 1000L
    }
}
