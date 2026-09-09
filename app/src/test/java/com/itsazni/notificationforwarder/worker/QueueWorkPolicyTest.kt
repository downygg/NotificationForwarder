package com.itsazni.notificationforwarder.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QueueWorkPolicyTest {
    @Test
    fun `manual retry uses its own unique work name`() {
        assertNotEquals(QueueWorkPolicy.automatic.uniqueWorkName, QueueWorkPolicy.manual.uniqueWorkName)
    }

    @Test
    fun `manual and automatic work preserve existing exponential backoff base`() {
        assertEquals(30L, QueueWorkPolicy.automatic.backoffSeconds)
        assertEquals(QueueWorkPolicy.automatic.backoffSeconds, QueueWorkPolicy.manual.backoffSeconds)
    }
}
