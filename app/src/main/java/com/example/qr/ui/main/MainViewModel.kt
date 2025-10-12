package com.example.qr.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.first
import com.example.qr.data.dao.EventDao
import com.example.qr.data.dao.ParticipantDao
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.data.entity.Event
import com.example.qr.data.entity.Participant
import com.example.qr.data.entity.ParticipantWithScanInfo
// TODO: Convert to CSV
// import com.example.qr.utils.ExcelReader
// import com.example.qr.utils.ExcelWriter
import com.example.qr.service.SmsService
import kotlinx.coroutines.launch
import android.app.AlertDialog

class MainViewModel(
    private val eventDao: EventDao,
    private val participantDao: ParticipantDao,
    private val scanRecordDao: ScanRecordDao
) : ViewModel() {

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _participants = MutableLiveData<List<ParticipantWithScanInfo>>()
    val participants: LiveData<List<ParticipantWithScanInfo>> = _participants


    suspend fun importExcelFile(context: Context, uri: Uri) {
        _isLoading.value = true

        try {
            // 활성 이벤트 확인 또는 생성
            var activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                // 기본 이벤트 생성
                val eventId = eventDao.insertEvent(
                    Event(
                        eventName = "대한해부학회 2025",
                        eventDate = "2025-10-15",
                        description = " ",
                        isActive = true
                    )
                )
                activeEvent = eventDao.getEventById(eventId)
            }

            if (activeEvent != null) {
                // TODO: Convert to CSV
                /*
                // 엑셀 파일 읽기
                val result = ExcelReader.readParticipantsFromExcel(context, uri, activeEvent.id)

                result.fold(
                    onSuccess = { participants ->
                        // 참가자 데이터 저장
                        val insertedIds = participantDao.insertParticipants(participants)
                        _message.value = "성공적으로 ${insertedIds.size}명의 참가자를 불러왔습니다."
                    },
                    onFailure = { exception ->
                        _message.value = exception.message ?: "엑셀 파일 처리 중 오류가 발생했습니다."
                    }
                )
                */
                _message.value = "Excel import temporarily disabled - use CSV export instead"
            } else {
                _message.value = "이벤트 생성에 실패했습니다."
            }

        } catch (e: Exception) {
            _message.value = "파일 처리 중 오류가 발생했습니다: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun exportEventData(context: Context) {
        _isLoading.value = true

        try {
            val activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                _message.value = "활성화된 이벤트가 없습니다."
                return
            }

            // 참가자와 스캔 정보 가져오기
            val participants = participantDao.getParticipantsByEvent(activeEvent.id)

            // TODO: ParticipantWithScanInfo 리스트 생성 로직 구현
            // 현재는 빈 리스트로 처리
            val participantsWithScanInfo = emptyList<com.example.qr.data.entity.ParticipantWithScanInfo>()

            // TODO: Convert to CSV
            /*
            val result = ExcelWriter.exportEventData(context, activeEvent, participantsWithScanInfo)

            result.fold(
                onSuccess = { file ->
                    ExcelWriter.shareExcelFile(context, file)
                    _message.value = "데이터가 성공적으로 내보내졌습니다."
                },
                onFailure = { exception ->
                    _message.value = exception.message ?: "데이터 내보내기 중 오류가 발생했습니다."
                }
            )
            */
            _message.value = "Excel export temporarily disabled - use participant management CSV export instead"

        } catch (e: Exception) {
            _message.value = "데이터 처리 중 오류가 발생했습니다: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun loadAllParticipants() {
        viewModelScope.launch {
            try {
                val activeEvent = eventDao.getActiveEvent()
                if (activeEvent != null) {
                    // TODO: Implement proper ParticipantWithScanInfo loading
                    // For now, load basic participants and create empty scan info
                    val basicParticipants = participantDao.getParticipantsByEvent(activeEvent.id).first()
                    val participantsWithScanInfo = basicParticipants.map { participant ->
                        ParticipantWithScanInfo(
                            participant = participant,
                            firstScanTime = null,
                            lastScanTime = null,
                            lastScanType = null,
                            totalScans = 0,
                            isCurrentlyInside = false
                        )
                    }
                    _participants.value = participantsWithScanInfo
                } else {
                    _participants.value = emptyList()
                }
            } catch (e: Exception) {
                _message.value = "참가자 목록 로드 중 오류: ${e.message}"
                _participants.value = emptyList()
            }
        }
    }

    fun searchParticipants(query: String) {
        viewModelScope.launch {
            try {
                val activeEvent = eventDao.getActiveEvent()
                if (activeEvent != null) {
                    val searchQuery = "%$query%"
                    val basicParticipants = participantDao.searchParticipants(activeEvent.id, searchQuery).first()
                    val participantsWithScanInfo = basicParticipants.map { participant ->
                        // TODO: 실제 스캔 데이터와 연결
                        val scanRecords = scanRecordDao.getScanRecordsByParticipant(participant.id).first()
                        val entryScan = scanRecords.find { it.scanType == com.example.qr.data.entity.ScanType.ENTRY }
                        val exitScan = scanRecords.find { it.scanType == com.example.qr.data.entity.ScanType.EXIT }

                        ParticipantWithScanInfo(
                            participant = participant,
                            firstScanTime = entryScan?.scanTime,
                            lastScanTime = scanRecords.maxByOrNull { it.scanTime }?.scanTime,
                            lastScanType = scanRecords.maxByOrNull { it.scanTime }?.scanType,
                            totalScans = scanRecords.size,
                            isCurrentlyInside = entryScan != null && (exitScan == null || entryScan.scanTime > exitScan.scanTime)
                        )
                    }
                    _participants.value = participantsWithScanInfo
                } else {
                    _participants.value = emptyList()
                }
            } catch (e: Exception) {
                _message.value = "참가자 검색 중 오류: ${e.message}"
                _participants.value = emptyList()
            }
        }
    }

    suspend fun downloadExcelTemplate(context: Context) {
        _isLoading.value = true
        try {
            // TODO: Convert to CSV template
            /*
            val result = ExcelWriter.exportParticipantsTemplate(context)
            result.fold(
                onSuccess = { file ->
                    ExcelWriter.shareExcelFile(context, file)
                    _message.value = "csv 템플릿이 다운로드되었습니다."
                },
                onFailure = { exception ->
                    _message.value = exception.message ?: "템플릿 생성 중 오류가 발생했습니다."
                }
            )
            */
            _message.value = "Excel template temporarily disabled"
        } catch (e: Exception) {
            _message.value = "템플릿 생성 중 오류: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun previewExcelFile(context: Context, uri: Uri) {
        _isLoading.value = true
        try {
            _message.value = "Excel 파일 미리보기 기능을 준비 중입니다."
        } catch (e: Exception) {
            _message.value = "파일 처리 중 오류: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun confirmImportExcel(context: Context, uri: Uri) {
        importExcelFile(context, uri)
    }

    suspend fun createSampleData() {
        _isLoading.value = true
        try {
            // 활성 이벤트 확인 또는 생성
            var activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                val eventId = eventDao.insertEvent(
                    Event(
                        eventName = "대한해부학회 2025",
                        eventDate = "2025-10-15",
                        description = " ",
                        isActive = true
                    )
                )
                activeEvent = eventDao.getEventById(eventId)
            }

            if (activeEvent != null) {
                // 샘플 참가자 데이터 생성
                val sampleParticipants = createSampleParticipants(activeEvent.id)

                // 데이터베이스에 저장
                val insertedIds = participantDao.insertParticipants(sampleParticipants)

                // 샘플 스캔 기록도 생성 (일부 참가자들이 이미 입장한 것처럼)
                createSampleScanRecords(activeEvent.id, insertedIds)

                _message.value = "✅ 성공적으로 ${insertedIds.size}명의 샘플 참가자와 스캔 기록을 생성했습니다!"
            } else {
                _message.value = "❌ 이벤트 생성에 실패했습니다."
            }
        } catch (e: Exception) {
            _message.value = "❌ 샘플 데이터 생성 중 오류: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    private fun createSampleParticipants(eventId: Long): List<Participant> {
        val sampleData = listOf(
            "김철수" to "010-1234-5678",
            "이영희" to "010-2345-6789",
            "박민수" to "010-3456-7890",
            "최지연" to "010-4567-8901",
            "정우성" to "010-5678-9012",
            "강호동" to "010-6789-0123",
            "송혜교" to "010-7890-1234",
            "현빈" to "010-8901-2345",
            "전지현" to "010-9012-3456",
            "이병헌" to "010-0123-4567",
            "김태희" to "010-1357-2468",
            "원빈" to "010-2468-1357",
            "손예진" to "010-3579-2468",
            "공유" to "010-4680-1357",
            "박서준" to "010-5791-2468"
        )

        return sampleData.map { (name, phone) ->
            val licenseNo = "LIC${(100000..999999).random()}"
            val barcodeData = generateBarcodeData(name, phone, licenseNo)

            Participant(
                fullName = name,
                phoneNumber = phone,
                licenseNo = licenseNo,
                barcodeData = barcodeData,
                eventId = eventId
            )
        }
    }

    private fun generateBarcodeData(fullName: String, phoneNumber: String, licenseNo: String): String {
        val timestamp = System.currentTimeMillis()
        val hash = "${fullName}_${phoneNumber}_${licenseNo}_$timestamp".hashCode()
        return "BC${Math.abs(hash)}"
    }

    private suspend fun createSampleScanRecords(eventId: Long, participantIds: List<Long>) {
        val now = System.currentTimeMillis()
        val sampleScans = mutableListOf<com.example.qr.data.entity.ScanRecord>()

        // 70%의 참가자들이 입장했다고 가정
        val enteredParticipants = participantIds.shuffled().take((participantIds.size * 0.7).toInt())

        enteredParticipants.forEachIndexed { index, participantId ->
            // 입장 시간을 과거 1-3시간 사이로 랜덤 설정
            val entryTime = now - (1..180).random() * 60 * 1000 // 1-180분 전

            sampleScans.add(
                com.example.qr.data.entity.ScanRecord(
                    participantId = participantId,
                    eventId = eventId,
                    scanTime = entryTime,
                    scanType = com.example.qr.data.entity.ScanType.ENTRY,
                    deviceId = "DEVICE_SAMPLE"
                )
            )

            // 30%의 입장한 참가자들은 이미 퇴장했다고 가정
            if (index % 3 == 0) { // 약 30%
                val exitTime = entryTime + (30..120).random() * 60 * 1000 // 30-120분 후 퇴장

                sampleScans.add(
                    com.example.qr.data.entity.ScanRecord(
                        participantId = participantId,
                        eventId = eventId,
                        scanTime = exitTime,
                        scanType = com.example.qr.data.entity.ScanType.EXIT,
                        deviceId = "DEVICE_SAMPLE"
                    )
                )
            }
        }

        // 스캔 기록 저장
        scanRecordDao.insertScanRecords(sampleScans)
    }

    suspend fun sendQrCodesToParticipants(context: Context, isResend: Boolean) {
        _isLoading.value = true
        try {
            val activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                _message.value = "활성화된 이벤트가 없습니다."
                return
            }

            val participants = participantDao.getParticipantsByEvent(activeEvent.id).first()
            if (participants.isEmpty()) {
                _message.value = "발송할 참가자가 없습니다."
                return
            }

            val smsService = SmsService(context)

            // SMS 권한 확인
            if (!smsService.hasSmsPermission()) {
                _message.value = "SMS 권한이 필요합니다. 설정에서 권한을 허용해주세요."
                return
            }

            // 저장된 템플릿 불러오기
            val prefs = context.getSharedPreferences("message_templates", Context.MODE_PRIVATE)
            val defaultTemplate = SmsService.DEFAULT_MESSAGE_TEMPLATE
            val defaultResendTemplate = SmsService.RESEND_MESSAGE_TEMPLATE

            // 메시지 템플릿 선택
            val messageTemplate = if (isResend) {
                prefs.getString("resend_template", defaultResendTemplate) ?: defaultResendTemplate
            } else {
                prefs.getString("default_template", defaultTemplate) ?: defaultTemplate
            }

            _message.value = "QR 코드 발송 시작... (${participants.size}명)"

            // 참가자 정보 변환
            val participantInfos = participants.map { participant ->
                SmsService.ParticipantInfo(
                    name = participant.fullName,
                    phoneNumber = participant.phoneNumber,
                    barcodeData = participant.barcodeData
                )
            }

            // 일괄 발송
            smsService.sendBatchBarcodeMessages(
                participants = participantInfos,
                messageTemplate = messageTemplate,
                onProgress = { current, total ->
                    _message.value = "발송 중... ($current/$total)"
                },
                onComplete = { successCount, failureCount ->
                    _message.value = "발송 완료: 성공 ${successCount}건, 실패 ${failureCount}건"
                }
            )

        } catch (e: Exception) {
            _message.value = "QR 코드 발송 실패: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}