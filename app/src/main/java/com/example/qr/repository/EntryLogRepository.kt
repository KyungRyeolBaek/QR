package com.example.qr.repository

import com.example.qr.data.dao.EntryLogDao
import com.example.qr.data.entities.EntryLog
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone
class EntryLogRepository(
    private val entryLogDao: EntryLogDao
) {
    fun getAllEntryLogs(): Flow<List<EntryLog>> = entryLogDao.getAllEntryLogs()

    fun getEntryLogsByUser(userId: String): Flow<List<EntryLog>> =
        entryLogDao.getEntryLogsByUser(userId)

    suspend fun getEntryLogsByDateRange(startDate: Long, endDate: Long): List<EntryLog> =
        entryLogDao.getEntryLogsByDateRange(startDate, endDate)

    suspend fun getEntryLogsByUserAndDateRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): List<EntryLog> = entryLogDao.getEntryLogsByUserAndDateRange(userId, startDate, endDate)

    suspend fun getLastEntryLogByUser(userId: String): EntryLog? =
        entryLogDao.getLastEntryLogByUser(userId)

    suspend fun getEntryCountByDateRange(startDate: Long, endDate: Long): Int =
        entryLogDao.getEntryCountByDateRange(startDate, endDate)

    suspend fun getUserEntryCount(userId: String): Int =
        entryLogDao.getUserEntryCount(userId)

    suspend fun insertEntryLog(entryLog: EntryLog): Long =
        entryLogDao.insertEntryLog(entryLog)

    suspend fun deleteOldEntryLogs(cutoffTime: Long) =
        entryLogDao.deleteOldEntryLogs(cutoffTime)

    // Business logic methods
    suspend fun isUserCurrentlyInside(userId: String): Boolean {
        val lastEntry = getLastEntryLogByUser(userId)
        return lastEntry?.entryType == "ENTER"
    }

    suspend fun recordEntry(userId: String, userName: String, location: String? = null): Long {
        val entryLog = EntryLog(
            userId = userId,
            userName = userName,
            entryType = "ENTER",
            location = location
        )
        return insertEntryLog(entryLog)
    }

    suspend fun recordExit(userId: String, userName: String, location: String? = null): Long {
        val entryLog = EntryLog(
            userId = userId,
            userName = userName,
            entryType = "EXIT",
            location = location
        )
        return insertEntryLog(entryLog)
    }

    // 오늘 통계를 위한 함수들
    private fun getTodayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))

        // 오늘 00:00:00
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        // 오늘 23:59:59
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return Pair(startOfDay, endOfDay)
    }

    suspend fun getTodayStats(): TodayStats {
        val (startOfDay, endOfDay) = getTodayRange()
        val currentTime = System.currentTimeMillis()

        val totalEntries = entryLogDao.getTodayEntryCount(startOfDay, endOfDay)
        val totalExits = entryLogDao.getTodayExitCount(startOfDay, endOfDay)
        val currentlyInside = entryLogDao.getCurrentlyInsideCount(currentTime)

        return TodayStats(
            totalEntries = totalEntries,
            totalExits = totalExits,
            currentlyInside = currentlyInside
        )
    }

    fun getTodayEntryLogsFlow(): Flow<List<EntryLog>> {
        val (startOfDay, endOfDay) = getTodayRange()
        return entryLogDao.getTodayEntryLogs(startOfDay, endOfDay)
    }

    suspend fun getTodayActiveUserCount(): Int {
        val (startOfDay, endOfDay) = getTodayRange()
        return entryLogDao.getTodayActiveUserIds(startOfDay, endOfDay).size
    }
}

data class TodayStats(
    val totalEntries: Int,
    val totalExits: Int,
    val currentlyInside: Int
)