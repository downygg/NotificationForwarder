package com.itsazni.notificationforwarder.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class VisibleApplication(
    val packageName: String,
    val label: String?
)

class PackageManagerAppSource(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun getVisibleApplications(): List<VisibleApplication> {
        val applications = runCatching { getInstalledApplications() }.getOrDefault(emptyList())
        return applications
            .map { applicationInfo ->
                VisibleApplication(
                    packageName = applicationInfo.packageName,
                    label = resolveLabel(applicationInfo)
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label ?: it.packageName })
    }

    @Suppress("DEPRECATION")
    private fun getInstalledApplications(): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            packageManager.getInstalledApplications(0)
        }
    }

    private fun resolveLabel(applicationInfo: ApplicationInfo): String? {
        return runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString().takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
