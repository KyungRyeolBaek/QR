package com.example.qr.ui.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr.ViewModelFactory
import com.example.qr.databinding.FragmentMonitoringBinding
import com.example.qr.service.SmsService
import com.example.qr.ui.adapter.ScanRecordsAdapter
import com.example.qr.ui.monitoring.MonitoringViewModel

class MonitoringFragment : Fragment() {

    private var _binding: FragmentMonitoringBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MonitoringViewModel
    private lateinit var adapter: ScanRecordsAdapter

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
        viewModel = ViewModelProvider(this, factory)[MonitoringViewModel::class.java]

        setupRecyclerView()
        setupUI()
        observeViewModel()
        updateSmsPermissionStatus()

        viewModel.loadStatistics()
        viewModel.loadRecentScans()
    }

    private fun setupRecyclerView() {
        adapter = ScanRecordsAdapter()
        binding.rvRecentScans.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MonitoringFragment.adapter
        }
    }

    private fun setupUI() {
        binding.apply {
            btnStartServer.setOnClickListener {
                viewModel.startWebServer()
            }

            btnStopServer.setOnClickListener {
                viewModel.stopWebServer()
            }
        }
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