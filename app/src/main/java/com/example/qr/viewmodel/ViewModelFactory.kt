package com.example.qr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.qr.QRApplication

@Suppress("UNCHECKED_CAST")
class ViewModelFactory(private val application: QRApplication) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            UserRegistrationViewModel::class.java -> {
                UserRegistrationViewModel(
                    userRepository = application.userRepository,
                    smsRepository = application.smsRepository,
                    smsService = application.smsService,
                    nativeSMSService = application.nativeSMSService,
                    context = application.applicationContext
                ) as T
            }
            QRScannerViewModel::class.java -> {
                QRScannerViewModel(
                    userRepository = application.userRepository,
                    entryLogRepository = application.entryLogRepository
                ) as T
            }
            ExcelExportViewModel::class.java -> {
                ExcelExportViewModel(
                    excelExportService = application.excelExportService,
                    nativeSMSService = application.nativeSMSService
                ) as T
            }
            SettingsViewModel::class.java -> {
                SettingsViewModel(
                    context = application.applicationContext
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}