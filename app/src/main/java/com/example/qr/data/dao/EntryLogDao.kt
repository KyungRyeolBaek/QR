package com.example.qr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.qr.data.entities.EntryLog
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryLogDao {

    @Query("SELECT * FROM entry_logs ORDER BY timestamp DESC")
    fun getAllEntryLogs(): Flow<List<EntryLog>>

    @Query("SELECT * FROM entry_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getEntryLogsByUser(userId: String): Flow<List<EntryLog>>

    @Query("""
        SELECT * FROM entry_logs
        WHERE timestamp BETWEEN :startDate AND :endDate
        ORDER BY timestamp DESC
    """)
    suspend fun getEntryLogsByDateRange(startDate: Long, endDate: Long): List<EntryLog>

    @Query("""
        SELECT * FROM entry_logs
        WHERE userId = :userId AND timestamp BETWEEN :startDate AND :endDate
        ORDER BY timestamp DESC
    """)
    suspend fun getEntryLogsByUserAndDateRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): List<EntryLog>

    @Query("""
        SELECT * FROM entry_logs
        WHERE userId = :userId
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLastEntryLogByUser(userId: String): EntryLog?

    @Query("""
        SELECT COUNT(*) FROM entry_logs
        WHERE entryType = 'ENTER' AND timestamp BETWEEN :startDate AND :endDate
    """)
    suspend fun getEntryCountByDateRange(startDate: Long, endDate: Long): Int

    @Query("""
        SELECT COUNT(*) FROM entry_logs
        WHERE userId = :userId AND entryType = 'ENTER'
    """)
    suspend fun getUserEntryCount(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryLog(entryLog: EntryLog): Long

    @Query("DELETE FROM entry_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEntryLogs(cutoffTime: Long)

    // 오늘 통계를 위한 쿼리들
    @Query("""
        SELECT COUNT(*) FROM entry_logs
        WHERE entryType = 'ENTER' AND timestamp BETWEEN :startOfDay AND :endOfDay
    """)
    suspend fun getTodayEntryCount(startOfDay: Long, endOfDay: Long): Int

    @Query("""
        SELECT COUNT(*) FROM entry_logs
        WHERE entryType = 'EXIT' AND timestamp BETWEEN :startOfDay AND :endOfDay
    """)
    suspend fun getTodayExitCount(startOfDay: Long, endOfDay: Long): Int

    @Query("""
        SELECT * FROM entry_logs
        WHERE timestamp BETWEEN :startOfDay AND :endOfDay
        ORDER BY timestamp DESC
    """)
    fun getTodayEntryLogs(startOfDay: Long, endOfDay: Long): Flow<List<EntryLog>>

    @Query("""
        SELECT DISTINCT userId FROM entry_logs
        WHERE timestamp BETWEEN :startOfDay AND :endOfDay
    """)
    suspend fun getTodayActiveUserIds(startOfDay: Long, endOfDay: Long): List<String>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT last.userId
            FROM (
                SELECT userId, MAX(timestamp) AS lastTimestamp
                FROM entry_logs
                WHERE timestamp <= :currentTime
                GROUP BY userId
            ) AS last
            JOIN entry_logs e
              ON e.userId = last.userId AND e.timestamp = last.lastTimestamp
            WHERE e.entryType = 'ENTER'
        )
    """)
    suspend fun getCurrentlyInsideCount(currentTime: Long): Int
}