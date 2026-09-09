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
    fun `per item retry has stable id scoped unique work name`() {
        assertEquals("queue_retry_item_123", QueueWorkPolicy.retryItem(123).uniqueWorkName)
        assertEquals(QueueWorkPolicy.retryItem(123).uniqueWorkName, QueueWorkPolicy.retryItem(123).uniqueWorkName)
        assertNotEquals(QueueWorkPolicy.retryItem(123).uniqueWorkName, QueueWorkPolicy.retryItem(124).uniqueWorkName)
    }

    @Test
    fun `all one time retry paths preserve exponential backoff base`() {
        assertEquals(30L, QueueWorkPolicy.automatic.backoffSeconds)
        assertEquals(QueueWorkPolicy.automatic.backoffSeconds, QueueWorkPolicy.manual.backoffSeconds)
        assertEquals(QueueWorkPolicy.automatic.backoffSeconds, QueueWorkPolicy.retryItem(123).backoffSeconds)
    }
}
