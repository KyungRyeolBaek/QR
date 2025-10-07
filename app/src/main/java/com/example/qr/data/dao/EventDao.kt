package com.example.qr.data.dao

import androidx.room.*
import com.example.qr.data.entity.Event
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY createdAt DESC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveEvent(): Event?

    @Query("SELECT * FROM events WHERE isActive = 1 LIMIT 1")
    fun getActiveEventFlow(): Flow<Event?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Long): Event?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)

    @Query("UPDATE events SET isActive = 0")
    suspend fun deactivateAllEvents()

    @Query("UPDATE events SET isActive = 1 WHERE id = :eventId")
    suspend fun activateEvent(eventId: Long)

    @Transaction
    suspend fun setActiveEvent(eventId: Long) {
        deactivateAllEvents()
        activateEvent(eventId)
    }
}