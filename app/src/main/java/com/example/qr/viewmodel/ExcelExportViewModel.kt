package com.example.qr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.service.ExcelExportService
import com.example.qr.service.NativeSMSService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
class ExcelExportViewModel(
    private val excelExportService: ExcelExportService,
    private val nativeSMSService: NativeSMSService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExcelExportUiState())
    val uiState: StateFlow<ExcelExportUiState> = _uiState.asStateFlow()

    init {
        // 기본 날짜 설정 (오늘 기준 한 달)
        val today = Calendar.getInstance()
        val endDate = today.timeInMillis

        today.add(Calendar.MONTH, -1)
        val startDate = today.timeInMillis

        _uiState.value = _uiState.value.copy(
            startDate = startDate,
            endDate = endDate
        )
    }

    fun updateStartDate(date: Long) {
        _uiState.value = _uiState.value.copy(
            startDate = date,
            dateError = null
        )
    }

    fun updateEndDate(date: Long) {
        _uiState.value = _uiState.value.copy(
            endDate = date,
            dateError = null
        )
    }

    fun updateExportType(type: ExportType) {
        _uiState.value = _uiState.value.copy(exportType = type)
    }

    fun updateSelectedUserId(userId: String?) {
        _uiState.value = _uiState.value.copy(selectedUserId = userId)
    }

    fun updateRecipientPhoneNumber(phoneNumber: String) {
        _uiState.value = _uiState.value.copy(recipientPhoneNumber = phoneNumber)
    }

    fun exportData() {
        val currentState = _uiState.value

        // 날짜 검증
        if (currentState.startDate > currentState.endDate) {
            _uiState.value = currentState.copy(
                dateError = "시작 날짜는 종료 날짜보다 이전이어야 합니다."
            )
            return
        }

        if (currentState.exportType == ExportType.USER_DETAIL && currentState.selectedUserId == null) {
            _uiState.value = currentState.copy(
                errorMessage = "개인별 상세 리포트를 위해 사용자를 선택해주세요."
            )
            return
        }

        _uiState.value = currentState.copy(
            isExporting = true,
            errorMessage = null,
            successMessage = null,
            exportedFile = null
        )

        viewModelScope.launch {
            try {
                val result = when (currentState.exportType) {
                    ExportType.ALL_ENTRIES -> {
                        excelExportService.exportAllEntryLogs(
                            currentState.startDate,
                            currentState.endDate
                        )
                    }
                    ExportType.USER_LIST -> {
                        excelExportService.exportUserList()
                    }
                    ExportType.DAILY_STATISTICS -> {
                        excelExportService.exportDailyStatistics(
                            currentState.startDate,
                            currentState.endDate
                        )
                    }
                    ExportType.USER_DETAIL -> {
                        currentState.selectedUserId?.let { userId ->
                            excelExportService.exportUserEntryLogs(
                                userId,
                                currentState.startDate,
                                currentState.endDate
                            )
                        } ?: Result.failure(Exception("사용자 ID가 없습니다."))
                    }
                }

                result.fold(
                    onSuccess = { file ->
                        _uiState.value = currentState.copy(
                            isExporting = false,
                            exportedFile = file,
                            successMessage = "엑셀 파일이 생성되었습니다: ${file.name}"
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = currentState.copy(
                            isExporting = false,
                            errorMessage = "엑셀 파일 생성 중 오류가 발생했습니다: ${exception.message}"
                        )
                    }
                )

            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isExporting = false,
                    errorMessage = "예상치 못한 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun shareFile() {
        val file = _uiState.value.exportedFile
        if (file != null) {
            try {
                val shareIntent = excelExportService.shareExcelFile(file)
                _uiState.value = _uiState.value.copy(
                    shareIntent = shareIntent
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "파일 공유 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun sendViaMMS() {
        val file = _uiState.value.exportedFile
        val phoneNumber = _uiState.value.recipientPhoneNumber

        if (file == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "전송할 파일이 없습니다. 먼저 엑셀 파일을 생성해주세요."
            )
            return
        }

        if (phoneNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "전화번호를 입력해주세요."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isSending = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val result = nativeSMSService.sendExcelFileMMS(
                    phoneNumber = phoneNumber,
                    file = file,
                    message = "QR 출입 관리 시스템에서 생성된 엑셀 리포트입니다.\n파일명: ${file.name}"
                )

                result.fold(
                    onSuccess = { message ->
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            successMessage = message
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = "MMS 전송 실패: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "전송 중 오류 발생: ${e.message}"
                )
            }
        }
    }

    fun sendViaMessenger() {
        val file = _uiState.value.exportedFile

        if (file == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "전송할 파일이 없습니다. 먼저 엑셀 파일을 생성해주세요."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isSending = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val result = nativeSMSService.sendExcelFileToMessenger(
                    file = file,
                    message = "QR 출입 관리 시스템에서 생성된 엑셀 리포트입니다.\n파일명: ${file.name}"
                )

                result.fold(
                    onSuccess = { intent ->
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            shareIntent = intent,
                            successMessage = "메신저 앱을 선택하여 파일을 전송해주세요."
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = "메신저 전송 준비 실패: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "전송 중 오류 발생: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null,
            shareIntent = null,
            isSending = false
        )
    }

    fun getExportDescription(type: ExportType): String {
        return when (type) {
            ExportType.ALL_ENTRIES -> "선택된 기간의 모든 출입 기록을 엑셀 파일로 추출합니다."
            ExportType.USER_LIST -> "등록된 모든 사용자의 정보를 엑셀 파일로 추출합니다."
            ExportType.DAILY_STATISTICS -> "선택된 기간의 일별 출입 통계를 엑셀 파일로 추출합니다."
            ExportType.USER_DETAIL -> "특정 사용자의 상세 출입 기록을 엑셀 파일로 추출합니다."
        }
    }
}

data class ExcelExportUiState(
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val exportType: ExportType = ExportType.ALL_ENTRIES,
    val selectedUserId: String? = null,
    val recipientPhoneNumber: String = "",
    val isExporting: Boolean = false,
    val isSending: Boolean = false,
    val exportedFile: File? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val dateError: String? = null,
    val shareIntent: android.content.Intent? = null
)

enum class ExportType(val displayName: String) {
    ALL_ENTRIES("전체 출입 기록"),
    USER_LIST("사용자 목록"),
    DAILY_STATISTICS("일별 통계"),
    USER_DETAIL("개인별 상세 기록")
}