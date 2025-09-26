package com.example.qr.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.data.entities.User
import com.example.qr.repository.SMSRepository
import com.example.qr.repository.UserRepository
import com.example.qr.service.SMSService
import com.example.qr.service.NativeSMSService
import com.example.qr.utils.QRCodeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class UserRegistrationViewModel(
    private val userRepository: UserRepository,
    private val smsRepository: SMSRepository,
    private val smsService: SMSService,
    private val nativeSMSService: NativeSMSService,
    private val context: Context
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("qr_settings", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(UserRegistrationUiState())
    val uiState: StateFlow<UserRegistrationUiState> = _uiState.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    init {
        loadUsers()
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null
        )
    }

    fun updatePhoneNumber(phoneNumber: String) {
        _uiState.value = _uiState.value.copy(
            phoneNumber = phoneNumber,
            phoneNumberError = null
        )
    }

    fun registerUser() {
        val currentState = _uiState.value

        // 입력값 검증
        if (!validateInput(currentState)) {
            return
        }

        _uiState.value = currentState.copy(isLoading = true)

        viewModelScope.launch {
            try {
                // 1. 중복 체크
                val existingUser = userRepository.getUserByPhoneNumber(currentState.phoneNumber)
                if (existingUser != null) {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        phoneNumberError = "이미 등록된 전화번호입니다."
                    )
                    return@launch
                }

                // 2. 고유 ID 생성
                val userId = QRCodeGenerator.generateUserId()

                // 3. QR 데이터 생성
                val qrData = QRCodeGenerator.generateSecureQRData(
                    userId = userId,
                    userName = currentState.name,
                    phoneNumber = currentState.phoneNumber
                )

                // 4. QR 비트맵 생성
                val qrBitmap = QRCodeGenerator.generateQRBitmap(qrData)

                // 5. 사용자 정보 저장
                val user = User(
                    id = userId,
                    name = currentState.name,
                    phoneNumber = currentState.phoneNumber,
                    qrCode = qrData.toQRString(),
                    smsStatus = "PENDING"
                )

                userRepository.insertUser(user)

                // 6. SMS 발송 (네이티브 SMS 사용) - 설정에서 이미지 첨부 여부 확인
                val shouldAttachImage = prefs.getBoolean("attach_qr_image", true)
                val smsResult = nativeSMSService.sendQRCodeSMS(
                    name = currentState.name,
                    phoneNumber = currentState.phoneNumber,
                    qrBitmap = qrBitmap,
                    attachImage = shouldAttachImage
                )

                if (smsResult.isSuccess) {
                    // SMS 발송 성공
                    userRepository.updateSmsStatus(userId, "SUCCESS")
                    smsRepository.recordSMSSent(userId, currentState.phoneNumber)

                    _uiState.value = currentState.copy(
                        isLoading = false,
                        isSuccess = true,
                        successMessage = "${currentState.name}님의 QR코드가 생성되고 SMS가 발송되었습니다."
                    )
                } else {
                    // SMS 발송 실패
                    userRepository.updateSmsStatus(userId, "FAILED")
                    smsRepository.recordSMSFailed(
                        userId,
                        currentState.phoneNumber,
                        smsResult.exceptionOrNull()?.message ?: "알 수 없는 오류"
                    )

                    _uiState.value = currentState.copy(
                        isLoading = false,
                        errorMessage = "사용자는 등록되었지만 SMS 발송에 실패했습니다: ${smsResult.exceptionOrNull()?.message}"
                    )
                }

                loadUsers() // 사용자 목록 새로고침

            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = "사용자 등록 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun resendSMS(user: User) {
        viewModelScope.launch {
            try {
                val result = nativeSMSService.resendQRCode(
                    name = user.name,
                    phoneNumber = user.phoneNumber,
                    userId = user.id
                )

                if (result.isSuccess) {
                    userRepository.updateSmsStatus(user.id, "SUCCESS")
                    smsRepository.recordSMSSent(user.id, user.phoneNumber)
                    _uiState.value = _uiState.value.copy(
                        successMessage = "${user.name}님에게 QR코드를 재발송했습니다."
                    )
                } else {
                    smsRepository.recordSMSFailed(
                        user.id,
                        user.phoneNumber,
                        result.exceptionOrNull()?.message ?: "재발송 실패"
                    )
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "SMS 재발송에 실패했습니다: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "SMS 재발송 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun deactivateUser(user: User) {
        viewModelScope.launch {
            try {
                userRepository.deactivateUser(user.id)
                loadUsers()
                _uiState.value = _uiState.value.copy(
                    successMessage = "${user.name}님이 비활성화되었습니다."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "사용자 비활성화 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            try {
                userRepository.deleteUser(user)
                loadUsers()
                _uiState.value = _uiState.value.copy(
                    successMessage = "${user.name}님이 삭제되었습니다."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "사용자 삭제 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }

    fun clearForm() {
        _uiState.value = UserRegistrationUiState()
    }

    private fun validateInput(state: UserRegistrationUiState): Boolean {
        var isValid = true
        var newState = state

        // 이름 검증
        if (state.name.isBlank()) {
            newState = newState.copy(nameError = "이름을 입력해주세요.")
            isValid = false
        } else if (state.name.length < 2) {
            newState = newState.copy(nameError = "이름은 2자 이상 입력해주세요.")
            isValid = false
        }

        // 전화번호 검증
        if (state.phoneNumber.isBlank()) {
            newState = newState.copy(phoneNumberError = "전화번호를 입력해주세요.")
            isValid = false
        } else if (!nativeSMSService.isValidPhoneNumber(state.phoneNumber)) {
            newState = newState.copy(phoneNumberError = "유효한 전화번호를 입력해주세요.")
            isValid = false
        }

        _uiState.value = newState
        return isValid
    }

    private fun loadUsers() {
        viewModelScope.launch {
            userRepository.getAllUsers().collect { userList ->
                _users.value = userList
            }
        }
    }
}

data class UserRegistrationUiState(
    val name: String = "",
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val nameError: String? = null,
    val phoneNumberError: String? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)