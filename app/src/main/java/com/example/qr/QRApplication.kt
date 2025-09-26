package com.example.qr

import android.app.Application
import com.example.qr.data.QRDatabase

class QRApplication : Application() {

    // Database
    val database by lazy { QRDatabase.getDatabase(this) }

    // DAOs
    val userDao by lazy { database.userDao() }
    val entryLogDao by lazy { database.entryLogDao() }
    val smsLogDao by lazy { database.smsLogDao() }

    // Repositories (간단한 팩토리 패턴)
    val userRepository by lazy {
        com.example.qr.repository.UserRepository(userDao)
    }

    val entryLogRepository by lazy {
        com.example.qr.repository.EntryLogRepository(entryLogDao)
    }

    val smsRepository by lazy {
        com.example.qr.repository.SMSRepository(smsLogDao)
    }

    // Services
    val smsService by lazy {
        com.example.qr.service.SMSService(this)
    }

    val nativeSMSService by lazy {
        com.example.qr.service.NativeSMSService(this)
    }

    val excelExportService by lazy {
        com.example.qr.service.ExcelExportService(
            context = this,
            userRepository = userRepository,
            entryLogRepository = entryLogRepository
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}