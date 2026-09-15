package com.itsazni.notificationforwarder.data

import android.content.Context
import android.util.Log

class DiagnosticsRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context.applicationContext).diagnosticsDao()

    suspend fun record(event: DiagnosticEvent) {
        runCatching {
            dao.insert(event)
            if ((event.id + event.timestamp) % PRUNE_EVERY == 0L) dao.pruneToNewest(MAX_EVENTS)
        }.onFailure { Log.w(TAG, "Diagnostic logging failed", it) }
    }

    fun observeRecent(limit: Int = 200) = dao.observeRecent(limit)
    fun observeLastTimestamp(type: DiagnosticEventType) = dao.observeLastTimestamp(type)
    suspend fun prune() = runCatching { dao.pruneToNewest(MAX_EVENTS) }

    companion object {
        const val MAX_EVENTS = 3000
        private const val PRUNE_EVERY = 50L
        private const val TAG = "DiagnosticsRepository"
    }
}
