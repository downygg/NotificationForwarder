package com.itsazni.notificationforwarder.worker

import android.content.Context
import android.provider.Settings
import com.itsazni.notificationforwarder.data.NotificationRepository
import com.itsazni.notificationforwarder.data.QueueItem
import com.itsazni.notificationforwarder.network.WebhookClient
import com.itsazni.notificationforwarder.settings.AppSettings
import com.itsazni.notificationforwarder.settings.AuthMode
import com.itsazni.notificationforwarder.settings.SettingsStore

internal data class QueueProcessResult(
    val claimed: Boolean,
    val shouldRetryWorker: Boolean
)

internal class QueueItemProcessor(
    context: Context,
    private val repository: NotificationRepository = NotificationRepository(context.applicationContext),
    private val settings: SettingsStore = SettingsStore(context.applicationContext),
    private val webhookClient: WebhookClient = WebhookClient()
) {
    private val appContext = context.applicationContext

    suspend fun process(item: QueueItem, config: AppSettings): QueueProcessResult {
        val deviceId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
        val headers = buildHeaders(config.authMode, config.bearerToken, settings.parseHeaders())

        return processWithAtomicClaim(
            item = item,
            claim = repository::claimRetryableForSending,
            deliver = { claimedItem ->
                val result = webhookClient.send(
                    url = config.webhookUrl,
                    method = config.webhookMethod,
                    headers = headers,
                    queryParams = settings.parseQueryParams(),
                    payloadTemplate = config.payloadTemplateRaw,
                    item = claimedItem,
                    deviceId = deviceId
                )
                DeliveryOutcome(result.success, result.isPermanentFailure)
            },
            onSuccess = { repository.markSent(it.id) },
            onFailure = { failedItem, outcome ->
                val attempt = failedItem.attemptCount + 1
                repository.markFailure(
                    id = failedItem.id,
                    attemptCount = if (outcome.permanentFailure) config.maxRetries else attempt,
                    maxRetry = config.maxRetries,
                    lastError = if (outcome.permanentFailure) "Permanent webhook failure" else "Webhook delivery failed"
                )
            }
        )
    }

    private fun buildHeaders(
        authMode: AuthMode,
        bearerToken: String,
        customHeaders: Map<String, String>
    ): Map<String, String> {
        val finalHeaders = linkedMapOf("Content-Type" to "application/json")
        if (authMode == AuthMode.BEARER && bearerToken.isNotBlank()) {
            finalHeaders["Authorization"] = "Bearer $bearerToken"
        }
        finalHeaders.putAll(customHeaders)
        return finalHeaders
    }
}
