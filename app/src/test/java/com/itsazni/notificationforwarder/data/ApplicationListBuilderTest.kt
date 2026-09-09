package com.itsazni.notificationforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun visibleLabelIsUsedAndNotLostByDuplicateWithoutLabel() {
        val item = ApplicationListBuilder.build(
            listOf(
                VisibleApplication("com.whatsapp", "WhatsApp"),
                VisibleApplication("com.whatsapp", null)
            ),
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

    @Test
    fun manualPackageDuplicateAcrossAnySourceDoesNotDuplicateList() {
        val initial = ApplicationListBuilder.build(
            visibleApplications = listOf(VisibleApplication("com.visible.app", "Visible")),
            discoveredPackages = setOf("com.discovered.app"),
            configuredPackages = setOf("com.configured.app")
        )

        assertEquals(initial, ApplicationListBuilder.ensurePackage(initial, " com.visible.app "))
        assertEquals(initial, ApplicationListBuilder.ensurePackage(initial, "com.discovered.app"))
        assertEquals(initial, ApplicationListBuilder.ensurePackage(initial, "com.configured.app"))
        assertEquals(initial, ApplicationListBuilder.ensurePackage(initial, "   "))
    }

    @Test
    fun manualPackageAppearsImmediatelyAndCanRemainSelected() {
        val selected = ApplicationListBuilder.addManualPackage(emptySet(), " unusual.package_name-1 ")
        val items = ApplicationListBuilder.ensurePackage(emptyList(), " unusual.package_name-1 ")

        assertEquals(setOf("unusual.package_name-1"), selected)
        assertEquals("unusual.package_name-1", items.single().packageName)
        assertTrue(items.single().packageName in selected)
    }

    @Test
    fun refreshSourceRebuildAddsNewlyDiscoveredUnknownPackage() {
        val before = ApplicationListBuilder.build(emptyList(), emptySet(), emptySet())
        val after = ApplicationListBuilder.build(
            visibleApplications = emptyList(),
            discoveredPackages = setOf("com.example.new"),
            configuredPackages = emptySet()
        )

        assertTrue(before.isEmpty())
        val item = after.single()
        assertEquals("com.example.new", item.packageName)
        assertEquals("Unknown application", item.displayLabel)
    }

    @Test
    fun staleConfiguredPackageRemainsVisibleAndSelectedByPackageIdentity() {
        val configured = setOf("com.old.bank")
        val item = ApplicationListBuilder.build(
            visibleApplications = emptyList(),
            discoveredPackages = emptySet(),
            configuredPackages = configured
        ).single()

        assertEquals("Unknown application", item.displayLabel)
        assertTrue(item.packageName in configured)
        assertFalse(item.packageName.isBlank())
    }

    @Test
    fun orderingIsDeterministicByLabelThenPackageName() {
        val first = ApplicationListBuilder.build(
            visibleApplications = listOf(
                VisibleApplication("com.zeta.second", "Alpha"),
                VisibleApplication("com.zeta.first", "Alpha"),
                VisibleApplication("com.beta", "beta")
            ),
            discoveredPackages = setOf("com.unknown.z", "com.unknown.a"),
            configuredPackages = emptySet()
        )
        val second = ApplicationListBuilder.build(
            visibleApplications = listOf(
                VisibleApplication("com.beta", "beta"),
                VisibleApplication("com.zeta.first", "Alpha"),
                VisibleApplication("com.zeta.second", "Alpha")
            ),
            discoveredPackages = linkedSetOf("com.unknown.a", "com.unknown.z"),
            configuredPackages = emptySet()
        )

        assertEquals(first.map { it.packageName }, second.map { it.packageName })
        assertEquals(
            listOf("com.zeta.first", "com.zeta.second", "com.beta", "com.unknown.a", "com.unknown.z"),
            first.map { it.packageName }
        )
    }
}
