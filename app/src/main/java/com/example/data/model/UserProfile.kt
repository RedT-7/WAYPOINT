package com.example.data.model

data class UserProfile(
    val userId: String = "",
    val displayName: String = "My Device",
    val avatarColorIndex: Int = 0, // 0 to 5
    val currentRoomCode: String = "",
    val isSharingLocation: Boolean = true,
    val trackingProfile: TrackingProfile = TrackingProfile.BALANCED,
    val isDarkMode: Boolean? = null, // null for system, true/false for forced
    val isTrafficEnabled: Boolean = true,
    val isNightMapEnabled: Boolean = true,
    val geofenceAlertsEnabled: Boolean = true,
    val partnerLowBatteryAlertEnabled: Boolean = true,
    val soundAlertsEnabled: Boolean = true,
    val customFirebaseUrl: String = "",
    val customMapsApiKey: String = ""
)
