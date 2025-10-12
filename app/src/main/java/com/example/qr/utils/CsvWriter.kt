package com.example.qr.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.qr.data.entity.Event
import com.example.qr.data.entity.Participant
import com.example.qr.data.entity.ScanRecord
import com.example.qr.data.entity.ScanType
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class CsvWriter {

    companion object {
        /**
         * Export all scan records with participant info (detailed)
         * Each scan record becomes a separate row
         */
        fun exportEventDataWithAllScans(
            context: Context,
            event: Event,
            participantScans: List<Pair<Participant, List<ScanRecord>>>
        ): Result<File> {
            return try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                val fileName = "${event.eventName}_상세_${System.currentTimeMillis()}.csv"
                val file = File(context.getExternalFilesDir(null), fileName)

                FileWriter(file).use { writer ->
                    // UTF-8 BOM for Excel compatibility
                    writer.append('\uFEFF')

                    // CSV Header - QR코드 컬럼 추가
                    writer.append("성명(국문),성명(영문),성명(한문),휴대폰,소속,면허번호,QR코드,출입시간,출입유형\n")

                    // Data rows - one row per scan record
                    participantScans.forEach { (participant, scanRecords) ->
                        val koreanName = escapeCsv(participant.fullName)
                        val englishName = escapeCsv(participant.englishName)
                        val chineseName = escapeCsv(participant.chineseName)
                        val phone = "=\"${participant.phoneNumber}\""
                        val organization = escapeCsv(participant.organization)
                        val license = escapeCsv(participant.licenseNo)
                        val qrCode = escapeCsv(participant.barcodeData)

                        if (scanRecords.isEmpty()) {
                            // Participant with no scans
                            writer.append("$koreanName,$englishName,$chineseName,$phone,$organization,$license,$qrCode,,미입장\n")
                        } else {
                            // One row per scan record
                            scanRecords.sortedBy { it.scanTime }.forEach { scan ->
                                val scanTime = "=\"${dateFormat.format(Date(scan.scanTime))}\""
                                val scanType = when (scan.scanType) {
                                    ScanType.ENTRY -> "입장"
                                    ScanType.EXIT -> "퇴장"
                                }
                                writer.append("$koreanName,$englishName,$chineseName,$phone,$organization,$license,$qrCode,$scanTime,$scanType\n")
                            }
                        }
                    }
                }

                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Export current participants (unique, sorted by Korean name)
         * No scan records, just participant list
         */
        fun exportCurrentParticipants(
            context: Context,
            event: Event,
            participants: List<Participant>
        ): Result<File> {
            return try {
                val fileName = "${event.eventName}_참가자목록_${System.currentTimeMillis()}.csv"
                val file = File(context.getExternalFilesDir(null), fileName)

                FileWriter(file).use { writer ->
                    // UTF-8 BOM for Excel compatibility
                    writer.append('\uFEFF')

                    // CSV Header - QR코드 컬럼 추가
                    writer.append("성명(국문),성명(영문),성명(한문),휴대폰,소속,면허번호,QR코드\n")

                    // Sort by Korean name and export unique participants
                    participants.sortedBy { it.fullName }.forEach { participant ->
                        val koreanName = escapeCsv(participant.fullName)
                        val englishName = escapeCsv(participant.englishName)
                        val chineseName = escapeCsv(participant.chineseName)
                        val phone = "=\"${participant.phoneNumber}\""
                        val organization = escapeCsv(participant.organization)
                        val license = escapeCsv(participant.licenseNo)
                        val qrCode = escapeCsv(participant.barcodeData)

                        writer.append("$koreanName,$englishName,$chineseName,$phone,$organization,$license,$qrCode\n")
                    }
                }

                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Export empty CSV template for participant registration
         */
        fun exportParticipantTemplate(context: Context): Result<File> {
            return try {
                val fileName = "참가자_등록_템플릿_${System.currentTimeMillis()}.csv"
                val file = File(context.getExternalFilesDir(null), fileName)

                FileWriter(file).use { writer ->
                    // UTF-8 BOM for Excel compatibility
                    writer.append('\uFEFF')

                    // CSV Header - QR코드 컬럼 추가 (선택 사항)
                    writer.append("성명(국문),성명(영문),성명(한문),휴대폰,소속,면허번호,QR코드\n")

                    // Add sample rows - QR코드는 비워두면 자동 생성됨
                    writer.append("홍길동,Hong Gildong,洪吉童,010-1234-5678,서울병원,12345,\n")
                    writer.append("김철수,Kim Chulsoo,金哲洙,010-9876-5432,부산의료원,67890,QR_67890\n")
                }

                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Keep old method for compatibility (deprecated)
        @Deprecated("Use exportEventDataWithAllScans instead")
        fun exportEventData(
            context: Context,
            event: Event,
            participantsWithScanInfo: List<com.example.qr.data.entity.ParticipantWithScanInfo>
        ): Result<File> {
            return try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val fileName = "${event.eventName}_${System.currentTimeMillis()}.csv"
                val file = File(context.getExternalFilesDir(null), fileName)

                FileWriter(file).use { writer ->
                    writer.append('\uFEFF')
                    writer.append("이름,전화번호,출입 시각,출입 유형\n")

                    participantsWithScanInfo.forEach { participantInfo ->
                        val participant = participantInfo.participant
                        val name = escapeCsv(participant.fullName)
                        val phone = "=\"${participant.phoneNumber}\""
                        val scanTime = participantInfo.lastScanTime?.let {
                            dateFormat.format(Date(it))
                        } ?: ""
                        val scanType = when (participantInfo.lastScanType) {
                            ScanType.ENTRY -> "입장"
                            ScanType.EXIT -> "퇴장"
                            null -> "미입장"
                        }

                        writer.append("$name,$phone,$scanTime,$scanType\n")
                    }
                }

                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        fun shareCsvFile(context: Context, file: File) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "CSV 파일 공유"))
        }

        private fun escapeCsv(value: String): String {
            return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }
        }
    }
}
