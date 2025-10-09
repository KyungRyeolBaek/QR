package com.example.qr.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.qr.R
import com.example.qr.ViewModelFactory
import com.example.qr.databinding.FragmentHomeBinding
import com.example.qr.ui.main.MainViewModel
import com.example.qr.ui.scanner.BarcodeScannerActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                // 먼저 미리보기 표시
                viewModel.previewExcelFile(requireContext(), it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            btnImportExcel.setOnClickListener {
                openExcelFilePicker()
            }

            btnDownloadTemplate.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.downloadExcelTemplate(requireContext())
                }
            }

            btnCreateSampleData.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.createSampleData()
                }
            }

            btnScanBarcode.setOnClickListener {
                startActivity(Intent(requireContext(), BarcodeScannerActivity::class.java))
            }

            btnExportData.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.exportEventData(requireContext())
                }
            }

            btnSendQrCodes.setOnClickListener {
                showSendQrCodesDialog(isResend = false)
            }

            btnResendQrCodes.setOnClickListener {
                showSendQrCodesDialog(isResend = true)
            }

            // 로그 파일 보기 버튼 (디버깅용)
            btnCreateSampleData.setOnLongClickListener {
                showLogFile()
                true
            }
        }
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

    private fun showLogFile() {
        val logPath = com.example.qr.utils.CrashLogger.getLogFilePath()
        if (logPath != null) {
            val logFile = java.io.File(logPath)
            if (logFile.exists()) {
                try {
                    val content = logFile.readText()
                    AlertDialog.Builder(requireContext())
                        .setTitle("크래시 로그")
                        .setMessage(content.takeLast(3000)) // 마지막 3000자만 표시
                        .setPositiveButton("복사") { _, _ ->
                            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("crash_log", content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(requireContext(), "로그가 클립보드에 복사되었습니다", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("닫기", null)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "로그 읽기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "로그 파일 없음: $logPath", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "로그 파일 경로를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.message.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.apply {
                btnImportExcel.isEnabled = !isLoading
                btnDownloadTemplate.isEnabled = !isLoading
                btnScanBarcode.isEnabled = !isLoading
                btnExportData.isEnabled = !isLoading
            }
        }

        // Excel preview dialog observing removed temporarily
    }

    // Temporarily removed Excel preview dialog function

    private fun openExcelFilePicker() {
        filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}