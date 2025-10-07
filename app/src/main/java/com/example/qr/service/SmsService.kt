package com.example.qr.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.qr.utils.BarcodeImageGenerator
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Telephony
import android.telephony.TelephonyManager

/**
 * MMS 전송 상태를 감지하는 BroadcastReceiver
 */
class MmsStatusReceiver(private val callback: (String, Boolean, String?) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return

        when (resultCode) {
            -1 -> { // Activity.RESULT_OK
                println("✅ MMS 전송 성공: $action")
                callback(action, true, "MMS 전송 완료")
            }
            0 -> { // Activity.RESULT_CANCELED
                println("❌ MMS 전송 실패: $action")
                callback(action, false, "MMS 전송 실패")
            }
            1 -> { // SmsManager.RESULT_ERROR_GENERIC_FAILURE
                println("❌ MMS 일반 오류: $action")
                callback(action, false, "일반 전송 오류")
            }
            2 -> { // SmsManager.RESULT_ERROR_NO_SERVICE
                println("❌ MMS 서비스 없음: $action")
                callback(action, false, "네트워크 서비스 없음")
            }
            3 -> { // SmsManager.RESULT_ERROR_NULL_PDU
                println("❌ MMS PDU 오류: $action")
                callback(action, false, "PDU 오류")
            }
            4 -> { // SmsManager.RESULT_ERROR_RADIO_OFF
                println("❌ MMS 라디오 꺼짐: $action")
                callback(action, false, "모바일 라디오 비활성화")
            }
            else -> {
                println("❓ MMS 알 수 없는 결과: $action, resultCode: $resultCode")
                callback(action, false, "알 수 없는 오류 (코드: $resultCode)")
            }
        }
    }
}

class SmsService(private val context: Context) {

    companion object {
        const val SMS_PERMISSION_REQUEST = 1001
        const val DEFAULT_MESSAGE_TEMPLATE = """안녕하세요 {이름}님,
IFAA 2025 학회에 등록되셨습니다.
첨부된 QR 코드 이미지를 입장 시 제시해주세요.
일시: 2025-09-27
문의: 02-123-4567"""

        const val RESEND_MESSAGE_TEMPLATE = """{이름}님의 QR 코드를 재전송합니다.
첨부된 QR 코드 이미지를 입장 시 제시해주세요.
IFAA 2025 학회
문의: 02-123-4567"""

        // 발송 상태 상수
        const val SENDING_STATUS_PENDING = "pending"
        const val SENDING_STATUS_SENDING = "sending"
        const val SENDING_STATUS_SENT = "sent"
        const val SENDING_STATUS_FAILED = "failed"

        // MMS 타임아웃 설정
        const val MMS_TIMEOUT_SECONDS = 15 // 15초 타임아웃
    }

    // 발송 상태 콜백 인터페이스
    interface SendingStatusCallback {
        fun onStatusChanged(phoneNumber: String, status: String, message: String? = null)
        fun onProgressUpdate(current: Int, total: Int)
    }

    private var statusCallback: SendingStatusCallback? = null

