package com.example.qr.ui.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr.ViewModelFactory
import com.example.qr.databinding.FragmentMonitoringBinding
import com.example.qr.service.SmsService
import com.example.qr.ui.adapter.ScanRecordsAdapter
import com.example.qr.ui.adapter.ParticipantListAdapter
import com.example.qr.ui.monitoring.MonitoringViewModel
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MonitoringFragment : Fragment() {

    private var _binding: FragmentMonitoringBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MonitoringViewModel
    private lateinit var adapter: ScanRecordsAdapter
    private lateinit var participantAdapter: ParticipantListAdapter
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                viewModel.uploadExcelFile(requireContext(), it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonitoringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ViewModelFactory(requireContext())
        // Activity-scoped ViewModel로 변경 (탭 전환 시에도 유지)
        viewModel = ViewModelProvider(requireActivity(), factory)[MonitoringViewModel::class.java]

        setupRecyclerView()
        setupParticipantList()
        setupUI()
        setupParticipantManagement()
        observeViewModel()
        updateSmsPermissionStatus()

        viewModel.loadStatistics()
        viewModel.loadRecentScans()
        viewModel.loadParticipantsWithFilter()
    }

    private fun setupRecyclerView() {
        adapter = ScanRecordsAdapter()
        binding.rvRecentScans.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MonitoringFragment.adapter
        }
    }

    private fun setupParticipantList() {
        participantAdapter = ParticipantListAdapter(
            onItemClick = { item ->
                // Toggle selection on item click
                viewModel.toggleParticipantSelection(item.participant.id, !item.isSelected)
            },
            onSelectionChange = { item, isSelected ->
                viewModel.toggleParticipantSelection(item.participant.id, isSelected)
            },
            onDetailClick = { item ->
                showParticipantDetailDialog(item)
            }
        )

        binding.rvParticipants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = participantAdapter
        }
    }

    private fun setupUI() {
        // Load saved mode
        val prefs = requireContext().getSharedPreferences("device_settings", android.content.Context.MODE_PRIVATE)
        val isMasterMode = prefs.getBoolean("is_master_mode", true)
        val masterIp = prefs.getString("master_ip", "") ?: ""

        binding.apply {
            // 모드 선택 설정
            radioMasterMode.isChecked = isMasterMode
            radioClientMode.isChecked = !isMasterMode
            masterIpContainer.visibility = if (isMasterMode) View.GONE else View.VISIBLE
            etMasterIp.setText(masterIp)

            // 모드 변경 리스너
            radioGroupDeviceMode.setOnCheckedChangeListener { _, checkedId ->
                val isMaster = checkedId == radioMasterMode.id
                masterIpContainer.visibility = if (isMaster) View.GONE else View.VISIBLE

                // 설정 저장
                prefs.edit().apply {
                    putBoolean("is_master_mode", isMaster)
                    if (!isMaster) {
                        putString("master_ip", etMasterIp.text.toString())
                    }
                    apply()
                }

                // 버튼 텍스트 업데이트
                updateButtonsForMode(isMaster)

                Toast.makeText(
                    requireContext(),
                    if (isMaster) "마스터 모드로 설정되었습니다" else "클라이언트 모드로 설정되었습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // 마스터 IP 저장
            etMasterIp.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    prefs.edit().putString("master_ip", etMasterIp.text.toString()).apply()
                }
            }

            // 초기 버튼 설정
            updateButtonsForMode(isMasterMode)

            btnStartServer.setOnClickListener {
                val isMaster = prefs.getBoolean("is_master_mode", true)
                if (isMaster) {
                    viewModel.startWebServer()
                } else {
                    // 클라이언트 모드: 서버 연결
                    val masterUrl = etMasterIp.text.toString()
                    if (masterUrl.isBlank()) {
                        Toast.makeText(requireContext(), "마스터 서버 주소를 입력해주세요", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    prefs.edit().putString("master_ip", masterUrl).apply()
                    viewModel.connectToMasterServer(masterUrl)
                }
            }

            btnStopServer.setOnClickListener {
                val isMaster = prefs.getBoolean("is_master_mode", true)
                if (isMaster) {
                    viewModel.stopWebServer()
                } else {
                    // 클라이언트 모드: 연결 해제
                    viewModel.disconnectFromMasterServer()
                }
            }

            // 파일 관리 버튼들
            btnUploadExcel.setOnClickListener {
                openExcelFilePicker()
            }

            btnDownloadExcel.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.downloadExcelData(requireContext())
                }
            }

            btnDownloadDetailedExcel.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.downloadDetailedExcel(requireContext())
                }
            }

            btnDownloadTemplate.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.downloadExcelTemplate(requireContext())
                }
            }

            btnSendQrCodes.setOnClickListener {
                showSendQrCodesDialog(isResend = false)
            }

            btnResendQrCodes.setOnClickListener {
                showSendQrCodesDialog(isResend = true)
            }

            btnQrSettings.setOnClickListener {
                showQrSettingsDialog()
            }
        }
    }

    private fun setupParticipantManagement() {
        binding.apply {
            // Setup TabLayout
            tabStatusFilter.addTab(tabStatusFilter.newTab().setText("전체"))
            tabStatusFilter.addTab(tabStatusFilter.newTab().setText("입장중"))
            tabStatusFilter.addTab(tabStatusFilter.newTab().setText("퇴장"))
            tabStatusFilter.addTab(tabStatusFilter.newTab().setText("미입장"))

            tabStatusFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val statusFilter = when (tab?.position) {
                        0 -> "all"
                        1 -> "inside"
                        2 -> "exited"
                        3 -> "never"
                        else -> "all"
                    }
                    viewModel.loadParticipantsWithFilter(statusFilter)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            // Search functionality
            etParticipantSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    viewModel.searchParticipants(s?.toString() ?: "")
                }
            })

            btnClearParticipantSearch.setOnClickListener {
                etParticipantSearch.text.clear()
            }

            // Selection buttons
            btnSelectAllParticipants.setOnClickListener {
                viewModel.toggleSelectAllParticipants()
            }

            btnDeleteSelectedParticipants.setOnClickListener {
                showDeleteConfirmationDialog()
            }

            // QR send buttons for selected participants
            btnSendQrToSelected.setOnClickListener {
                showSendQrToSelectedDialog(isResend = false)
            }

            btnResendQrToSelected.setOnClickListener {
                showSendQrToSelectedDialog(isResend = true)
            }
        }
    }

    private fun showParticipantDetailDialog(item: com.example.qr.ui.adapter.ParticipantWithSelection) {
        val participant = item.participant
        val message = buildString {
            append("이름: ${participant.fullName}\n")
            append("전화번호: ${participant.phoneNumber}\n")
            append("라이센스: ${participant.licenseNo}\n")
            append("바코드: ${participant.barcodeData}\n")
            append("스캔 횟수: ${item.scanCount}회\n")
            append("상태: ${if (item.isCurrentlyInside) "입장중" else if (item.scanCount > 0) "퇴장" else "미입장"}\n")
            if (item.lastScanTime != null) {
                append("마지막 스캔: ${dateFormat.format(Date(item.lastScanTime))}")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("참가자 상세 정보")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showDeleteConfirmationDialog() {
        val selectedCount = viewModel.selectedParticipants.value?.size ?: 0
        if (selectedCount == 0) {
            Toast.makeText(requireContext(), "삭제할 참가자를 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("참가자 삭제")
            .setMessage("선택한 ${selectedCount}명의 참가자를 삭제하시겠습니까?\n\n스캔 기록도 함께 삭제됩니다.")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteSelectedParticipants()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showSendQrToSelectedDialog(isResend: Boolean) {
        val selectedCount = viewModel.selectedParticipants.value?.size ?: 0
        if (selectedCount == 0) {
            Toast.makeText(requireContext(), "발송할 참가자를 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        val title = if (isResend) "선택 참가자 QR 코드 재전송" else "선택 참가자 QR 코드 발송"
        val message = if (isResend) {
            "선택된 ${selectedCount}명의 참가자에게 QR 코드를 재전송하시겠습니까?\n\n참고: SMS 권한이 필요합니다."
        } else {
            "선택된 ${selectedCount}명의 참가자에게 QR 코드를 발송하시겠습니까?\n\n참고: SMS 권한이 필요합니다."
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("발송") { _, _ ->
                lifecycleScope.launch {
                    viewModel.sendQrCodesToSelectedParticipants(requireContext(), isResend)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showQrSettingsDialog() {
        val prefs = requireContext().getSharedPreferences("qr_settings", android.content.Context.MODE_PRIVATE)

        val defaultTemplate = com.example.qr.service.SmsService.DEFAULT_MESSAGE_TEMPLATE
        val defaultResendTemplate = com.example.qr.service.SmsService.RESEND_MESSAGE_TEMPLATE

        val currentDefaultTemplate = prefs.getString("default_template", defaultTemplate) ?: defaultTemplate
        val currentResendTemplate = prefs.getString("resend_template", defaultResendTemplate) ?: defaultResendTemplate

        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)

        val options = arrayOf(
            "기본 메시지 템플릿 편집",
            "재발송 메시지 템플릿 편집",
            "템플릿 초기화"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("⚙️ QR 코드 발송 설정")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTemplateEditDialog("기본 메시지 템플릿", currentDefaultTemplate, "default_template")
                    1 -> showTemplateEditDialog("재발송 메시지 템플릿", currentResendTemplate, "resend_template")
                    2 -> resetTemplates()
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showTemplateEditDialog(title: String, currentTemplate: String, key: String) {
        val editText = android.widget.EditText(requireContext()).apply {
            setText(currentTemplate)
            minLines = 6
            maxLines = 10
            hint = "사용 가능한 변수:\n{이름} - 참가자 이름"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val newTemplate = editText.text.toString()
                if (newTemplate.isNotBlank()) {
                    val prefs = requireContext().getSharedPreferences("qr_settings", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString(key, newTemplate).apply()
                    Toast.makeText(requireContext(), "템플릿이 저장되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "템플릿은 비워둘 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .setNeutralButton("미리보기") { _, _ ->
                showTemplatePreview(title, editText.text.toString())
            }
            .show()
    }

    private fun showTemplatePreview(title: String, template: String) {
        val preview = template.replace("{이름}", "홍길동")

        AlertDialog.Builder(requireContext())
            .setTitle("$title - 미리보기")
            .setMessage(preview)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun resetTemplates() {
        AlertDialog.Builder(requireContext())
            .setTitle("템플릿 초기화")
            .setMessage("모든 메시지 템플릿을 기본값으로 초기화하시겠습니까?")
            .setPositiveButton("초기화") { _, _ ->
                val prefs = requireContext().getSharedPreferences("qr_settings", android.content.Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                Toast.makeText(requireContext(), "템플릿이 초기화되었습니다", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun openExcelFilePicker() {
        // CSV files - use wildcard to handle different MIME types
        filePickerLauncher.launch("text/comma-separated-values")
    }

    private fun showSendQrCodesDialog(isResend: Boolean) {
        val title = if (isResend) "QR 코드 재전송" else "QR 코드 일괄 발송"
        val message = if (isResend) {
            "등록된 참가자에게 QR 코드를 재전송하시겠습니까?\n\n참고: SMS 권한이 필요합니다."
        } else {
            "등록된 참가자에게 QR 코드를 발송하시겠습니까?\n\n참고: SMS 권한이 필요합니다."
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("발송") { _, _ ->
                lifecycleScope.launch {
                    viewModel.sendQrCodesToParticipants(requireContext(), isResend)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.statistics.observe(viewLifecycleOwner) { stats ->
            binding.apply {
                tvTotalParticipants.text = stats.totalParticipants.toString()
                tvCurrentInside.text = stats.currentInside.toString()
                tvTotalVisits.text = stats.totalVisits.toString()
            }
        }

        viewModel.recentScans.observe(viewLifecycleOwner) { scans ->
            adapter.submitList(scans)
        }

        viewModel.serverStatus.observe(viewLifecycleOwner) { status ->
            binding.apply {
                when (status) {
                    is MonitoringViewModel.ServerStatus.Stopped -> {
                        tvServerStatus.text = "서버 중지됨"
                        btnStartServer.isEnabled = true
                        btnStopServer.isEnabled = false
                        tvServerUrl.visibility = View.GONE
                    }
                    is MonitoringViewModel.ServerStatus.Starting -> {
                        tvServerStatus.text = "서버 시작 중..."
                        btnStartServer.isEnabled = false
                        btnStopServer.isEnabled = false
                        tvServerUrl.visibility = View.GONE
                    }
                    is MonitoringViewModel.ServerStatus.Running -> {
                        tvServerStatus.text = "서버 실행 중"
                        btnStartServer.isEnabled = false
                        btnStopServer.isEnabled = true
                        tvServerUrl.text = status.url
                        tvServerUrl.visibility = View.VISIBLE
                    }
                    is MonitoringViewModel.ServerStatus.Error -> {
                        tvServerStatus.text = "서버 오류: ${status.message}"
                        btnStartServer.isEnabled = true
                        btnStopServer.isEnabled = false
                        tvServerUrl.visibility = View.GONE
                    }
                }
            }
        }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        // Participant management observers
        viewModel.participants.observe(viewLifecycleOwner) { participants ->
            participantAdapter.submitList(participants) {
                // Force RecyclerView to recalculate its height after list is submitted
                binding.rvParticipants.requestLayout()
            }
            binding.apply {
                tvParticipantsCount.text = "${participants.size}명"
                emptyStateParticipants.visibility = if (participants.isEmpty()) View.VISIBLE else View.GONE
                rvParticipants.visibility = if (participants.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        viewModel.selectedParticipants.observe(viewLifecycleOwner) { selectedIds ->
            val hasSelection = selectedIds.isNotEmpty()
            binding.apply {
                btnDeleteSelectedParticipants.isEnabled = hasSelection
                btnSendQrToSelected.isEnabled = hasSelection
                btnResendQrToSelected.isEnabled = hasSelection
            }
        }

        // 클라이언트 연결 상태 observer
        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            binding.apply {
                when (status) {
                    is MonitoringViewModel.ConnectionStatus.Disconnected -> {
                        tvServerStatus.text = "연결 안 됨"
                        btnStartServer.isEnabled = true
                        btnStopServer.isEnabled = false
                        tvServerUrl.visibility = View.GONE
                    }
                    is MonitoringViewModel.ConnectionStatus.Connecting -> {
                        tvServerStatus.text = "연결 중..."
                        btnStartServer.isEnabled = false
                        btnStopServer.isEnabled = false
                        tvServerUrl.visibility = View.GONE
                    }
                    is MonitoringViewModel.ConnectionStatus.Connected -> {
                        tvServerStatus.text = "연결됨"
                        btnStartServer.isEnabled = false
                        btnStopServer.isEnabled = true
                        tvServerUrl.text = status.masterUrl
                        tvServerUrl.visibility = View.VISIBLE
                    }
                    is MonitoringViewModel.ConnectionStatus.Error -> {
                        tvServerStatus.text = "연결 실패: ${status.message}"
                        btnStartServer.isEnabled = true
                        btnStopServer.isEnabled = false
                        tvServerUrl.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun updateButtonsForMode(isMasterMode: Boolean) {
        binding.apply {
            if (isMasterMode) {
                btnStartServer.text = "서버 시작"
                btnStopServer.text = "서버 중지"
            } else {
                btnStartServer.text = "서버 연결"
                btnStopServer.text = "연결 해제"
            }
        }
    }

    /**
     * SMS 권한 상태 업데이트
     */
    private fun updateSmsPermissionStatus() {
        val smsService = SmsService(requireContext())
        val hasPermission = smsService.hasSmsPermission()

        // 권한 상태 로그 출력
        println("📱 SMS 권한 상태: ${smsService.getPermissionStatusMessage()}")

        // 권한이 없을 때만 경고 표시 (처음 한 번만)
        if (!hasPermission && !hasShownPermissionWarning) {
            hasShownPermissionWarning = true
            showSmsPermissionWarning()
        }
    }

    companion object {
        private var hasShownPermissionWarning = false
    }

    /**
     * SMS 권한 경고 표시
     */
    private fun showSmsPermissionWarning() {
        val smsService = SmsService(requireContext())
        val statusMessage = smsService.getPermissionStatusMessage()
        val guideMessage = smsService.getPermissionGuideMessage()

        AlertDialog.Builder(requireContext())
            .setTitle("SMS 발송 권한 확인")
            .setMessage("$statusMessage\n\n$guideMessage")
            .setPositiveButton("설정에서 허용") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("자세히 보기") { _, _ ->
                showDetailedPermissionInfo(smsService)
            }
            .setNeutralButton("나중에") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 상세한 권한 정보 표시
     */
    private fun showDetailedPermissionInfo(smsService: SmsService) {
        val permissionStatus = smsService.getPermissionStatus()
        val details = StringBuilder()

        details.append("현재 권한 상태:\n\n")

        permissionStatus.forEach { (permission, granted) ->
            val permissionName = when (permission) {
                Manifest.permission.SEND_SMS -> "SMS 발송"
                Manifest.permission.READ_PHONE_STATE -> "전화 상태 읽기"
                else -> permission
            }
            val status = if (granted) "✅ 허용됨" else "❌ 거부됨"
            details.append("• $permissionName: $status\n")
        }

        details.append("\nQR 코드 발송 기능을 사용하려면 모든 권한이 필요합니다.")

        AlertDialog.Builder(requireContext())
            .setTitle("권한 상세 정보")
            .setMessage(details.toString())
            .setPositiveButton("설정으로 이동") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 앱 설정 화면으로 이동
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "설정 화면을 열 수 없습니다. 수동으로 설정 > 앱 > 권한에서 SMS 권한을 허용해주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Fragment가 다시 보여질 때 권한 상태 재확인
     */
    override fun onResume() {
        super.onResume()
        updateSmsPermissionStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}