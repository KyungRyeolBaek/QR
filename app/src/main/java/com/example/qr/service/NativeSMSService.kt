package com.example.qr.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.qr.utils.QRCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NativeSMSService(
    private val context: Context
) {

    /**
     * QR 코드와 함께 SMS 발송 (네이티브 Android SMS)
     */
    suspend fun sendQRCodeSMS(
        name: String,
        phoneNumber: String,
        qrBitmap: Bitmap,
        attachImage: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // SMS 권한 확인
            if (!hasPermissions()) {
                return@withContext Result.failure(
                    SecurityException("SMS 전송 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
                )
            }

            // 전화번호 유효성 검증
            val cleanedPhoneNumber = cleanPhoneNumber(phoneNumber)
            if (!isValidPhoneNumber(cleanedPhoneNumber)) {
                return@withContext Result.failure(
                    IllegalArgumentException("유효하지 않은 전화번호 형식입니다: $phoneNumber")
                )
            }

            // 설정에서 메시지 템플릿 가져오기
            val message = getCustomMessageTemplate(name)

            if (attachImage) {
                // QR 이미지를 저장하고 MMS 전송
                val imageUri = saveQRImageToFile(qrBitmap, name)
                sendMMS(cleanedPhoneNumber, message, imageUri)
            } else {
                // 텍스트 전용 SMS 전송
                sendSMS(cleanedPhoneNumber, message)
            }

            Result.success("SMS가 성공적으로 전송되었습니다: $cleanedPhoneNumber")

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 단순 텍스트 SMS 발송
     */
    suspend fun sendTextSMS(
        phoneNumber: String,
        message: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!hasPermissions()) {
                return@withContext Result.failure(
                    SecurityException("SMS 전송 권한이 필요합니다.")
                )
            }

            val cleanedPhoneNumber = cleanPhoneNumber(phoneNumber)
            sendSMS(cleanedPhoneNumber, message)

            Result.success("SMS 발송 완료: $cleanedPhoneNumber")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * QR 코드 재발송
     */
    suspend fun resendQRCode(
        name: String,
        phoneNumber: String,
        userId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!hasPermissions()) {
                return@withContext Result.failure(
                    SecurityException("SMS 전송 권한이 필요합니다.")
                )
            }

            // 새로운 QR 데이터 생성
            val qrData = QRCodeGenerator.generateSecureQRData(
                userId = userId,
                userName = name,
                phoneNumber = phoneNumber
            )

            // QR 비트맵 생성
            val qrBitmap = QRCodeGenerator.generateQRBitmap(qrData)

            // SMS 전송
            return@withContext sendQRCodeSMS(name, phoneNumber, qrBitmap)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 실제 SMS 전송 (Android SmsManager 사용)
     */
    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()

            // 긴 메시지인 경우 여러 개로 분할
            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null
                )
            }

            // 로그 출력
            android.util.Log.d("NativeSMSService", "SMS 전송 완료: $phoneNumber")
            android.util.Log.d("NativeSMSService", "메시지: $message")

        } catch (e: Exception) {
            android.util.Log.e("NativeSMSService", "SMS 전송 실패", e)
            throw e
        }
    }

    /**
     * QR 이미지를 파일로 저장
     */
    private fun saveQRImageToFile(qrBitmap: Bitmap, userName: String): Uri {
        val filename = "qr_${userName.replace(" ", "_")}_${System.currentTimeMillis()}.png"
        val qrImagesDir = File(context.getExternalFilesDir(null), "qr_images")

        // 디렉토리 생성
        if (!qrImagesDir.exists()) {
            qrImagesDir.mkdirs()
        }

        val file = File(qrImagesDir, filename)

        try {
            FileOutputStream(file).use { out ->
                // PNG 형식으로 최고 품질로 저장
                val success = qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()

                if (!success) {
                    throw Exception("비트맵 압축 실패")
                }
            }

            android.util.Log.d("NativeSMSService", "QR 이미지 저장 완료: ${file.absolutePath}")
            android.util.Log.d("NativeSMSService", "파일 크기: ${file.length()} bytes")
            android.util.Log.d("NativeSMSService", "파일 존재 여부: ${file.exists()}")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            android.util.Log.d("NativeSMSService", "생성된 URI: $uri")
            return uri

        } catch (e: Exception) {
            android.util.Log.e("NativeSMSService", "QR 이미지 저장 실패", e)
            throw e
        }
    }

    /**
     * MMS 전송 (텍스트 + 이미지)
     */
    private fun sendMMS(phoneNumber: String, message: String, imageUri: Uri) {
        try {
            // 파일 MIME 타입을 명시적으로 설정
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                // MIME 타입 확인
                val mimeType = context.contentResolver.getType(imageUri) ?: "image/png"
                android.util.Log.d("NativeSMSService", "이미지 MIME 타입: $mimeType")
            }

            // MMS 전송을 위한 Intent 방식 사용 - ACTION_SEND 사용
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra("address", phoneNumber)
                putExtra("sms_body", message)
                putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
                putExtra(android.content.Intent.EXTRA_TEXT, message)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // MMS 앱 선택 다이얼로그 표시
            val chooser = android.content.Intent.createChooser(intent, "MMS로 QR코드 전송")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            android.util.Log.d("NativeSMSService", "MMS 전송 Intent 실행: $phoneNumber")
            android.util.Log.d("NativeSMSService", "메시지: $message")
            android.util.Log.d("NativeSMSService", "이미지 URI: $imageUri")

        } catch (e: Exception) {
            android.util.Log.e("NativeSMSService", "MMS 전송 실패", e)

            // MMS 실패시 텍스트만 SMS로 전송
            sendSMS(phoneNumber, message + "\n\n※ QR 이미지는 별도로 전송됩니다.")
        }
    }

    /**
     * 엑셀 파일 MMS 전송 (QR 이미지와 동일한 방식)
     */
    suspend fun sendExcelFileMMS(
        phoneNumber: String,
        file: File,
        message: String = "QR 출입 관리 시스템에서 생성된 엑셀 리포트입니다."
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // SMS 권한 확인
            if (!hasPermissions()) {
                return@withContext Result.failure(
                    SecurityException("SMS 전송 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
                )
            }

            // 전화번호 유효성 검증
            val cleanedPhoneNumber = cleanPhoneNumber(phoneNumber)
            if (!isValidPhoneNumber(cleanedPhoneNumber)) {
                return@withContext Result.failure(
                    IllegalArgumentException("유효하지 않은 전화번호 형식입니다: $phoneNumber")
                )
            }

            // 파일 존재 여부 확인
            if (!file.exists()) {
                return@withContext Result.failure(
                    Exception("전송할 파일이 존재하지 않습니다: ${file.name}")
                )
            }

            // FileProvider URI 생성
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // 파일 크기 확인 (2MB 제한)
            val fileSizeMB = file.length() / (1024 * 1024.0)
            if (fileSizeMB > 2.0) {
                return@withContext Result.failure(
                    Exception("파일이 너무 큽니다 (${String.format("%.1f", fileSizeMB)}MB). MMS는 2MB 이하만 가능합니다.")
                )
            }

            // MMS 전송
            sendExcelMMS(cleanedPhoneNumber, message, fileUri, file.name)

            Result.success("엑셀 파일이 성공적으로 전송되었습니다: $cleanedPhoneNumber (${String.format("%.1f", fileSizeMB)}MB)")

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 엑셀 파일 메신저 앱 선택 전송
     */
    suspend fun sendExcelFileToMessenger(
        file: File,
        message: String = "QR 출입 관리 시스템에서 생성된 엑셀 리포트입니다."
    ): Result<android.content.Intent> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(
                    Exception("전송할 파일이 존재하지 않습니다: ${file.name}")
                )
            }

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                putExtra(android.content.Intent.EXTRA_TEXT, message)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "엑셀 리포트")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = android.content.Intent.createChooser(intent, "엑셀 파일 전송 방법 선택")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

            Result.success(chooser)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 엑셀 파일 MMS 전송 (내부 메서드)
     */
    private fun sendExcelMMS(phoneNumber: String, message: String, fileUri: Uri, fileName: String) {
        try {
            // MIME 타입 확인
            val mimeType = context.contentResolver.getType(fileUri)
                ?: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            android.util.Log.d("NativeSMSService", "엑셀 파일 MIME 타입: $mimeType")

            // MMS 전송을 위한 Intent
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra("address", phoneNumber)
                putExtra("sms_body", message)
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                putExtra(android.content.Intent.EXTRA_TEXT, message)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // MMS 앱 선택 다이얼로그 표시
            val chooser = android.content.Intent.createChooser(intent, "엑셀 파일을 MMS로 전송")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            android.util.Log.d("NativeSMSService", "엑셀 MMS 전송 Intent 실행: $phoneNumber")
            android.util.Log.d("NativeSMSService", "파일명: $fileName")
            android.util.Log.d("NativeSMSService", "메시지: $message")

        } catch (e: Exception) {
            android.util.Log.e("NativeSMSService", "엑셀 MMS 전송 실패", e)
            throw e
        }
    }

    /**
     * 설정에서 메시지 템플릿 가져오기
     */
    private fun getCustomMessageTemplate(name: String): String {
        val prefs = context.getSharedPreferences("qr_settings", Context.MODE_PRIVATE)
        val template = prefs.getString("message_template", getDefaultTemplate()) ?: getDefaultTemplate()
        return template.replace("{이름}", name)
    }

    /**
     * 기본 메시지 템플릿
     */
    private fun getDefaultTemplate(): String {
        return """안녕하세요 {이름}님!

출입용 QR코드가 생성되었습니다.

사용법:
1. 출입시 QR코드를 제시해주세요
2. 스캐너에 인식시키면 자동 기록됩니다
3. QR코드는 24시간 유효합니다

주의:
- 개인정보가 포함되어 있으니 공유하지 마세요
- 분실시 관리자에게 연락하여 재발급 받으세요

문의사항이 있으시면 언제든 연락주세요.

QR 출입관리 시스템"""
    }

    /**
     * SMS 권한 확인
     */
    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 필요한 권한들
     */
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.SEND_SMS)
    }

    /**
     * 전화번호 정리
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace("-", "").replace(" ", "").trim()
    }

    /**
     * 전화번호 유효성 검사
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = cleanPhoneNumber(phoneNumber)

        // 한국 휴대폰 번호 형식 검증
        val patterns = listOf(
            "^010\\d{8}$".toRegex(),  // 010-xxxx-xxxx
            "^011\\d{8}$".toRegex(),  // 011-xxxx-xxxx
            "^016\\d{8}$".toRegex(),  // 016-xxxx-xxxx
            "^017\\d{8}$".toRegex(),  // 017-xxxx-xxxx
            "^018\\d{8}$".toRegex(),  // 018-xxxx-xxxx
            "^019\\d{8}$".toRegex()   // 019-xxxx-xxxx
        )

        return patterns.any { it.matches(cleaned) }
    }

    /**
     * 전화번호 포맷팅
     */
    fun formatPhoneNumber(phoneNumber: String): String {
        val cleaned = cleanPhoneNumber(phoneNumber)

        return when {
            cleaned.length == 11 && cleaned.startsWith("010") -> {
                "${cleaned.substring(0, 3)}-${cleaned.substring(3, 7)}-${cleaned.substring(7)}"
            }
            cleaned.length == 11 -> {
                "${cleaned.substring(0, 3)}-${cleaned.substring(3, 7)}-${cleaned.substring(7)}"
            }
            else -> phoneNumber
        }
    }
}