package com.example.barcode.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix

class BarcodeGenerator {

    companion object {
        fun generateQRCode(
            data: String,
            width: Int = 512,
            height: Int = 512
        ): Bitmap? {
            return try {
                val writer = QRCodeWriter()
                val hints = hashMapOf<EncodeHintType, Any>().apply {
                    put(EncodeHintType.CHARACTER_SET, "UTF-8")
                    put(EncodeHintType.MARGIN, 1)
                }

                val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, width, height, hints)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }

                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun generateBarcodeWithInfo(
            data: String,
            participantName: String,
            width: Int = 512,
            height: Int = 512
        ): Bitmap? {
            // QR 코드에 참가자 정보를 JSON 형태로 포함
            val qrData = buildString {
                append("{")
                append("\"code\":\"$data\",")
                append("\"name\":\"$participantName\",")
                append("\"timestamp\":${System.currentTimeMillis()}")
                append("}")
            }

            return generateQRCode(qrData, width, height)
        }

        fun isValidBarcodeData(data: String): Boolean {
            return data.isNotEmpty() && data.length >= 3
        }
    }
}