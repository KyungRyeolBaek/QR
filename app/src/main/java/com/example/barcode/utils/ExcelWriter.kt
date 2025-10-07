package com.example.barcode.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.barcode.data.entity.Event
import com.example.barcode.data.entity.Participant
import com.example.barcode.data.entity.ParticipantWithScanInfo
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExcelWriter {

    companion object {
        fun exportEventData(
            context: Context,
            event: Event,
            participantsWithScanInfo: List<ParticipantWithScanInfo>
        ): Result<File> {
            return try {
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("${event.eventName} 출입 기록")

                // 헤더 스타일 생성
                val headerStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.LIGHT_BLUE.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                    borderBottom = BorderStyle.THIN
                    borderTop = BorderStyle.THIN
                    borderLeft = BorderStyle.THIN
                    borderRight = BorderStyle.THIN
                }

                val headerFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 12
                }
                headerStyle.setFont(headerFont)

                // 데이터 스타일 생성
                val dataStyle = workbook.createCellStyle().apply {
                    borderBottom = BorderStyle.THIN
                    borderTop = BorderStyle.THIN
                    borderLeft = BorderStyle.THIN
                    borderRight = BorderStyle.THIN
                }

                // 헤더 생성
                val headerRow = sheet.createRow(0)
                val headers = listOf(
                    "이름",
                    "전화번호",
                    "라이센스 번호",
                    "바코드 데이터",
                    "첫 입장 시간",
                    "마지막 스캔 시간",
                    "체류 시간",
                    "총 스캔 횟수",
                    "현재 상태"
                )

                headers.forEachIndexed { index, header ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = headerStyle
                }

                // 데이터 입력
                val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                participantsWithScanInfo.forEachIndexed { index, participantInfo ->
                    val row = sheet.createRow(index + 1)

                    // 이름
                    row.createCell(0).apply {
                        setCellValue(participantInfo.participant.fullName)
                        cellStyle = dataStyle
                    }

                    // 전화번호
                    row.createCell(1).apply {
                        setCellValue(participantInfo.participant.phoneNumber)
                        cellStyle = dataStyle
                    }

                    // 라이센스 번호
                    row.createCell(2).apply {
                        setCellValue(participantInfo.participant.licenseNo)
                        cellStyle = dataStyle
                    }

                    // 바코드 데이터
                    row.createCell(3).apply {
                        setCellValue(participantInfo.participant.barcodeData)
                        cellStyle = dataStyle
                    }

                    // 첫 입장 시간
                    row.createCell(4).apply {
                        setCellValue(
                            participantInfo.firstScanTime?.let {
                                dateFormatter.format(Date(it))
                            } ?: "미입장"
                        )
                        cellStyle = dataStyle
                    }

                    // 마지막 스캔 시간
                    row.createCell(5).apply {
                        setCellValue(
                            participantInfo.lastScanTime?.let {
                                dateFormatter.format(Date(it))
                            } ?: "기록없음"
                        )
                        cellStyle = dataStyle
                    }

                    // 체류 시간
                    row.createCell(6).apply {
                        setCellValue(participantInfo.formattedDuration)
                        cellStyle = dataStyle
                    }

                    // 총 스캔 횟수
                    row.createCell(7).apply {
                        setCellValue(participantInfo.totalScans.toDouble())
                        cellStyle = dataStyle
                    }

                    // 현재 상태
                    row.createCell(8).apply {
                        setCellValue(participantInfo.statusText)
                        cellStyle = dataStyle
                    }
                }

                // 컬럼 폭 자동 조정
                headers.indices.forEach { i ->
                    sheet.autoSizeColumn(i)
                }

                // 파일 저장
                val fileName = "${event.eventName}_출입기록_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
                val file = File(context.filesDir, fileName)

                val fileOutputStream = FileOutputStream(file)
                workbook.write(fileOutputStream)
                fileOutputStream.close()
                workbook.close()

                Result.success(file)

            } catch (e: Exception) {
                Result.failure(Exception("엑셀 파일 생성 실패: ${e.message}"))
            }
        }

        fun shareExcelFile(context: Context, file: File) {
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "이벤트 출입 기록")
                    putExtra(Intent.EXTRA_TEXT, "이벤트 출입 기록 데이터입니다.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, "엑셀 파일 공유"))

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun exportParticipantsTemplate(context: Context): Result<File> {
            return try {
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("참가자 템플릿")

                // 헤더 스타일
                val headerStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = IndexedColors.LIGHT_GREEN.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                    borderBottom = BorderStyle.THIN
                    borderTop = BorderStyle.THIN
                    borderLeft = BorderStyle.THIN
                    borderRight = BorderStyle.THIN
                }

                val headerFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 12
                }
                headerStyle.setFont(headerFont)

                // 헤더 생성
                val headerRow = sheet.createRow(0)
                val headers = listOf("이름", "전화번호", "라이센스번호")

                headers.forEachIndexed { index, header ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = headerStyle
                }

                // 예시 데이터 추가
                val exampleRow = sheet.createRow(1)
                exampleRow.createCell(0).setCellValue("홍길동")
                exampleRow.createCell(1).setCellValue("010-1234-5678")
                exampleRow.createCell(2).setCellValue("LIC123456")

                // 컬럼 폭 조정
                headers.indices.forEach { i ->
                    sheet.autoSizeColumn(i)
                }

                // 파일 저장
                val fileName = "참가자_템플릿_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
                val file = File(context.filesDir, fileName)

                val fileOutputStream = FileOutputStream(file)
                workbook.write(fileOutputStream)
                fileOutputStream.close()
                workbook.close()

                Result.success(file)

            } catch (e: Exception) {
                Result.failure(Exception("템플릿 파일 생성 실패: ${e.message}"))
            }
        }
    }
}