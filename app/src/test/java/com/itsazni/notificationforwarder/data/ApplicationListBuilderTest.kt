package com.itsazni.notificationforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationListBuilderTest {
    @Test
    fun unifiedListIncludesLauncherBroadDiscoveredAndConfiguredSources() {
        val result = ApplicationListBuilder.build(
            launcherApplications = listOf(
                VisibleApplication("com.whatsapp", "WhatsApp", isLauncher = true)
            ),
            broadApplications = listOf(
                VisibleApplication("com.background.service", "Background Service", isSystem = false)
            ),
            discoveredPackages = setOf("com.example.bank"),
            configuredPackages = setOf("com.hidden.payment")
        )

        assertEquals(
            setOf("com.whatsapp", "com.background.service", "com.example.bank", "com.hidden.payment"),
            result.map { it.packageName }.toSet()
        )
    }

    @Test
    fun duplicatePackageAcrossAllSourcesIsDeduplicated() {
        val result = ApplicationListBuilder.build(
            launcherApplications = listOf(
                VisibleApplication("com.example.app", "Example", isLauncher = true)
            ),
            broadApplications = listOf(
                VisibleApplication("com.example.app", null, isSystem = false)
            ),
            discoveredPackages = setOf("com.example.app"),
            configuredPackages = setOf("com.example.app")
        )

        assertEquals(1, result.size)
        assertEquals("com.example.app", result.single().packageName)
    }

    @Test
    fun launcherMetadataWinsOverNullBroadMetadata() {
        val item = ApplicationListBuilder.build(
            launcherApplications = listOf(
                VisibleApplication("com.example.app", "Example App", isLauncher = true)
            ),
            broadApplications = listOf(
                VisibleApplication("com.example.app", null, isSystem = false)
            ),
            discoveredPackages = emptySet(),
            configuredPackages = emptySet()
        ).single()

        assertEquals("Example App", item.displayLabel)
        assertTrue(item.isLauncher)
    }

    @Test
    fun broadLabelFillsMissingLauncherLabel() {
        val item = ApplicationListBuilder.build(
            launcherApplications = listOf(
                VisibleApplication("com.example.app", null, isLauncher = true)
            ),
            broadApplications = listOf(
                VisibleApplication("com.example.app", "Example App", isSystem = false)
            ),
            discoveredPackages = emptySet(),
            configuredPackages = emptySet()
        ).single()

        assertEquals("Example App", item.displayLabel)
        assertTrue(item.isLauncher)
    }

    @Test
    fun configuredPackageSurvivesWithoutMetadataWithFallbackLabel() {
        val item = ApplicationListBuilder.build(
            launcherApplications = emptyList(),
            broadApplications = emptyList(),
            discoveredPackages = emptySet(),
            configuredPackages = setOf("com.hidden.payment")
        ).single()

        assertEquals("com.hidden.payment", item.packageName)
        assertEquals("Unknown application", item.displayLabel)
    }

    @Test
    fun notificationOnlyPackageAppears() {
        val item = ApplicationListBuilder.build(
            launcherApplications = emptyList(),
            broadApplications = emptyList(),
            discoveredPackages = setOf("com.notification.only"),
            configuredPackages = emptySet()
        ).single()

        assertEquals("com.notification.only", item.packageName)
        assertEquals("Unknown application", item.displayLabel)
    }

    @Test
    fun manualOnlyPackageAppearsImmediatelyAndIsSelectedByPackageName() {
        val selected = ApplicationListBuilder.addManualPackage(emptySet(), "  com.manual.only  ")
        val items = ApplicationListBuilder.ensurePackage(emptyList(), "  com.manual.only  ")

        assertEquals(setOf("com.manual.only"), selected)
        assertEquals("com.manual.only", items.single().packageName)
        assertTrue(items.single().packageName in selected)
    }

    @Test
    fun manualDuplicateHandlingRemainsIdempotent() {
        val initial = listOf(ApplicationItem("com.example.app", "Example", isLauncher = true))

        assertEquals(initial, ApplicationListBuilder.ensurePackage(initial, " com.example.app "))
        assertEquals(initial, ApplicationListBuilder.ensurePackage(initial, "   "))
        assertEquals(
            setOf("com.example.app"),
            ApplicationListBuilder.addManualPackage(setOf("com.example.app"), " com.example.app ")
        )
    }

    @Test
    fun userFacingAppsArePrioritizedAboveBroadUserAndSystemPackages() {
        val result = ApplicationListBuilder.build(
            launcherApplications = listOf(
                VisibleApplication("com.launcher.z", "Zulu", isLauncher = true)
            ),
            broadApplications = listOf(
                VisibleApplication("com.user.a", "Alpha User", isSystem = false),
                VisibleApplication("com.system.a", "Alpha System", isSystem = true)
            ),
            discoveredPackages = emptySet(),
            configuredPackages = emptySet()
        )

        assertEquals(
            listOf("com.launcher.z", "com.user.a", "com.system.a"),
            result.map { it.packageName }
        )
    }

    @Test
    fun orderingIsDeterministicWithinPriorityGroups() {
        val first = ApplicationListBuilder.build(
            launcherApplications = listOf(
                VisibleApplication("com.launcher.b", "Beta", isLauncher = true),
                VisibleApplication("com.launcher.a", "Alpha", isLauncher = true)
            ),
            broadApplications = listOf(
                VisibleApplication("com.system.b", "Beta", isSystem = true),
                VisibleApplication("com.user.b", "Beta", isSystem = false),
                VisibleApplication("com.user.a", "Alpha", isSystem = false),
                VisibleApplication("com.system.a", "Alpha", isSystem = true)
            ),
            discoveredPackages = emptySet(),
            configuredPackages = emptySet()
        )
        val second = ApplicationListBuilder.build(
            launcherApplications = listOf(
                VisibleApplication("com.launcher.a", "Alpha", isLauncher = true),
                VisibleApplication("com.launcher.b", "Beta", isLauncher = true)
            ),
            broadApplications = listOf(
                VisibleApplication("com.system.a", "Alpha", isSystem = true),
                VisibleApplication("com.user.a", "Alpha", isSystem = false),
                VisibleApplication("com.user.b", "Beta", isSystem = false),
                VisibleApplication("com.system.b", "Beta", isSystem = true)
            ),
            discoveredPackages = emptySet(),
            configuredPackages = emptySet()
        )

        assertEquals(first.map { it.packageName }, second.map { it.packageName })
        assertEquals(
            listOf(
                "com.launcher.a",
                "com.launcher.b",
                "com.user.a",
                "com.user.b",
                "com.system.a",
                "com.system.b"
            ),
            first.map { it.packageName }
        )
    }

    @Test
    fun searchMatchesLabelPackageAndIsCaseInsensitive() {
        val items = listOf(
            ApplicationItem("com.whatsapp", "WhatsApp", isLauncher = true),
            ApplicationItem("com.example.bank", "Bank App")
        )

        assertEquals(listOf("com.whatsapp"), ApplicationListBuilder.search(items, "WHAT").map { it.packageName })
        assertEquals(listOf("com.whatsapp"), ApplicationListBuilder.search(items, "com.whatsapp").map { it.packageName })
        assertEquals(listOf("com.example.bank"), ApplicationListBuilder.search(items, "BANK").map { it.packageName })
        assertEquals(items, ApplicationListBuilder.search(items, ""))
    }

    @Test
    fun legacyBuildPathStillPreservesPackageIdentity() {
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
}
