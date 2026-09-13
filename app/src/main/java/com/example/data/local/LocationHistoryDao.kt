package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(point: LocationPointEntity): Long

    @Query("SELECT * FROM location_history WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(deviceId: String, limit: Int = 300): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_history ORDER BY timestamp DESC LIMIT :limit")
    fun getAllPoints(limit: Int = 500): Flow<List<LocationPointEntity>>

    @Query("SELECT COUNT(*) FROM location_history")
    suspend fun getCount(): Int

    @Query("DELETE FROM location_history")
    suspend fun clearAll()
}
