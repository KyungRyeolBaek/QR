package com.example.qr.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object QRCodeGenerator {

    private const val DEFAULT_SIZE = 512
    private const val SECRET_KEY = "QR_ACCESS_CONTROL_SECRET_KEY_2024"

    /**
     * QR 코드용 보안 데이터 생성
     */
    data class SecureQRData(
        val userId: String,
        val userName: String,
        val phoneNumber: String,
        val timestamp: Long,
        val signature: String
    ) {
        fun toQRString(): String {
            return "$userId|$userName|$phoneNumber|$timestamp|$signature"
        }

        companion object {
            fun fromQRString(qrString: String): SecureQRData? {
                return try {
                    val parts = qrString.split("|")
                    if (parts.size != 5) return null

                    SecureQRData(
                        userId = parts[0],
                        userName = parts[1],
                        phoneNumber = parts[2],
                        timestamp = parts[3].toLong(),
                        signature = parts[4]
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * 보안 QR 데이터 생성
     */
    fun generateSecureQRData(
        userId: String,
        userName: String,
        phoneNumber: String
    ): SecureQRData {
        val timestamp = System.currentTimeMillis()
        val dataToSign = "$userId|$userName|$phoneNumber|$timestamp"
        val signature = generateHMAC(dataToSign, SECRET_KEY)

        return SecureQRData(
            userId = userId,
            userName = userName,
            phoneNumber = phoneNumber,
            timestamp = timestamp,
            signature = signature
        )
    }

    /**
     * QR 코드 검증
     */
    fun validateQRData(qrData: SecureQRData): Boolean {
        return try {
            val dataToVerify = "${qrData.userId}|${qrData.userName}|${qrData.phoneNumber}|${qrData.timestamp}"
            val expectedSignature = generateHMAC(dataToVerify, SECRET_KEY)

            // HMAC 검증
            if (qrData.signature != expectedSignature) {
                return false
            }

            // 시간 검증 (24시간 유효)
            val currentTime = System.currentTimeMillis()
            val validityPeriod = 24 * 60 * 60 * 1000L // 24시간

            (currentTime - qrData.timestamp) <= validityPeriod
        } catch (e: Exception) {
            false
        }
    }

    /**
     * QR 코드 비트맵 생성
     */
    suspend fun generateQRBitmap(
        qrData: SecureQRData,
        size: Int = DEFAULT_SIZE
    ): Bitmap = withContext(Dispatchers.Default) {
        val writer = QRCodeWriter()
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )

        val bitMatrix = writer.encode(
            qrData.toQRString(),
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        bitmap
    }

    /**
     * 간단한 QR 코드 생성 (문자열로)
     */
    suspend fun generateSimpleQRBitmap(
        text: String,
        size: Int = DEFAULT_SIZE
    ): Bitmap = withContext(Dispatchers.Default) {
        val writer = QRCodeWriter()
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )

        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        bitmap
    }

    /**
     * 고유 사용자 ID 생성
     */
    fun generateUserId(): String {
        return UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
    }

    /**
     * HMAC-SHA256 서명 생성
     */
    private fun generateHMAC(data: String, key: String): String {
        return try {
            val secretKeySpec = SecretKeySpec(key.toByteArray(), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            val hashBytes = mac.doFinal(data.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 실패시 SHA-256 해시로 대체
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest("$data$key".toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}