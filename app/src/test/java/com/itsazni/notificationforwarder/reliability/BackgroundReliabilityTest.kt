package com.itsazni.notificationforwarder.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReliabilityTest {
    @Test
    fun `battery optimization true maps to unrestricted`() {
        assertEquals(
            BatteryOptimizationState.UNRESTRICTED,
            batteryOptimizationState(true)
        )
    }

    @Test
    fun `battery optimization false maps to optimized`() {
        assertEquals(
            BatteryOptimizationState.OPTIMIZED,
            batteryOptimizationState(false)
        )
    }

    @Test
    fun `notification access is granted when package is enabled`() {
        assertTrue(
            notificationAccessGranted(
                "com.itsazni.notificationforwarder",
                setOf("com.example.other", "com.itsazni.notificationforwarder"),
                listenerConnected = false
            )
        )
    }

    @Test
    fun `connected listener proves notification access despite package api false negative`() {
        assertTrue(
            notificationAccessGranted(
                "com.itsazni.notificationforwarder",
                emptySet(),
                listenerConnected = true
            )
        )
    }

    @Test
    fun `notification access is not granted for a different package when listener disconnected`() {
        assertFalse(
            notificationAccessGranted(
                "com.itsazni.notificationforwarder",
                setOf("com.itsazni.notificationforwarder.fake", "com.example.other"),
                listenerConnected = false
            )
        )
    }

    @Test
    fun `notification access does not use substring matching`() {
        assertFalse(
            notificationAccessGranted(
                "com.example.app",
                setOf("com.example.application"),
                listenerConnected = false
            )
        )
    }

    @Test
    fun `blank package never reports granted even when listener flag is true`() {
        assertFalse(notificationAccessGranted("", setOf(""), listenerConnected = true))
    }

    @Test
    fun `battery exemption package uri contains package name`() {
        assertEquals(
            "package:com.itsazni.notificationforwarder",
            batteryExemptionPackageUri("com.itsazni.notificationforwarder")
        )
    }

    @Test
    fun `unsupported direct exemption falls back safely`() {
        var fallbackCalled = false

        val launched = attemptWithFallback(
            primary = { error("unsupported") },
            fallback = { fallbackCalled = true }
        )

        assertTrue(launched)
        assertTrue(fallbackCalled)
    }

    @Test
    fun `successful direct exemption does not use fallback`() {
        var fallbackCalled = false

        val launched = attemptWithFallback(
            primary = {},
            fallback = { fallbackCalled = true }
        )

        assertTrue(launched)
        assertFalse(fallbackCalled)
    }

    @Test
    fun `manufacturer resolver handles supported vendors`() {
        val samsung = OemGuidanceResolver.resolve("Samsung")
        val xiaomi = OemGuidanceResolver.resolve("Xiaomi")
        val oppo = OemGuidanceResolver.resolve("OPPO")
        val vivo = OemGuidanceResolver.resolve("vivo")
        val realme = OemGuidanceResolver.resolve("realme")
        val onePlus = OemGuidanceResolver.resolve("OnePlus")

        assertEquals("Samsung", samsung.manufacturerLabel)
        assertEquals("Xiaomi / Redmi / POCO", xiaomi.manufacturerLabel)
        assertEquals("Oppo / ColorOS", oppo.manufacturerLabel)
        assertEquals("Vivo / iQOO", vivo.manufacturerLabel)
        assertEquals("Realme", realme.manufacturerLabel)
        assertEquals("OnePlus", onePlus.manufacturerLabel)
    }

    @Test
    fun `unknown manufacturer falls back to generic guidance`() {
        val guidance = OemGuidanceResolver.resolve("ExampleVendor")

        assertEquals("ExampleVendor", guidance.manufacturerLabel)
        assertTrue(guidance.summary.contains("App info"))
    }
}
