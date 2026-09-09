package com.itsazni.notificationforwarder.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    private const val QUEUE_PERIODIC_WORK = "queue_periodic_work"
    private const val RETRY_ITEM_PREFIX = "queue_retry_item_"

    fun enqueueImmediate(context: Context) {
        enqueueOneTime(context, QueueWorkPolicy.automatic, Data.EMPTY)
    }

    fun enqueueManualRetry(context: Context) {
        val data = Data.Builder().putBoolean(QueueWorker.INPUT_RETRY_ALL, true).build()
        enqueueOneTime(context, QueueWorkPolicy.manual, data)
    }

    fun enqueueItemRetry(context: Context, queueItemId: Long) {
        val constraints = networkConstraints()
        val data = Data.Builder().putLong(QueueWorker.INPUT_QUEUE_ITEM_ID, queueItemId).build()
        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, QueueWorkPolicy.BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$RETRY_ITEM_PREFIX$queueItemId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun enqueueOneTime(context: Context, spec: QueueWorkSpec, data: Data) {
        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setInputData(data)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, spec.backoffSeconds, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            spec.uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun ensurePeriodic(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<QueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            QUEUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
