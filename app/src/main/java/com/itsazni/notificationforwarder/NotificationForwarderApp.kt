package com.itsazni.notificationforwarder
import android.app.Application
import com.itsazni.notificationforwarder.data.*
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import kotlinx.coroutines.*
class NotificationForwarderApp:Application(){private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);override fun onCreate(){super.onCreate();WorkerScheduler.ensurePeriodic(this);scope.launch{DiagnosticsRepository(this@NotificationForwarderApp).apply{record(DiagnosticEvent(type=DiagnosticEventType.APP_STARTED,category=DiagnosticCategory.APP,message="Application process started"));prune()}}}}
