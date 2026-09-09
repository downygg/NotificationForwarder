package com.itsazni.notificationforwarder.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterConfigurationBehaviorTest {
    @Test
    fun whitelistToAllAppsToWhitelistPreservesPackages() {
        val initial = settings(FilterMode.WHITELIST, setOf("com.bank.app"))

        val allApps = initial.copy(filterMode = FilterMode.ALL_APPS)
        val restored = allApps.copy(filterMode = FilterMode.WHITELIST)

        assertEquals(setOf("com.bank.app"), allApps.filterPackages)
        assertEquals(initial.filterPackages, restored.filterPackages)
    }

    @Test
    fun blacklistToAllAppsToBlacklistPreservesPackages() {
        val initial = settings(FilterMode.BLACKLIST, setOf("com.noisy.app"))

        val allApps = initial.copy(filterMode = FilterMode.ALL_APPS)
        val restored = allApps.copy(filterMode = FilterMode.BLACKLIST)

        assertEquals(setOf("com.noisy.app"), allApps.filterPackages)
        assertEquals(initial.filterPackages, restored.filterPackages)
    }

    @Test
    fun whitelistAndBlacklistSwitchesDoNotChangeConfiguredPackages() {
        val initial = settings(FilterMode.WHITELIST, setOf("com.one", "com.two"))

        val blacklist = initial.copy(filterMode = FilterMode.BLACKLIST)
        val whitelist = blacklist.copy(filterMode = FilterMode.WHITELIST)

        assertEquals(initial.filterPackages, blacklist.filterPackages)
        assertEquals(initial.filterPackages, whitelist.filterPackages)
    }

    @Test
    fun manualSelectionRoundTripUsesExistingPackageFormat() {
        val selected = setOf("com.whatsapp", "com.example.bank")
        val persisted = selected.joinToString(",")
        val reloaded = SettingsStore.parsePackages(persisted)

        assertEquals(selected, reloaded)
        assertTrue("com.example.bank" in reloaded)
    }

    private fun settings(mode: FilterMode, packages: Set<String>): AppSettings {
        return AppSettings(
            webhookUrl = "",
            webhookMethod = "POST",
            forwardingEnabled = true,
            filterMode = mode,
            filterPackages = packages,
            authMode = AuthMode.NONE,
            bearerToken = "",
            customHeadersRaw = "",
            queryParamsRaw = "",
            payloadTemplateRaw = "",
            maxRetries = 10,
            batchSize = 20
        )
    }
}
