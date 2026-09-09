package com.itsazni.notificationforwarder.worker

import android.content.Context
import com.itsazni.notificationforwarder.data.NotificationRepository
import com.itsazni.notificationforwarder.data.QueueStatus

class ManualRetryCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val repository = NotificationRepository(appContext)

    suspend fun retryNow(): Int {
        val eligibleCount = repository.makePendingAndFailedEligibleNow()
        if (eligibleCount > 0) WorkerScheduler.enqueueManualRetry(appContext)
        return eligibleCount
    }

    suspend fun retryItemNow(queueItemId: Long): Boolean {
        val item = repository.getById(queueItemId) ?: return false
        if (item.status != QueueStatus.PENDING && item.status != QueueStatus.FAILED) return false
        WorkerScheduler.enqueueItemRetry(appContext, queueItemId)
        return true
    }
}
