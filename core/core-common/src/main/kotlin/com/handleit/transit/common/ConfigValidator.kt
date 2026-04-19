package com.handleit.transit.common


/**
 * Validates TransitConfig at app startup.
 *
 * DEBUG builds  → throws IllegalStateException pointing to TransitConfig.kt
 * RELEASE builds → logs warning, does not crash
 *
 * Call ConfigValidator.validate(BuildConfig.DEBUG) in Application.onCreate()
 */
object ConfigValidator {

    private val PLACEHOLDERS = listOf("YOUR_", "PLACEHOLDER", "TODO", "EXAMPLE")

    fun validate(isDebug: Boolean) {
        val errors = mutableListOf<String>()

        // Only validate Maps key if using Google Maps
        if (TransitConfig.MAP_PROVIDER_DEFAULT == MapProvider.GOOGLE) {
            check(
                value = TransitConfig.MAPS_API_KEY,
                name = "MAPS_API_KEY",
                hint = "Required for Google Maps. Get one at console.cloud.google.com " +
                       "or switch MAP_PROVIDER_DEFAULT to MapProvider.OSM",
                errors = errors,
            )
        }

        check(
            value = TransitConfig.GTFS_RT_VEHICLE_POSITIONS_URL,
            name = "GTFS_RT_VEHICLE_POSITIONS_URL",
            hint = "Pre-filled with LYNX URL. Only change if using a different agency.",
            errors = errors,
        )

        check(
            value = TransitConfig.GTFS_RT_TRIP_UPDATES_URL,
            name = "GTFS_RT_TRIP_UPDATES_URL",
            hint = "Pre-filled with LYNX URL. Only change if using a different agency.",
            errors = errors,
        )

        if (errors.isEmpty()) {
            println("ConfigValidator ✓ All config values set")
            return
        }

        val message = buildString {
            appendLine("═══════════════════════════════════════════════")
            appendLine("  TRANSIT PRESENCE — CONFIG INCOMPLETE")
            appendLine("  Fix: open core/core-common/.../TransitConfig.kt")
            appendLine("═══════════════════════════════════════════════")
            errors.forEach { appendLine("  ✗  $it") }
            appendLine("═══════════════════════════════════════════════")
        }

        if (isDebug) throw IllegalStateException(message)
        else println(message)
    }

    private fun check(value: String, name: String, hint: String, errors: MutableList<String>) {
        val isPlaceholder = value.isBlank() || PLACEHOLDERS.any { value.startsWith(it, true) }
        if (isPlaceholder) errors.add("$name not set — $hint")
    }
}
