package com.example.qr.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.qr.utils.QRCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.io.File
import java.io.FileOutputStream
// CoolSMS API 인터페이스 (예시)
interface CoolSMSApi {
    @Headers("Content-Type: application/json")
    @POST("messages/v4/send")
    suspend fun sendMessage(@Body request: SMSRequest): SMSResponse

    data class SMSRequest(
        val message: MessageContent
    )

    data class MessageContent(
        val to: String,
        val from: String,
        val text: String,
        val type: String = "SMS"
    )

    data class SMSResponse(
        val groupId: String?,
        val messageId: String?,
        val statusCode: String,
        val statusMessage: String,
        val to: String,
        val from: String
    )
}

class SMSService(
    private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://api.coolsms.co.kr/") // CoolSMS API 기본 URL
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CoolSMSApi::class.java)

    /**
     * QR 코드와 안내 문자를 함께 발송
     */
    suspend fun sendQRCodeSMS(
        name: String,
        phoneNumber: String,
        qrBitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. QR 코드 이미지를 임시 파일로 저장
            val qrImageFile = saveQRImageToFile(qrBitmap, name)

            // 2. SMS 메시지 생성
            val message = createQRMessage(name, qrImageFile)

            // 3. SMS 발송 (실제 구현시 API 키 필요)
            val result = sendSMSMessage(phoneNumber, message)

            Result.success("SMS가 성공적으로 발송되었습니다: $result")

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
            val result = sendSMSMessage(phoneNumber, message)
            Result.success("SMS 발송 완료: $result")
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
            // 새로운 QR 데이터 생성
            val qrData = QRCodeGenerator.generateSecureQRData(
                userId = userId,
                userName = name,
                phoneNumber = phoneNumber
            )

            // QR 비트맵 생성
            val qrBitmap = QRCodeGenerator.generateQRBitmap(qrData)

            // SMS 발송
            sendQRCodeSMS(name, phoneNumber, qrBitmap)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * QR 이미지를 파일로 저장
     */
    private suspend fun saveQRImageToFile(bitmap: Bitmap, userName: String): File {
        val fileName = "qr_${userName}_${System.currentTimeMillis()}.png"
        val file = File(context.cacheDir, fileName)

        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }

        return file
    }

    /**
     * QR 안내 메시지 생성
     */
    private fun createQRMessage(name: String, qrImageFile: File): String {
        return """
            안녕하세요 ${name}님!

            🔖 출입용 QR코드가 생성되었습니다.

            📱 QR코드 사용법:
            1. 출입시 QR코드를 제시해주세요
            2. 스캐너에 QR코드를 인식시키면 자동으로 출입 시간이 기록됩니다
            3. QR코드는 24시간 유효합니다

            ⚠️ 주의사항:
            - QR코드는 개인정보가 포함되어 있으니 타인에게 공유하지 마세요
            - 분실시 관리자에게 연락하여 재발급 받으세요

            문의사항이 있으시면 언제든 연락주세요.

            QR 출입관리 시스템
        """.trimIndent()
    }

    /**
     * 실제 SMS 발송 (API 호출)
     */
    private suspend fun sendSMSMessage(phoneNumber: String, message: String): String {
        return try {
            // 실제 환경에서는 여기에 CoolSMS, NCS 등의 API 호출 구현
            // 현재는 시뮬레이션용 코드

            // 전화번호 포맷 정리
            val cleanedPhoneNumber = phoneNumber.replace("-", "").replace(" ", "")

            val request = CoolSMSApi.SMSRequest(
                message = CoolSMSApi.MessageContent(
                    to = cleanedPhoneNumber,
                    from = "01012345678", // 발신자 번호 (실제로는 설정에서 가져와야 함)
                    text = message
                )
            )

            // API 호출 (실제 환경에서는 인증키 필요)
            // val response = api.sendMessage(request)

            // 시뮬레이션: 성공 응답
            "SMS 발송 시뮬레이션 성공 -> $cleanedPhoneNumber"

        } catch (e: Exception) {
            throw Exception("SMS 발송 실패: ${e.message}", e)
        }
    }

    /**
     * 전화번호 유효성 검사
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = phoneNumber.replace("-", "").replace(" ", "")

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
        val cleaned = phoneNumber.replace("-", "").replace(" ", "")

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