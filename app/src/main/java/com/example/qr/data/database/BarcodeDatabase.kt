package com.example.qr.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.qr.data.dao.EventDao
import com.example.qr.data.dao.ParticipantDao
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.data.entity.Event
import com.example.qr.data.entity.Participant
import com.example.qr.data.entity.ScanRecord

@Database(
    entities = [
        Event::class,
        Participant::class,
        ScanRecord::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BarcodeDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun participantDao(): ParticipantDao
    abstract fun scanRecordDao(): ScanRecordDao

    companion object {
        @Volatile
        private var INSTANCE: BarcodeDatabase? = null

        fun getDatabase(context: Context): BarcodeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BarcodeDatabase::class.java,
                    "barcode_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}