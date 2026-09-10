package com.itsazni.notificationforwarder.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerHealthTest {
    @Test
    fun `listener health starts unknown`() {
        val tracker = ListenerHealthTracker()

        assertEquals(ListenerHealthState.UNKNOWN, tracker.state.value)
    }

    @Test
    fun `connected callback transitions to connected without rebind`() {
        val tracker = ListenerHealthTracker(ListenerHealthState.DISCONNECTED)
        var rebindRequested = false

        tracker.markConnected()

        assertEquals(ListenerHealthState.CONNECTED, tracker.state.value)
        assertFalse(rebindRequested)
    }

    @Test
    fun `disconnected callback transitions and requests rebind once`() {
        val tracker = ListenerHealthTracker(ListenerHealthState.CONNECTED)
        var rebindCount = 0

        tracker.markDisconnected(requestRebind = { rebindCount++ })

        assertEquals(ListenerHealthState.DISCONNECTED, tracker.state.value)
        assertEquals(1, rebindCount)
    }

    @Test
    fun `rebind failure is reported without escaping`() {
        val tracker = ListenerHealthTracker(ListenerHealthState.CONNECTED)
        var failureReported = false

        tracker.markDisconnected(
            requestRebind = { error("rebind unavailable") },
            onFailure = { failureReported = true }
        )

        assertEquals(ListenerHealthState.DISCONNECTED, tracker.state.value)
        assertTrue(failureReported)
    }
}
