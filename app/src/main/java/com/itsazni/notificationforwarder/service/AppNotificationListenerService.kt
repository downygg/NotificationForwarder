package com.itsazni.notificationforwarder.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.itsazni.notificationforwarder.data.*
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import kotlinx.coroutines.*

class AppNotificationListenerService:NotificationListenerService(){
 private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private data class RecentEvent(val contentHash:Int,val postedAt:Long,val seenAt:Long)
 private val dedupLock=Any();private val recentEvents=LinkedHashMap<String,RecentEvent>(MAX_RECENT_EVENTS,.75f,true)
 private fun log(event:DiagnosticEvent){serviceScope.launch{DiagnosticsRepository(applicationContext).record(event)}}
 override fun onListenerConnected(){super.onListenerConnected();ListenerHealth.markConnected();log(DiagnosticEvent(type=DiagnosticEventType.LISTENER_CONNECTED,category=DiagnosticCategory.LISTENER,message="Notification listener connected"));Log.i(TAG,"Notification listener connected")}
 override fun onListenerDisconnected(){super.onListenerDisconnected();log(DiagnosticEvent(type=DiagnosticEventType.LISTENER_DISCONNECTED,category=DiagnosticCategory.LISTENER,severity=DiagnosticSeverity.WARNING,message="Notification listener disconnected"));ListenerHealth.markDisconnected(requestRebind={log(DiagnosticEvent(type=DiagnosticEventType.LISTENER_REBIND_REQUESTED,category=DiagnosticCategory.LISTENER,message="Android listener rebind requested"));NotificationListenerService.requestRebind(ComponentName(this,AppNotificationListenerService::class.java))},onFailure={Log.w(TAG,"Failed to request notification listener rebind",it)})}
 override fun onNotificationPosted(sbn:StatusBarNotification?){super.onNotificationPosted(sbn);val item=sbn?:return;if(item.packageName==packageName)return;val capturedAt=System.currentTimeMillis();log(DiagnosticEvent(type=DiagnosticEventType.NOTIFICATION_RECEIVED,timestamp=capturedAt,category=DiagnosticCategory.NOTIFICATION,sourcePackage=item.packageName,message="Notification callback received"));recordDiscoveryBeforeProcessing(item.packageName,{PackageDiscoveryStore(applicationContext).recordPackage(it)},{Log.w(TAG,"Failed to record discovered package: ${item.packageName}",it)},{processNotification(item,capturedAt)})}
 private fun processNotification(item:StatusBarNotification,capturedAt:Long){val n=item.notification;val e=n.extras;val title=e?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty();val text=e?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty();val big=e?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty();val skip=skipReason(item,n,title,text,big);if(skip!=null){log(DiagnosticEvent(type=DiagnosticEventType.FILTER_REJECTED,category=DiagnosticCategory.NOTIFICATION,sourcePackage=item.packageName,failureReason=skip,message="Notification skipped before queue"));return};serviceScope.launch{NotificationRepository(applicationContext).enqueue(item.packageName,resolveAppName(item.packageName),title,text,item.postTime,item.key,capturedAt)?.let{WorkerScheduler.enqueueImmediate(applicationContext)}}}
 private fun resolveAppName(pkg:String)=runCatching{packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)
 private fun skipReason(sbn:StatusBarNotification,n:Notification,title:String,text:String,big:String):String?{if((n.flags and Notification.FLAG_GROUP_SUMMARY)!=0)return "group_summary";val key=buildStableKey(sbn);val hash=listOf(title,text,big).joinToString("\u001f").hashCode();val now=System.currentTimeMillis();synchronized(dedupLock){val p=recentEvents[key];if(p!=null&&p.contentHash==hash&&(p.postedAt==sbn.postTime||now-p.seenAt<=DUPLICATE_WINDOW_MS))return "duplicate_notification";recentEvents[key]=RecentEvent(hash,sbn.postTime,now);while(recentEvents.size>MAX_RECENT_EVENTS)recentEvents.remove(recentEvents.entries.first().key)};return null}
 private fun buildStableKey(sbn:StatusBarNotification)=sbn.key.ifBlank{"${sbn.packageName}|${sbn.id}|${sbn.tag?:""}|${if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR1)sbn.user.hashCode() else "legacy-user"}"}
 companion object{private const val TAG="NotifForwarderListener";private const val DUPLICATE_WINDOW_MS=500L;private const val MAX_RECENT_EVENTS=512}
}
