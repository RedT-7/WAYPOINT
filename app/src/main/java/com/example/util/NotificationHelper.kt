package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.service.LocationTrackingService

object NotificationHelper {

    const val CHANNEL_TRACKING_ID = "pairtrack_tracking_channel"
    const val CHANNEL_GEOFENCE_ID = "pairtrack_geofence_channel"
    const val CHANNEL_ALERTS_ID = "pairtrack_alerts_channel"

    const val NOTIFICATION_ID_FOREGROUND = 1001
    private var alertNotificationId = 2000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Tracking service channel (Foreground Service)
            val trackingChannel = NotificationChannel(
                CHANNEL_TRACKING_ID,
                "Live Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing location tracking and real-time synchronization"
                setShowBadge(false)
            }

            // Geofencing channel
            val geofenceChannel = NotificationChannel(
                CHANNEL_GEOFENCE_ID,
                "Geofence Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant notifications when entering or exiting geofence zones"
                enableVibration(true)
                setShowBadge(true)
            }

            // Device alerts channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Partner & Device Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Partner connection, low battery, and pairing alerts"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(trackingChannel, geofenceChannel, alertsChannel)
            )
        }
    }

    fun buildForegroundNotification(
        context: Context,
        isSharing: Boolean,
        speedKmh: Float,
        batteryPercent: Int,
        roomCode: String,
        partnerConnected: Boolean
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Toggle pause/resume
        val toggleActionIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = if (isSharing) LocationTrackingService.ACTION_PAUSE else LocationTrackingService.ACTION_RESUME
        }
        val pendingToggle = PendingIntent.getService(
            context,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop service
        val stopActionIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            context,
            2,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val partnerStatus = if (partnerConnected) "Partner Connected" else "Waiting for Partner"
        val roomDisplay = if (roomCode.isNotEmpty()) "Room: $roomCode • " else ""
        val statusText = if (isSharing) {
            "${roomDisplay}${partnerStatus} • ${String.format("%.1f", speedKmh)} km/h • Bat: $batteryPercent%"
        } else {
            "Sharing paused • ${partnerStatus}"
        }

        return NotificationCompat.Builder(context, CHANNEL_TRACKING_ID)
            .setContentTitle("Waypoint • Live Location Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(
                if (isSharing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isSharing) "Pause" else "Resume",
                pendingToggle
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                pendingStop
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun sendGeofenceAlert(
        context: Context,
        zoneName: String,
        deviceName: String,
        isEntry: Boolean
    ) {
        val title = if (isEntry) "Entered Geofence" else "Exited Geofence"
        val message = if (isEntry) {
            "$deviceName entered zone \"$zoneName\""
        } else {
            "$deviceName exited zone \"$zoneName\""
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_GEOFENCE_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alertNotificationId++, notification)
    }

    fun sendPartnerLowBatteryAlert(context: Context, partnerName: String, batteryLevel: Int) {
        val openIntent = Intent(context, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setContentTitle("Partner Battery Low")
            .setContentText("$partnerName's battery is at $batteryLevel%.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alertNotificationId++, notification)
    }
}
