package com.example.barcode.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventName: String,
    val eventDate: String,
    val description: String? = null,
    val isActive: Boolean = false,
    val headerText: String = "이벤트",
    val backgroundColor: String = "#FFFFFF",
    val textColor: String = "#000000",
    val logoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)