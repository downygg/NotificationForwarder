package com.itsazni.notificationforwarder.worker

import android.content.Context
import android.provider.Settings
import com.itsazni.notificationforwarder.data.*
import com.itsazni.notificationforwarder.network.WebhookClient
import com.itsazni.notificationforwarder.settings.*

internal data class QueueProcessResult(val claimed:Boolean,val shouldRetryWorker:Boolean)
internal class QueueItemProcessor(context:Context,private val repository:NotificationRepository=NotificationRepository(context.applicationContext),private val settings:SettingsStore=SettingsStore(context.applicationContext),private val webhookClient:WebhookClient=WebhookClient()){
    private val appContext=context.applicationContext
    private val diagnostics=DiagnosticsRepository(appContext)
    suspend fun process(item:QueueItem,config:AppSettings):QueueProcessResult{
        val deviceId=Settings.Secure.getString(appContext.contentResolver,Settings.Secure.ANDROID_ID)?:"unknown-device"
        val headers=buildHeaders(config.authMode,config.bearerToken,settings.parseHeaders())
        return processWithAtomicClaim(item,repository::claimRetryableForSending,deliver={claimed->
            val attempt=claimed.attemptCount+1
            diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.WEBHOOK_ATTEMPT,category=DiagnosticCategory.WEBHOOK,queueItemId=claimed.id,sourcePackage=claimed.packageName,attemptNumber=attempt,message="Webhook delivery attempt started"))
            val result=webhookClient.send(config.webhookUrl,config.webhookMethod,headers,settings.parseQueryParams(),config.payloadTemplateRaw,claimed,deviceId)
            DeliveryOutcome(result.success,result.isPermanentFailure,result.message,result.httpStatus)
        },onSuccess={successful->
            repository.markSent(successful.id)
            diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.WEBHOOK_SUCCESS,category=DiagnosticCategory.WEBHOOK,queueItemId=successful.id,sourcePackage=successful.packageName,attemptNumber=successful.attemptCount+1,message="Webhook delivered successfully"))
        },onFailure={failed,outcome->
            val attempt=failed.attemptCount+1
            repository.markFailure(failed.id,attempt,config.maxRetries,outcome.permanentFailure,outcome.message)
            diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.WEBHOOK_FAILED,category=DiagnosticCategory.WEBHOOK,severity=DiagnosticSeverity.WARNING,queueItemId=failed.id,sourcePackage=failed.packageName,attemptNumber=attempt,httpStatus=outcome.httpStatus,failureReason=outcome.message,message="Webhook delivery failed"))
            val current=repository.getById(failed.id)
            if(current?.status==QueueStatus.PENDING&&current.nextRetryAt>0) diagnostics.record(DiagnosticEvent(type=DiagnosticEventType.RETRY_SCHEDULED,category=DiagnosticCategory.QUEUE,queueItemId=failed.id,sourcePackage=failed.packageName,attemptNumber=attempt,message="Automatic retry scheduled at ${current.nextRetryAt}"))
        })
    }
    private fun buildHeaders(authMode:AuthMode,bearerToken:String,custom:Map<String,String>)=linkedMapOf("Content-Type" to "application/json").apply{if(authMode==AuthMode.BEARER&&bearerToken.isNotBlank())this["Authorization"]="Bearer $bearerToken";putAll(custom)}
}
