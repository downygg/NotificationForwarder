package com.itsazni.notificationforwarder.data

import android.content.Context
import androidx.core.content.edit

internal interface PackageSetStorage {
    fun read(): Set<String>
    fun write(packages: Set<String>)
}

private class SharedPreferencesPackageSetStorage(context: Context) : PackageSetStorage {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun read(): Set<String> {
        return prefs.getStringSet(KEY_DISCOVERED_PACKAGES, emptySet()).orEmpty().toSet()
    }

    override fun write(packages: Set<String>) {
        prefs.edit { putStringSet(KEY_DISCOVERED_PACKAGES, packages.toSet()) }
    }

    companion object {
        private const val PREFS_NAME = "package_discovery"
        private const val KEY_DISCOVERED_PACKAGES = "discovered_packages"
    }
}

class PackageDiscoveryStore internal constructor(
    private val storage: PackageSetStorage
) {
    constructor(context: Context) : this(SharedPreferencesPackageSetStorage(context))

    fun recordPackage(packageName: String) {
        val normalized = normalizePackageName(packageName) ?: return
        synchronized(lock) {
            val current = storage.read()
            if (normalized in current) {
                return
            }
            storage.write(current + normalized)
        }
    }

    fun getDiscoveredPackages(): Set<String> {
        return synchronized(lock) { storage.read().mapNotNull(::normalizePackageName).toSet() }
    }

    fun removeDiscoveredPackage(packageName: String) {
        val normalized = normalizePackageName(packageName) ?: return
        synchronized(lock) {
            val current = storage.read()
            if (normalized !in current) {
                return
            }
            storage.write(current - normalized)
        }
    }

    fun clearDiscoveredPackages() {
        synchronized(lock) {
            if (storage.read().isNotEmpty()) {
                storage.write(emptySet())
            }
        }
    }

    companion object {
        private val lock = Any()

        private fun normalizePackageName(packageName: String): String? {
            return packageName.trim().takeIf { it.isNotEmpty() }
        }
    }
}