    // MMS 상태 추적을 위한 변수들
    private val pendingMmsRequests = ConcurrentHashMap<String, String>() // requestId -> phoneNumber
    private val pendingTimeouts = ConcurrentHashMap<String, Runnable>() // requestId -> timeout runnable
    private var mmsStatusReceiver: MmsStatusReceiver? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())

    /**
     * 발송 상태 콜백을 설정합니다.
     */
    fun setSendingStatusCallback(callback: SendingStatusCallback?) {
        this.statusCallback = callback
    }

    /**
     * MMS 상태 추적을 초기화합니다.
     */
    private fun initMmsStatusTracking() {
        if (mmsStatusReceiver == null) {
            mmsStatusReceiver = MmsStatusReceiver { action, success, errorMessage ->
                // action에서 requestId 추출: "MMS_SENT_1234567890"
                val requestId = action.substringAfter("MMS_SENT_")
                val phoneNumber = pendingMmsRequests.remove(requestId)

                // 타임아웃 취소
                val timeoutRunnable = pendingTimeouts.remove(requestId)
                timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }

                if (phoneNumber != null) {
                    if (success) {
                        println("✅ [RECEIVER] MMS 전송 성공 확인: $phoneNumber (요청ID: $requestId)")
                        updateSendingStatus(phoneNumber, SENDING_STATUS_SENT, "MMS 전송 완료")
                    } else {
                        println("❌ [RECEIVER] MMS 전송 실패 확인: $phoneNumber - $errorMessage (요청ID: $requestId)")
                        updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, "MMS 전송 실패: $errorMessage")
                    }
                } else {
                    println("⚠️ [RECEIVER] 대응하는 전화번호를 찾을 수 없음: $requestId")
                }
            }
        }
    }

    /**
     * MMS 상태 추적을 정리합니다.
     */
    fun cleanup() {
        try {
            // BroadcastReceiver 정리
            mmsStatusReceiver?.let { receiver ->
                context.unregisterReceiver(receiver)
                mmsStatusReceiver = null
                println("🧹 MMS 상태 추적 정리 완료")
            }

            // 모든 pending 타임아웃 제거
            pendingTimeouts.values.forEach { timeoutHandler.removeCallbacks(it) }
            pendingTimeouts.clear()

            // pending 요청 정리
            pendingMmsRequests.clear()

            println("🧹 MMS 타임아웃 및 요청 정리 완료")
        } catch (e: Exception) {
            println("⚠️ MMS 상태 추적 정리 오류: ${e.message}")
        }
    }

    /**
     * MMS 서비스 사용 가능 여부를 검증합니다.
     */
    private fun verifyMmsServiceAvailability(): MmsVerificationResult {
        val issues = mutableListOf<String>()

        try {
            // 1. 네트워크 연결 확인
            val networkStatus = checkNetworkConnection()
            if (!networkStatus.isConnected) {
                issues.add("네트워크 연결 없음: ${networkStatus.reason}")
            }

            // 2. MMS 권한 확인
            if (!hasSmsPermission()) {
                issues.add("SMS/MMS 발송 권한 없음")
            }

            // 3. 기본 MMS 앱 확인
            val defaultMmsApp = getDefaultSmsApp()
            if (defaultMmsApp.isNullOrEmpty()) {
                issues.add("기본 MMS 앱이 설정되지 않음")
            }

            // 4. SIM 카드 상태 확인
            val simStatus = checkSimCardStatus()
            if (!simStatus.isValid) {
                issues.add("SIM 카드 상태 이상: ${simStatus.reason}")
            }

            return MmsVerificationResult(
                isAvailable = issues.isEmpty(),
                issues = issues,
                networkStatus = networkStatus,
                defaultMmsApp = defaultMmsApp,
                simStatus = simStatus
            )

        } catch (e: Exception) {
            issues.add("검증 중 오류 발생: ${e.message}")
            return MmsVerificationResult(
                isAvailable = false,
                issues = issues,
                networkStatus = NetworkStatus(false, "검증 실패"),
                defaultMmsApp = null,
                simStatus = SimStatus(false, "검증 실패")
            )
        }
    }

    /**
     * 네트워크 연결 상태를 확인합니다.
     */
    private fun checkNetworkConnection(): NetworkStatus {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork

            if (network == null) {
                return NetworkStatus(false, "활성 네트워크 없음")
            }

            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities == null) {
                return NetworkStatus(false, "네트워크 정보 없음")
            }

            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            when {
                !hasInternet -> NetworkStatus(false, "인터넷 연결 없음")
                !hasValidated -> NetworkStatus(false, "네트워크 검증 실패")
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    NetworkStatus(true, "WiFi 연결됨")
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    NetworkStatus(true, "모바일 데이터 연결됨")
                else -> NetworkStatus(true, "네트워크 연결됨")
            }
        } catch (e: Exception) {
            NetworkStatus(false, "네트워크 확인 실패: ${e.message}")
        }
    }

    /**
     * 기본 SMS 앱을 확인합니다.
     */
    private fun getDefaultSmsApp(): String? {
        return try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * SIM 카드 상태를 확인합니다.
     */
    private fun checkSimCardStatus(): SimStatus {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            when (telephonyManager.simState) {
                TelephonyManager.SIM_STATE_READY ->
                    SimStatus(true, "SIM 카드 준비됨")
                TelephonyManager.SIM_STATE_ABSENT ->
                    SimStatus(false, "SIM 카드 없음")
                TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED ->
                    SimStatus(false, "SIM 카드 잠김")
                TelephonyManager.SIM_STATE_UNKNOWN ->
                    SimStatus(false, "SIM 카드 상태 불명")
                else ->
                    SimStatus(false, "SIM 카드 준비되지 않음")
            }
        } catch (e: Exception) {
            SimStatus(false, "SIM 카드 확인 실패: ${e.message}")
        }
    }

    /**
     * MMS 검증 결과 데이터 클래스
     */
    data class MmsVerificationResult(
        val isAvailable: Boolean,
        val issues: List<String>,
        val networkStatus: NetworkStatus,
        val defaultMmsApp: String?,
        val simStatus: SimStatus
    )

    data class NetworkStatus(
        val isConnected: Boolean,
        val reason: String
    )

    data class SimStatus(
        val isValid: Boolean,
        val reason: String
    )

    /**
     * MMS 자동 전송 실패 시 수동 전송 옵션을 제공합니다.
     */
    fun offerManualMmsSending(phoneNumber: String, message: String, barcodeImageFile: File): Boolean {
        return try {
            println("📱 [MANUAL] 수동 MMS 전송 옵션 제공: $phoneNumber")

            // FileProvider URI 생성
            val imageUri = BarcodeImageGenerator.getFileProviderUri(barcodeImageFile, context)
            if (imageUri == null) {
                println("❌ [MANUAL] FileProvider URI 생성 실패")
                return false
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra("address", phoneNumber)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // MMS 앱을 우선으로 설정
                `package` = getDefaultSmsApp() ?: run {
                    // 기본 MMS 앱이 없으면 일반 공유로 전환
                    Intent.createChooser(this, "QR 코드 이미지 전송")
                    return@apply
                }
            }

            context.startActivity(intent)

            println("✅ [MANUAL] 수동 전송 화면 열기 완료")
            println("   📧 메시지 앱이 열렸습니다. 수동으로 전송을 완료해주세요.")

            true
        } catch (e: Exception) {
            println("❌ [MANUAL] 수동 전송 화면 열기 실패: ${e.message}")
            false
        }
    }

    /**
     * QR 코드 이미지를 일반 공유로 전송합니다.
     */
    fun shareBarcodeFallback(phoneNumber: String, message: String, barcodeImageFile: File): Boolean {
        return try {
            println("📤 [SHARE] QR 코드 이미지 공유 옵션 제공")

            val imageUri = BarcodeImageGenerator.getFileProviderUri(barcodeImageFile, context)
            if (imageUri == null) {
                println("❌ [SHARE] FileProvider URI 생성 실패")
                return false
            }

            val shareText = """
                $message

                📞 수신자: $phoneNumber

                ※ 이 QR 코드 이미지를 $phoneNumber 로 전송해주세요.
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "QR 코드 이미지 공유")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(chooserIntent)

            println("✅ [SHARE] 공유 화면 열기 완료")
            true
        } catch (e: Exception) {
            println("❌ [SHARE] 공유 화면 열기 실패: ${e.message}")
            false
        }
    }

    /**
     * 발송 상태를 업데이트합니다.
     */
    private fun updateSendingStatus(phoneNumber: String, status: String, message: String? = null) {
        statusCallback?.onStatusChanged(phoneNumber, status, message)
        println("📊 발송 상태 업데이트: $phoneNumber -> $status ${message?.let { "($it)" } ?: ""}")
    }

    /**
     * SMS 발송 권한이 있는지 확인합니다.
     */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 필요한 권한 목록을 반환합니다.
     */
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    /**
     * 개별 권한별 허용 상태를 확인합니다.
     */
    fun getPermissionStatus(): Map<String, Boolean> {
        return mapOf(
            Manifest.permission.SEND_SMS to (ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED),
            Manifest.permission.READ_PHONE_STATE to (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED)
        )
    }

    /**
     * 권한 상태에 대한 상세한 메시지를 반환합니다.
     */
    fun getPermissionStatusMessage(): String {
        val status = getPermissionStatus()
        val smsPermission = status[Manifest.permission.SEND_SMS] ?: false
        val phoneStatePermission = status[Manifest.permission.READ_PHONE_STATE] ?: false

        return when {
            smsPermission && phoneStatePermission ->
                "✅ 모든 SMS 권한이 허용되었습니다. QR 코드 발송이 가능합니다."
            smsPermission && !phoneStatePermission ->
                "⚠️ SMS 발송 권한은 있지만 전화 상태 권한이 없습니다. 일부 기능이 제한될 수 있습니다."
            !smsPermission && phoneStatePermission ->
                "❌ SMS 발송 권한이 없습니다. QR 코드를 발송할 수 없습니다."
            else ->
                "❌ SMS 관련 권한이 모두 없습니다. QR 코드 발송 기능을 사용할 수 없습니다."
        }
    }

    /**
     * 권한이 부족할 때 사용자에게 표시할 안내 메시지를 반환합니다.
     */
    fun getPermissionGuideMessage(): String {
        return """
            QR 코드 SMS 발송을 위해 다음 권한이 필요합니다:

            📱 SMS 발송 권한
            • 참가자에게 QR 코드 이미지를 MMS로 전송하기 위해 필요

            📞 전화 상태 읽기 권한
            • SMS 발송 시스템 접근을 위해 필요

            권한 허용 방법:
            1. 설정 > 앱 > QR 코드 앱 선택
            2. 권한 > SMS, 전화 권한 허용
            3. 앱 재시작 후 QR 코드 발송 기능 사용
        """.trimIndent()
    }

    /**
     * 텍스트 메시지만 발송합니다.
     * @param phoneNumber 수신자 전화번호
     * @param message 메시지 내용
     * @return 발송 성공 여부
     */
    fun sendTextMessage(phoneNumber: String, message: String): Boolean {
        return try {
            if (!hasSmsPermission()) {
                println("SMS 권한이 없습니다.")
                return false
            }

            val smsManager = SmsManager.getDefault()

            // 긴 메시지의 경우 자동으로 분할하여 발송
            val parts = smsManager.divideMessage(message)

            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }

            println("SMS 발송 완료: $phoneNumber")
            true
        } catch (e: Exception) {
            println("SMS 발송 실패: ${e.message}")
            false
        }
    }

    /**
     * QR 코드 이미지와 함께 MMS 메시지를 발송합니다. (선택적 기능)
     * MMS 발송에 실패하면 자동으로 텍스트 SMS로 폴백됩니다.
     * @param phoneNumber 수신자 전화번호
     * @param message 메시지 내용
     * @param barcodeText QR 코드에 포함될 텍스트
     * @return 발송 성공 여부
     */
    fun sendBarcodeImageMessage(
        phoneNumber: String,
        message: String,
        barcodeText: String,
        isBatchMode: Boolean = false
    ): Boolean {
        return try {
            println("🚀 [MMS] QR 코드 이미지 메시지 발송 시작")
            println("   📞 수신자: $phoneNumber")
            println("   📊 QR 코드: $barcodeText")
            println("   🔄 배치모드: $isBatchMode")
            println("   📝 메시지: ${message.take(50)}${if (message.length > 50) "..." else ""}")

            // 권한 확인
            if (!hasSmsPermission()) {
                val errorMsg = "SMS 발송 권한이 없습니다. 설정에서 권한을 허용해주세요."
                println("❌ [권한] $errorMsg")
                updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, errorMsg)
                return false
            }

            println("✅ [권한] SMS 권한 확인 완료")

            // QR 코드 텍스트 유효성 검사
            if (!BarcodeImageGenerator.validateBarcodeText(barcodeText)) {
                val errorMsg = "QR 코드 텍스트가 유효하지 않습니다. ASCII 문자 80자 이하만 지원됩니다."
                println("❌ [검증] $errorMsg (입력: '$barcodeText')")
                updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, errorMsg)
                return false
            }

            println("✅ [검증] QR 코드 텍스트 유효성 검사 통과")

            // MMS 서비스 사전 검증
            println("🔍 [검증] MMS 서비스 사용 가능 여부 확인 중...")
            val mmsVerification = verifyMmsServiceAvailability()

            if (!mmsVerification.isAvailable) {
                val issues = mmsVerification.issues.joinToString(", ")
                val errorMsg = "MMS 서비스 사용 불가: $issues"
                println("❌ [검증] $errorMsg")
                println("   📱 네트워크: ${mmsVerification.networkStatus.reason}")
                println("   📧 기본 앱: ${mmsVerification.defaultMmsApp ?: "미설정"}")
                println("   📞 SIM 카드: ${mmsVerification.simStatus.reason}")
                updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, errorMsg)
                return false
            }

            println("✅ [검증] MMS 서비스 사용 가능")
            println("   📱 네트워크: ${mmsVerification.networkStatus.reason}")
            println("   📧 기본 앱: ${mmsVerification.defaultMmsApp}")
            println("   📞 SIM 카드: ${mmsVerification.simStatus.reason}")

            // QR 코드 이미지 생성
            println("🎨 [이미지] QR 코드 이미지 생성 시작...")
            updateSendingStatus(phoneNumber, SENDING_STATUS_SENDING, "QR 코드 이미지 생성 중...")

            val barcodeImageFile = BarcodeImageGenerator.generateSmsOptimizedBarcodeToAppFiles(barcodeText, context)

            if (barcodeImageFile == null) {
                val errorMsg = "QR 코드 이미지 생성에 실패했습니다. 저장 공간을 확인해주세요."
                println("❌ [이미지] $errorMsg")
                updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, errorMsg)
                return false
            }

            val fileSizeKB = barcodeImageFile.length() / 1024
            println("✅ [이미지] QR 코드 이미지 생성 완료")
            println("   📁 파일 위치: ${barcodeImageFile.absolutePath}")
            println("   📏 파일 크기: ${fileSizeKB}KB")

            // MMS 발송
            println("📤 [전송] MMS 자동 발송 시작...")
            updateSendingStatus(phoneNumber, SENDING_STATUS_SENDING, "MMS 자동 발송 중...")

            val success = sendAutomaticMmsWithImage(phoneNumber, message, barcodeImageFile, barcodeText, isBatchMode)

            // 발송 후 파일 정리 스케줄링
            cleanupBarcodeFile(barcodeImageFile)

            if (success) {
                println("✅ [API] MMS 발송 요청 완료: $phoneNumber")
                println("   ⏳ 실제 전송 결과를 ${MMS_TIMEOUT_SECONDS}초간 대기 중...")
                println("   📡 BroadcastReceiver 또는 타임아웃에서 최종 상태가 결정됩니다.")

                // 상태를 "발송 중"으로 업데이트 (최종 결과 대기 중임을 명시)
                updateSendingStatus(phoneNumber, SENDING_STATUS_SENDING,
                    "MMS 발송 요청 완료. 전송 결과 확인 중...")
            } else {
                val errorMsg = "MMS 발송 요청에 실패했습니다. 네트워크 연결을 확인해주세요."
                println("❌ [API] $errorMsg: $phoneNumber")

                // 즉시 실패 시 폴백 옵션 제공
                println("🔄 [FALLBACK] 즉시 실패로 인한 수동 전송 옵션 제공 시도")

                try {
                    // 1차: 기본 MMS 앱으로 전송 시도
                    val manualSendSuccess = offerManualMmsSending(phoneNumber, message, barcodeImageFile)
                    if (manualSendSuccess) {
                        updateSendingStatus(
                            phoneNumber,
                            SENDING_STATUS_FAILED,
                            "❌ 자동 MMS 전송 실패. 수동 전송 앱을 열었습니다. 직접 전송 버튼을 눌러주세요."
                        )
                        return false // 자동 전송 실패이므로 false 반환
                    }

                    // 2차: 일반 공유로 전송 시도
                    val shareSuccess = shareBarcodeFallback(phoneNumber, message, barcodeImageFile)
                    if (shareSuccess) {
                        updateSendingStatus(
                            phoneNumber,
                            SENDING_STATUS_FAILED,
                            "❌ 자동 MMS 전송 실패. 공유 앱을 열었습니다. 직접 전송해주세요."
                        )
                        return false // 자동 전송 실패이므로 false 반환
                    }
                } catch (e: Exception) {
                    println("⚠️ [FALLBACK] 폴백 옵션 제공 실패: ${e.message}")
                }

                // 모든 폴백 실패 시 최종 실패 상태
                updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, "$errorMsg 수동 전송 옵션도 실패했습니다.")
                return false
            }

            success
        } catch (e: SecurityException) {
            val errorMsg = "권한 오류: ${e.message}"
            println("❌ [권한오류] $errorMsg")
            updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, errorMsg)
            false
        } catch (e: Exception) {
            val errorMsg = "예기치 못한 오류: ${e.message}"
            println("❌ [시스템오류] $errorMsg")
            println("   스택 트레이스: ${e.stackTrace.take(3).joinToString(" -> ")}")
            updateSendingStatus(phoneNumber, SENDING_STATUS_FAILED, errorMsg)
            false
        }
    }

    /**
     * 배치 발송용 자동 MMS 발송 (사용자 개입 없음, Intent 방식 제외)
     * @param phoneNumber 수신자 전화번호
     * @param message 메시지 내용
     * @param imageFile 첨부할 이미지 파일
     * @param barcodeText QR 코드 텍스트 (fallback용)
     * @param isBatchMode 배치 모드 여부 (true면 Intent 방식 사용 안함)
     * @return 발송 성공 여부
     */
    private fun sendAutomaticMmsWithImage(
        phoneNumber: String,
        message: String,
        imageFile: File,
        barcodeText: String,
        isBatchMode: Boolean = false
    ): Boolean {
        return try {
            println("🔧 [MMS_CORE] 자동 MMS 발송 시작")
            println("   📞 수신자: $phoneNumber")
            println("   🔄 배치모드: $isBatchMode")
            println("   📁 이미지 파일: ${imageFile.name} (${imageFile.length() / 1024}KB)")

            // FileProvider URI 생성
            println("🔗 [URI] FileProvider URI 생성 중...")
            val imageUri = BarcodeImageGenerator.getFileProviderUri(imageFile, context)
            if (imageUri == null) {
                val errorMsg = "FileProvider URI 생성에 실패했습니다. 파일 접근 권한을 확인해주세요."
                println("❌ [URI] $errorMsg")
                return false
            }

            println("✅ [URI] FileProvider URI 생성 완료: $imageUri")

            // MMS 발송 방법 선택 및 실행
            val methodName = if (isBatchMode) "배치모드 직접 발송" else "단일모드 직접 발송"
            println("📤 [METHOD] $methodName 방식 사용")

            val success = if (isBatchMode) {
                sendMmsDirectlyBatchMode(phoneNumber, message, imageUri, imageFile)
            } else {
                sendMmsDirectly(phoneNumber, message, imageUri, imageFile)
            }

            if (!success) {
                val errorMsg = "MMS API 호출에 실패했습니다. 통신사 서비스를 확인해주세요."
                println("❌ [API] $errorMsg")
                return false
            }

            println("✅ [API] MMS API 호출 성공")

            // 배치 모드 안정화 대기
            if (isBatchMode) {
                println("⏳ [BATCH] 배치 발송 안정화 대기 중... (2초)")
                Thread.sleep(2000)
                println("✅ [BATCH] 안정화 대기 완료")
            }

            println("🎉 [완료] 자동 MMS 발송 처리 완료")
            true
        } catch (e: SecurityException) {
            val errorMsg = "MMS 발송 권한 오류: ${e.message}"
            println("❌ [권한] $errorMsg")
            false
        } catch (e: Exception) {
            val errorMsg = "MMS 발송 시스템 오류: ${e.message}"
            println("❌ [시스템] $errorMsg")
            println("   오류 타입: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 배치 모드 전용 MMS 발송 (Intent 방식 제외, API 방식만 사용)
     */
    private fun sendMmsDirectlyBatchMode(
        phoneNumber: String,
        message: String,
        imageUri: Uri,
        imageFile: File
    ): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            val imageBytes = imageFile.readBytes()

            println("📱 배치 모드 MMS 발송 시작: $phoneNumber")

            // Android 5.0 이상에서만 MMS API 사용
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    // SmsManager API 직접 사용 (ContentResolver 제거)
                    val smsManagerSuccess = sendMmsViaSmsManager(phoneNumber, message, imageUri, imageBytes, imageFile)
                    if (smsManagerSuccess) {
                        println("✅ 배치 모드 SmsManager MMS API 발송 성공: $phoneNumber")
                        return true
                    }
                } catch (e: Exception) {
                    println("⚠️ 배치 모드 MMS API 발송 실패: ${e.message}")
                }
            }

            println("❌ 배치 모드 MMS 발송 실패: $phoneNumber")
            false

        } catch (e: Exception) {
            println("❌ 배치 모드 MMS 발송 오류: ${e.message}")
            false
        }
    }

    /**
     * SmsManager를 사용하여 직접 MMS를 발송합니다.
     */
    private fun sendMmsDirectly(
        phoneNumber: String,
        message: String,
        imageUri: Uri,
        imageFile: File
    ): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()

            // MMS 발송용 PendingIntent 생성
            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 바이트 배열로 이미지 변환
            val imageBytes = imageFile.readBytes()

            // MMS 발송 (Android 5.0 이상에서 지원)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    // SmsManager API 직접 사용 (ContentResolver 제거)
                    val smsManagerSuccess = sendMmsViaSmsManager(phoneNumber, message, imageUri, imageBytes, imageFile)
                    if (smsManagerSuccess) {
                        println("✅ SmsManager MMS API 발송 성공")
                        return true
                    }
                } catch (e: Exception) {
                    println("⚠️ MMS API 발송 실패: ${e.message}")
                }
            }

            // Intent 방식은 메시지 앱을 열어서 서버를 중단시키므로 사용하지 않음
            println("❌ 모든 MMS API 방식 실패, SMS 텍스트로 폴백")
            return false

        } catch (e: Exception) {
            println("❌ 직접 MMS 발송 실패: ${e.message}")
            false
        }
    }

    /**
     * Intent를 통해 MMS를 발송합니다. (사용 중지됨 - 메시지 앱을 열어서 서버를 중단시킴)
     */
    private fun sendMmsViaIntent(phoneNumber: String, message: String, imageUri: Uri): Boolean {
        println("⚠️ Intent 방식 MMS는 앱 전환으로 인해 사용하지 않음")
        return false
    }


    /**
     * MMS 발송 실패 시 대체 발송 방법
     */







    /**
     * SmsManager API를 통해 MMS를 전송합니다 (개선된 방식).
     */
    private fun sendMmsViaSmsManager(phoneNumber: String, message: String, imageUri: Uri, imageBytes: ByteArray, barcodeImageFile: File? = null): Boolean {
        return try {
            println("📲 [SMS_API] SmsManager API를 통한 MMS 전송 시도")

            // MMS 상태 추적 초기화
            initMmsStatusTracking()

            val smsManager = SmsManager.getDefault()

            // 고유한 요청 ID 생성 (Integer 범위 내로 조정)
            val timestamp = System.currentTimeMillis()
            val requestId = (timestamp % Int.MAX_VALUE).toString()
            val actionName = "MMS_SENT_$requestId"

            // 추적을 위해 요청 ID와 전화번호 매핑 저장
            pendingMmsRequests[requestId] = phoneNumber

            // BroadcastReceiver 등록 (해당 액션만 수신)
            val intentFilter = IntentFilter(actionName)
            mmsStatusReceiver?.let { receiver ->
                // Android 14+ 호환: RECEIVER_NOT_EXPORTED 플래그 추가 (내부 사용만)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, intentFilter)
                }
                println("🔔 MMS 상태 수신기 등록: $actionName")
            }

            // PendingIntent 생성 (전송 상태 확인용)
            val sentIntent = PendingIntent.getBroadcast(
                context,
                requestId.toInt(),
                Intent(actionName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Android 5.0 이상에서만 지원
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                println("📱 Android ${android.os.Build.VERSION.SDK_INT}에서 sendMultimediaMessage 호출")
                println("   수신자: $phoneNumber")
                println("   메시지: ${message.take(50)}...")
                println("   이미지 크기: ${imageBytes.size} bytes")
                println("   이미지 URI: $imageUri")
                println("   요청 ID: $requestId")

                // sendMultimediaMessage 직접 호출 (Bundle 없이)
                smsManager.sendMultimediaMessage(
                    context,
                    imageUri,
                    null, // locationUrl (사용하지 않음)
                    null, // configOverrides (기본 설정 사용)
                    sentIntent // 전송 상태 확인용
                )

                println("✅ [SMS_API] sendMultimediaMessage() 호출 완료")
                println("   📡 비동기 전송이 시작되었습니다. ${MMS_TIMEOUT_SECONDS}초 후 타임아웃")

                // 타임아웃 메커니즘 설정
                val timeoutRunnable = Runnable {
                    val timeoutPhoneNumber = pendingMmsRequests.remove(requestId)
                    pendingTimeouts.remove(requestId)

                    if (timeoutPhoneNumber != null) {
                        println("⏰ [TIMEOUT] MMS 전송 타임아웃: $timeoutPhoneNumber (${MMS_TIMEOUT_SECONDS}초)")

                        // 폴백 옵션 제공
                        if (barcodeImageFile != null && barcodeImageFile.exists()) {
                            println("🔄 [FALLBACK] 수동 전송 옵션 제공 시도")

                            try {
                                // 1차: 기본 MMS 앱으로 전송 시도
                                val manualSendSuccess = offerManualMmsSending(timeoutPhoneNumber, message, barcodeImageFile)
                                if (manualSendSuccess) {
                                    updateSendingStatus(
                                        timeoutPhoneNumber,
                                        SENDING_STATUS_FAILED,
                                        "⏰ MMS 자동 전송 타임아웃 (${MMS_TIMEOUT_SECONDS}초). 수동 전송 앱을 열었습니다. 직접 전송 버튼을 눌러주세요."
                                    )
                                    return@Runnable
                                }

                                // 2차: 일반 공유로 전송 시도
                                val shareSuccess = shareBarcodeFallback(timeoutPhoneNumber, message, barcodeImageFile)
                                if (shareSuccess) {
                                    updateSendingStatus(
                                        timeoutPhoneNumber,
                                        SENDING_STATUS_FAILED,
                                        "⏰ MMS 자동 전송 타임아웃 (${MMS_TIMEOUT_SECONDS}초). 공유 앱을 열었습니다. 직접 전송해주세요."
                                    )
                                    return@Runnable
                                }
                            } catch (e: Exception) {
                                println("⚠️ [FALLBACK] 폴백 옵션 제공 실패: ${e.message}")
                            }
                        }

                        // 모든 폴백 실패 시 최종 실패 상태
                        updateSendingStatus(
                            timeoutPhoneNumber,
                            SENDING_STATUS_FAILED,
                            "MMS 전송 시간 초과. 네트워크 연결 및 MMS 설정을 확인하거나 수동으로 전송해주세요."
                        )
                    }
                }

                // 타임아웃 스케줄링
                pendingTimeouts[requestId] = timeoutRunnable
                timeoutHandler.postDelayed(timeoutRunnable, (MMS_TIMEOUT_SECONDS * 1000).toLong())

                println("⏰ [TIMEOUT] ${MMS_TIMEOUT_SECONDS}초 타이머 설정됨 (요청ID: $requestId)")

                // 비동기 전송이므로 일정 시간 대기 후 true 반환
                Thread.sleep(1000) // 1초 대기 (빠른 피드백)
                return true

            } else {
                println("❌ Android 5.0 미만 (API ${android.os.Build.VERSION.SDK_INT})에서는 MMS API 미지원")
                pendingMmsRequests.remove(requestId) // 실패 시 추적 제거
                return false
            }

        } catch (e: SecurityException) {
            println("❌ SmsManager MMS 권한 오류: ${e.message}")
            println("   필요한 권한: SEND_SMS, READ_PHONE_STATE")
            false
        } catch (e: Exception) {
            println("❌ SmsManager MMS 전송 오류: ${e.message}")
            println("   오류 타입: ${e.javaClass.simpleName}")
            e.printStackTrace()
            false
        }
    }



    /**
     * QR 코드 파일을 정리합니다 (일정 시간 후).
     */
    private fun cleanupBarcodeFile(file: File) {
        try {
            // 5분 후 파일 삭제 (다른 앱에서 접근할 시간 확보)
            Thread {
                Thread.sleep(5 * 60 * 1000) // 5분 대기
                if (file.exists()) {
                    file.delete()
                    println("🗑️ QR 코드 파일 정리 완료: ${file.name}")
                }
            }.start()
        } catch (e: Exception) {
            println("⚠️ 파일 정리 오류: ${e.message}")
        }
    }

    /**
     * 메시지 템플릿에 참가자 정보를 적용합니다.
     * @param template 메시지 템플릿
     * @param participantName 참가자 이름
     * @param phoneNumber 참가자 전화번호 (선택적)
     * @param licenseNo 라이센스 번호 (선택적)
     * @return 완성된 메시지
     */
    fun formatMessage(
        template: String,
        participantName: String,
        phoneNumber: String? = null,
        licenseNo: String? = null
    ): String {
        var formattedMessage = template

        formattedMessage = formattedMessage.replace("{이름}", participantName)

        phoneNumber?.let {
            formattedMessage = formattedMessage.replace("{전화번호}", it)
        }

        licenseNo?.let {
            formattedMessage = formattedMessage.replace("{라이센스번호}", it)
        }

        return formattedMessage
    }

    /**
     * 여러 참가자에게 배치로 QR 코드 메시지를 발송합니다.
     * @param participants 참가자 정보 목록 (이름, 전화번호, QR 코드)
     * @param messageTemplate 메시지 템플릿
     * @param onProgress 진행률 콜백 (현재 인덱스, 전체 개수)
     * @param onComplete 완료 콜백 (성공 개수, 실패 개수)
     */
    fun sendBatchBarcodeMessages(
        participants: List<ParticipantInfo>,
        messageTemplate: String = DEFAULT_MESSAGE_TEMPLATE,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onComplete: ((successCount: Int, failureCount: Int) -> Unit)? = null
    ) {
        var successCount = 0
        var failureCount = 0

        println("🚀 배치 QR 코드 발송 시작: 총 ${participants.size}명")
        println("📋 발송 대상자: ${participants.map { it.name }.joinToString(", ")}")

        participants.forEachIndexed { index, participant ->
            try {
                val message = formatMessage(
                    messageTemplate,
                    participant.name,
                    participant.phoneNumber,
                    participant.licenseNo
                )

                println("📧 배치 MMS QR 코드 전송 시작: ${participant.name} (${index + 1}/${participants.size})")

                // 배치 모드로 MMS 텍스트 메시지 + QR 코드 이미지 한 번에 전송
                val success = sendBarcodeImageMessage(
                    phoneNumber = participant.phoneNumber,
                    message = message, // 기본 메시지를 MMS에 포함
                    barcodeText = participant.barcodeData,
                    isBatchMode = true // 배치 모드 활성화
                )

                if (success) {
                    println("✅ 배치 MMS QR 코드 전송 성공: ${participant.name}")
                    successCount++
                } else {
                    println("❌ 배치 MMS QR 코드 전송 실패: ${participant.name}")
                    failureCount++
                }

                // 진행률 콜백 호출
                onProgress?.invoke(index + 1, participants.size)

                // 배치 발송 안정화를 위한 추가 대기 (통신사 제한 방지)
                if (index < participants.size - 1) { // 마지막 발송이 아닌 경우에만
                    println("⏳ 다음 발송 전 대기 중... (${index + 1}/${participants.size})")
                    Thread.sleep(3000) // 3초 대기로 늘림
                }

            } catch (e: Exception) {
                println("❌ 배치 발송 오류 (${participant.name}): ${e.message}")
                e.printStackTrace()
                failureCount++
            }
        }

        // 배치 발송 결과 요약
        println("📊 배치 QR 코드 발송 요청 완료:")
        println("   📤 요청 성공: ${successCount}명")
        println("   ❌ 요청 실패: ${failureCount}명")
        println("   📈 요청 성공률: ${if (participants.isNotEmpty()) (successCount * 100.0 / participants.size).toInt() else 0}%")
        println("   ⏳ 실제 전송 결과는 ${MMS_TIMEOUT_SECONDS}초 내에 개별적으로 확인됩니다.")

        if (successCount > 0) {
            println("📡 ${successCount}명의 MMS 전송 상태를 BroadcastReceiver에서 추적 중...")
        }

        if (failureCount > 0) {
            println("⚠️ ${failureCount}명의 발송 요청이 실패했습니다. 개별 발송을 통해 재시도하세요.")
        }

        // 완료 콜백 호출
        onComplete?.invoke(successCount, failureCount)
    }

    /**
     * 참가자 정보 데이터 클래스
     */
    data class ParticipantInfo(
        val name: String,
        val phoneNumber: String,
        val barcodeData: String,
        val licenseNo: String? = null
    )
}