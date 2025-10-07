package com.example.barcode.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.barcode.R
import com.example.barcode.ViewModelFactory
import com.example.barcode.databinding.FragmentEventBinding
import com.example.barcode.ui.event.EventViewModel
import com.example.barcode.ui.scanner.BarcodeScannerActivity
import com.example.barcode.data.entity.ParticipantWithScanInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EventFragment : Fragment() {

    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: EventViewModel

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[EventViewModel::class.java]

        setupUI()
        observeViewModel()

        viewModel.loadActiveEvent()
    }

    private fun setupUI() {
        // USB 바코드 리더기만 사용 - 화면 클릭으로 카메라 스캐너 비활성화
        // binding.root.setOnClickListener {
        //     startBarcodeScanner()
        // }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSettings.setOnClickListener {
            Toast.makeText(requireContext(), "설정 기능은 추후 구현 예정입니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.activeEvent.observe(viewLifecycleOwner) { event ->
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
            if (success) {
                showScanSuccessEffect()
            }
        }
    }

    private fun updateParticipantInfo(participantInfo: ParticipantWithScanInfo?) {
        if (participantInfo == null) {
            binding.apply {
                tvFullName.text = ""
                tvLicenseNo.text = ""
                tvFirstScanTime.text = ""
                tvLastScanTime.text = ""
                tvDurationTime.text = "00:00:00"
                tvStatus.text = "바코드를 스캔해주세요"
                tvStatus.setTextColor(requireContext().getColor(R.color.status_waiting))
            }
        } else {
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
                tvStatus.text = participantInfo.statusText

                val statusColor = when (participantInfo.statusText) {
                    "입장중" -> requireContext().getColor(R.color.status_inside)
                    "퇴장완료" -> requireContext().getColor(R.color.status_exited)
                    else -> requireContext().getColor(R.color.status_waiting)
                }
                tvStatus.setTextColor(statusColor)
            }
        }
    }

    private fun startBarcodeScanner() {
        val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
        scannerLauncher.launch(intent)
    }

    private fun handleBarcodeScanned(barcodeData: String) {
        lifecycleScope.launch {
            viewModel.processBarcodeScanned(barcodeData)
        }
    }

    /**
     * USB 바코드 리더기에서 스캔된 바코드를 처리합니다.
     * MainActivity에서 호출됩니다.
     */
    fun handleUsbBarcodeScanned(barcodeData: String) {
        handleBarcodeScanned(barcodeData)
    }

    private fun showScanSuccessEffect() {
        binding.scanIndicator.apply {
            alpha = 1f
            animate()
                .alpha(0f)
                .setDuration(1000)
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.clearCurrentParticipant()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}