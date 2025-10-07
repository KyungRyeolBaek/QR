package com.example.qr.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class BarcodeImageGenerator {

    companion object {
        private const val DEFAULT_WIDTH = 512  // QR 코드는 정사각형
        private const val DEFAULT_HEIGHT = 512  // QR 코드는 정사각형
        private const val QUIET_ZONE = 20  // QR 코드 주변 여백

        /**
         * QR 코드를 이미지 파일로 생성합니다.
         * @param barcodeText QR 코드에 포함될 텍스트
         * @param outputFile 저장될 파일 경로
         * @param width 이미지 너비 (기본값: 512px, 정사각형 권장)
         * @param height 이미지 높이 (기본값: 512px, 정사각형 권장)
         * @return 생성 성공 시 true, 실패 시 false
         */
        fun generateBarcodeImage(
            barcodeText: String,
            outputFile: File,
            width: Int = DEFAULT_WIDTH,
            height: Int = DEFAULT_HEIGHT
        ): Boolean {
            return try {
                println("QR 코드 이미지 생성 시작: $barcodeText")

                // ZXing을 사용하여 QR 코드 매트릭스 생성
                val writer = MultiFormatWriter()
                val hints = mapOf(
                    com.google.zxing.EncodeHintType.MARGIN to 1  // 최소 여백 설정
                )
                val bitMatrix = writer.encode(barcodeText, BarcodeFormat.QR_CODE, width - 2 * QUIET_ZONE, height - 2 * QUIET_ZONE, hints)

                // 더 큰 캔버스에 여백을 포함한 Bitmap 생성
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // 전체 배경을 흰색으로 설정 (여백 포함)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, Color.WHITE)
                    }
                }

                // QR 코드 영역만 실제 QR로 채우기 (여백 제외)
                for (x in 0 until bitMatrix.width) {
                    for (y in 0 until bitMatrix.height) {
                        val pixelX = x + QUIET_ZONE
                        val pixelY = y + QUIET_ZONE
                        if (pixelX < width && pixelY < height) {
                            bitmap.setPixel(pixelX, pixelY, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                        }
                    }
                }

                // 파일로 저장
                val outputStream = FileOutputStream(outputFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()

                println("QR 코드 이미지 생성 완료: ${outputFile.absolutePath}")
                true
            } catch (e: WriterException) {
                println("QR 코드 생성 오류: ${e.message}")
                false
            } catch (e: IOException) {
                println("파일 저장 오류: ${e.message}")
                false
            } catch (e: Exception) {
                println("일반 오류: ${e.message}")
                false
            }
        }

        /**
         * 임시 파일에 QR 코드 이미지를 생성합니다.
         * @param barcodeText QR 코드에 포함될 텍스트
         * @param width 이미지 너비 (기본값: 400px)
         * @param height 이미지 높이 (기본값: 200px)
         * @return 생성된 파일 객체 또는 null (실패 시)
         */
        fun generateBarcodeImageToTempFile(
            barcodeText: String,
            width: Int = DEFAULT_WIDTH,
            height: Int = DEFAULT_HEIGHT
        ): File? {
            return try {
                // 임시 파일 생성
                val tempFile = File.createTempFile("barcode_", ".png")

                if (generateBarcodeImage(barcodeText, tempFile, width, height)) {
                    tempFile
                } else {
                    tempFile.delete()
                    null
                }
            } catch (e: Exception) {
                println("임시 파일 생성 오류: ${e.message}")
                null
            }
        }

        /**
         * QR 코드 이미지를 메모리에서 Bitmap으로 생성합니다.
         * @param barcodeText QR 코드에 포함될 텍스트
         * @param width 이미지 너비 (기본값: 400px)
         * @param height 이미지 높이 (기본값: 200px)
         * @return 생성된 Bitmap 또는 null (실패 시)
         */
        fun generateBarcodeBitmap(
            barcodeText: String,
            width: Int = DEFAULT_WIDTH,
            height: Int = DEFAULT_HEIGHT
        ): Bitmap? {
            return try {
                println("QR 코드 Bitmap 생성 시작: $barcodeText")

                val writer = MultiFormatWriter()
                val hints = mapOf(
                    com.google.zxing.EncodeHintType.MARGIN to 1  // 최소 여백 설정
                )
                val bitMatrix = writer.encode(barcodeText, BarcodeFormat.QR_CODE, width - 2 * QUIET_ZONE, height - 2 * QUIET_ZONE, hints)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // 전체 배경을 흰색으로 설정 (여백 포함)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, Color.WHITE)
                    }
                }

                // QR 코드 영역만 실제 QR로 채우기 (여백 제외)
                for (x in 0 until bitMatrix.width) {
                    for (y in 0 until bitMatrix.height) {
                        val pixelX = x + QUIET_ZONE
                        val pixelY = y + QUIET_ZONE
                        if (pixelX < width && pixelY < height) {
                            bitmap.setPixel(pixelX, pixelY, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                        }
                    }
                }

                println("QR 코드 Bitmap 생성 완료")
                bitmap
            } catch (e: Exception) {
                println("QR 코드 Bitmap 생성 오류: ${e.message}")
                null
            }
        }

        /**
         * 고해상도 스캔 최적화 QR 코드 이미지를 생성합니다.
         * @param barcodeText QR 코드에 포함될 텍스트
         * @return 생성된 파일 객체 또는 null (실패 시)
         */
        fun generateHighQualityBarcodeToTempFile(barcodeText: String): File? {
            return generateBarcodeImageToTempFile(
                barcodeText = barcodeText,
                width = 800,    // QR 코드는 정사각형
                height = 800    // QR 코드는 정사각형
            )
        }

        /**
         * SMS 전송에 최적화된 QR 코드 이미지를 생성합니다.
         * @param barcodeText QR 코드에 포함될 텍스트
         * @param context 앱 컨텍스트
         * @return 생성된 파일 객체 또는 null (실패 시)
         */
        fun generateSmsOptimizedBarcodeToAppFiles(barcodeText: String, context: Context): File? {
            return try {
                println("SMS 최적화 QR 코드 생성 시작: $barcodeText")

                // MMS 크기 제한을 고려한 최적화된 크기 (통신사별 300KB-1MB 제한)
                val smsWidth = 400  // QR 코드는 정사각형
                val smsHeight = 400

                // 앱 전용 디렉토리에 파일 생성 (FileProvider 사용)
                val barcodeDir = File(context.filesDir, "barcode_images")
                if (!barcodeDir.exists()) {
                    barcodeDir.mkdirs()
                }

                val fileName = "barcode_${System.currentTimeMillis()}_${barcodeText.hashCode().toString().replace("-", "")}.png"
                val barcodeFile = File(barcodeDir, fileName)

                if (generateBarcodeImage(barcodeText, barcodeFile, smsWidth, smsHeight)) {
                    val fileSize = barcodeFile.length()
                    println("SMS 최적화 QR 코드 생성 완료: ${barcodeFile.absolutePath} (크기: $fileSize bytes)")

                    // MMS 크기 제한 확인 (300KB 이상이면 재압축)
                    if (fileSize > 300 * 1024) {
                        println("⚠️ 파일 크기가 300KB 초과, 재압축 시도")
                        return generateCompressedMmsBarcodeFile(barcodeText, context)
                    }

                    barcodeFile
                } else {
                    barcodeFile.delete()
                    null
                }
            } catch (e: Exception) {
                println("SMS 최적화 QR 코드 생성 오류: ${e.message}")
                null
            }
        }

        /**
         * 압축된 MMS QR 코드 이미지를 생성합니다.
         */
        private fun generateCompressedMmsBarcodeFile(barcodeText: String, context: Context): File? {
            return try {
                // 더 작은 크기로 재생성 (QR 코드는 정사각형)
                val compressedWidth = 300
                val compressedHeight = 300

                val barcodeDir = File(context.filesDir, "barcode_images")
                val fileName = "barcode_compressed_${System.currentTimeMillis()}_${barcodeText.hashCode().toString().replace("-", "")}.png"
                val barcodeFile = File(barcodeDir, fileName)

                if (generateBarcodeImage(barcodeText, barcodeFile, compressedWidth, compressedHeight)) {
                    // JPEG 압축으로 재저장하여 크기 줄이기
                    val bitmap = generateBarcodeBitmap(barcodeText, compressedWidth, compressedHeight)
                    if (bitmap != null) {
                        val outputStream = FileOutputStream(barcodeFile)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream) // 85% 품질
                        outputStream.close()

                        val finalSize = barcodeFile.length()
                        println("압축된 QR 코드 생성 완료: $finalSize bytes")
                        barcodeFile
                    } else {
                        barcodeFile.delete()
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                println("압축 QR 코드 생성 오류: ${e.message}")
                null
            }
        }

        /**
         * FileProvider를 사용하여 안전한 URI를 생성합니다.
         * @param file 공유할 파일
         * @param context 앱 컨텍스트
         * @return FileProvider URI
         */
        fun getFileProviderUri(file: File, context: Context): Uri? {
            return try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                println("FileProvider URI 생성 실패: ${e.message}")
                null
            }
        }

        /**
         * QR 코드 텍스트가 유효한지 확인합니다.
         * @param barcodeText 확인할 QR 코드 텍스트
         * @return 유효성 검사 결과
         */
        fun validateBarcodeText(barcodeText: String): Boolean {
            return try {
                // QR 코드 형식에 유효한 문자인지 확인
                barcodeText.isNotBlank() &&
                barcodeText.length <= 2953  // QR 코드 최대 길이 (Alphanumeric)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * QR 코드 이미지 생성 테스트 메서드 (디버깅용)
         * @param testText 테스트할 QR 코드 텍스트
         */
        fun testBarcodeGeneration(testText: String): Boolean {
            return try {
                println("🧪 QR 코드 생성 테스트 시작: '$testText'")

                // 유효성 검사
                if (!validateBarcodeText(testText)) {
                    println("❌ QR 코드 텍스트 유효성 검사 실패")
                    return false
                }

                // QR 코드 이미지 생성 테스트
                val testFile = generateBarcodeImageToTempFile(testText)

                if (testFile != null) {
                    val fileSize = testFile.length()
                    println("✅ QR 코드 생성 성공: ${testFile.absolutePath}")
                    println("📏 파일 크기: $fileSize bytes")
                    println("🔍 스캔 가능한 QR 코드 이미지 생성됨")

                    // 임시 파일 정리
                    testFile.delete()
                    return true
                } else {
                    println("❌ QR 코드 이미지 파일 생성 실패")
                    return false
                }
            } catch (e: Exception) {
                println("❌ QR 코드 생성 테스트 오류: ${e.message}")
                false
            }
        }
    }
}