package com.example.data.model

data class LocationPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f, // in m/s
    val bearing: Float = 0f, // in degrees
    val timestamp: Long = System.currentTimeMillis(),
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isMock: Boolean = false
) {
    val speedKmh: Float get() = speed * 3.6f
}
