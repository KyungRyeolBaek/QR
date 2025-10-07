package com.example.barcode.data.dao

import androidx.room.*
import com.example.barcode.data.entity.Participant
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {

    @Query("SELECT * FROM participants WHERE eventId = :eventId ORDER BY fullName ASC")
    fun getParticipantsByEvent(eventId: Long): Flow<List<Participant>>

    @Query("SELECT * FROM participants WHERE barcodeData = :barcodeData LIMIT 1")
    suspend fun getParticipantByBarcode(barcodeData: String): Participant?

    @Query("SELECT * FROM participants WHERE id = :id")
    suspend fun getParticipantById(id: Long): Participant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: Participant): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<Participant>): List<Long>

    @Update
    suspend fun updateParticipant(participant: Participant)

    @Delete
    suspend fun deleteParticipant(participant: Participant)

    @Query("DELETE FROM participants WHERE id = :participantId")
    suspend fun deleteParticipant(participantId: Long): Int

    @Query("DELETE FROM participants WHERE eventId = :eventId")
    suspend fun deleteParticipantsByEvent(eventId: Long)

    @Query("SELECT COUNT(*) FROM participants WHERE eventId = :eventId")
    suspend fun getParticipantCount(eventId: Long): Int

    @Query("SELECT * FROM participants WHERE eventId = :eventId AND (fullName LIKE :query OR licenseNo LIKE :query) ORDER BY fullName ASC")
    fun searchParticipants(eventId: Long, query: String): Flow<List<Participant>>

    @Query("SELECT COUNT(*) FROM participants")
    fun getTotalParticipantCount(): Flow<Int>
}