package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import com.example.data.local.GeofenceZoneEntity
import com.example.data.local.LocationPointEntity
import com.example.data.local.PairTrackDatabase
import com.example.data.model.DeviceState
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.TrackingProfile
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseLocationManager
import com.example.util.GeofenceEngine
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class TrackingRepository(
    private val context: Context,
    private val database: PairTrackDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences("pairtrack_prefs", Context.MODE_PRIVATE)

    val firebaseManager = FirebaseLocationManager()
    val geofenceEngine = GeofenceEngine()

    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    val partnerState: StateFlow<DeviceState?> = firebaseManager.partnerState
    val connectionLatencyMs: StateFlow<Long> = firebaseManager.connectionLatencyMs

    // Live Geofences from Room
    val geofences: Flow<List<GeofenceZone>> = database.geofenceDao().getAllGeofences().map { entities ->
        entities.map { it.toGeofenceZone() }
    }

    // Offline Breadcrumbs from Room
    val locationHistory: Flow<List<LocationPointEntity>> = database.locationHistoryDao().getAllPoints(300)

    private var partnerLowBatteryAlerted = false
    private var lastRecordedLocation: LocationPoint? = null

    init {
        // Initialize Firebase with custom URL if saved
        val customUrl = _userProfile.value.customFirebaseUrl
        firebaseManager.initialize(if (customUrl.isNotBlank()) customUrl else null)

        // If user already had an active room code, rejoin
        val savedRoom = _userProfile.value.currentRoomCode
        if (savedRoom.isNotBlank()) {
            joinRoom(savedRoom)
        }

        // Monitor partner state for geofence & low battery alerts
        scope.launch {
            partnerState.collect { partner ->
                if (partner != null && partner.location != null) {
                    // Check partner low battery alert
                    val bat = partner.location.batteryLevel
                    if (bat <= 15 && !partnerLowBatteryAlerted && _userProfile.value.partnerLowBatteryAlertEnabled) {
                        partnerLowBatteryAlerted = true
                        NotificationHelper.sendPartnerLowBatteryAlert(context, partner.deviceName, bat)
                    } else if (bat > 25) {
                        partnerLowBatteryAlerted = false
                    }

                    // Save partner breadcrumb in Room for offline replay
                    database.locationHistoryDao().insertLocation(
                        LocationPointEntity.fromLocationPoint(
                            partner.location,
                            deviceId = partner.deviceId,
                            isPartner = true
                        )
                    )

                    // Evaluate Geofences for partner
                    val currentZones = database.geofenceDao().getAllGeofences()
                    // Collect single list from flow
                    // Evaluate
                    evaluatePartnerGeofences(partner.deviceName, partner.deviceId, partner.location)
                }
            }
        }
    }

    fun onSelfLocationUpdated(location: LocationPoint) {
        _currentLocation.value = location

        val profile = _userProfile.value
        val shouldPublish = profile.isSharingLocation

        // Battery level reading
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val locationWithBattery = location.copy(batteryLevel = batteryLevel)

        // Threshold filtering: only publish/record if moved > min distance or 30s elapsed
        val last = lastRecordedLocation
        val shouldRecord = last == null ||
                (System.currentTimeMillis() - last.timestamp > 20_000L) ||
                (android.location.Location.distanceBetween(
                    last.latitude, last.longitude,
                    location.latitude, location.longitude,
                    FloatArray(1)
                ).let { FloatArray(1)[0] > 3f })

        if (shouldPublish && shouldRecord) {
            firebaseManager.publishLocation(
                location = locationWithBattery,
                deviceName = profile.displayName,
                avatarColorIndex = profile.avatarColorIndex,
                isSharing = true
            )
            lastRecordedLocation = locationWithBattery
        }

        // Save local breadcrumb in Room
        scope.launch {
            database.locationHistoryDao().insertLocation(
                LocationPointEntity.fromLocationPoint(
                    locationWithBattery,
                    deviceId = profile.userId,
                    isPartner = false
                )
            )

            // Evaluate Self Geofences
            evaluateSelfGeofences(profile.displayName, profile.userId, locationWithBattery)
        }
    }

    private suspend fun evaluateSelfGeofences(name: String, deviceId: String, location: LocationPoint) {
        if (!_userProfile.value.geofenceAlertsEnabled) return
        val entities = database.geofenceDao().getAllGeofences()
        // We can query once or collect
        // For efficiency, observe in a background collector or execute query
    }

    private suspend fun evaluatePartnerGeofences(name: String, deviceId: String, location: LocationPoint) {
        if (!_userProfile.value.geofenceAlertsEnabled) return
        // Geofence evaluation will trigger NotificationHelper.sendGeofenceAlert
    }

    fun joinRoom(roomCode: String) {
        val trimmedCode = roomCode.trim().uppercase()
        val profile = _userProfile.value
        updateUserProfile(profile.copy(currentRoomCode = trimmedCode))
        firebaseManager.joinRoom(
            roomCode = trimmedCode,
            deviceId = profile.userId,
            deviceName = profile.displayName,
            avatarColorIndex = profile.avatarColorIndex
        )
    }

    fun leaveRoom() {
        firebaseManager.leaveCurrentRoom()
        val profile = _userProfile.value
        updateUserProfile(profile.copy(currentRoomCode = ""))
    }

    fun toggleLocationSharing(isSharing: Boolean) {
        val profile = _userProfile.value
        updateUserProfile(profile.copy(isSharingLocation = isSharing))
        _currentLocation.value?.let { loc ->
            firebaseManager.publishLocation(
                location = loc,
                deviceName = profile.displayName,
                avatarColorIndex = profile.avatarColorIndex,
                isSharing = isSharing
            )
        }
    }

    fun setTrackingProfile(profile: TrackingProfile) {
        updateUserProfile(_userProfile.value.copy(trackingProfile = profile))
    }

    fun addGeofence(zone: GeofenceZone) {
        scope.launch {
            database.geofenceDao().insertGeofence(GeofenceZoneEntity.fromGeofenceZone(zone))
        }
    }

    fun deleteGeofence(zoneId: String) {
        scope.launch {
            database.geofenceDao().deleteById(zoneId)
        }
    }

    fun toggleGeofence(zone: GeofenceZone, isEnabled: Boolean) {
        scope.launch {
            database.geofenceDao().updateGeofence(
                GeofenceZoneEntity.fromGeofenceZone(zone.copy(isEnabled = isEnabled))
            )
        }
    }

    fun clearHistory() {
        scope.launch {
            database.locationHistoryDao().clearAll()
        }
    }

    fun updateUserProfile(profile: UserProfile) {
        _userProfile.value = profile
        prefs.edit().apply {
            putString("user_id", profile.userId)
            putString("display_name", profile.displayName)
            putInt("avatar_color", profile.avatarColorIndex)
            putString("room_code", profile.currentRoomCode)
            putBoolean("is_sharing", profile.isSharingLocation)
            putString("tracking_profile", profile.trackingProfile.name)
            profile.isDarkMode?.let { putBoolean("dark_mode", it) } ?: remove("dark_mode")
            putBoolean("traffic_enabled", profile.isTrafficEnabled)
            putBoolean("night_map", profile.isNightMapEnabled)
            putBoolean("geofence_alerts", profile.geofenceAlertsEnabled)
            putBoolean("low_battery_alerts", profile.partnerLowBatteryAlertEnabled)
            putBoolean("sound_alerts", profile.soundAlertsEnabled)
            putString("custom_firebase_url", profile.customFirebaseUrl)
            putString("custom_maps_key", profile.customMapsApiKey)
            apply()
        }
    }

    private fun loadUserProfile(): UserProfile {
        var uid = prefs.getString("user_id", null)
        if (uid.isNullOrEmpty()) {
            uid = "dev_" + UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString("user_id", uid).apply()
        }

        val name = prefs.getString("display_name", "Device-${uid.takeLast(4)}") ?: "My Device"
        val avatar = prefs.getInt("avatar_color", 0)
        val room = prefs.getString("room_code", "") ?: ""
        val sharing = prefs.getBoolean("is_sharing", true)
        val profileStr = prefs.getString("tracking_profile", TrackingProfile.BALANCED.name) ?: TrackingProfile.BALANCED.name
        val trackingProfile = try {
            TrackingProfile.valueOf(profileStr)
        } catch (_: Exception) {
            TrackingProfile.BALANCED
        }
        val dark = if (prefs.contains("dark_mode")) prefs.getBoolean("dark_mode", true) else null
        val traffic = prefs.getBoolean("traffic_enabled", true)
        val nightMap = prefs.getBoolean("night_map", true)
        val geofenceAlerts = prefs.getBoolean("geofence_alerts", true)
        val lowBatAlerts = prefs.getBoolean("low_battery_alerts", true)
        val sound = prefs.getBoolean("sound_alerts", true)
        val fbUrl = prefs.getString("custom_firebase_url", "") ?: ""
        val mapsKey = prefs.getString("custom_maps_key", "") ?: ""

        return UserProfile(
            userId = uid,
            displayName = name,
            avatarColorIndex = avatar,
            currentRoomCode = room,
            isSharingLocation = sharing,
            trackingProfile = trackingProfile,
            isDarkMode = dark,
            isTrafficEnabled = traffic,
            isNightMapEnabled = nightMap,
            geofenceAlertsEnabled = geofenceAlerts,
            partnerLowBatteryAlertEnabled = lowBatAlerts,
            soundAlertsEnabled = sound,
            customFirebaseUrl = fbUrl,
            customMapsApiKey = mapsKey
        )
    }
}
