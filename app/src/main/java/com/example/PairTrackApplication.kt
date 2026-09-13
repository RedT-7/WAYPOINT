package com.example

import android.app.Application
import com.example.data.local.PairTrackDatabase
import com.example.data.repository.TrackingRepository
import com.example.util.NotificationHelper
import com.google.firebase.FirebaseApp

class PairTrackApplication : Application() {

    lateinit var database: PairTrackDatabase
        private set

    lateinit var repository: TrackingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Notification Channels for Android 8.0+
        NotificationHelper.createNotificationChannels(this)

        // Initialize Firebase if possible
        try {
            FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
            // Handled gracefully if google-services.json is absent or default
        }

        // Initialize Room Database
        database = PairTrackDatabase.getDatabase(this)
        repository = TrackingRepository(this, database)
    }

    companion object {
        lateinit var instance: PairTrackApplication
            private set
    }
}
