package com.example.qr.ui.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr.R
import com.example.qr.ViewModelFactory
import com.example.qr.databinding.FragmentParticipantManagementBinding
import com.example.qr.ui.adapter.ParticipantListAdapter
import com.example.qr.ui.adapter.ParticipantWithSelection
import com.example.qr.ui.participant.ParticipantManagementViewModel
import com.example.qr.utils.CsvWriter

class ParticipantManagementFragment : Fragment() {

    private var _binding: FragmentParticipantManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ParticipantManagementViewModel
    private lateinit var adapter: ParticipantListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParticipantManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[ParticipantManagementViewModel::class.java]

        setupRecyclerView()
        setupSearchBar()
        setupFilterSpinner()
        setupButtons()
        observeViewModel()

        // Load participants
        viewModel.loadParticipants()
    }

    private fun setupRecyclerView() {
        adapter = ParticipantListAdapter(
            onItemClick = { participant ->
                // Toggle selection on item click
                viewModel.toggleSelection(
                    participant.participant.id,
                    !participant.isSelected
                )
            },
            onSelectionChange = { participant, isSelected ->
                viewModel.toggleSelection(participant.participant.id, isSelected)
            },
            onDetailClick = { participant ->
                showParticipantDetail(participant)
            }
        )

        binding.rvParticipants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ParticipantManagementFragment.adapter
        }
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.searchParticipants(s?.toString() ?: "")
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun setupFilterSpinner() {
        val statusFilters = arrayOf("전체", "입장중", "퇴장", "미입장")
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            statusFilters
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatusFilter.adapter = spinnerAdapter

        binding.spinnerStatusFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val statusFilter = when (position) {
                    1 -> "inside"      // 입장중
                    2 -> "outside"     // 퇴장
                    3 -> "never"       // 미입장
                    else -> "all"      // 전체
                }
                viewModel.loadParticipants(statusFilter)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnSelectAll.setOnClickListener {
            viewModel.selectAll()
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selectedCount = viewModel.selectedParticipants.value?.size ?: 0
            if (selectedCount > 0) {
                showDeleteConfirmDialog(selectedCount)
            } else {
                Toast.makeText(requireContext(), "선택된 참가자가 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnExportCsv.setOnClickListener {
            exportToCsv()
        }
    }

    private fun exportToCsv() {
        val participants = viewModel.participants.value
        if (participants.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "다운로드할 데이터가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        // Get current event (assuming we have it from arguments or viewModel)
        val eventId = arguments?.getLong("eventId") ?: 1L
        viewModel.exportParticipantsToCsv(requireContext(), eventId) { result ->
            result.fold(
                onSuccess = { file ->
                    Toast.makeText(requireContext(), "CSV 다운로드 완료: ${file.name}", Toast.LENGTH_LONG).show()
                    CsvWriter.shareCsvFile(requireContext(), file)
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "다운로드 실패: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun observeViewModel() {
        viewModel.participants.observe(viewLifecycleOwner) { participants ->
            adapter.submitList(participants)

            // Update participant count
            binding.tvParticipantCount.text = "총 ${participants.size}명"

            // Show/hide empty state
            if (participants.isEmpty()) {
                binding.rvParticipants.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
            } else {
                binding.rvParticipants.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
            }
        }

        viewModel.selectedParticipants.observe(viewLifecycleOwner) { selected ->
            // Enable/disable delete button based on selection
            binding.btnDeleteSelected.isEnabled = selected.isNotEmpty()

            // Update button text
            if (selected.isNotEmpty()) {
                binding.btnDeleteSelected.text = "삭제 (${selected.size})"
            } else {
                binding.btnDeleteSelected.text = "삭제"
            }
        }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun showDeleteConfirmDialog(count: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("참가자 삭제")
            .setMessage("선택한 ${count}명의 참가자를 삭제하시겠습니까?\n\n※ 스캔 기록도 함께 삭제됩니다.")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteSelectedParticipants()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showParticipantDetail(participant: ParticipantWithSelection) {
        val message = """
            이름: ${participant.participant.fullName}
            전화번호: ${participant.participant.phoneNumber}
            라이센스: ${participant.participant.licenseNo}
            QR 코드: ${participant.participant.barcodeData}

            스캔 횟수: ${participant.scanCount}회
            상태: ${if (participant.isCurrentlyInside) "입장중" else if (participant.scanCount > 0) "퇴장" else "미입장"}
            최근 스캔: ${if (participant.lastScanTime != null) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(participant.lastScanTime)) else "-"}
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("참가자 상세 정보")
            .setMessage(message)
            .setPositiveButton("닫기", null)
            .setNegativeButton("삭제") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("참가자 삭제")
                    .setMessage("${participant.participant.fullName}님을 삭제하시겠습니까?\n\n※ 스캔 기록도 함께 삭제됩니다.")
                    .setPositiveButton("삭제") { _, _ ->
                        viewModel.deleteParticipant(participant.participant.id)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
