package com.example.qr.utils

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * USB QR 코드 리더기의 키보드 입력을 처리하는 클래스
 * QR 코드 리더기는 일반적으로 키보드 입력으로 인식되며, Enter 키로 입력을 완료합니다.
 */
class UsbBarcodeInputHandler(
    private val coroutineScope: CoroutineScope,
    private val onBarcodeScanned: (String) -> Unit,
    private val onScanStart: (() -> Unit)? = null,
    private val onScanEnd: (() -> Unit)? = null
) {
    private val inputBuffer = StringBuilder()
    private var inputTimeoutJob: Job? = null
    private var isScanning = false

    // 입력 타임아웃 (밀리초) - QR 코드 입력이 중단된 후 자동으로 처리
    private val INPUT_TIMEOUT_MS = 100L

    // 최소 QR 코드 길이 - 너무 짧은 입력은 무시
    private val MIN_BARCODE_LENGTH = 3

    // 최대 QR 코드 길이 - 너무 긴 입력은 제한
    private val MAX_BARCODE_LENGTH = 100

    /**
     * 키 이벤트를 처리합니다.
     * @param keyCode 키 코드
     * @param event 키 이벤트
     * @return 이벤트가 처리되었으면 true, 그렇지 않으면 false
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        com.example.qr.utils.CrashLogger.log("🔑 handleKeyEvent: keyCode=$keyCode, char=${event.unicodeChar}, displayLabel=${event.displayLabel}")

        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                // Enter 키가 눌리면 QR 코드 입력 완료
                com.example.qr.utils.CrashLogger.log("⏎ Enter 키 감지!")
                processBarcodeInput()
                true
            }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                // 백스페이스/삭제 키 처리
                if (inputBuffer.isNotEmpty()) {
                    inputBuffer.deleteCharAt(inputBuffer.length - 1)
                }
                resetInputTimeout()
                true
            }
            else -> {
                // 일반 문자/숫자 키 처리
                val character = getCharacterFromKeyEvent(keyCode, event)
                if (character != null) {
                    handleCharacterInput(character)
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * 키 이벤트로부터 문자를 추출합니다.
     */
    private fun getCharacterFromKeyEvent(keyCode: Int, event: KeyEvent): Char? {
        // 숫자 키 처리
        when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                return ('0' + (keyCode - KeyEvent.KEYCODE_0))
            }
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val baseChar = 'a' + (keyCode - KeyEvent.KEYCODE_A)
                return if (event.isShiftPressed) {
                    baseChar.uppercaseChar()
                } else {
                    baseChar
                }
            }
        }

        // 특수 문자 처리
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> ' '
            KeyEvent.KEYCODE_MINUS -> '-'
            KeyEvent.KEYCODE_PERIOD -> '.'
            KeyEvent.KEYCODE_COMMA -> ','
            KeyEvent.KEYCODE_SEMICOLON -> ';'
            KeyEvent.KEYCODE_SLASH -> '/'
            KeyEvent.KEYCODE_BACKSLASH -> '\\'
            KeyEvent.KEYCODE_LEFT_BRACKET -> '['
            KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'
            KeyEvent.KEYCODE_EQUALS -> '='
            KeyEvent.KEYCODE_GRAVE -> '`'
            KeyEvent.KEYCODE_APOSTROPHE -> '\''
            else -> null
        }
    }

    /**
     * 문자 입력을 처리합니다.
     */
    private fun handleCharacterInput(character: Char) {
        if (inputBuffer.length < MAX_BARCODE_LENGTH) {
            // 첫 문자 입력 시 스캔 시작
            if (inputBuffer.isEmpty() && !isScanning) {
                isScanning = true
                com.example.qr.utils.CrashLogger.log("🔵 USB QR 스캔 시작")
                onScanStart?.invoke()
            }

            inputBuffer.append(character)
            resetInputTimeout()
        }
    }

    /**
     * 입력 타임아웃을 재설정합니다.
     */
    private fun resetInputTimeout() {
        inputTimeoutJob?.cancel()
        inputTimeoutJob = coroutineScope.launch {
            delay(INPUT_TIMEOUT_MS)
            processBarcodeInput()
        }
    }

    /**
     * QR 코드 입력을 처리합니다.
     */
    private fun processBarcodeInput() {
        inputTimeoutJob?.cancel()

        val barcodeData = inputBuffer.toString().trim()
        inputBuffer.clear()

        if (barcodeData.length >= MIN_BARCODE_LENGTH) {
            com.example.qr.utils.CrashLogger.log("🟢 USB QR 스캔 완료: $barcodeData")
            onBarcodeScanned(barcodeData)
        } else {
            com.example.qr.utils.CrashLogger.log("⚠️ USB QR 스캔 무시 (길이 부족): $barcodeData")
        }

        // 스캔 종료
        if (isScanning) {
            isScanning = false
            com.example.qr.utils.CrashLogger.log("🔴 USB QR 스캔 종료")
            onScanEnd?.invoke()
        }
    }

    /**
     * 입력 버퍼를 초기화합니다.
     */
    fun clearBuffer() {
        inputTimeoutJob?.cancel()
        inputBuffer.clear()

        // 스캔 종료
        if (isScanning) {
            isScanning = false
            com.example.qr.utils.CrashLogger.log("🔴 USB QR 스캔 종료 (버퍼 초기화)")
            onScanEnd?.invoke()
        }
    }

    /**
     * 현재 입력 중인 데이터를 반환합니다.
     */
    fun getCurrentInput(): String {
        return inputBuffer.toString()
    }

    /**
     * 리소스를 정리합니다.
     */
    fun cleanup() {
        inputTimeoutJob?.cancel()
        inputBuffer.clear()
    }
}