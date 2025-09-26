package com.example.qr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.qr.data.entities.SMSLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SMSLogDao {

    @Query("SELECT * FROM sms_logs ORDER BY sentAt DESC")
    fun getAllSMSLogs(): Flow<List<SMSLog>>

    @Query("SELECT * FROM sms_logs WHERE userId = :userId ORDER BY sentAt DESC")
    fun getSMSLogsByUser(userId: String): Flow<List<SMSLog>>

    @Query("""
        SELECT * FROM sms_logs
        WHERE status = :status
        ORDER BY sentAt DESC
    """)
    suspend fun getSMSLogsByStatus(status: String): List<SMSLog>

    @Query("""
        SELECT COUNT(*) FROM sms_logs
        WHERE status = 'SUCCESS' AND sentAt BETWEEN :startDate AND :endDate
    """)
    suspend fun getSuccessfulSMSCount(startDate: Long, endDate: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sms_logs
        WHERE status = 'FAILED' AND sentAt BETWEEN :startDate AND :endDate
    """)
    suspend fun getFailedSMSCount(startDate: Long, endDate: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSMSLog(smsLog: SMSLog): Long

    @Query("DELETE FROM sms_logs WHERE sentAt < :cutoffTime")
    suspend fun deleteOldSMSLogs(cutoffTime: Long)
}