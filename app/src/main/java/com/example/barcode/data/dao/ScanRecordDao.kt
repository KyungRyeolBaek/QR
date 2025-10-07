package com.example.barcode.data.dao

import androidx.room.*
import com.example.barcode.data.entity.ScanRecord
import com.example.barcode.data.entity.ScanType
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {

    @Query("SELECT * FROM scan_records WHERE eventId = :eventId ORDER BY scanTime DESC")
    fun getScanRecordsByEvent(eventId: Long): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE participantId = :participantId ORDER BY scanTime DESC")
    fun getScanRecordsByParticipant(participantId: Long): Flow<List<ScanRecord>>

    @Query("""
        SELECT * FROM scan_records
        WHERE participantId = :participantId AND eventId = :eventId
        ORDER BY scanTime DESC
        LIMIT 1
    """)
    suspend fun getLastScanRecord(participantId: Long, eventId: Long): ScanRecord?

    @Query("""
        SELECT * FROM scan_records
        WHERE participantId = :participantId AND eventId = :eventId AND scanType = :scanType
        ORDER BY scanTime ASC
        LIMIT 1
    """)
    suspend fun getFirstScanByType(participantId: Long, eventId: Long, scanType: ScanType): ScanRecord?

    @Query("""
        SELECT * FROM scan_records
        WHERE participantId = :participantId AND eventId = :eventId AND scanType = :scanType
        ORDER BY scanTime DESC
        LIMIT 1
    """)
    suspend fun getLastScanByType(participantId: Long, eventId: Long, scanType: ScanType): ScanRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRecord(scanRecord: ScanRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRecords(scanRecords: List<ScanRecord>): List<Long>

    @Update
    suspend fun updateScanRecord(scanRecord: ScanRecord)

    @Delete
    suspend fun deleteScanRecord(scanRecord: ScanRecord)

    @Query("DELETE FROM scan_records WHERE eventId = :eventId")
    suspend fun deleteScanRecordsByEvent(eventId: Long)

    @Query("DELETE FROM scan_records WHERE participantId = :participantId")
    suspend fun deleteScanRecordsByParticipant(participantId: Long): Int

    @Query("SELECT COUNT(*) FROM scan_records WHERE eventId = :eventId")
    suspend fun getScanRecordCount(eventId: Long): Int

    @Query("SELECT COUNT(*) FROM scan_records WHERE participantId = :participantId AND eventId = :eventId")
    suspend fun getScanRecordCountByParticipant(participantId: Long, eventId: Long): Int

    @Query("SELECT COUNT(*) FROM scan_records WHERE eventId = :eventId AND scanType = 'ENTRY'")
    suspend fun getEntryCount(eventId: Long): Int

    @Query("SELECT COUNT(*) FROM scan_records WHERE eventId = :eventId AND scanType = 'EXIT'")
    suspend fun getExitCount(eventId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM scan_records
    """)
    fun getTotalScanCount(): Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT participantId) FROM scan_records
        WHERE eventId = :eventId AND participantId IN (
            SELECT participantId FROM scan_records s1
            WHERE s1.eventId = :eventId
            AND s1.scanType = 'ENTRY'
            AND NOT EXISTS (
                SELECT 1 FROM scan_records s2
                WHERE s2.participantId = s1.participantId
                AND s2.eventId = :eventId
                AND s2.scanType = 'EXIT'
                AND s2.scanTime > s1.scanTime
            )
        )
    """)
    fun getCurrentInsideCount(eventId: Long): Flow<Int>

    @Query("""
        SELECT sr.*, p.fullName as participantName
        FROM scan_records sr
        INNER JOIN participants p ON sr.participantId = p.id
        WHERE sr.eventId = :eventId
        ORDER BY sr.scanTime DESC
        LIMIT :limit
    """)
    fun getRecentScansWithParticipants(eventId: Long, limit: Int): Flow<List<ScanRecordWithParticipant>>

    data class ScanRecordWithParticipant(
        val id: Long,
        val participantId: Long,
        val eventId: Long,
        val scanTime: Long,
        val scanType: ScanType,
        val deviceId: String?,
        val participantName: String
    )
}