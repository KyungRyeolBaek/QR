package com.example.qr.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.qr.R
import com.example.qr.ViewModelFactory
import com.example.qr.databinding.FragmentEventBinding
import com.example.qr.ui.event.EventViewModel
import com.example.qr.ui.scanner.BarcodeScannerActivity
import com.example.qr.data.entity.ParticipantWithScanInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EventFragment : Fragment() {

    private var _binding: FragmentEventBinding? = null
    // Safe binding accessor - null이면 함수 early return 필요
    private val binding: FragmentEventBinding?
        get() = _binding
    private lateinit var viewModel: EventViewModel
    private var backPressedCallback: OnBackPressedCallback? = null

    // 10초 후 자동 초기화를 위한 Handler
    private val clearHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var clearRunnable: Runnable? = null

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scannedData = result.data?.getStringExtra("SCAN_RESULT")
            scannedData?.let {
                handleBarcodeScanned(it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return _binding!!.root  // onCreate에서만 안전하게 !! 사용
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        com.example.qr.utils.CrashLogger.log("EventFragment onViewCreated 시작")

        // Hide bottom navigation bar in EventFragment
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)?.visibility = View.GONE

        val factory = ViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[EventViewModel::class.java]

        setupUI()
        setupBackPressedHandler()
        observeViewModel()

        com.example.qr.utils.CrashLogger.log("이벤트 로드 중...")
        viewModel.loadActiveEvent()
        com.example.qr.utils.CrashLogger.log("EventFragment 초기화 완료")
    }

    private fun setupBackPressedHandler() {
        // 뒤로가기 버튼 동작 커스터마이징
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                com.example.qr.utils.CrashLogger.log("EventFragment: 뒤로가기 눌림 - 모니터링 화면으로 이동")
                // 앱 종료 대신 모니터링 화면으로 이동
                findNavController().navigate(R.id.nav_monitoring)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback!!)
    }

    /**
     * USB QR 스캔 중 상태를 설정합니다.
     * MainActivity에서 호출됩니다.
     */
    fun setScanningState(isScanning: Boolean) {
        backPressedCallback?.isEnabled = !isScanning
        com.example.qr.utils.CrashLogger.log("🔧 뒤로가기 버튼 ${if (isScanning) "비활성화 (스캔 중)" else "활성화 (스캔 완료)"}")
    }

    private fun setupUI() {
        val binding = binding ?: return  // binding이 null이면 early return

        // USB QR 코드 리더기만 사용 - 화면 클릭으로 카메라 스캐너 비활성화
        // binding.root.setOnClickListener {
        //     startBarcodeScanner()
        // }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSettings.setOnClickListener {
            showEventSettingsDialog()
        }
    }

    private fun showEventSettingsDialog() {
        val event = viewModel.activeEvent.value ?: run {
            Toast.makeText(requireContext(), "활성화된 이벤트가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_event_settings,
            null
        )

        val etEventTitle = dialogView.findViewById<android.widget.EditText>(R.id.etEventTitle)
        val etEventDate = dialogView.findViewById<android.widget.EditText>(R.id.etEventDate)

        // 현재 이벤트 정보로 초기화
        etEventTitle.setText(event.eventName)
        etEventDate.setText(event.eventDate)

        AlertDialog.Builder(requireContext())
            .setTitle("이벤트 설정")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val newTitle = etEventTitle.text.toString().trim()
                val newDate = etEventDate.text.toString().trim()

                if (newTitle.isEmpty() || newDate.isEmpty()) {
                    Toast.makeText(requireContext(), "타이틀과 날짜를 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.updateEventInfo(event.id, newTitle, newDate)
                Toast.makeText(requireContext(), "이벤트 정보가 업데이트되었습니다", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.activeEvent.observe(viewLifecycleOwner) { event ->
            val binding = binding ?: return@observe  // binding null 체크
            event?.let {
                binding.tvEventTitle.text = it.eventName
                binding.tvEventDate.text = it.eventDate

                try {
                    binding.root.setBackgroundColor(android.graphics.Color.parseColor(it.backgroundColor))
                    binding.tvEventTitle.setTextColor(android.graphics.Color.parseColor(it.textColor))
                    binding.tvEventDate.setTextColor(android.graphics.Color.parseColor(it.textColor))
                } catch (e: Exception) {
                    // 색상 파싱 실패시 기본 색상 사용
                }
            }
        }

        viewModel.currentParticipant.observe(viewLifecycleOwner) { participantInfo ->
            updateParticipantInfo(participantInfo)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }

        viewModel.scanSuccess.observe(viewLifecycleOwner) { success ->
            try {
                com.example.qr.utils.CrashLogger.log("scanSuccess observer: success=$success, isAdded=$isAdded, binding=${_binding != null}, view.isAttachedToWindow=${view?.isAttachedToWindow}")

                // success가 true일 때만 애니메이션 실행
                if (success == true && isAdded && binding != null && view?.isAttachedToWindow == true) {
                    com.example.qr.utils.CrashLogger.log("showScanSuccessEffect 호출 시작")

                    // 직접 메인 스레드에서 실행 (view.post 제거로 타이밍 개선)
                    try {
                        showScanSuccessEffect()
                        com.example.qr.utils.CrashLogger.log("showScanSuccessEffect 호출 완료")
                    } catch (animError: Exception) {
                        com.example.qr.utils.CrashLogger.log("❌ 애니메이션 오류: ${animError.message}")
                        val sw = java.io.StringWriter()
                        animError.printStackTrace(java.io.PrintWriter(sw))
                        com.example.qr.utils.CrashLogger.log(sw.toString())
                    }
                } else {
                    com.example.qr.utils.CrashLogger.log("scanSuccess 조건 불일치, 애니메이션 스킵 (isAdded=$isAdded, binding=${binding != null}, attached=${view?.isAttachedToWindow})")
                }
            } catch (e: Exception) {
                println("스캔 성공 효과 표시 오류 (무시됨): ${e.message}")
                com.example.qr.utils.CrashLogger.log("❌ scanSuccess observer 오류: ${e.message}")
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                com.example.qr.utils.CrashLogger.log(sw.toString())
            }
        }
    }

    private fun updateParticipantInfo(participantInfo: ParticipantWithScanInfo?) {
        try {
            com.example.qr.utils.CrashLogger.log("updateParticipantInfo 시작: participantInfo=${participantInfo != null}, isAdded=$isAdded, binding=${_binding != null}")

            val binding = binding  // 한 번만 접근
            if (!isAdded || binding == null || view?.isAttachedToWindow != true) {
                com.example.qr.utils.CrashLogger.log("Fragment 비활성 상태, UI 업데이트 스킵 (isAdded=$isAdded, binding=${binding != null}, attached=${view?.isAttachedToWindow})")
                return
            }

            if (participantInfo == null) {
                binding.apply {
                    tvFullName.text = ""
                    tvOrganization.text = ""
                    tvLicenseNo.text = ""
                    tvFirstScanTime.text = ""
                    tvLastScanTime.text = ""
                    tvDurationTime.text = ""
                    tvStatus.text = "QR 코드를 스캔해주세요"
                    tvStatus.setTextColor(requireContext().getColor(R.color.status_waiting))
                }
                com.example.qr.utils.CrashLogger.log("빈 정보 표시 완료")
            } else {
                com.example.qr.utils.CrashLogger.log("참가자 정보 표시 중: ${participantInfo.participant.fullName}")
                binding.apply {
                    tvFullName.text = participantInfo.participant.fullName
                    tvOrganization.text = participantInfo.participant.organization.ifEmpty { "-" }
                    tvLicenseNo.text = participantInfo.participant.licenseNo

                    // 입장 시간: 최근 입장 시간 표시
                    tvFirstScanTime.text = participantInfo.firstScanTime?.let {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: ""

                    // 입장 중일 때는 퇴장 시간과 체류 시간을 비움
                    if (participantInfo.isCurrentlyInside) {
                        // 입장 중: 퇴장 시간과 체류 시간 비우기
                        tvLastScanTime.text = ""
                        tvDurationTime.text = ""
                    } else {
                        // 퇴장 완료: 퇴장 시간과 체류 시간 표시
                        tvLastScanTime.text = participantInfo.exitTime?.let {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: ""
                        tvDurationTime.text = participantInfo.formattedDuration
                    }

                    tvStatus.text = participantInfo.statusText

                    val statusColor = when (participantInfo.statusText) {
                        "입장중" -> requireContext().getColor(R.color.status_inside)
                        "퇴장완료" -> requireContext().getColor(R.color.status_exited)
                        else -> requireContext().getColor(R.color.status_waiting)
                    }
                    tvStatus.setTextColor(statusColor)
                }
                com.example.qr.utils.CrashLogger.log("참가자 정보 표시 완료")

                // 10초 후 자동 초기화 타이머 시작
                clearRunnable?.let { clearHandler.removeCallbacks(it) }
                clearRunnable = Runnable {
                    if (isAdded) {
                        com.example.qr.utils.CrashLogger.log("⏰ 10초 타이머 만료 - 참가자 정보 초기화")
                        viewModel.clearCurrentParticipant()
                    }
                }
                clearHandler.postDelayed(clearRunnable!!, 10000) // 10초
                com.example.qr.utils.CrashLogger.log("⏰ 10초 자동 초기화 타이머 시작")
            }
        } catch (e: Exception) {
            com.example.qr.utils.CrashLogger.log("❌ updateParticipantInfo 오류: ${e.message}")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            com.example.qr.utils.CrashLogger.log(sw.toString())
        }
    }

    private fun startBarcodeScanner() {
        val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
        scannerLauncher.launch(intent)
    }

    private fun handleBarcodeScanned(barcodeData: String) {
        try {
            com.example.qr.utils.CrashLogger.log("EventFragment: handleBarcodeScanned 시작")
            com.example.qr.utils.CrashLogger.log("QR 데이터: $barcodeData")

            lifecycleScope.launch {
                try {
                    com.example.qr.utils.CrashLogger.log("ViewModel 호출 전...")
                    viewModel.processBarcodeScanned(barcodeData)
                    com.example.qr.utils.CrashLogger.log("ViewModel 호출 완료")
                } catch (e: Exception) {
                    println("❌ QR 코드 처리 오류: ${e.message}")
                    e.printStackTrace()
                    com.example.qr.utils.CrashLogger.log("❌ 처리 오류: ${e.javaClass.simpleName}: ${e.message}")
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    com.example.qr.utils.CrashLogger.log(sw.toString())

                    if (isAdded) {
                        Toast.makeText(requireContext(), "QR 코드 처리 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ QR 코드 스캔 시작 오류: ${e.message}")
            e.printStackTrace()
            com.example.qr.utils.CrashLogger.log("❌ 스캔 시작 오류: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * USB QR 코드 리더기에서 스캔된 QR 코드를 처리합니다.
     * MainActivity에서 호출됩니다.
     */
    fun handleUsbBarcodeScanned(barcodeData: String) {
        handleBarcodeScanned(barcodeData)
    }

    private fun showScanSuccessEffect() {
        try {
            com.example.qr.utils.CrashLogger.log("showScanSuccessEffect 시작: isAdded=$isAdded, binding=${_binding != null}")

            // Fragment가 아직 활성 상태인지 확인
            val binding = binding
            if (!isAdded || binding == null || view?.isAttachedToWindow != true) {
                com.example.qr.utils.CrashLogger.log("showScanSuccessEffect 중단: Fragment 비활성")
                return
            }

            com.example.qr.utils.CrashLogger.log("애니메이션 시작...")
            binding.scanIndicator.apply {
                alpha = 1f
                animate()
                    .alpha(0f)
                    .setDuration(1000)
                    .withEndAction {
                        // 애니메이션 완료 후 alpha를 0으로 확실하게 설정
                        if (_binding != null) {
                            alpha = 0f
                        }
                    }
                    .start()
            }
        } catch (e: Exception) {
            // 애니메이션 실패는 무시 (UI 효과일 뿐이므로)
            println("스캔 성공 애니메이션 오류 (무시됨): ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        com.example.qr.utils.CrashLogger.log("❌❌❌ EventFragment onPause - Fragment가 일시정지됨")
    }

    override fun onStop() {
        super.onStop()
        com.example.qr.utils.CrashLogger.log("❌❌❌ EventFragment onStop - Fragment가 중지됨")
    }

    override fun onResume() {
        super.onResume()
        com.example.qr.utils.CrashLogger.log("✅ EventFragment onResume - Fragment 재개됨")
        // QR 스캔 후 정보를 유지하기 위해 clearCurrentParticipant 제거
        // 필요시 다른 Fragment로 이동할 때만 지우도록 변경
    }

    override fun onDestroyView() {
        super.onDestroyView()
        com.example.qr.utils.CrashLogger.log("❌❌❌ EventFragment onDestroyView - View 파괴됨")

        // 타이머 취소
        clearRunnable?.let { clearHandler.removeCallbacks(it) }

        // Restore bottom navigation bar visibility
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)?.visibility = View.VISIBLE

        _binding = null
    }
}