package com.example.qr.utils

import android.content.Context
import android.net.Uri
import com.example.qr.data.entity.Participant
import java.io.BufferedReader
import java.io.InputStreamReader

class CsvReader {
    companion object {
        /**
         * Read participants from CSV file with new template format:
         * 성명(국문), 성명(영문), 성명(한문), 휴대폰, 면허번호
         */
        fun readParticipantsFromCsv(
            context: Context,
            uri: Uri,
            eventId: Long
        ): Result<List<Participant>> {
            return try {
                val participants = mutableListOf<Participant>()

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        // Skip BOM if present
                        reader.mark(1)
                        if (reader.read() != 0xFEFF) {
                            reader.reset()
                        }

                        // Read and validate header
                        val header = reader.readLine() ?: throw Exception("CSV 파일이 비어있습니다")

                        // Read data rows
                        var lineNumber = 2
                        reader.lineSequence().forEach { line ->
                            if (line.isNotBlank()) {
                                try {
                                    val participant = parseCsvLine(line, eventId, lineNumber)
                                    participants.add(participant)
                                } catch (e: Exception) {
                                    throw Exception("${lineNumber}번째 줄 처리 실패: ${e.message}")
                                }
                            }
                            lineNumber++
                        }
                    }
                }

                if (participants.isEmpty()) {
                    Result.failure(Exception("유효한 참가자 데이터가 없습니다"))
                } else {
                    Result.success(participants)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        private fun parseCsvLine(line: String, eventId: Long, lineNumber: Int): Participant {
            val values = line.split(",").map { it.trim() }

            if (values.size < 5) {
                throw Exception("필수 컬럼이 부족합니다 (최소 5개 필요)")
            }

            val koreanName = values[0].removeQuotes()
            val englishName = values[1].removeQuotes()
            val chineseName = values[2].removeQuotes()
            val phoneNumber = values[3].removeQuotes().removePhoneFormatting()
            val licenseNo = values[4].removeQuotes()

            // QR코드 컬럼이 있으면 사용, 없거나 비어있으면 자동 생성
            val qrCodeFromCsv = if (values.size > 5) values[5].removeQuotes() else ""

            // Validate required fields
            if (koreanName.isBlank()) {
                throw Exception("성명(국문)이 비어있습니다")
            }
            if (phoneNumber.isBlank()) {
                throw Exception("휴대폰 번호가 비어있습니다")
            }
            if (licenseNo.isBlank()) {
                throw Exception("면허번호가 비어있습니다")
            }

            // QR 코드: CSV에 값이 있으면 사용, 없으면 면허번호 기반으로 자동 생성 (QR_ prefix)
            val barcodeData = if (qrCodeFromCsv.isNotBlank()) {
                qrCodeFromCsv
            } else {
                "QR_${licenseNo}"
            }

            return Participant(
                fullName = koreanName,
                englishName = englishName,
                chineseName = chineseName,
                phoneNumber = phoneNumber,
                licenseNo = licenseNo,
                barcodeData = barcodeData,
                eventId = eventId
            )
        }

        private fun String.removeQuotes(): String {
            var result = this.trim()
            // Remove Excel formula format: ="value"
            if (result.startsWith("=\"") && result.endsWith("\"")) {
                result = result.substring(2, result.length - 1)
            }
            // Remove regular quotes
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result = result.substring(1, result.length - 1)
            }
            return result.trim()
        }

        private fun String.removePhoneFormatting(): String {
            // Remove all non-digit characters except leading +
            return this.replace(Regex("[^0-9+]"), "")
        }
    }
}
