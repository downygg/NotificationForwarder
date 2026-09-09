package com.itsazni.notificationforwarder.service

internal fun recordDiscoveryBeforeProcessing(
    packageName: String,
    recordPackage: (String) -> Unit,
    onDiscoveryFailure: (Throwable) -> Unit,
    processNotification: () -> Unit
) {
    runCatching { recordPackage(packageName) }
        .onFailure(onDiscoveryFailure)
    processNotification()
}
