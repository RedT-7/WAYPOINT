package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.PairTrackApplication
import com.example.data.model.LocationPoint
import com.example.data.model.TrackingProfile
import com.example.util.NotificationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private val tag = "LocationTrackingService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var isSharing = true
    private var currentSpeedKmh = 0f
    private var currentBattery = 100
    private var isCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    currentBattery = (level * 100) / scale
                }
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                updateNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        // Observe partner state to update ongoing notification
        serviceScope.launch {
            PairTrackApplication.instance.repository.partnerState.collectLatest {
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForegroundServiceCompat()
                startLocationUpdates()
            }
            ACTION_PAUSE -> {
                isSharing = false
                PairTrackApplication.instance.repository.toggleLocationSharing(false)
                updateNotification()
            }
            ACTION_RESUME -> {
                isSharing = true
                PairTrackApplication.instance.repository.toggleLocationSharing(true)
                updateNotification()
            }
            ACTION_UPDATE_PROFILE -> {
                startLocationUpdates() // Restart with updated profile intervals
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceCompat() {
        val notification = NotificationHelper.buildForegroundNotification(
            context = this,
            isSharing = isSharing,
            speedKmh = currentSpeedKmh,
            batteryPercent = currentBattery,
            roomCode = PairTrackApplication.instance.repository.userProfile.value.currentRoomCode,
            partnerConnected = PairTrackApplication.instance.repository.partnerState.value != null
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun updateNotification() {
        val notification = NotificationHelper.buildForegroundNotification(
            context = this,
            isSharing = isSharing,
            speedKmh = currentSpeedKmh,
            batteryPercent = currentBattery,
            roomCode = PairTrackApplication.instance.repository.userProfile.value.currentRoomCode,
            partnerConnected = PairTrackApplication.instance.repository.partnerState.value != null
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_FOREGROUND, notification)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        stopLocationUpdates()

        val profile = PairTrackApplication.instance.repository.userProfile.value.trackingProfile
        val priority = when (profile) {
            TrackingProfile.NAVIGATION -> Priority.PRIORITY_HIGH_ACCURACY
            TrackingProfile.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            TrackingProfile.BATTERY_SAVER -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(priority, profile.intervalMs)
            .setMinUpdateIntervalMillis(profile.intervalMs / 2)
            .setMinUpdateDistanceMeters(profile.minDistanceMeters)
            .setWaitForAccurateLocation(profile == TrackingProfile.NAVIGATION)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location = result.lastLocation ?: return
                currentSpeedKmh = loc.speed * 3.6f

                val point = LocationPoint(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude,
                    accuracy = loc.accuracy,
                    speed = loc.speed,
                    bearing = loc.bearing,
                    timestamp = loc.time,
                    batteryLevel = currentBattery,
                    isCharging = isCharging,
                    isMock = loc.isFromMockProvider
                )

                PairTrackApplication.instance.repository.onSelfLocationUpdated(point)
                updateNotification()
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(tag, "Location updates requested with profile: ${profile.displayName}")
        } catch (e: SecurityException) {
            Log.e(tag, "Location permission missing: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.pairtrack.ACTION_START"
        const val ACTION_PAUSE = "com.example.pairtrack.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.pairtrack.ACTION_RESUME"
        const val ACTION_STOP = "com.example.pairtrack.ACTION_STOP"
        const val ACTION_UPDATE_PROFILE = "com.example.pairtrack.ACTION_UPDATE_PROFILE"

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
