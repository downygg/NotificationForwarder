package com.itsazni.notificationforwarder.settings

object PackageFilter {
    fun shouldForward(
        packageName: String,
        filterMode: FilterMode,
        configuredPackages: Set<String>
    ): Boolean {
        return when (filterMode) {
            FilterMode.ALL_APPS -> true
            FilterMode.WHITELIST -> configuredPackages.contains(packageName)
            FilterMode.BLACKLIST -> !configuredPackages.contains(packageName)
        }
    }
}
