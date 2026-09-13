package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LocationPointEntity::class, GeofenceZoneEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PairTrackDatabase : RoomDatabase() {

    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun geofenceDao(): GeofenceDao

    companion object {
        @Volatile
        private var INSTANCE: PairTrackDatabase? = null

        fun getDatabase(context: Context): PairTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PairTrackDatabase::class.java,
                    "pairtrack_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
