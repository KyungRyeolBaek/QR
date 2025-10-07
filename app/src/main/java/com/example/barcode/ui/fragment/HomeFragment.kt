package com.example.barcode.ui.fragment

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
import com.example.barcode.R
import com.example.barcode.ViewModelFactory
import com.example.barcode.databinding.FragmentHomeBinding
import com.example.barcode.ui.main.MainViewModel
import com.example.barcode.ui.scanner.BarcodeScannerActivity
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