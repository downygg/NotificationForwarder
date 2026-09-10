package com.itsazni.notificationforwarder.reliability

enum class BatteryOptimizationState {
    UNRESTRICTED,
    OPTIMIZED
}

fun batteryOptimizationState(isIgnoringBatteryOptimizations: Boolean): BatteryOptimizationState {
    return if (isIgnoringBatteryOptimizations) {
        BatteryOptimizationState.UNRESTRICTED
    } else {
        BatteryOptimizationState.OPTIMIZED
    }
}

data class OemGuidance(
    val manufacturerLabel: String,
    val summary: String
)

object OemGuidanceResolver {
    fun resolve(manufacturer: String): OemGuidance {
        val normalized = manufacturer.trim().lowercase()
        return when {
            normalized.contains("samsung") -> OemGuidance(
                manufacturerLabel = "Samsung",
                summary = "Consider Battery → Background usage limits → Never sleeping apps, and set battery usage to Unrestricted where available."
            )
            normalized.contains("xiaomi") || normalized.contains("redmi") || normalized.contains("poco") -> OemGuidance(
                manufacturerLabel = "Xiaomi / Redmi / POCO",
                summary = "Enable Background autostart / Auto Start and set battery usage to Unrestricted where available."
            )
            normalized.contains("oppo") -> OemGuidance(
                manufacturerLabel = "Oppo / ColorOS",
                summary = "Enable Auto Launch, allow background activity, and set battery usage to Unrestricted where available."
            )
            normalized.contains("vivo") || normalized.contains("iqoo") -> OemGuidance(
                manufacturerLabel = "Vivo / iQOO",
                summary = "Enable Auto Start, allow background activity, and set battery optimization to Unrestricted where available."
            )
            normalized.contains("realme") -> OemGuidance(
                manufacturerLabel = "Realme",
                summary = "Enable Auto Launch, allow background activity, and set battery usage to Unrestricted where available."
            )
            normalized.contains("oneplus") -> OemGuidance(
                manufacturerLabel = "OnePlus",
                summary = "Allow background usage and set battery optimization to Don't optimize / Unrestricted where available."
            )
            else -> OemGuidance(
                manufacturerLabel = manufacturer.ifBlank { "Android device" },
                summary = "Check App info and Battery settings for Unrestricted background usage or Auto Start options if your device provides them."
            )
        }
    }
}

fun batteryExemptionPackageUri(packageName: String): String = "package:$packageName"
