package com.example.qr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.data.entities.EntryLog
import com.example.qr.data.entities.User
import com.example.qr.repository.EntryLogRepository
import com.example.qr.repository.TodayStats
import com.example.qr.repository.UserRepository
import com.example.qr.utils.QRCodeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class QRScannerViewModel(
    private val userRepository: UserRepository,
    private val entryLogRepository: EntryLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QRScannerUiState())
    val uiState: StateFlow<QRScannerUiState> = _uiState.asStateFlow()

    private val _recentEntries = MutableStateFlow<List<EntryLog>>(emptyList())
    val recentEntries: StateFlow<List<EntryLog>> = _recentEntries.asStateFlow()

    private val _todayStats = MutableStateFlow(TodayStats(0, 0, 0))
    val todayStats: StateFlow<TodayStats> = _todayStats.asStateFlow()

    init {
        loadRecentEntries()
        loadTodayStats()
    }

    fun processQRCode(qrCodeText: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isProcessing = true)

                // 1. QR 데이터 파싱
                val qrData = QRCodeGenerator.SecureQRData.fromQRString(qrCodeText)
                if (qrData == null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error("유효하지 않은 QR 코드입니다.")
                    )
                    return@launch
                }

                // 2. QR 데이터 검증
                if (!QRCodeGenerator.validateQRData(qrData)) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error("만료되었거나 위조된 QR 코드입니다.")
                    )
                    return@launch
                }

                // 3. 사용자 정보 조회
                val user = userRepository.getUserById(qrData.userId)
                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error("등록되지 않은 사용자입니다.")
                    )
                    return@launch
                }

                if (!user.isActive) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        scanResult = ScanResult.Error("비활성화된 사용자입니다.")
                    )
                    return@launch
                }

                // 4. 마지막 출입 기록 확인
                val isCurrentlyInside = entryLogRepository.isUserCurrentlyInside(user.id)

                // 5. 출입 기록 처리
                val entryType = if (isCurrentlyInside) "EXIT" else "ENTER"
                val entryId = if (isCurrentlyInside) {
                    entryLogRepository.recordExit(user.id, user.name)
                } else {
                    entryLogRepository.recordEntry(user.id, user.name)
                }

                // 6. 결과 업데이트
                val successMessage = if (entryType == "ENTER") {
                    "${user.name}님이 입장하셨습니다."
                } else {
                    "${user.name}님이 퇴장하셨습니다."
                }

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    scanResult = ScanResult.Success(
                        user = user,
                        entryType = entryType,
                        message = successMessage
                    )
                )

                // 7. 최근 출입 기록 및 통계 새로고침
                loadRecentEntries()
                loadTodayStats()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    scanResult = ScanResult.Error("출입 처리 중 오류가 발생했습니다: ${e.message}")
                )
            }
        }
    }

    fun clearScanResult() {
        _uiState.value = _uiState.value.copy(scanResult = null)
    }

    fun toggleScanner() {
        _uiState.value = _uiState.value.copy(
            isScannerActive = !_uiState.value.isScannerActive
        )
    }

    private fun loadRecentEntries() {
        viewModelScope.launch {
            entryLogRepository.getAllEntryLogs().collect { entries ->
                _recentEntries.value = entries.take(20) // 최근 20개 기록만
            }
        }
    }

    private fun loadTodayStats() {
        viewModelScope.launch {
            try {
                val stats = entryLogRepository.getTodayStats()
                _todayStats.value = stats
            } catch (e: Exception) {
                android.util.Log.e("QRScannerViewModel", "오늘 통계 로드 실패", e)
                // 에러 시 기본값 유지
            }
        }
    }
}

data class QRScannerUiState(
    val isProcessing: Boolean = false,
    val isScannerActive: Boolean = true,
    val scanResult: ScanResult? = null
)

sealed class ScanResult {
    data class Success(
        val user: User,
        val entryType: String, // "ENTER" or "EXIT"
        val message: String
    ) : ScanResult()

    data class Error(val message: String) : ScanResult()
}