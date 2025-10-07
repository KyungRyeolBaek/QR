package com.example.qr.utils

import android.content.Context
import android.net.Uri
import com.example.qr.data.entity.Participant
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.util.*

class ExcelReader {

    data class ExcelParticipant(
        val fullName: String,
        val phoneNumber: String,
        val licenseNo: String = ""
    )

    companion object {
        suspend fun readParticipantsFromExcel(
            context: Context,
            uri: Uri,
            eventId: Long
        ): Result<List<Participant>> {
            return try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    return Result.failure(Exception("파일을 열 수 없습니다."))
                }

                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0) // 첫 번째 시트 사용
                val participants = mutableListOf<Participant>()

                // 첫 번째 행은 헤더로 건너뛰기
                for (rowIndex in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    try {
                        // 컬럼 읽기 (A: 이름, B: 전화번호, C: 라이센스 번호)
                        val nameCell = row.getCell(0)
                        val phoneCell = row.getCell(1)
                        val licenseCell = row.getCell(2)

                        val fullName = when {
                            nameCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                nameCell.stringCellValue.trim()
                            nameCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                nameCell.numericCellValue.toString().trim()
                            else -> ""
                        }

                        val phoneNumber = when {
                            phoneCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                phoneCell.stringCellValue.trim()
                            phoneCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                phoneCell.numericCellValue.toLong().toString()
                            else -> ""
                        }

                        val licenseNo = when {
                            licenseCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                licenseCell.stringCellValue.trim()
                            licenseCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                licenseCell.numericCellValue.toString().trim()
                            else -> generateLicenseNumber()
                        }

                        // 필수 필드 검증
                        if (fullName.isNotEmpty() && phoneNumber.isNotEmpty()) {
                            val barcodeData = generateBarcodeData(fullName, phoneNumber, licenseNo)

                            participants.add(
                                Participant(
                                    fullName = fullName,
                                    phoneNumber = phoneNumber,
                                    licenseNo = licenseNo.ifEmpty { generateLicenseNumber() },
                                    barcodeData = barcodeData,
                                    eventId = eventId
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // 개별 행 처리 실패는 로그만 남기고 계속 진행
                        println("Row $rowIndex processing error: ${e.message}")
                    }
                }

                workbook.close()
                inputStream.close()

                if (participants.isEmpty()) {
                    Result.failure(Exception("유효한 참가자 데이터를 찾을 수 없습니다.\n엑셀 파일 형식을 확인해주세요.\n(A열: 이름, B열: 전화번호, C열: 라이센스번호)"))
                } else {
                    Result.success(participants)
                }

            } catch (e: Exception) {
                Result.failure(Exception("엑셀 파일 읽기 실패: ${e.message}"))
            }
        }

        private fun generateBarcodeData(fullName: String, phoneNumber: String, licenseNo: String): String {
            // QR 코드 데이터: 이벤트별 고유 식별자 생성
            val timestamp = System.currentTimeMillis()
            val hash = "${fullName}_${phoneNumber}_${licenseNo}_$timestamp".hashCode()
            return "BC${Math.abs(hash)}"
        }

        private fun generateLicenseNumber(): String {
            // 라이센스 번호가 없는 경우 자동 생성
            val random = Random()
            return "LIC${String.format("%06d", random.nextInt(1000000))}"
        }

        data class ExcelPreview(
            val totalRows: Int,
            val validRows: Int,
            val invalidRows: Int,
            val previewData: List<ExcelParticipant>,
            val errors: List<String>
        )

        fun previewExcelFile(context: Context, uri: Uri): Result<ExcelPreview> {
            return try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    return Result.failure(Exception("파일을 열 수 없습니다."))
                }

                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)

                val previewData = mutableListOf<ExcelParticipant>()
                val errors = mutableListOf<String>()
                var validRows = 0
                var invalidRows = 0

                // 최대 10개 행만 미리보기
                val maxPreviewRows = minOf(10, sheet.lastRowNum)

                for (rowIndex in 1..maxPreviewRows) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    try {
                        val nameCell = row.getCell(0)
                        val phoneCell = row.getCell(1)
                        val licenseCell = row.getCell(2)

                        val fullName = when {
                            nameCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                nameCell.stringCellValue.trim()
                            nameCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                nameCell.numericCellValue.toString().trim()
                            else -> ""
                        }

                        val phoneNumber = when {
                            phoneCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                phoneCell.stringCellValue.trim()
                            phoneCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                phoneCell.numericCellValue.toLong().toString()
                            else -> ""
                        }

                        val licenseNo = when {
                            licenseCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                licenseCell.stringCellValue.trim()
                            licenseCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                licenseCell.numericCellValue.toString().trim()
                            else -> ""
                        }

                        if (fullName.isNotEmpty() && phoneNumber.isNotEmpty()) {
                            previewData.add(ExcelParticipant(fullName, phoneNumber, licenseNo.ifEmpty { "자동생성" }))
                            validRows++
                        } else {
                            invalidRows++
                            errors.add("행 ${rowIndex + 1}: 이름 또는 전화번호가 비어있습니다")
                        }

                    } catch (e: Exception) {
                        invalidRows++
                        errors.add("행 ${rowIndex + 1}: ${e.message}")
                    }
                }

                workbook.close()
                inputStream.close()

                Result.success(ExcelPreview(
                    totalRows = sheet.lastRowNum,
                    validRows = validRows,
                    invalidRows = invalidRows,
                    previewData = previewData,
                    errors = errors
                ))

            } catch (e: Exception) {
                Result.failure(Exception("엑셀 파일 미리보기 실패: ${e.message}"))
            }
        }

        fun validateExcelFormat(context: Context, uri: Uri): Result<String> {
            return try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    return Result.failure(Exception("파일을 열 수 없습니다."))
                }

                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)

                val headerRow = sheet.getRow(0)
                if (headerRow == null) {
                    return Result.failure(Exception("헤더 행이 없습니다."))
                }

                val totalRows = sheet.lastRowNum
                workbook.close()
                inputStream.close()

                Result.success("유효한 엑셀 파일입니다. (총 ${totalRows}개 행)")

            } catch (e: Exception) {
                Result.failure(Exception("엑셀 파일 형식 오류: ${e.message}"))
            }
        }
    }
}