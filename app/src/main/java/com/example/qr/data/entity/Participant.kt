package com.example.qr.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "participants")
data class Participant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fullName: String,              // 성명(국문)
    val englishName: String = "",      // 성명(영문)
    val chineseName: String = "",      // 성명(한문)
    val phoneNumber: String,
    val licenseNo: String,
    val barcodeData: String,
    val eventId: Long,
    val cmeCredits: Double? = 5.0, // CME Credits 기본값 5.0
    val createdAt: Long = System.currentTimeMillis()
)