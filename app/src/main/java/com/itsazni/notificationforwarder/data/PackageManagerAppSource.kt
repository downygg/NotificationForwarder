package com.itsazni.notificationforwarder.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class VisibleApplication(
    val packageName: String,
    val label: String?,
    val isLauncher: Boolean = false,
    val isSystem: Boolean = false
)

class PackageManagerAppSource(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun getLauncherApplications(): List<VisibleApplication> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = runCatching { queryLauncherActivities(intent) }.getOrDefault(emptyList())

        return resolveInfos
            .mapNotNull { it.activityInfo?.applicationInfo }
            .map { applicationInfo -> applicationInfo.toVisibleApplication(isLauncher = true) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label ?: it.packageName })
    }

    fun getBroadInstalledApplications(): List<VisibleApplication> {
        val applications = runCatching { getInstalledApplications() }.getOrDefault(emptyList())
        return applications
            .map { applicationInfo -> applicationInfo.toVisibleApplication(isLauncher = false) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label ?: it.packageName })
    }

    fun getVisibleApplications(): List<VisibleApplication> = getBroadInstalledApplications()

    @Suppress("DEPRECATION")
    private fun queryLauncherActivities(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }

    @Suppress("DEPRECATION")
    private fun getInstalledApplications(): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            packageManager.getInstalledApplications(0)
        }
    }

    private fun ApplicationInfo.toVisibleApplication(isLauncher: Boolean): VisibleApplication {
        return VisibleApplication(
            packageName = packageName,
            label = resolveLabel(this),
            isLauncher = isLauncher,
            isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        )
    }

    private fun resolveLabel(applicationInfo: ApplicationInfo): String? {
        return runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString().takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
