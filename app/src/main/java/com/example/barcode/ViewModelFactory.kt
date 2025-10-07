package com.example.barcode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.barcode.data.database.BarcodeDatabase
import com.example.barcode.ui.event.EventViewModel
import com.example.barcode.ui.main.MainViewModel
import com.example.barcode.ui.monitoring.MonitoringViewModel

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    private val database by lazy { BarcodeDatabase.getDatabase(context) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(
                    database.eventDao(),
                    database.participantDao(),
                    database.scanRecordDao()
                ) as T
            }
            modelClass.isAssignableFrom(EventViewModel::class.java) -> {
                EventViewModel(
                    database.eventDao(),
                    database.participantDao(),
                    database.scanRecordDao()
                ) as T
            }
            modelClass.isAssignableFrom(MonitoringViewModel::class.java) -> {
                MonitoringViewModel(
                    database.eventDao(),
                    database.participantDao(),
                    database.scanRecordDao(),
                    context
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}