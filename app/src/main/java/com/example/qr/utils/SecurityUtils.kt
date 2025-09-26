package com.example.qr.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {

    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_LENGTH = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16

    /**
     * 개인정보 암호화
     */
    fun encryptSensitiveData(data: String, key: String): String {
        return try {
            val secretKey = SecretKeySpec(sha256(key).sliceArray(0..31), "AES")
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)

            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val encryptedData = cipher.doFinal(data.toByteArray())

            // IV + 암호화된 데이터를 함께 Base64 인코딩
            val result = iv + encryptedData
            Base64.encodeToString(result, Base64.DEFAULT)

        } catch (e: Exception) {
            // 암호화 실패시 원본 반환 (로그에 오류 기록)
            data
        }
    }

    /**
     * 개인정보 복호화
     */
    fun decryptSensitiveData(encryptedData: String, key: String): String {
        return try {
            val secretKey = SecretKeySpec(sha256(key).sliceArray(0..31), "AES")
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)

            val decodedData = Base64.decode(encryptedData, Base64.DEFAULT)
            val iv = decodedData.sliceArray(0 until GCM_IV_LENGTH)
            val encrypted = decodedData.sliceArray(GCM_IV_LENGTH until decodedData.size)

            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedData = cipher.doFinal(encrypted)
            String(decryptedData)

        } catch (e: Exception) {
            // 복호화 실패시 원본 반환
            encryptedData
        }
    }

    /**
     * 전화번호 마스킹 (저장용)
     */
    fun maskPhoneNumber(phoneNumber: String): String {
        return when {
            phoneNumber.length >= 11 -> {
                phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7)
            }
            phoneNumber.length >= 8 -> {
                phoneNumber.substring(0, 2) + "****" + phoneNumber.substring(6)
            }
            else -> "****"
        }
    }

    /**
     * 이름 마스킹 (로그용)
     */
    fun maskUserName(name: String): String {
        return when {
            name.length <= 1 -> "*"
            name.length == 2 -> name[0] + "*"
            else -> name[0] + "*".repeat(name.length - 2) + name.last()
        }
    }

    /**
     * QR 코드 해시값 생성 (중복 방지용)
     */
    fun generateQRHash(qrData: String): String {
        val digest = sha256(qrData)
        return Base64.encodeToString(digest, Base64.NO_WRAP).take(16)
    }

    /**
     * 안전한 랜덤 문자열 생성
     */
    fun generateSecureRandomString(length: Int): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length)
            .map { characters[random.nextInt(characters.length)] }
            .joinToString("")
    }

    /**
     * 입력값 검증 및 보안 검사
     */
    fun validateUserInput(input: String): ValidationResult {
        return when {
            input.isBlank() -> ValidationResult.EMPTY
            input.length > 100 -> ValidationResult.TOO_LONG
            containsSuspiciousPatterns(input) -> ValidationResult.SUSPICIOUS
            else -> ValidationResult.VALID
        }
    }

    /**
     * SQL 인젝션 등 의심스러운 패턴 검사
     */
    private fun containsSuspiciousPatterns(input: String): Boolean {
        val suspiciousPatterns = listOf(
            "<script", "javascript:", "onload=", "onerror=",
            "SELECT ", "INSERT ", "UPDATE ", "DELETE ", "DROP ",
            "UNION ", "OR 1=1", "' OR '", "-- ", "/*", "*/"
        )

        val upperInput = input.uppercase()
        return suspiciousPatterns.any { pattern ->
            upperInput.contains(pattern.uppercase())
        }
    }

    /**
     * SHA-256 해시 생성
     */
    private fun sha256(input: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
    }

    enum class ValidationResult {
        VALID,
        EMPTY,
        TOO_LONG,
        SUSPICIOUS
    }
}