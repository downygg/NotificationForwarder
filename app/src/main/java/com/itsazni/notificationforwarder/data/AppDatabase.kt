package com.itsazni.notificationforwarder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [QueueItem::class, DiagnosticEvent::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
    abstract fun diagnosticsDao(): DiagnosticsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_queue ADD COLUMN capturedAt INTEGER")
                db.execSQL("ALTER TABLE notification_queue ADD COLUMN firstAttemptAt INTEGER")
                db.execSQL("ALTER TABLE notification_queue ADD COLUMN lastAttemptAt INTEGER")
                db.execSQL("ALTER TABLE notification_queue ADD COLUMN sentAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS diagnostic_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, timestamp INTEGER NOT NULL, severity TEXT NOT NULL, category TEXT NOT NULL, queueItemId INTEGER, sourcePackage TEXT, ruleInfo TEXT, attemptNumber INTEGER, httpStatus INTEGER, failureReason TEXT, message TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_events_timestamp ON diagnostic_events(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_events_queueItemId ON diagnostic_events(queueItemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_events_category ON diagnostic_events(category)")
            }
        }
        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "notif_forwarder.db")
                .addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
