package com.example.barcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.barcode.databinding.ActivityMainBinding
import com.example.barcode.ui.fragment.EventFragment
import com.example.barcode.utils.UsbBarcodeInputHandler

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbBarcodeInputHandler: UsbBarcodeInputHandler

    companion object {
        private const val SMS_PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_SMS_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        checkAndRequestSmsPermissions()
        setupUsbBarcodeHandler()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun setupUsbBarcodeHandler() {
        // USB 바코드 입력 핸들러 초기화
        usbBarcodeInputHandler = UsbBarcodeInputHandler(
            coroutineScope = lifecycleScope,
            onBarcodeScanned = { barcodeData ->
                handleBarcodeForCurrentFragment(barcodeData)
            }
        )
    }

    private fun handleBarcodeForCurrentFragment(barcodeData: String) {
        // 현재 활성 Fragment가 EventFragment인지 확인하고 바코드 처리
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

        if (currentFragment is EventFragment) {
            currentFragment.handleUsbBarcodeScanned(barcodeData)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // USB 바코드 리더기 키 입력 처리
        event?.let {
            if (::usbBarcodeInputHandler.isInitialized &&
                usbBarcodeInputHandler.handleKeyEvent(keyCode, it)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // USB 바코드 입력 핸들러 정리
        if (::usbBarcodeInputHandler.isInitialized) {
            usbBarcodeInputHandler.cleanup()
        }
    }

    /**
     * SMS 권한 확인 및 요청
     */
    private fun checkAndRequestSmsPermissions() {
        val deniedPermissions = REQUIRED_SMS_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isNotEmpty()) {
            if (shouldShowRequestPermissionRationale()) {
                showPermissionRationaleDialog(deniedPermissions.toTypedArray())
            } else {
                requestSmsPermissions(deniedPermissions.toTypedArray())
            }
        }
    }

    /**
     * 권한 요청 이유 설명이 필요한지 확인
     */
    private fun shouldShowRequestPermissionRationale(): Boolean {
        return REQUIRED_SMS_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }
    }

    /**
     * 권한 요청 이유 설명 다이얼로그
     */
    private fun showPermissionRationaleDialog(permissions: Array<String>) {
        AlertDialog.Builder(this)
            .setTitle("SMS 발송 권한 필요")
            .setMessage("바코드를 SMS로 발송하기 위해 다음 권한이 필요합니다:\n\n" +
                    "• SMS 발송 권한: 참가자에게 바코드 이미지를 전송\n" +
                    "• 전화 상태 읽기: SMS 발송 기능을 위한 시스템 접근\n\n" +
                    "권한을 허용하지 않으면 바코드 발송 기능을 사용할 수 없습니다.")
            .setPositiveButton("권한 허용") { _, _ ->
                requestSmsPermissions(permissions)
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * SMS 권한 요청
     */
    private fun requestSmsPermissions(permissions: Array<String>) {
        ActivityCompat.requestPermissions(this, permissions, SMS_PERMISSION_REQUEST_CODE)
    }

    /**
     * 권한 요청 결과 처리
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            SMS_PERMISSION_REQUEST_CODE -> {
                val grantedPermissions = mutableListOf<String>()
                val deniedPermissions = mutableListOf<String>()

                permissions.forEachIndexed { index, permission ->
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                        grantedPermissions.add(permission)
                    } else {
                        deniedPermissions.add(permission)
                    }
                }

                when {
                    deniedPermissions.isEmpty() -> {
                        // 모든 권한이 허용됨
                        showPermissionResultDialog(
                            "권한 허용 완료",
                            "SMS 발송 권한이 허용되었습니다. 이제 바코드를 SMS로 발송할 수 있습니다."
                        )
                    }
                    grantedPermissions.isNotEmpty() -> {
                        // 일부 권한만 허용됨
                        showPermissionResultDialog(
                            "일부 권한 허용",
                            "일부 권한이 허용되지 않았습니다. 바코드 발송 기능이 제한될 수 있습니다.\n\n" +
                                    "설정 > 앱 > 권한에서 수동으로 권한을 허용할 수 있습니다."
                        )
                    }
                    else -> {
                        // 모든 권한이 거부됨
                        showPermissionResultDialog(
                            "권한 거부됨",
                            "SMS 발송 권한이 거부되었습니다. 바코드 발송 기능을 사용할 수 없습니다.\n\n" +
                                    "나중에 설정 > 앱 > 권한에서 수동으로 권한을 허용할 수 있습니다."
                        )
                    }
                }
            }
        }
    }

    /**
     * 권한 요청 결과 다이얼로그
     */
    private fun showPermissionResultDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}