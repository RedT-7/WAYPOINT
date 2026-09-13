package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceDao {

    @Query("SELECT * FROM geofences ORDER BY createdAt DESC")
    fun getAllGeofences(): Flow<List<GeofenceZoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeofence(zone: GeofenceZoneEntity)

    @Update
    suspend fun updateGeofence(zone: GeofenceZoneEntity)

    @Query("DELETE FROM geofences WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM geofences")
    suspend fun clearAll()
}
