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
        if (!repository.claimRetryableForSending(item.id)) {
            return QueueProcessResult(claimed = false, shouldRetryWorker = false)
        }

        val deviceId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
        val headers = buildHeaders(config.authMode, config.bearerToken, settings.parseHeaders())
        val result = webhookClient.send(
            url = config.webhookUrl,
            method = config.webhookMethod,
            headers = headers,
            queryParams = settings.parseQueryParams(),
            payloadTemplate = config.payloadTemplateRaw,
            item = item,
            deviceId = deviceId
        )

        if (result.success) {
            repository.markSent(item.id)
            return QueueProcessResult(claimed = true, shouldRetryWorker = false)
        }

        val attempt = item.attemptCount + 1
        repository.markFailure(
            id = item.id,
            attemptCount = if (result.isPermanentFailure) config.maxRetries else attempt,
            maxRetry = config.maxRetries,
            lastError = result.message
        )
        return QueueProcessResult(
            claimed = true,
            shouldRetryWorker = !result.isPermanentFailure
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
