package com.example.qr.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "entry_logs",
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
data class EntryLog(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String, // For quick lookup without join
    val entryType: String, // "ENTER" or "EXIT"
    val timestamp: Long = System.currentTimeMillis(),
    val location: String? = null
)