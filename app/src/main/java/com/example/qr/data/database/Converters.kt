package com.example.qr.data.database

import androidx.room.TypeConverter
import com.example.qr.data.entity.ScanType

class Converters {

    @TypeConverter
    fun fromScanType(scanType: ScanType): String {
        return scanType.name
    }

    @TypeConverter
    fun toScanType(scanType: String): ScanType {
        return ScanType.valueOf(scanType)
    }
}