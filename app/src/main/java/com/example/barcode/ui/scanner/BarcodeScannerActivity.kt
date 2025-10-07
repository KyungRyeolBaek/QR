package com.example.barcode.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.barcode.databinding.ActivityBarcodeScannerBinding
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
class BarcodeScannerActivity : AppCompatActivity(), DecoratedBarcodeView.TorchListener {

    private lateinit var binding: ActivityBarcodeScannerBinding
    private var barcodeView: DecoratedBarcodeView? = null
    private var isTorchOn: Boolean = false

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
            // 바코드 감지 포인트 표시 (선택사항)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkCameraPermission()
    }

    private fun setupUI() {
        barcodeView = binding.barcodeScanner

        // 스캐너 설정
        barcodeView?.apply {
            decodeContinuous(callback)
            setStatusText("바코드를 카메라에 비춰주세요")
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
        // 스캔 결과를 이전 액티비티로 전달
        val resultIntent = Intent().apply {
            putExtra("SCAN_RESULT", scannedData)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        barcodeView?.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView?.pause()
    }

    companion object {
        const val REQUEST_CODE_SCAN = 1001
    }
}