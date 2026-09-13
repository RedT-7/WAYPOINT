package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.GeofenceCategory
import com.example.data.model.GeofenceZone

@Entity(tableName = "geofences")
data class GeofenceZoneEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val categoryName: String,
    val notifyOnEntry: Boolean,
    val notifyOnExit: Boolean,
    val isEnabled: Boolean,
    val createdAt: Long
) {
    fun toGeofenceZone(): GeofenceZone {
        val category = try {
            GeofenceCategory.valueOf(categoryName)
        } catch (_: Exception) {
            GeofenceCategory.CUSTOM
        }
        return GeofenceZone(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            category = category,
            notifyOnEntry = notifyOnEntry,
            notifyOnExit = notifyOnExit,
            isEnabled = isEnabled,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromGeofenceZone(zone: GeofenceZone): GeofenceZoneEntity {
            return GeofenceZoneEntity(
                id = zone.id,
                name = zone.name,
                latitude = zone.latitude,
                longitude = zone.longitude,
                radiusMeters = zone.radiusMeters,
                categoryName = zone.category.name,
                notifyOnEntry = zone.notifyOnEntry,
                notifyOnExit = zone.notifyOnExit,
                isEnabled = zone.isEnabled,
                createdAt = zone.createdAt
            )
        }
    }
}
