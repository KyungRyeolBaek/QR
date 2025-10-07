package com.example.qr.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_records",
    indices = [
        androidx.room.Index("participantId"),
        androidx.room.Index("eventId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = Participant::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val participantId: Long,
    val eventId: Long,
    val scanTime: Long,
    val scanType: ScanType,
    val deviceId: String? = null
)

enum class ScanType {
    ENTRY,  // 입장
    EXIT    // 퇴장
}