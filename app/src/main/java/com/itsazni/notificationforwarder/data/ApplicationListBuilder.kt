package com.itsazni.notificationforwarder.data

data class ApplicationItem(
    val packageName: String,
    val label: String?
) {
    val displayLabel: String
        get() = label?.takeIf { it.isNotBlank() } ?: "Unknown application"
}

object ApplicationListBuilder {
    fun build(
        visibleApplications: List<VisibleApplication>,
        discoveredPackages: Set<String>,
        configuredPackages: Set<String>
    ): List<ApplicationItem> {
        val labelsByPackage = visibleApplications
            .filter { it.packageName.isNotBlank() }
            .associate { it.packageName.trim() to it.label?.takeIf(String::isNotBlank) }

        val packageNames = linkedSetOf<String>()
        visibleApplications.mapTo(packageNames) { it.packageName.trim() }
        discoveredPackages.mapTo(packageNames) { it.trim() }
        configuredPackages.mapTo(packageNames) { it.trim() }

        return packageNames
            .filter { it.isNotEmpty() }
            .distinct()
            .map { packageName -> ApplicationItem(packageName, labelsByPackage[packageName]) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label ?: it.packageName })
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
}
