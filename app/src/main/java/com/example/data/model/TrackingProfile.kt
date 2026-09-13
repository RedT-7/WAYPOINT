package com.example.data.model

enum class TrackingProfile(
    val displayName: String,
    val description: String,
    val intervalMs: Long,
    val minDistanceMeters: Float,
    val iconName: String
) {
    NAVIGATION(
        displayName = "Navigation (High Precision)",
        description = "2-sec interval, optimal for driving and turn-by-turn navigation.",
        intervalMs = 2_000L,
        minDistanceMeters = 1.0f,
        iconName = "Navigation"
    ),
    BALANCED(
        displayName = "Balanced (City & Walking)",
        description = "8-sec interval, smooth updates with optimized battery drain.",
        intervalMs = 8_000L,
        minDistanceMeters = 5.0f,
        iconName = "DirectionsWalk"
    ),
    BATTERY_SAVER(
        displayName = "Battery Saver (Long-Term)",
        description = "30-sec interval, ideal for multi-hour standby and long trips.",
        intervalMs = 30_000L,
        minDistanceMeters = 20.0f,
        iconName = "BatteryChargingFull"
    )
}
