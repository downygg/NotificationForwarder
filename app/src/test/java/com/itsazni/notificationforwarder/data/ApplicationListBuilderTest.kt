package com.itsazni.notificationforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationListBuilderTest {
    @Test
    fun unifiedListIncludesAllSourcesAndDeduplicates() {
        val result = ApplicationListBuilder.build(
            visibleApplications = listOf(
                VisibleApplication("com.whatsapp", "WhatsApp"),
                VisibleApplication("com.google.android.gm", "Gmail")
            ),
            discoveredPackages = setOf("com.example.bank", "com.whatsapp"),
            configuredPackages = setOf("com.hidden.payment")
        )

        assertEquals(4, result.size)
        assertEquals(4, result.map { it.packageName }.toSet().size)
        assertTrue(result.any { it.packageName == "com.example.bank" })
        assertTrue(result.any { it.packageName == "com.hidden.payment" })
    }

    @Test
    fun configuredPackageSurvivesWithoutMetadataWithFallbackLabel() {
        val item = ApplicationListBuilder.build(emptyList(), emptySet(), setOf("com.hidden.payment")).single()

        assertEquals("com.hidden.payment", item.packageName)
        assertEquals("Unknown application", item.displayLabel)
    }

    @Test
    fun visibleLabelIsUsed() {
        val item = ApplicationListBuilder.build(
            listOf(VisibleApplication("com.whatsapp", "WhatsApp")),
            emptySet(),
            emptySet()
        ).single()

        assertEquals("WhatsApp", item.displayLabel)
    }

    @Test
    fun searchMatchesLabelPackageAndIsCaseInsensitive() {
        val items = listOf(
            ApplicationItem("com.whatsapp", "WhatsApp"),
            ApplicationItem("com.example.bank", "Bank App")
        )

        assertEquals(listOf("com.whatsapp"), ApplicationListBuilder.search(items, "WHAT").map { it.packageName })
        assertEquals(listOf("com.whatsapp"), ApplicationListBuilder.search(items, "com.whatsapp").map { it.packageName })
        assertEquals(listOf("com.example.bank"), ApplicationListBuilder.search(items, "BANK").map { it.packageName })
        assertEquals(items, ApplicationListBuilder.search(items, ""))
    }

    @Test
    fun selectionAndManualAdditionUsePackageNamesOnly() {
        val existing = setOf("com.whatsapp")
        val added = ApplicationListBuilder.addManualPackage(existing, "  com.example.bank  ")

        assertEquals(setOf("com.whatsapp", "com.example.bank"), added)
        assertEquals(added, ApplicationListBuilder.addManualPackage(added, "com.example.bank"))
        assertEquals(added, ApplicationListBuilder.addManualPackage(added, "   "))
        assertEquals(setOf("com.example.bank"), added - "com.whatsapp")
    }
}
