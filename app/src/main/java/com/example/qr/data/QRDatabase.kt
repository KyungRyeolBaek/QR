package com.example.qr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.qr.data.dao.EntryLogDao
import com.example.qr.data.dao.SMSLogDao
import com.example.qr.data.dao.UserDao
import com.example.qr.data.entities.EntryLog
import com.example.qr.data.entities.SMSLog
import com.example.qr.data.entities.User

@Database(
    entities = [User::class, EntryLog::class, SMSLog::class],
    version = 1,
    exportSchema = false
)
abstract class QRDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun entryLogDao(): EntryLogDao
    abstract fun smsLogDao(): SMSLogDao

    companion object {
        @Volatile
        private var INSTANCE: QRDatabase? = null

        fun getDatabase(context: Context): QRDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QRDatabase::class.java,
                    "qr_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}