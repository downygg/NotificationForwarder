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
        val labelsByPackage = linkedMapOf<String, String?>()
        visibleApplications.forEach { application ->
            val packageName = application.packageName.trim()
            if (packageName.isEmpty()) return@forEach
            val label = application.label?.trim()?.takeIf { it.isNotEmpty() }
            if (packageName !in labelsByPackage || (labelsByPackage[packageName] == null && label != null)) {
                labelsByPackage[packageName] = label
            }
        }

        val packageNames = linkedSetOf<String>()
        visibleApplications.mapTo(packageNames) { it.packageName.trim() }
        discoveredPackages.mapTo(packageNames) { it.trim() }
        configuredPackages.mapTo(packageNames) { it.trim() }

        return sort(
            packageNames
                .filter { it.isNotEmpty() }
                .distinct()
                .map { packageName -> ApplicationItem(packageName, labelsByPackage[packageName]) }
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
            compareBy<ApplicationItem> { (it.label ?: it.packageName).lowercase() }
                .thenBy { it.packageName.lowercase() }
                .thenBy { it.packageName }
        )
    }
}
