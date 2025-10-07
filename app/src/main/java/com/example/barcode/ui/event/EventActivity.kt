package com.example.barcode.ui.event

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.barcode.R
import com.example.barcode.ViewModelFactory
import com.example.barcode.databinding.ActivityEventBinding
import com.example.barcode.ui.scanner.BarcodeScannerActivity
import com.example.barcode.data.entity.ParticipantWithScanInfo
import com.example.barcode.utils.UsbBarcodeInputHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.*

class EventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventBinding
    private lateinit var viewModel: EventViewModel
    private lateinit var usbBarcodeInputHandler: UsbBarcodeInputHandler
    private var durationUpdateJob: Job? = null

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedData = result.data?.getStringExtra("SCAN_RESULT")
            scannedData?.let {
                handleBarcodeScanned(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ViewModel 초기화
        val factory = ViewModelFactory(this)
        viewModel = ViewModelProvider(this, factory)[EventViewModel::class.java]

        // USB 바코드 입력 핸들러 초기화
        usbBarcodeInputHandler = UsbBarcodeInputHandler(
            coroutineScope = lifecycleScope,
            onBarcodeScanned = { barcodeData ->
                handleBarcodeScanned(barcodeData)
            }
        )

        setupUI()
        observeViewModel()

        // 활성 이벤트 로드
        viewModel.loadActiveEvent()
    }

    private fun setupUI() {
        // USB 바코드 리더기 입력 처리 설정
        setupBarcodeInput()

        // 뒤로 가기 버튼
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 설정 버튼 (테스트 데이터 생성)
        binding.btnSettings.setOnClickListener {
            // 테스트 데이터 생성
            viewModel.createTestData()
            Toast.makeText(this, "테스트 데이터를 생성합니다...", Toast.LENGTH_SHORT).show()
        }

        // USB 바코드 리더기만 사용 - 화면 클릭으로 카메라 스캐너 비활성화
        // binding.root.setOnClickListener {
        //     startBarcodeScanner()
        // }

        // 스캔 안내 메시지 설정
        updateScanGuideMessage()
    }

    private fun setupBarcodeInput() {
        // USB 바코드 리더기는 UsbBarcodeInputHandler로만 처리
        // EditText는 포커스 유지용으로만 사용
        binding.etHiddenBarcodeInput.requestFocus()

        // 포커스 유지를 위한 처리 (실제 입력은 onKeyDown에서 처리)
        binding.etHiddenBarcodeInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // 포커스가 사라지면 다시 포커스 설정
                binding.etHiddenBarcodeInput.requestFocus()
            }
        }
    }


    private fun observeViewModel() {
        viewModel.activeEvent.observe(this) { event ->
            event?.let {
                binding.tvEventTitle.text = it.eventName
                binding.tvEventDate.text = it.eventDate

                // 이벤트 커스터마이징 적용
                try {
                    binding.root.setBackgroundColor(android.graphics.Color.parseColor(it.backgroundColor))
                    binding.tvEventTitle.setTextColor(android.graphics.Color.parseColor(it.textColor))
                    binding.tvEventDate.setTextColor(android.graphics.Color.parseColor(it.textColor))
                } catch (e: Exception) {
                    // 색상 파싱 실패시 기본 색상 사용
                }
            }
        }

        viewModel.currentParticipant.observe(this) { participantInfo ->
            updateParticipantInfo(participantInfo)
        }

        viewModel.errorMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        viewModel.scanSuccess.observe(this) { success ->
            if (success) {
                // 스캔 성공 효과 (예: 진동, 소리 등)
                showScanSuccessEffect()
            }
        }
    }

    private fun updateParticipantInfo(participantInfo: ParticipantWithScanInfo?) {
        try {
            println("📱 [UI_UPDATE] 참가자 정보 업데이트: ${if (participantInfo != null) participantInfo.participant.fullName else "null"}")

            // Activity 상태 확인
            if (isFinishing || isDestroyed) {
                println("⚠️ [UI_UPDATE] Activity가 종료 중이므로 UI 업데이트 무시")
                return
            }

            if (participantInfo == null) {
                println("🔄 [UI_UPDATE] 참가자 정보 초기화")
                // 타이머 정지
                stopDurationTimer()
                // 정보 초기화
                binding.apply {
                    tvFullName.text = ""
                    tvLicenseNo.text = ""
                    tvFirstScanTime.text = ""
                    tvLastScanTime.text = ""
                    tvDurationTime.text = "00:00:00"
                    tvCmeCredits.text = ""
                    tvStatus.text = "바코드를 스캔해주세요"
                    tvStatus.setTextColor(getColor(R.color.status_waiting))
                }
            } else {
                println("✅ [UI_UPDATE] 참가자 정보 표시: ${participantInfo.participant.fullName}")
                println("   - 첫 입장: ${participantInfo.firstScanTime?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "없음"}")
                println("   - 마지막 스캔: ${participantInfo.lastScanTime?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "없음"}")
                println("   - 체류시간: ${participantInfo.formattedDuration}")
                println("   - 상태: ${participantInfo.statusText}")

                binding.apply {
                    tvFullName.text = participantInfo.participant.fullName
                    tvLicenseNo.text = participantInfo.participant.licenseNo

                    tvFirstScanTime.text = participantInfo.firstScanTime?.let {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: ""

                    tvLastScanTime.text = participantInfo.lastScanTime?.let {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: ""

                    tvDurationTime.text = participantInfo.formattedDuration

                    // 입장중인 경우 실시간 체류시간 업데이트 시작
                    if (participantInfo.isCurrentlyInside) {
                        startDurationTimer(participantInfo)
                    } else {
                        stopDurationTimer()
                    }

                    // CME Credits 표시 (기본값 또는 참가자별 설정값)
                    tvCmeCredits.text = participantInfo.participant.cmeCredits?.toString() ?: "5.0"

                    tvStatus.text = participantInfo.statusText

                    // 상태에 따른 색상 변경
                    val statusColor = when (participantInfo.statusText) {
                        "입장중" -> getColor(R.color.status_inside)
                        "퇴장완료" -> getColor(R.color.status_exited)
                        else -> getColor(R.color.status_waiting)
                    }
                    tvStatus.setTextColor(statusColor)
                }
            }

            // 스캔 안내 메시지 업데이트
            updateScanGuideMessage()
        } catch (e: Exception) {
            println("❌ [UI_UPDATE] 참가자 정보 업데이트 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startDurationTimer(participantInfo: ParticipantWithScanInfo) {
        try {
            stopDurationTimer() // 기존 타이머 정지

            if (participantInfo.firstScanTime != null && !isFinishing && !isDestroyed) {
                durationUpdateJob = lifecycleScope.launch {
                    try {
                        while (true) {
                            // Activity 상태 재확인
                            if (isFinishing || isDestroyed) {
                                break
                            }

                            delay(1000) // 1초마다 업데이트
                            val currentDuration = System.currentTimeMillis() - participantInfo.firstScanTime
                            val hours = currentDuration / (1000 * 60 * 60)
                            val minutes = (currentDuration % (1000 * 60 * 60)) / (1000 * 60)
                            val seconds = (currentDuration % (1000 * 60)) / 1000
                            val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                            // UI 업데이트 시 안전 확인
                            if (!isFinishing && !isDestroyed) {
                                binding.tvDurationTime.text = formattedTime
                            }
                        }
                    } catch (e: Exception) {
                        println("❌ [TIMER_ERROR] 체류시간 타이머 실행 실패: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ [TIMER_ERROR] 체류시간 타이머 시작 실패: ${e.message}")
        }
    }

    private fun stopDurationTimer() {
        durationUpdateJob?.cancel()
        durationUpdateJob = null
    }

    private fun updateScanGuideMessage() {
        try {
            // Activity가 살아있고 바인딩이 유효한지 확인
            if (!isFinishing && !isDestroyed) {
                // 참가자 정보가 있으면 안내 메시지를 숨기고, 없으면 표시
                val hasParticipant = viewModel.currentParticipant.value != null
                binding.tvScanGuide.visibility = if (hasParticipant) android.view.View.GONE else android.view.View.VISIBLE
            }
        } catch (e: Exception) {
            println("❌ [UI_ERROR] 스캔 안내 메시지 업데이트 실패: ${e.message}")
        }
    }

    private fun startBarcodeScanner() {
        val intent = Intent(this, BarcodeScannerActivity::class.java)
        scannerLauncher.launch(intent)
    }

    private fun handleBarcodeScanned(barcodeData: String) {
        try {
            println("🔍 [USB_BARCODE] 바코드 스캔 시작: $barcodeData")

            // Activity 상태 확인
            if (isFinishing || isDestroyed) {
                println("⚠️ [USB_BARCODE] Activity가 종료 중이므로 스캔 무시")
                return
            }

            // 스캔 성공 효과 표시
            showScanSuccessEffect()

            // 상태를 "처리 중"으로 임시 표시
            binding.tvStatus.text = "바코드 처리 중..."
            binding.tvStatus.setTextColor(getColor(R.color.status_waiting))

            // 현재 시간 표시로 스캔 확인
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            Toast.makeText(this, "USB 바코드 스캔: $barcodeData ($currentTime)", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    println("🚀 [USB_BARCODE] ViewModel 처리 시작")
                    viewModel.processBarcodeScanned(barcodeData)
                } catch (e: Exception) {
                    println("❌ [USB_BARCODE] ViewModel 처리 실패: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            println("❌ [USB_BARCODE] 바코드 처리 전체 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showScanSuccessEffect() {
        try {
            // Activity 상태 확인
            if (isFinishing || isDestroyed) {
                return
            }

            // 스캔 성공시 시각적 피드백
            binding.scanIndicator.apply {
                alpha = 1f
                animate()
                    .alpha(0f)
                    .setDuration(1000)
                    .start()
            }
        } catch (e: Exception) {
            println("❌ [UI_ERROR] 스캔 성공 효과 표시 실패: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        try {
            // Activity 상태 확인
            if (isFinishing || isDestroyed) {
                return super.onKeyDown(keyCode, event)
            }

            // USB 바코드 리더기 키 입력 처리
            event?.let {
                if (::usbBarcodeInputHandler.isInitialized &&
                    usbBarcodeInputHandler.handleKeyEvent(keyCode, it)) {
                    return true
                }
            }
        } catch (e: Exception) {
            println("❌ [USB_INPUT_ERROR] 키 입력 처리 실패: ${e.message}")
            e.printStackTrace()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        println("🔄 [LIFECYCLE] EventActivity onResume")
        // 포커스 재설정 (참가자 정보는 유지)
        binding.etHiddenBarcodeInput.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        println("🔄 [LIFECYCLE] EventActivity onDestroy")
        // 타이머 정리
        stopDurationTimer()
        // USB 바코드 입력 핸들러 정리
        usbBarcodeInputHandler.cleanup()
    }
}