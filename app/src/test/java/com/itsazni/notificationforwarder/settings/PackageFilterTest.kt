package com.itsazni.notificationforwarder.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageFilterTest {
    @Test
    fun allAppsAcceptsArbitraryPackage() {
        assertTrue(PackageFilter.shouldForward("com.example.bank", FilterMode.ALL_APPS, emptySet()))
    }

    @Test
    fun whitelistAcceptsConfiguredPackage() {
        assertTrue(
            PackageFilter.shouldForward(
                "com.whatsapp",
                FilterMode.WHITELIST,
                setOf("com.whatsapp")
            )
        )
    }

    @Test
    fun whitelistRejectsPackageOutsideList() {
        assertFalse(
            PackageFilter.shouldForward(
                "com.example.bank",
                FilterMode.WHITELIST,
                setOf("com.whatsapp")
            )
        )
    }

    @Test
    fun blacklistRejectsConfiguredPackage() {
        assertFalse(
            PackageFilter.shouldForward(
                "com.example.bank",
                FilterMode.BLACKLIST,
                setOf("com.example.bank")
            )
        )
    }

    @Test
    fun blacklistAcceptsPackageOutsideList() {
        assertTrue(
            PackageFilter.shouldForward(
                "com.whatsapp",
                FilterMode.BLACKLIST,
                setOf("com.example.bank")
            )
        )
    }

    @Test
    fun emptyPackageListPreservesExistingBehavior() {
        assertTrue(PackageFilter.shouldForward("com.example.bank", FilterMode.ALL_APPS, emptySet()))
        assertFalse(PackageFilter.shouldForward("com.example.bank", FilterMode.WHITELIST, emptySet()))
        assertTrue(PackageFilter.shouldForward("com.example.bank", FilterMode.BLACKLIST, emptySet()))
    }
}
