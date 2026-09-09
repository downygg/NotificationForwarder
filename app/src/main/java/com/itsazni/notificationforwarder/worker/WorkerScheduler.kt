package com.itsazni.notificationforwarder.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    internal const val QUEUE_SYNC_WORK = "queue_sync_work"
    internal const val QUEUE_MANUAL_RETRY_WORK = "queue_manual_retry_work"
    private const val QUEUE_PERIODIC_WORK = "queue_periodic_work"
    internal const val BACKOFF_SECONDS = 30L

    fun enqueueImmediate(context: Context) {
        enqueueOneTime(context, QUEUE_SYNC_WORK)
    }

    fun enqueueManualRetry(context: Context) {
        enqueueOneTime(context, QUEUE_MANUAL_RETRY_WORK)
    }

    private fun enqueueOneTime(context: Context, uniqueWorkName: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun ensurePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodic = PeriodicWorkRequestBuilder<QueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            QUEUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }
}
