package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.LocationPoint

@Entity(tableName = "location_history")
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long,
    val batteryLevel: Int,
    val isPartnerPoint: Boolean = false
) {
    fun toLocationPoint(): LocationPoint {
        return LocationPoint(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            speed = speed,
            bearing = bearing,
            timestamp = timestamp,
            batteryLevel = batteryLevel
        )
    }

    companion object {
        fun fromLocationPoint(point: LocationPoint, deviceId: String, isPartner: Boolean = false): LocationPointEntity {
            return LocationPointEntity(
                deviceId = deviceId,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                accuracy = point.accuracy,
                speed = point.speed,
                bearing = point.bearing,
                timestamp = point.timestamp,
                batteryLevel = point.batteryLevel,
                isPartnerPoint = isPartner
            )
        }
    }
}
