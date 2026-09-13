package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PairTrackApplication
import com.example.data.local.LocationPointEntity
import com.example.data.model.DeviceState
import com.example.data.model.GeofenceCategory
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.TrackingProfile
import com.example.data.model.UserProfile
import com.example.service.LocationTrackingService
import com.example.util.LocationInterpolator
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class NavTab {
    MAP,
    GEOFENCES,
    OFFLINE_HISTORY,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PairTrackApplication.instance.repository

    val userProfile: StateFlow<UserProfile> = repository.userProfile
    val selfLocation: StateFlow<LocationPoint?> = repository.currentLocation
    val partnerState: StateFlow<DeviceState?> = repository.partnerState
    val connectionLatencyMs: StateFlow<Long> = repository.connectionLatencyMs

    val geofences: StateFlow<List<GeofenceZone>> = repository.geofences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val locationHistory: StateFlow<List<LocationPointEntity>> = repository.locationHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedTab = MutableStateFlow(NavTab.MAP)
    val selectedTab: StateFlow<NavTab> = _selectedTab.asStateFlow()

    private val _isPairingDialogVisible = MutableStateFlow(false)
    val isPairingDialogVisible: StateFlow<Boolean> = _isPairingDialogVisible.asStateFlow()

    private val _isAddGeofenceDialogVisible = MutableStateFlow(false)
    val isAddGeofenceDialogVisible: StateFlow<Boolean> = _isAddGeofenceDialogVisible.asStateFlow()

    private val _isOfflineModeForced = MutableStateFlow(
        com.example.BuildConfig.MAPS_API_KEY == "DEFAULT_MAPS_KEY" || com.example.BuildConfig.MAPS_API_KEY.isBlank()
    )
    val isOfflineModeForced: StateFlow<Boolean> = _isOfflineModeForced.asStateFlow()

    // Smooth Latency Interpolation for Partner Movement
    private val _smoothedPartnerLatLng = MutableStateFlow<LatLng?>(null)
    val smoothedPartnerLatLng: StateFlow<LatLng?> = _smoothedPartnerLatLng.asStateFlow()

    private val _smoothedPartnerBearing = MutableStateFlow(0f)
    val smoothedPartnerBearing: StateFlow<Float> = _smoothedPartnerBearing.asStateFlow()

    // Real-time Relative Vector (Distance & Direction)
    val distanceToPartnerMeters: StateFlow<Float?> = combine(selfLocation, partnerState) { self, partner ->
        if (self != null && partner?.location != null) {
            val from = LatLng(self.latitude, self.longitude)
            val to = LatLng(partner.location.latitude, partner.location.longitude)
            LocationInterpolator.computeDistanceMeters(from, to)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bearingToPartner: StateFlow<Float?> = combine(selfLocation, partnerState) { self, partner ->
        if (self != null && partner?.location != null) {
            val from = LatLng(self.latitude, self.longitude)
            val to = LatLng(partner.location.latitude, partner.location.longitude)
            LocationInterpolator.computeBearing(from, to)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var partnerSmoothAnimJob: Job? = null
    private var simulationJob: Job? = null

    init {
        // Latency and Dead-Reckoning smoothing collector
        viewModelScope.launch {
            partnerState.collect { state ->
                val targetLoc = state?.location
                if (targetLoc != null) {
                    val targetLatLng = LatLng(targetLoc.latitude, targetLoc.longitude)
                    val currentSmooth = _smoothedPartnerLatLng.value

                    if (currentSmooth == null) {
                        _smoothedPartnerLatLng.value = targetLatLng
                        _smoothedPartnerBearing.value = targetLoc.bearing
                    } else {
                        // Smoothly animate from current to target over 1.2 seconds in 60fps increments
                        animatePartnerMovement(currentSmooth, targetLatLng, _smoothedPartnerBearing.value, targetLoc.bearing)
                    }
                } else {
                    _smoothedPartnerLatLng.value = null
                }
            }
        }
    }

    private fun animatePartnerMovement(from: LatLng, to: LatLng, fromBearing: Float, toBearing: Float) {
        partnerSmoothAnimJob?.cancel()
        partnerSmoothAnimJob = viewModelScope.launch {
            val steps = 24
            val durationMs = 1200L
            val stepDelay = durationMs / steps

            for (i in 1..steps) {
                val fraction = i.toFloat() / steps
                _smoothedPartnerLatLng.value = LocationInterpolator.interpolate(fraction, from, to)
                _smoothedPartnerBearing.value = LocationInterpolator.interpolateBearing(fraction, fromBearing, toBearing)
                delay(stepDelay)
            }
            _smoothedPartnerLatLng.value = to
            _smoothedPartnerBearing.value = toBearing
        }
    }

    fun selectTab(tab: NavTab) {
        _selectedTab.value = tab
    }

    fun showPairingDialog(show: Boolean) {
        _isPairingDialogVisible.value = show
    }

    fun showAddGeofenceDialog(show: Boolean) {
        _isAddGeofenceDialogVisible.value = show
    }

    fun toggleOfflineMode() {
        _isOfflineModeForced.value = !_isOfflineModeForced.value
    }

    fun generateNewRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun joinRoom(code: String) {
        repository.joinRoom(code)
        _isPairingDialogVisible.value = false
    }

    fun leaveRoom() {
        repository.leaveRoom()
    }

    fun toggleLocationSharing() {
        val current = userProfile.value.isSharingLocation
        repository.toggleLocationSharing(!current)
    }

    fun updateTrackingProfile(profile: TrackingProfile) {
        repository.setTrackingProfile(profile)
        LocationTrackingService.start(getApplication())
    }

    fun updateProfile(displayName: String, avatarIndex: Int) {
        repository.updateUserProfile(
            userProfile.value.copy(
                displayName = displayName.trim().ifEmpty { "My Device" },
                avatarColorIndex = avatarIndex
            )
        )
    }

    fun toggleDarkMode(isDark: Boolean?) {
        repository.updateUserProfile(userProfile.value.copy(isDarkMode = isDark))
    }

    fun toggleTraffic(enabled: Boolean) {
        repository.updateUserProfile(userProfile.value.copy(isTrafficEnabled = enabled))
    }

    fun toggleNightMap(enabled: Boolean) {
        repository.updateUserProfile(userProfile.value.copy(isNightMapEnabled = enabled))
    }

    fun toggleGeofenceAlerts(enabled: Boolean) {
        repository.updateUserProfile(userProfile.value.copy(geofenceAlertsEnabled = enabled))
    }

    fun togglePartnerLowBatteryAlert(enabled: Boolean) {
        repository.updateUserProfile(userProfile.value.copy(partnerLowBatteryAlertEnabled = enabled))
    }

    fun toggleSoundAlerts(enabled: Boolean) {
        repository.updateUserProfile(userProfile.value.copy(soundAlertsEnabled = enabled))
    }

    fun updateCustomFirebaseUrl(url: String) {
        repository.updateUserProfile(userProfile.value.copy(customFirebaseUrl = url.trim()))
        repository.firebaseManager.initialize(url.trim().ifEmpty { null })
    }

    fun addGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        category: GeofenceCategory
    ) {
        val zone = GeofenceZone(
            id = UUID.randomUUID().toString(),
            name = name.ifEmpty { "${category.label} Zone" },
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            category = category
        )
        repository.addGeofence(zone)
        _isAddGeofenceDialogVisible.value = false
    }

    fun deleteGeofence(id: String) {
        repository.deleteGeofence(id)
    }

    fun toggleGeofence(zone: GeofenceZone, isEnabled: Boolean) {
        repository.toggleGeofence(zone, isEnabled)
    }

    fun clearBreadcrumbHistory() {
        repository.clearHistory()
    }

    /**
     * Interactive Partner Simulator:
     * When tested on single device or emulator without a 2nd phone,
     * simulates smooth live partner driving towards or around user!
     */
    fun togglePartnerSimulation() {
        if (simulationJob?.isActive == true) {
            simulationJob?.cancel()
            simulationJob = null
            repository.firebaseManager.leaveCurrentRoom()
            return
        }

        simulationJob = viewModelScope.launch {
            val baseLat = selfLocation.value?.latitude ?: 37.7749
            val baseLng = selfLocation.value?.longitude ?: -122.4194

            var angle = 0.0
            val radiusDegrees = 0.005 // ~500 meters

            while (true) {
                val simLat = baseLat + radiusDegrees * cos(Math.toRadians(angle))
                val simLng = baseLng + radiusDegrees * sin(Math.toRadians(angle))
                val bearing = ((angle + 90) % 360).toFloat()

                val simPoint = LocationPoint(
                    latitude = simLat,
                    longitude = simLng,
                    altitude = 25.0,
                    accuracy = 4.2f,
                    speed = 8.5f, // ~30 km/h
                    bearing = bearing,
                    timestamp = System.currentTimeMillis(),
                    batteryLevel = (85 - (angle / 20).toInt()).coerceIn(12, 100),
                    isCharging = false
                )

                repository.firebaseManager.injectSimulatedPartnerLocation(simPoint, "Navigator Device B")

                angle = (angle + 12) % 360
                delay(2500L)
            }
        }
    }

    val isSimulating: Boolean
        get() = simulationJob?.isActive == true
}
