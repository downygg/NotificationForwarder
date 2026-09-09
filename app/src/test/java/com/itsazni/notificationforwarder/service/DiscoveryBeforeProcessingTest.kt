package com.itsazni.notificationforwarder.service

import com.itsazni.notificationforwarder.settings.FilterMode
import com.itsazni.notificationforwarder.settings.PackageFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryBeforeProcessingTest {
    @Test
    fun discoveryOccursBeforeRejectedFilterAndQueueActionIsNotInvoked() {
        val events = mutableListOf<String>()
        val discoveredPackages = mutableSetOf<String>()
        var queueActionInvoked = false

        recordDiscoveryBeforeProcessing(
            packageName = "com.example.bank",
            recordPackage = { packageName ->
                events += "discover"
                discoveredPackages += packageName
            },
            onDiscoveryFailure = { error -> throw AssertionError(error) },
            processNotification = {
                events += "filter"
                val shouldForward = PackageFilter.shouldForward(
                    packageName = "com.example.bank",
                    filterMode = FilterMode.WHITELIST,
                    configuredPackages = setOf("com.whatsapp")
                )
                if (shouldForward) {
                    queueActionInvoked = true
                }
            }
        )

        assertEquals(listOf("discover", "filter"), events)
        assertTrue("com.example.bank" in discoveredPackages)
        assertFalse(queueActionInvoked)
    }

    @Test
    fun discoveryFailureDoesNotPreventExistingProcessing() {
        var failureReported = false
        var processingContinued = false

        recordDiscoveryBeforeProcessing(
            packageName = "com.example.bank",
            recordPackage = { error("storage failed") },
            onDiscoveryFailure = { failureReported = true },
            processNotification = { processingContinued = true }
        )

        assertTrue(failureReported)
        assertTrue(processingContinued)
    }
}
