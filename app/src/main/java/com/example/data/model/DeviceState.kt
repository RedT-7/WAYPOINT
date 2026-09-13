package com.example.data.model

data class DeviceState(
    val deviceId: String = "",
    val deviceName: String = "Device",
    val avatarColorIndex: Int = 0,
    val location: LocationPoint? = null,
    val lastSeenTimestamp: Long = 0L,
    val isSharing: Boolean = true,
    val trackingProfile: String = "BALANCED",
    val isOnline: Boolean = false
) {
    val isRecentlyActive: Boolean
        get() = (System.currentTimeMillis() - lastSeenTimestamp) < 60_000L && isSharing
}
