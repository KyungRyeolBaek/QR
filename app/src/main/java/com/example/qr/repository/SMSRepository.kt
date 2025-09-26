package com.example.qr.repository

import com.example.qr.data.dao.SMSLogDao
import com.example.qr.data.entities.SMSLog
import kotlinx.coroutines.flow.Flow
class SMSRepository(
    private val smsLogDao: SMSLogDao
) {
    fun getAllSMSLogs(): Flow<List<SMSLog>> = smsLogDao.getAllSMSLogs()

    fun getSMSLogsByUser(userId: String): Flow<List<SMSLog>> =
        smsLogDao.getSMSLogsByUser(userId)

    suspend fun getSMSLogsByStatus(status: String): List<SMSLog> =
        smsLogDao.getSMSLogsByStatus(status)

    suspend fun getSuccessfulSMSCount(startDate: Long, endDate: Long): Int =
        smsLogDao.getSuccessfulSMSCount(startDate, endDate)

    suspend fun getFailedSMSCount(startDate: Long, endDate: Long): Int =
        smsLogDao.getFailedSMSCount(startDate, endDate)

    suspend fun insertSMSLog(smsLog: SMSLog): Long =
        smsLogDao.insertSMSLog(smsLog)

    suspend fun deleteOldSMSLogs(cutoffTime: Long) =
        smsLogDao.deleteOldSMSLogs(cutoffTime)

    // Business logic methods
    suspend fun recordSMSSent(userId: String, phoneNumber: String): Long {
        val smsLog = SMSLog(
            userId = userId,
            phoneNumber = phoneNumber,
            status = "SUCCESS"
        )
        return insertSMSLog(smsLog)
    }

    suspend fun recordSMSFailed(userId: String, phoneNumber: String, errorMessage: String): Long {
        val smsLog = SMSLog(
            userId = userId,
            phoneNumber = phoneNumber,
            status = "FAILED",
            errorMessage = errorMessage
        )
        return insertSMSLog(smsLog)
    }

    suspend fun getPendingSMSLogs(): List<SMSLog> = getSMSLogsByStatus("PENDING")

    suspend fun getFailedSMSLogs(): List<SMSLog> = getSMSLogsByStatus("FAILED")
}