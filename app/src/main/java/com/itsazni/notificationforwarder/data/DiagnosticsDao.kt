package com.itsazni.notificationforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticsDao {
    @Insert suspend fun insert(event: DiagnosticEvent): Long
    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DiagnosticEvent>>
    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    suspend fun pruneToNewest(keep: Int): Int
    @Query("SELECT MAX(timestamp) FROM diagnostic_events WHERE type = :type")
    fun observeLastTimestamp(type: DiagnosticEventType): Flow<Long?>
}
