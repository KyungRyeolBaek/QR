package com.example.qr.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null

    fun init(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "crash_log_${dateFormat.format(Date())}.txt"
            logFile = File(logDir, fileName)

            fileWriter = FileWriter(logFile, true)

            log("===============================================")
            log("앱 시작: ${dateFormat.format(Date())}")
            log("디바이스: ${Build.MANUFACTURER} ${Build.MODEL}")
            log("안드로이드 버전: ${Build.VERSION.SDK_INT}")
            log("===============================================")

            // 전역 예외 핸들러 설정
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logCrash(thread, throwable)

                // 원래 핸들러 호출 (앱 종료)
                val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun log(message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] $message\n"

            // 콘솔 출력
            println(logMessage.trim())

            // 파일 출력
            fileWriter?.write(logMessage)
            fileWriter?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun logCrash(thread: Thread, throwable: Throwable) {
        try {
            log("===============================================")
            log("❌❌❌ 크래시 발생! ❌❌❌")
            log("스레드: ${thread.name}")
            log("예외: ${throwable.javaClass.simpleName}")
            log("메시지: ${throwable.message}")
            log("-----------------------------------------------")
            log("스택 트레이스:")

            val stringWriter = java.io.StringWriter()
            val printWriter = PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            log(stringWriter.toString())

            log("===============================================")

            fileWriter?.flush()
            fileWriter?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }

    fun close() {
        try {
            fileWriter?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
