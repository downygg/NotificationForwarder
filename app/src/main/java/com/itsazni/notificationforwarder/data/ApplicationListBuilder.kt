package com.itsazni.notificationforwarder.data

data class ApplicationItem(
    val packageName: String,
    val label: String?,
    val isLauncher: Boolean = false,
    val isSystem: Boolean = false
) {
    val displayLabel: String
        get() = label?.takeIf { it.isNotBlank() } ?: "Unknown application"
}

object ApplicationListBuilder {
    fun build(
        launcherApplications: List<VisibleApplication>,
        broadApplications: List<VisibleApplication>,
        discoveredPackages: Set<String>,
        configuredPackages: Set<String>
    ): List<ApplicationItem> {
        val metadataByPackage = linkedMapOf<String, ApplicationItem>()

        fun merge(application: VisibleApplication) {
            val packageName = application.packageName.trim()
            if (packageName.isEmpty()) return
            val label = application.label?.trim()?.takeIf { it.isNotEmpty() }
            val existing = metadataByPackage[packageName]
            metadataByPackage[packageName] = ApplicationItem(
                packageName = packageName,
                label = existing?.label ?: label,
                isLauncher = existing?.isLauncher == true || application.isLauncher,
                isSystem = existing?.isSystem == true || application.isSystem
            )
        }

        launcherApplications.forEach(::merge)
        broadApplications.forEach(::merge)

        discoveredPackages.forEach { packageName ->
            val normalized = packageName.trim()
            if (normalized.isNotEmpty() && normalized !in metadataByPackage) {
                metadataByPackage[normalized] = ApplicationItem(normalized, null)
            }
        }
        configuredPackages.forEach { packageName ->
            val normalized = packageName.trim()
            if (normalized.isNotEmpty() && normalized !in metadataByPackage) {
                metadataByPackage[normalized] = ApplicationItem(normalized, null)
            }
        }

        return sort(metadataByPackage.values.toList())
    }

    fun build(
        visibleApplications: List<VisibleApplication>,
        discoveredPackages: Set<String>,
        configuredPackages: Set<String>
    ): List<ApplicationItem> {
        return build(
            launcherApplications = visibleApplications.filter { it.isLauncher },
            broadApplications = visibleApplications,
            discoveredPackages = discoveredPackages,
            configuredPackages = configuredPackages
        )
    }

    fun search(items: List<ApplicationItem>, query: String): List<ApplicationItem> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return items
        return items.filter {
            it.packageName.contains(normalized, ignoreCase = true) ||
                it.label?.contains(normalized, ignoreCase = true) == true
        }
    }

    fun addManualPackage(selected: Set<String>, input: String): Set<String> {
        val normalized = input.trim()
        return if (normalized.isEmpty()) selected else selected + normalized
    }

    fun ensurePackage(items: List<ApplicationItem>, packageName: String): List<ApplicationItem> {
        val normalized = packageName.trim()
        if (normalized.isEmpty() || items.any { it.packageName == normalized }) {
            return items
        }
        return sort(items + ApplicationItem(normalized, null))
    }

    private fun sort(items: List<ApplicationItem>): List<ApplicationItem> {
        return items.sortedWith(
            compareBy<ApplicationItem> { priority(it) }
                .thenBy { (it.label ?: it.packageName).lowercase() }
                .thenBy { it.packageName.lowercase() }
                .thenBy { it.packageName }
        )
    }

    private fun priority(item: ApplicationItem): Int {
        return when {
            item.isLauncher -> 0
            !item.isSystem -> 1
            else -> 2
        }
    }
}
