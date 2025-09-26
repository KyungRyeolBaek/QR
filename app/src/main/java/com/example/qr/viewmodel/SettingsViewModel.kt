package com.example.qr.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("qr_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    companion object {
        const val KEY_MESSAGE_TEMPLATE = "message_template"
        const val KEY_ATTACH_QR_IMAGE = "attach_qr_image"

        const val DEFAULT_MESSAGE_TEMPLATE = """안녕하세요 {이름}님!

출입용 QR코드가 생성되었습니다.

사용법:
1. 출입시 QR코드를 제시해주세요
2. 스캐너에 인식시키면 자동 기록됩니다
3. QR코드는 24시간 유효합니다

주의:
- 개인정보가 포함되어 있으니 공유하지 마세요
- 분실시 관리자에게 연락하여 재발급 받으세요

문의사항이 있으시면 언제든 연락주세요.

QR 출입관리 시스템"""
    }

    init {
        loadSettings()
    }

    fun updateMessageTemplate(template: String) {
        _uiState.value = _uiState.value.copy(messageTemplate = template)
    }

    fun updateAttachQRImage(attach: Boolean) {
        _uiState.value = _uiState.value.copy(attachQRImage = attach)
        // 즉시 저장
        saveAttachQRImageSetting(attach)
    }

    fun saveMessageTemplate() {
        viewModelScope.launch {
            try {
                prefs.edit()
                    .putString(KEY_MESSAGE_TEMPLATE, _uiState.value.messageTemplate)
                    .apply()

                _uiState.value = _uiState.value.copy(
                    successMessage = "메시지 템플릿이 저장되었습니다."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "설정 저장 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun resetToDefaultTemplate() {
        _uiState.value = _uiState.value.copy(
            messageTemplate = DEFAULT_MESSAGE_TEMPLATE,
            successMessage = "기본 메시지 템플릿으로 복원되었습니다."
        )
    }

    fun getPreviewMessage(): String {
        return _uiState.value.messageTemplate.replace("{이름}", "홍길동")
    }

    fun getMessageTemplate(): String {
        return prefs.getString(KEY_MESSAGE_TEMPLATE, DEFAULT_MESSAGE_TEMPLATE) ?: DEFAULT_MESSAGE_TEMPLATE
    }

    fun shouldAttachQRImage(): Boolean {
        return prefs.getBoolean(KEY_ATTACH_QR_IMAGE, true)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }

    private fun loadSettings() {
        val messageTemplate = prefs.getString(KEY_MESSAGE_TEMPLATE, DEFAULT_MESSAGE_TEMPLATE) ?: DEFAULT_MESSAGE_TEMPLATE
        val attachQRImage = prefs.getBoolean(KEY_ATTACH_QR_IMAGE, true)

        _uiState.value = _uiState.value.copy(
            messageTemplate = messageTemplate,
            attachQRImage = attachQRImage
        )
    }

    private fun saveAttachQRImageSetting(attach: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ATTACH_QR_IMAGE, attach)
            .apply()
    }
}

data class SettingsUiState(
    val messageTemplate: String = SettingsViewModel.DEFAULT_MESSAGE_TEMPLATE,
    val attachQRImage: Boolean = true,
    val successMessage: String? = null,
    val errorMessage: String? = null
)