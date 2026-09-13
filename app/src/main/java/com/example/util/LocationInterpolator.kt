package com.example.util

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationInterpolator {

    /**
     * Interpolates smoothly between two LatLng positions using spherical linear interpolation (slerp).
     */
    fun interpolate(fraction: Float, from: LatLng, to: LatLng): LatLng {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        val fromLat = Math.toRadians(from.latitude)
        val fromLng = Math.toRadians(from.longitude)
        val toLat = Math.toRadians(to.latitude)
        val toLng = Math.toRadians(to.longitude)

        val cosFromLat = cos(fromLat)
        val cosToLat = cos(toLat)

        // Spherical law of cosines for angular distance
        val angle = computeAngleBetween(fromLat, fromLng, toLat, toLng)
        if (angle < 1e-6) {
            return to
        }

        val sinAngle = sin(angle)
        val a = sin((1 - clampedFraction) * angle) / sinAngle
        val b = sin(clampedFraction * angle) / sinAngle

        // Transform back to Cartesian, then back to spherical
        val x = a * cosFromLat * cos(fromLng) + b * cosToLat * cos(toLng)
        val y = a * cosFromLat * sin(fromLng) + b * cosToLat * sin(toLng)
        val z = a * sin(fromLat) + b * sin(toLat)

        val lat = atan2(z, sqrt(x * x + y * y))
        val lng = atan2(y, x)

        return LatLng(Math.toDegrees(lat), Math.toDegrees(lng))
    }

    /**
     * Interpolates between two bearing angles taking the shortest rotational path.
     */
    fun interpolateBearing(fraction: Float, from: Float, to: Float): Float {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        var diff = (to - from) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return (from + diff * clampedFraction + 360f) % 360f
    }

    /**
     * Calculates initial bearing between two LatLng coordinates.
     */
    fun computeBearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }

    /**
     * Calculates distance in meters between two LatLng points using Haversine formula.
     */
    fun computeDistanceMeters(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            results
        )
        return results[0]
    }

    private fun computeAngleBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = lat1 - lat2
        val dLng = lng1 - lng2
        return 2 * kotlin.math.asin(
            sqrt(
                sin(dLat / 2) * sin(dLat / 2) +
                        cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
            )
        )
    }
}
