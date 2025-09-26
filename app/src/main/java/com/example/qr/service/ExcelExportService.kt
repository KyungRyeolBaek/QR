package com.example.qr.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.qr.data.entities.EntryLog
import com.example.qr.data.entities.User
import com.example.qr.repository.EntryLogRepository
import com.example.qr.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
class ExcelExportService(
    private val context: Context,
    private val userRepository: UserRepository,
    private val entryLogRepository: EntryLogRepository
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 전체 출입 기록 엑셀 파일 생성
     */
    suspend fun exportAllEntryLogs(
        startDate: Long,
        endDate: Long
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("출입기록")

            // 스타일 생성
            val headerStyle = createHeaderStyle(workbook)
            val dateStyle = createDateStyle(workbook)

            // 헤더 행 생성
            val headerRow = sheet.createRow(0)
            val headers = listOf("번호", "이름", "전화번호", "출입타입", "출입시간", "위치")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.setCellStyle(headerStyle)
            }

            // 데이터 조회
            val entryLogs = entryLogRepository.getEntryLogsByDateRange(startDate, endDate)

            // N+1 쿼리 문제 해결: 모든 사용자를 한 번에 조회하여 맵으로 변환
            val allUsers = userRepository.getAllUsers().first()
            val userMap = allUsers.associateBy { it.id }

            // 데이터 행 생성
            entryLogs.forEachIndexed { index, entry ->
                val row = sheet.createRow(index + 1)

                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue(entry.userName)

                // 사용자 정보 조회 (전화번호) - 맵에서 즉시 조회
                val user = userMap[entry.userId]
                row.createCell(2).setCellValue(user?.phoneNumber ?: "알수없음")

                row.createCell(3).setCellValue(if (entry.entryType == "ENTER") "입장" else "퇴장")

                val timeCell = row.createCell(4)
                timeCell.setCellValue(timeFormat.format(Date(entry.timestamp)))
                timeCell.setCellStyle(dateStyle)

                row.createCell(5).setCellValue(entry.location ?: "")
            }

            // 열 너비 고정 설정 (Android AWT 호환성)
            sheet.setColumnWidth(0, 10 * 256)  // 번호
            sheet.setColumnWidth(1, 20 * 256)  // 이름
            sheet.setColumnWidth(2, 25 * 256)  // 전화번호
            sheet.setColumnWidth(3, 15 * 256)  // 출입타입
            sheet.setColumnWidth(4, 30 * 256)  // 출입시간
            sheet.setColumnWidth(5, 20 * 256)  // 위치

            val fileName = "출입기록_${dateFormat.format(Date(startDate))}_${dateFormat.format(Date(endDate))}.xlsx"
            val file = saveWorkbook(workbook, fileName)
            workbook.close()

            Result.success(file)

        } catch (e: NoClassDefFoundError) {
            Result.failure(Exception("현재 기기에서 엑셀 자동 열폭 조정을 지원하지 않습니다. 고정 폭으로 내보냅니다.", e))
        } catch (e: Exception) {
            Result.failure(Exception("엑셀 내보내기 중 오류가 발생했습니다: ${e.message}", e))
        }
    }

    /**
     * 사용자별 상세 출입 기록
     */
    suspend fun exportUserEntryLogs(
        userId: String,
        startDate: Long,
        endDate: Long
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val user = userRepository.getUserById(userId)
                ?: return@withContext Result.failure(Exception("사용자를 찾을 수 없습니다"))

            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("${user.name}_출입기록")

            val headerStyle = createHeaderStyle(workbook)
            val dateStyle = createDateStyle(workbook)

            // 사용자 정보 섹션
            var currentRow = 0
            val userInfoRow = sheet.createRow(currentRow++)
            userInfoRow.createCell(0).setCellValue("사용자 정보")
            userInfoRow.getCell(0).setCellStyle(headerStyle)

            val nameRow = sheet.createRow(currentRow++)
            nameRow.createCell(0).setCellValue("이름:")
            nameRow.createCell(1).setCellValue(user.name)

            val phoneRow = sheet.createRow(currentRow++)
            phoneRow.createCell(0).setCellValue("전화번호:")
            phoneRow.createCell(1).setCellValue(user.phoneNumber)

            val registrationRow = sheet.createRow(currentRow++)
            registrationRow.createCell(0).setCellValue("등록일:")
            registrationRow.createCell(1).setCellValue(dateFormat.format(Date(user.createdAt)))

            // 빈 줄
            currentRow++

            // 출입 기록 헤더
            val headerRow = sheet.createRow(currentRow++)
            val headers = listOf("번호", "출입타입", "출입시간", "근무시간", "위치")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.setCellStyle(headerStyle)
            }

            // 출입 기록 데이터
            val entryLogs = entryLogRepository.getEntryLogsByUserAndDateRange(userId, startDate, endDate)

            entryLogs.forEachIndexed { index, entry ->
                val row = sheet.createRow(currentRow + index)

                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue(if (entry.entryType == "ENTER") "입장" else "퇴장")

                val timeCell = row.createCell(2)
                timeCell.setCellValue(timeFormat.format(Date(entry.timestamp)))
                timeCell.setCellStyle(dateStyle)

                // 근무시간 계산 (입장-퇴장 쌍)
                if (entry.entryType == "EXIT" && index > 0) {
                    val previousEntry = entryLogs[index - 1]
                    if (previousEntry.entryType == "ENTER") {
                        val workingHours = (entry.timestamp - previousEntry.timestamp) / (1000 * 60 * 60.0)
                        row.createCell(3).setCellValue(String.format("%.1f시간", workingHours))
                    }
                } else {
                    row.createCell(3).setCellValue("")
                }

                row.createCell(4).setCellValue(entry.location ?: "")
            }

            // 열 너비 고정 설정 (Android AWT 호환성)
            sheet.setColumnWidth(0, 10 * 256)  // 번호
            sheet.setColumnWidth(1, 15 * 256)  // 출입타입
            sheet.setColumnWidth(2, 30 * 256)  // 출입시간
            sheet.setColumnWidth(3, 20 * 256)  // 근무시간
            sheet.setColumnWidth(4, 20 * 256)  // 위치

            val fileName = "${user.name}_출입기록_${dateFormat.format(Date(startDate))}.xlsx"
            val file = saveWorkbook(workbook, fileName)
            workbook.close()

            Result.success(file)

        } catch (e: NoClassDefFoundError) {
            Result.failure(Exception("현재 기기에서 엑셀 자동 열폭 조정을 지원하지 않습니다. 고정 폭으로 내보냅니다.", e))
        } catch (e: Exception) {
            Result.failure(Exception("엑셀 내보내기 중 오류가 발생했습니다: ${e.message}", e))
        }
    }

    /**
     * 사용자 목록 엑셀 파일 생성
     */
    suspend fun exportUserList(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("사용자목록")

            val headerStyle = createHeaderStyle(workbook)
            val dateStyle = createDateStyle(workbook)

            // 헤더 행
            val headerRow = sheet.createRow(0)
            val headers = listOf("번호", "이름", "전화번호", "등록일", "SMS상태", "활성상태", "총출입횟수")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.setCellStyle(headerStyle)
            }

            // 사용자 데이터 조회 (Flow 무한 대기 문제 해결)
            val users = userRepository.getAllUsers().first()

            // 데이터 행 생성
            users.forEachIndexed { index, user ->
                val row = sheet.createRow(index + 1)

                row.createCell(0).setCellValue((index + 1).toDouble())
                row.createCell(1).setCellValue(user.name)
                row.createCell(2).setCellValue(user.phoneNumber)

                val dateCell = row.createCell(3)
                dateCell.setCellValue(dateFormat.format(Date(user.createdAt)))
                dateCell.setCellStyle(dateStyle)

                row.createCell(4).setCellValue(
                    when (user.smsStatus) {
                        "SUCCESS" -> "발송완료"
                        "PENDING" -> "발송대기"
                        "FAILED" -> "발송실패"
                        else -> "알수없음"
                    }
                )

                row.createCell(5).setCellValue(if (user.isActive) "활성" else "비활성")

                // 출입 횟수 계산
                val entryCount = entryLogRepository.getUserEntryCount(user.id)
                row.createCell(6).setCellValue(entryCount.toDouble())
            }

            // 열 너비 고정 설정 (Android AWT 호환성)
            sheet.setColumnWidth(0, 10 * 256)  // 번호
            sheet.setColumnWidth(1, 20 * 256)  // 이름
            sheet.setColumnWidth(2, 25 * 256)  // 전화번호
            sheet.setColumnWidth(3, 20 * 256)  // 등록일
            sheet.setColumnWidth(4, 15 * 256)  // SMS상태
            sheet.setColumnWidth(5, 15 * 256)  // 활성상태
            sheet.setColumnWidth(6, 18 * 256)  // 총출입횟수

            val fileName = "사용자목록_${dateFormat.format(Date())}.xlsx"
            val file = saveWorkbook(workbook, fileName)
            workbook.close()

            Result.success(file)

        } catch (e: NoClassDefFoundError) {
            Result.failure(Exception("현재 기기에서 엑셀 자동 열폭 조정을 지원하지 않습니다. 고정 폭으로 내보냅니다.", e))
        } catch (e: Exception) {
            Result.failure(Exception("엑셀 내보내기 중 오류가 발생했습니다: ${e.message}", e))
        }
    }

    /**
     * 일별 통계 리포트
     */
    suspend fun exportDailyStatistics(
        startDate: Long,
        endDate: Long
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("일별통계")

            val headerStyle = createHeaderStyle(workbook)
            val dateStyle = createDateStyle(workbook)

            // 헤더 행
            val headerRow = sheet.createRow(0)
            val headers = listOf("날짜", "총출입자수", "총입장", "총퇴장", "평균근무시간", "최대근무시간", "최소근무시간")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.setCellStyle(headerStyle)
            }

            // 날짜별 데이터 생성
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startDate
            var rowIndex = 1

            while (calendar.timeInMillis <= endDate) {
                val dayStart = calendar.timeInMillis
                val dayEnd = dayStart + (24 * 60 * 60 * 1000) - 1

                val dayEntries = entryLogRepository.getEntryLogsByDateRange(dayStart, dayEnd)

                if (dayEntries.isNotEmpty()) {
                    val row = sheet.createRow(rowIndex++)

                    val dateCell = row.createCell(0)
                    dateCell.setCellValue(dateFormat.format(Date(dayStart)))
                    dateCell.setCellStyle(dateStyle)

                    val uniqueUsers = dayEntries.map { it.userId }.distinct()
                    val totalEntries = dayEntries.count { it.entryType == "ENTER" }
                    val totalExits = dayEntries.count { it.entryType == "EXIT" }

                    row.createCell(1).setCellValue(uniqueUsers.size.toDouble())
                    row.createCell(2).setCellValue(totalEntries.toDouble())
                    row.createCell(3).setCellValue(totalExits.toDouble())

                    // 근무시간 통계 계산 (간단화)
                    val workingHours = calculateDailyWorkingHours(dayEntries)
                    if (workingHours.isNotEmpty()) {
                        val avgHours = workingHours.average()
                        val maxHours = workingHours.maxOrNull() ?: 0.0
                        val minHours = workingHours.minOrNull() ?: 0.0

                        row.createCell(4).setCellValue(String.format("%.1f시간", avgHours))
                        row.createCell(5).setCellValue(String.format("%.1f시간", maxHours))
                        row.createCell(6).setCellValue(String.format("%.1f시간", minHours))
                    } else {
                        row.createCell(4).setCellValue("N/A")
                        row.createCell(5).setCellValue("N/A")
                        row.createCell(6).setCellValue("N/A")
                    }
                }

                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            // 열 너비 고정 설정 (Android AWT 호환성)
            sheet.setColumnWidth(0, 20 * 256)  // 날짜
            sheet.setColumnWidth(1, 18 * 256)  // 총출입자수
            sheet.setColumnWidth(2, 15 * 256)  // 총입장
            sheet.setColumnWidth(3, 15 * 256)  // 총퇴장
            sheet.setColumnWidth(4, 20 * 256)  // 평균근무시간
            sheet.setColumnWidth(5, 20 * 256)  // 최대근무시간
            sheet.setColumnWidth(6, 20 * 256)  // 최소근무시간

            val fileName = "일별통계_${dateFormat.format(Date(startDate))}_${dateFormat.format(Date(endDate))}.xlsx"
            val file = saveWorkbook(workbook, fileName)
            workbook.close()

            Result.success(file)

        } catch (e: NoClassDefFoundError) {
            Result.failure(Exception("현재 기기에서 엑셀 자동 열폭 조정을 지원하지 않습니다. 고정 폭으로 내보냅니다.", e))
        } catch (e: Exception) {
            Result.failure(Exception("엑셀 내보내기 중 오류가 발생했습니다: ${e.message}", e))
        }
    }

    /**
     * 엑셀 파일 공유 인텐트 생성
     */
    fun shareExcelFile(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "QR 출입 관리 리포트")
            putExtra(Intent.EXTRA_TEXT, "QR 출입 관리 시스템에서 생성된 리포트입니다.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(intent, "엑셀 파일 공유")
    }

    /**
     * 파일 크기가 MMS 제한을 초과하는지 확인
     */
    fun isFileSizeExceedsMMSLimit(file: File): Boolean {
        val fileSizeMB = file.length() / (1024 * 1024.0)
        return fileSizeMB > 2.0
    }

    /**
     * 파일 크기 정보 반환
     */
    fun getFileSizeInfo(file: File): String {
        val fileSizeMB = file.length() / (1024 * 1024.0)
        return String.format("%.1f", fileSizeMB)
    }

    // Private Helper Methods

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()

        font.bold = true
        font.fontHeightInPoints = 12
        style.setFont(font)

        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND

        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN

        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER

        return style
    }

    private fun createDateStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val format = workbook.createDataFormat()
        style.dataFormat = format.getFormat("yyyy-mm-dd hh:mm:ss")
        return style
    }

    private fun saveWorkbook(workbook: Workbook, fileName: String): File {
        val exportsDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }

        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }

        return file
    }

    private fun calculateDailyWorkingHours(entries: List<EntryLog>): List<Double> {
        val workingHours = mutableListOf<Double>()

        // 사용자별로 그룹화
        val entriesByUser = entries.groupBy { it.userId }

        entriesByUser.forEach { (_, userEntries) ->
            val sortedEntries = userEntries.sortedBy { it.timestamp }

            var i = 0
            while (i < sortedEntries.size - 1) {
                val current = sortedEntries[i]
                val next = sortedEntries[i + 1]

                if (current.entryType == "ENTER" && next.entryType == "EXIT") {
                    val hours = (next.timestamp - current.timestamp) / (1000 * 60 * 60.0)
                    workingHours.add(hours)
                    i += 2 // 쌍을 처리했으므로 2칸 이동
                } else {
                    i++
                }
            }
        }

        return workingHours
    }
}