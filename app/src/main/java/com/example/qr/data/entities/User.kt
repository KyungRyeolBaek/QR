package com.example.qr.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val qrCode: String,
    val qrImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val smsStatus: String = "PENDING", // PENDING, SUCCESS, FAILED
    val isActive: Boolean = true
)