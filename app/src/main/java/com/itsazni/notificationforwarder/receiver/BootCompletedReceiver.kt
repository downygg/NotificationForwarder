package com.itsazni.notificationforwarder.receiver
import android.content.*
import com.itsazni.notificationforwarder.data.*
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import kotlinx.coroutines.*
class BootCompletedReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action==Intent.ACTION_BOOT_COMPLETED||intent.action==Intent.ACTION_LOCKED_BOOT_COMPLETED){WorkerScheduler.ensurePeriodic(context);WorkerScheduler.enqueueImmediate(context);val pending=goAsync();CoroutineScope(Dispatchers.IO).launch{try{DiagnosticsRepository(context).record(DiagnosticEvent(type=DiagnosticEventType.BOOT_COMPLETED,category=DiagnosticCategory.APP,message="Boot recovery handled: ${intent.action}"))}finally{pending.finish()}}}}}
