package com.quark.leaks.data.room

import androidx.room.*
import com.quark.leaks.data.model.TrackerEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerEventDao {

    @Query("SELECT * FROM tracker_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<TrackerEvent>>

    @Query("SELECT * FROM tracker_events ORDER BY timestamp DESC")
    suspend fun getAllEventsList(): List<TrackerEvent>

    @Query("SELECT * FROM tracker_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getEventsByTimeRange(startTime: Long, endTime: Long): Flow<List<TrackerEvent>>

    @Query("SELECT COUNT(*) FROM tracker_events WHERE blocked = 1")
    suspend fun getBlockedCount(): Int

    @Insert
    suspend fun insert(event: TrackerEvent)

    @Delete
    suspend fun delete(event: TrackerEvent)

    @Query("DELETE FROM tracker_events WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM tracker_events")
    suspend fun deleteAll()
}

