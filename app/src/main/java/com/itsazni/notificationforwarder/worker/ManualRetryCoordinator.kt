package com.itsazni.notificationforwarder.worker

import android.content.Context
import com.itsazni.notificationforwarder.data.NotificationRepository

class ManualRetryCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val repository = NotificationRepository(appContext)

    suspend fun retryNow(): Int {
        val eligibleCount = repository.makePendingAndFailedEligibleNow()
        if (eligibleCount > 0) {
            WorkerScheduler.enqueueManualRetry(appContext)
        }
        return eligibleCount
    }
}
