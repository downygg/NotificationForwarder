package com.itsazni.notificationforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageDiscoveryStoreTest {
    @Test
    fun recordsNewPackage() {
        val store = PackageDiscoveryStore(InMemoryStorage())

        store.recordPackage("com.example.bank")

        assertEquals(setOf("com.example.bank"), store.getDiscoveredPackages())
    }

    @Test
    fun duplicateRecordDoesNotDuplicate() {
        val store = PackageDiscoveryStore(InMemoryStorage())

        store.recordPackage("com.example.bank")
        store.recordPackage("com.example.bank")

        assertEquals(setOf("com.example.bank"), store.getDiscoveredPackages())
    }

    @Test
    fun blankInputIsIgnored() {
        val store = PackageDiscoveryStore(InMemoryStorage())

        store.recordPackage("   ")

        assertTrue(store.getDiscoveredPackages().isEmpty())
    }

    @Test
    fun trimmedPackageIsPersisted() {
        val storage = InMemoryStorage()
        val store = PackageDiscoveryStore(storage)

        store.recordPackage("  com.example.bank  ")

        assertEquals(setOf("com.example.bank"), storage.read())
    }

    @Test
    fun multiplePackagesArePreserved() {
        val storage = InMemoryStorage()
        PackageDiscoveryStore(storage).recordPackage("com.example.bank")
        PackageDiscoveryStore(storage).recordPackage("com.whatsapp")

        assertEquals(
            setOf("com.example.bank", "com.whatsapp"),
            PackageDiscoveryStore(storage).getDiscoveredPackages()
        )
    }

    @Test
    fun discoveredPackagesSurviveStoreRecreation() {
        val storage = InMemoryStorage()
        PackageDiscoveryStore(storage).recordPackage("com.example.bank")

        val recreatedStore = PackageDiscoveryStore(storage)

        assertEquals(setOf("com.example.bank"), recreatedStore.getDiscoveredPackages())
    }

    @Test
    fun discoveryStorageDoesNotOverwriteConfiguredPackages() {
        val storage = InMemoryStorage()
        val configuredPackages = setOf("com.whatsapp")

        PackageDiscoveryStore(storage).recordPackage("com.example.bank")

        assertEquals(setOf("com.example.bank"), storage.read())
        assertEquals(setOf("com.whatsapp"), configuredPackages)
    }

    @Test
    fun removeWorks() {
        val storage = InMemoryStorage(setOf("com.example.bank", "com.whatsapp"))
        val store = PackageDiscoveryStore(storage)

        store.removeDiscoveredPackage(" com.example.bank ")

        assertEquals(setOf("com.whatsapp"), store.getDiscoveredPackages())
    }

    @Test
    fun clearWorks() {
        val storage = InMemoryStorage(setOf("com.example.bank", "com.whatsapp"))
        val store = PackageDiscoveryStore(storage)

        store.clearDiscoveredPackages()

        assertTrue(store.getDiscoveredPackages().isEmpty())
    }

    private class InMemoryStorage(initial: Set<String> = emptySet()) : PackageSetStorage {
        private var packages = initial.toSet()

        override fun read(): Set<String> = packages.toSet()

        override fun write(packages: Set<String>) {
            this.packages = packages.toSet()
        }
    }
}
