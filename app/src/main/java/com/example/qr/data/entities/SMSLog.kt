package com.example.qr.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sms_logs",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class SMSLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val phoneNumber: String,
    val status: String, // "SUCCESS", "FAILED", "PENDING"
    val sentAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)