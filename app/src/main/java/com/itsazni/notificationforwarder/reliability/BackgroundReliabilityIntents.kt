package com.itsazni.notificationforwarder.reliability

import android.content.Intent
import android.net.Uri
import android.provider.Settings

object BackgroundReliabilityIntents {
    fun requestBatteryExemption(packageName: String): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse(batteryExemptionPackageUri(packageName))
        )
    }

    fun batteryOptimizationSettings(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    fun appDetails(packageName: String): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    }
}
