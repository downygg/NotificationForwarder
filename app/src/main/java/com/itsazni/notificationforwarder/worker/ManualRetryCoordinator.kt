package com.itsazni.notificationforwarder.worker
import android.content.Context
import com.itsazni.notificationforwarder.data.*
class ManualRetryCoordinator(context:Context){private val appContext=context.applicationContext;private val repository=NotificationRepository(appContext);private val diagnostics=DiagnosticsRepository(appContext)
 suspend fun retryNow():Int{val count=repository.makePendingAndFailedEligibleNow();diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.RETRY_ALL_REQUESTED,category=DiagnosticCategory.QUEUE,message="Retry All requested for $count eligible items"));if(count>0)WorkerScheduler.enqueueManualRetry(appContext);return count}
 suspend fun retryItemNow(id:Long):Boolean{val item=repository.getById(id)?:return false;if(item.status!=QueueStatus.PENDING&&item.status!=QueueStatus.FAILED)return false;diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.MANUAL_RETRY_REQUESTED,category=DiagnosticCategory.QUEUE,queueItemId=id,sourcePackage=item.packageName,message="Manual retry requested"));WorkerScheduler.enqueueItemRetry(appContext,id);return true}}
