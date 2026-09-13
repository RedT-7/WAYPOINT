package com.example.util

import android.location.Location
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint

data class GeofenceTransition(
    val zone: GeofenceZone,
    val deviceName: String,
    val isEntry: Boolean,
    val distanceMeters: Float
)

class GeofenceEngine {

    // Map of deviceId to (zoneId to wasInside)
    private val deviceZoneStates = mutableMapOf<String, MutableMap<String, Boolean>>()

    /**
     * Evaluates location against active geofences.
     * Returns any transitions (entry or exit).
     */
    fun evaluate(
        deviceId: String,
        deviceName: String,
        location: LocationPoint,
        zones: List<GeofenceZone>
    ): List<GeofenceTransition> {
        val transitions = mutableListOf<GeofenceTransition>()
        val deviceStates = deviceZoneStates.getOrPut(deviceId) { mutableMapOf() }

        val distanceResults = FloatArray(1)

        for (zone in zones) {
            if (!zone.isEnabled) continue

            Location.distanceBetween(
                location.latitude,
                location.longitude,
                zone.latitude,
                zone.longitude,
                distanceResults
            )
            val distance = distanceResults[0]

            // 5m hysteresis buffer to prevent boundary jitter
            val isInside = distance <= zone.radiusMeters
            val previousState = deviceStates[zone.id]

            if (previousState == null) {
                // First evaluation, record state without triggering false exit
                deviceStates[zone.id] = isInside
                if (isInside && zone.notifyOnEntry) {
                    transitions.add(GeofenceTransition(zone, deviceName, isEntry = true, distanceMeters = distance))
                }
            } else if (previousState != isInside) {
                // State changed!
                deviceStates[zone.id] = isInside
                if (isInside && zone.notifyOnEntry) {
                    transitions.add(GeofenceTransition(zone, deviceName, isEntry = true, distanceMeters = distance))
                } else if (!isInside && zone.notifyOnExit) {
                    transitions.add(GeofenceTransition(zone, deviceName, isEntry = false, distanceMeters = distance))
                }
            }
        }

        return transitions
    }

    fun getDistanceToZone(location: LocationPoint, zone: GeofenceZone): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            zone.latitude,
            zone.longitude,
            results
        )
        return results[0]
    }
}
