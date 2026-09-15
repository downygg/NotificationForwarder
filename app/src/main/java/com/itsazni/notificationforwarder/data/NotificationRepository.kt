package com.itsazni.notificationforwarder.data

import android.content.Context
import com.itsazni.notificationforwarder.settings.PackageFilter
import com.itsazni.notificationforwarder.settings.SettingsStore

class NotificationRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).queueDao()
    private val diagnostics = DiagnosticsRepository(context)
    private val settingsStore = SettingsStore(context)

    suspend fun enqueue(packageName: String, appName: String, title: String, text: String, postedAt: Long, notificationKey: String, capturedAt: Long): Long? {
        if (!settingsStore.forwardingEnabled) {
            diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.FILTER_REJECTED, category=DiagnosticCategory.NOTIFICATION, sourcePackage=packageName, failureReason="forwarding_disabled", message="Forwarding is disabled"))
            return null
        }
        if (!PackageFilter.shouldForward(packageName, settingsStore.filterMode, settingsStore.filterPackages)) {
            diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.FILTER_REJECTED, category=DiagnosticCategory.NOTIFICATION, sourcePackage=packageName, ruleInfo=settingsStore.filterMode.name, failureReason="package_filter_rejected", message="Package did not match forwarding filter"))
            return null
        }
        diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.FILTER_MATCHED, category=DiagnosticCategory.NOTIFICATION, sourcePackage=packageName, ruleInfo=settingsStore.filterMode.name))
        val now = System.currentTimeMillis()
        val id = dao.insert(QueueItem(packageName=packageName, appName=appName, title=title, text=text, postedAt=postedAt, notificationKey=notificationKey, nextRetryAt=now, createdAt=now, updatedAt=now, capturedAt=capturedAt))
        if (id > 0) diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.QUEUE_CREATED, category=DiagnosticCategory.QUEUE, queueItemId=id, sourcePackage=packageName, message="Notification accepted into delivery queue"))
        return id.takeIf { it > 0 }
    }

    suspend fun getPending(limit: Int) = dao.getPending(System.currentTimeMillis(), limit)
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun claimRetryableForSending(id: Long) = dao.claimRetryableForSending(id, System.currentTimeMillis()) == 1
    suspend fun recoverStaleSending(staleAfterMillis: Long): Int { val now=System.currentTimeMillis(); return dao.recoverStaleSending(now-staleAfterMillis, now) }
    suspend fun makePendingAndFailedEligibleNow() = dao.makePendingAndFailedEligibleNow(System.currentTimeMillis())
    suspend fun markSent(id: Long) = dao.markSent(id, System.currentTimeMillis()) == 1

    suspend fun markFailure(id: Long, attemptCount: Int, maxRetry: Int, permanentFailure: Boolean, lastError: String): Boolean {
        val failed = permanentFailure || attemptCount >= maxRetry
        val delay = if (failed) 0L else calculateBackoff(attemptCount)
        val now = System.currentTimeMillis()
        return dao.updateFailure(id, if (failed) QueueStatus.FAILED else QueueStatus.PENDING, if (failed) 0 else now+delay, lastError, now) == 1
    }
    fun observeStats()=dao.observeStats(); fun observeRecent(limit:Int)=dao.observeRecent(limit)
    suspend fun deleteQueueItem(id:Long)=dao.deleteById(id); suspend fun clearQueue()=dao.clearAll()
    private fun calculateBackoff(attemptCount:Int):Long { val base=30_000L; return base*(1L shl attemptCount.coerceAtMost(6))+(0..4_000).random() }
}
