package com.example.data.model

enum class GeofenceCategory(val label: String, val defaultRadius: Float) {
    HOME("Home", 150f),
    WORK("Work", 200f),
    SCHOOL("School", 250f),
    DANGER("Restricted Area", 300f),
    CUSTOM("Custom Zone", 100f)
}

data class GeofenceZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val category: GeofenceCategory = GeofenceCategory.CUSTOM,
    val notifyOnEntry: Boolean = true,
    val notifyOnExit: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
