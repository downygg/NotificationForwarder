package com.itsazni.notificationforwarder.data

import android.content.Context
import com.itsazni.notificationforwarder.settings.PackageFilter
import com.itsazni.notificationforwarder.settings.SettingsStore

class NotificationRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).queueDao()
    private val settingsStore = SettingsStore(context)

    suspend fun enqueue(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        postedAt: Long,
        notificationKey: String
    ) {
        if (!settingsStore.forwardingEnabled) return
        if (!PackageFilter.shouldForward(packageName, settingsStore.filterMode, settingsStore.filterPackages)) return

        val now = System.currentTimeMillis()
        dao.insert(
            QueueItem(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                postedAt = postedAt,
                notificationKey = notificationKey,
                nextRetryAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun getPending(limit: Int): List<QueueItem> = dao.getPending(System.currentTimeMillis(), limit)

    suspend fun getById(id: Long): QueueItem? = dao.getById(id)

    suspend fun claimRetryableForSending(id: Long): Boolean =
        dao.claimRetryableForSending(id, System.currentTimeMillis()) == 1

    suspend fun recoverStaleSending(staleAfterMillis: Long): Int {
        val now = System.currentTimeMillis()
        return dao.recoverStaleSending(now - staleAfterMillis, now)
    }

    suspend fun makePendingAndFailedEligibleNow(): Int =
        dao.makePendingAndFailedEligibleNow(System.currentTimeMillis())

    suspend fun markSent(id: Long): Boolean = dao.markSent(id, System.currentTimeMillis()) == 1

    suspend fun markFailure(id: Long, attemptCount: Int, maxRetry: Int, lastError: String): Boolean {
        val failed = attemptCount >= maxRetry
        val delayMillis = if (failed) 0L else calculateBackoff(attemptCount)
        val now = System.currentTimeMillis()
        return dao.updateFailure(
            id = id,
            status = if (failed) QueueStatus.FAILED else QueueStatus.PENDING,
            attemptCount = attemptCount,
            nextRetryAt = now + delayMillis,
            lastError = lastError,
            updatedAt = now
        ) == 1
    }

    fun observeStats() = dao.observeStats()
    fun observeRecent(limit: Int) = dao.observeRecent(limit)

    suspend fun deleteQueueItem(id: Long) = dao.deleteById(id)
    suspend fun clearQueue() = dao.clearAll()

    private fun calculateBackoff(attemptCount: Int): Long {
        val base = 30_000L
        val exponential = base * (1L shl (attemptCount.coerceAtMost(6)))
        val jitter = (0..4_000).random().toLong()
        return exponential + jitter
    }
}
