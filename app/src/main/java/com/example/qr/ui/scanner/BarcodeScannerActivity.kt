package com.example.qr.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.qr.R
import com.example.qr.api.RetrofitClient
import com.example.qr.api.ScanRequest
import com.example.qr.databinding.ActivityBarcodeScannerBinding
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BarcodeScannerActivity : AppCompatActivity(), DecoratedBarcodeView.TorchListener {

    private lateinit var binding: ActivityBarcodeScannerBinding
    private var barcodeView: DecoratedBarcodeView? = null
    private var isTorchOn: Boolean = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // 모드 및 설정
    private var isMasterMode: Boolean = true
    private var masterServerUrl: String = ""

    // 연결 상태 모니터링
    private val healthCheckHandler = Handler(Looper.getMainLooper())
    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            checkServerConnection()
            healthCheckHandler.postDelayed(this, 30000) // 30초마다
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScanning()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            result?.let {
                handleScanResult(it.text)
            }
        }

        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {
            // QR 코드 감지 포인트 표시 (선택사항)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 모드 설정 로드
        loadDeviceMode()

        setupUI()
        checkCameraPermission()

        // 클라이언트 모드일 경우 연결 상태 모니터링 시작
        if (!isMasterMode) {
            startConnectionMonitoring()
        }
    }

    private fun loadDeviceMode() {
        val prefs = getSharedPreferences("device_settings", MODE_PRIVATE)
        isMasterMode = prefs.getBoolean("is_master_mode", true)
        masterServerUrl = prefs.getString("master_ip", "") ?: ""

        println("📱 스캐너 모드: ${if (isMasterMode) "마스터" else "클라이언트"}")
        if (!isMasterMode) {
            println("📱 마스터 서버: $masterServerUrl")
        }
    }

    private fun setupUI() {
        barcodeView = binding.barcodeScanner

        // QR 코드 스캐너 설정 (QR_CODE만 인식)
        val formats = listOf(com.google.zxing.BarcodeFormat.QR_CODE)
        barcodeView?.barcodeView?.decoderFactory = com.journeyapps.barcodescanner.DefaultDecoderFactory(formats)
        barcodeView?.apply {
            decodeContinuous(callback)
            setStatusText("QR 코드를 카메라에 비춰주세요")
            setTorchListener(this@BarcodeScannerActivity)
        }

        // 뒤로 가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 플래시 토글 버튼
        binding.btnFlash.setOnClickListener {
            toggleTorch()
        }

        // 연결 상태 인디케이터 표시 (클라이언트 모드일 때만)
        binding.connectionIndicator.visibility = if (isMasterMode) View.GONE else View.VISIBLE
    }

    private fun toggleTorch() {
        barcodeView?.let {
            if (isTorchOn) {
                it.setTorchOff()
            } else {
                it.setTorchOn()
            }
        }
    }

    private fun updateFlashButton() {
        binding.btnFlash.text = if (isTorchOn) "플래시 끄기" else "플래시 켜기"
    }

    // TorchListener 구현
    override fun onTorchOn() {
        isTorchOn = true
        updateFlashButton()
    }

    override fun onTorchOff() {
        isTorchOn = false
        updateFlashButton()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startScanning()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startScanning() {
        barcodeView?.resume()
    }

    private fun handleScanResult(scannedData: String) {
        if (isMasterMode) {
            // 마스터 모드: 기존 로직 (이전 액티비티로 전달)
            val resultIntent = Intent().apply {
                putExtra("SCAN_RESULT", scannedData)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } else {
            // 클라이언트 모드: 마스터 서버 API 호출
            sendScanToMasterServer(scannedData)
        }
    }

    /**
     * 클라이언트 모드: 마스터 서버로 스캔 기록 전송
     */
    private fun sendScanToMasterServer(barcodeData: String) {
        if (masterServerUrl.isBlank()) {
            showError("마스터 서버 주소가 설정되지 않았습니다")
            return
        }

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.createApi(masterServerUrl)
                val request = ScanRequest(barcodeData = barcodeData)
                val response = api.recordScan(request)

                if (response.isSuccessful && response.body() != null) {
                    val scanResponse = response.body()!!
                    if (scanResponse.success && scanResponse.participant != null) {
                        // 성공: 참가자 정보 표시
                        showScanSuccess(scanResponse)
                    } else {
                        // 실패: 에러 메시지 표시
                        showError(scanResponse.message ?: "스캔 처리 실패")
                    }
                } else {
                    showError("서버 응답 오류: ${response.code()}")
                }
            } catch (e: java.net.SocketTimeoutException) {
                showScanFailureDialog(barcodeData, "서버 연결 시간 초과 (5초)")
            } catch (e: java.net.ConnectException) {
                showScanFailureDialog(barcodeData, "서버에 연결할 수 없습니다")
            } catch (e: Exception) {
                showScanFailureDialog(barcodeData, "네트워크 오류: ${e.message}")
            }
        }
    }

    /**
     * 스캔 성공 표시
     */
    private fun showScanSuccess(response: com.example.qr.api.ScanResponse) {
        val participant = response.participant!!
        val scanType = response.scanType ?: "UNKNOWN"
        val scanTime = dateFormat.format(Date(response.scanTime ?: System.currentTimeMillis()))

        val message = """
            ✅ 스캔 완료

            이름: ${participant.fullName}
            전화번호: ${participant.phoneNumber}
            라이센스: ${participant.licenseNo}

            ${if (scanType == "ENTRY") "🟢 입장" else "🔴 퇴장"}
            시간: $scanTime
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("스캔 성공")
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
                // 스캔 계속
                barcodeView?.resume()
            }
            .setCancelable(false)
            .show()

        // 스캔 일시 중지
        barcodeView?.pause()
    }

    /**
     * 스캔 실패 다이얼로그
     */
    private fun showScanFailureDialog(barcodeData: String, errorMessage: String) {
        val scanTime = dateFormat.format(Date())

        val message = """
            ⚠️ 서버 연결 실패

            마스터 서버에 기록되지 않았습니다.
            아래 정보를 수기로 기록해주세요:

            📋 스캔 정보
            ━━━━━━━━━━━━━━━━━━━━━━
            바코드: $barcodeData
            시간: $scanTime
            ━━━━━━━━━━━━━━━━━━━━━━

            오류: $errorMessage
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("⚠️ 서버 연결 실패")
            .setMessage(message)
            .setPositiveButton("재시도") { dialog, _ ->
                dialog.dismiss()
                sendScanToMasterServer(barcodeData)
            }
            .setNegativeButton("확인") { dialog, _ ->
                dialog.dismiss()
                barcodeView?.resume()
            }
            .setCancelable(false)
            .show()

        barcodeView?.pause()
    }

    /**
     * 간단한 에러 메시지 표시
     */
    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            barcodeView?.resume()
        }
    }

    /**
     * 연결 상태 모니터링 시작
     */
    private fun startConnectionMonitoring() {
        healthCheckHandler.post(healthCheckRunnable)
    }

    /**
     * 연결 상태 모니터링 중지
     */
    private fun stopConnectionMonitoring() {
        healthCheckHandler.removeCallbacks(healthCheckRunnable)
    }

    /**
     * 서버 연결 상태 체크
     */
    private fun checkServerConnection() {
        if (masterServerUrl.isBlank()) return

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.createApi(masterServerUrl)
                val response = api.healthCheck()

                if (response.isSuccessful) {
                    updateConnectionIndicator(true)
                } else {
                    updateConnectionIndicator(false)
                }
            } catch (e: Exception) {
                updateConnectionIndicator(false)
            }
        }
    }

    /**
     * 연결 상태 인디케이터 업데이트
     */
    private fun updateConnectionIndicator(isConnected: Boolean) {
        runOnUiThread {
            binding.connectionIndicator.setBackgroundResource(
                if (isConnected) R.drawable.circle_indicator_connected
                else R.drawable.circle_indicator_disconnected
            )
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView?.resume()

        // 클라이언트 모드일 경우 즉시 연결 체크
        if (!isMasterMode) {
            checkServerConnection()
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopConnectionMonitoring()
    }

    companion object {
        const val REQUEST_CODE_SCAN = 1001
    }
}