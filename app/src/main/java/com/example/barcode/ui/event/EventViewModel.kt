package com.example.barcode.ui.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barcode.data.dao.EventDao
import com.example.barcode.data.dao.ParticipantDao
import com.example.barcode.data.dao.ScanRecordDao
import com.example.barcode.data.entity.Event
import com.example.barcode.data.entity.Participant
import com.example.barcode.data.entity.ParticipantWithScanInfo
import com.example.barcode.data.entity.ScanRecord
import com.example.barcode.data.entity.ScanType
import kotlinx.coroutines.launch

class EventViewModel(
    private val eventDao: EventDao,
    private val participantDao: ParticipantDao,
    private val scanRecordDao: ScanRecordDao
) : ViewModel() {

    private val _activeEvent = MutableLiveData<Event?>()
    val activeEvent: LiveData<Event?> = _activeEvent

    private val _currentParticipant = MutableLiveData<ParticipantWithScanInfo?>()
    val currentParticipant: LiveData<ParticipantWithScanInfo?> = _currentParticipant

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _scanSuccess = MutableLiveData<Boolean>()
    val scanSuccess: LiveData<Boolean> = _scanSuccess

    fun loadActiveEvent() {
        viewModelScope.launch {
            try {
                val event = eventDao.getActiveEvent()
                _activeEvent.value = event
                if (event == null) {
                    _errorMessage.value = "활성화된 이벤트가 없습니다."
                }
            } catch (e: Exception) {
                _errorMessage.value = "이벤트 로드 실패: ${e.message}"
            }
        }
    }

    fun processBarcodeScanned(barcodeData: String) {
        viewModelScope.launch {
            try {
                val event = _activeEvent.value
                if (event == null) {
                    _errorMessage.value = "활성화된 이벤트가 없습니다."
                    return@launch
                }

                // 바코드로 참가자 찾기
                val participant = findParticipantByBarcode(barcodeData)
                if (participant == null) {
                    _errorMessage.value = "등록되지 않은 바코드입니다."
                    return@launch
                }

                // 스캔 기록 추가
                val scanType = determineScanType(participant, event.id)
                val scanRecord = ScanRecord(
                    participantId = participant.id,
                    eventId = event.id,
                    scanTime = System.currentTimeMillis(),
                    scanType = scanType
                )

                scanRecordDao.insertScanRecord(scanRecord)

                // 참가자 정보와 스캔 정보 업데이트
                val participantWithScanInfo = createParticipantWithScanInfo(participant, event.id)

                // 메인 스레드에서 LiveData 업데이트 보장
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _currentParticipant.value = participantWithScanInfo
                    _scanSuccess.value = true
                }

            } catch (e: Exception) {
                println("❌ [SCAN_ERROR] 스캔 처리 실패: ${e.message}")
                e.printStackTrace()

                // 메인 스레드에서 에러 메시지 업데이트
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _errorMessage.value = "스캔 처리 실패: ${e.message}"
                }

                // 에러 발생시에도 참가자 정보는 표시 (스캔 기록 없이)
                try {
                    val participant = findParticipantByBarcode(barcodeData)
                    if (participant != null) {
                        val basicInfo = ParticipantWithScanInfo(
                            participant = participant,
                            firstScanTime = null,
                            lastScanTime = null,
                            lastScanType = null,
                            totalScans = 0,
                            isCurrentlyInside = false
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _currentParticipant.value = basicInfo
                        }
                    }
                } catch (secondaryError: Exception) {
                    println("❌ [SCAN_ERROR] 기본 참가자 정보 로드도 실패: ${secondaryError.message}")
                }
            }
        }
    }

    private suspend fun findParticipantByBarcode(barcodeData: String): Participant? {
        // 직접 바코드 데이터로 찾기
        var participant = participantDao.getParticipantByBarcode(barcodeData)

        if (participant == null) {
            // JSON 형태의 바코드인 경우 파싱
            try {
                if (barcodeData.startsWith("{") && barcodeData.contains("\"code\"")) {
                    // JSON에서 code 추출
                    val codePattern = "\"code\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val matchResult = codePattern.find(barcodeData)
                    val code = matchResult?.groupValues?.get(1)

                    if (code != null) {
                        participant = participantDao.getParticipantByBarcode(code)
                    }
                }
            } catch (e: Exception) {
                // JSON 파싱 실패시 원본 데이터 사용
            }
        }

        return participant
    }

    private suspend fun determineScanType(participant: Participant, eventId: Long): ScanType {
        val lastScan = scanRecordDao.getLastScanRecord(participant.id, eventId)

        return when (lastScan?.scanType) {
            ScanType.ENTRY -> ScanType.EXIT  // 마지막이 입장이면 퇴장
            ScanType.EXIT -> ScanType.ENTRY   // 마지막이 퇴장이면 입장
            null -> ScanType.ENTRY            // 처음 스캔이면 입장
        }
    }

    private suspend fun createParticipantWithScanInfo(
        participant: Participant,
        eventId: Long
    ): ParticipantWithScanInfo {
        return try {
            println("📊 [SCAN_INFO] 참가자 스캔 정보 로드 시작: ${participant.fullName} (ID: ${participant.id})")

            val firstEntry = scanRecordDao.getFirstScanByType(participant.id, eventId, ScanType.ENTRY)
            val lastScan = scanRecordDao.getLastScanRecord(participant.id, eventId)
            val totalScans = scanRecordDao.getScanRecordCountByParticipant(participant.id, eventId)

            // 현재 내부에 있는지 확인 (마지막 스캔이 입장인지)
            val isCurrentlyInside = lastScan?.scanType == ScanType.ENTRY

            println("📊 [SCAN_INFO] 로드 완료:")
            println("   첫 입장: ${firstEntry?.scanTime?.let { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "없음"}")
            println("   마지막 스캔: ${lastScan?.scanTime?.let { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "없음"}")
            println("   총 스캔 횟수: $totalScans")
            println("   현재 상태: ${if (isCurrentlyInside) "입장중" else "퇴장완료"}")

            ParticipantWithScanInfo(
                participant = participant,
                firstScanTime = firstEntry?.scanTime,
                lastScanTime = lastScan?.scanTime,
                lastScanType = lastScan?.scanType,
                totalScans = totalScans,
                isCurrentlyInside = isCurrentlyInside
            )
        } catch (e: Exception) {
            println("❌ [SCAN_INFO] 스캔 정보 로드 실패: ${e.message}")
            e.printStackTrace()

            // 에러 발생 시 기본값으로 반환
            ParticipantWithScanInfo(
                participant = participant,
                firstScanTime = null,
                lastScanTime = null,
                lastScanType = null,
                totalScans = 0,
                isCurrentlyInside = false
            )
        }
    }

    fun clearCurrentParticipant() {
        _currentParticipant.value = null
    }

    // 테스트용 데이터 생성 함수
    fun createTestData() {
        viewModelScope.launch {
            try {
                val event = _activeEvent.value
                if (event == null) {
                    _errorMessage.value = "활성화된 이벤트가 없습니다."
                    return@launch
                }

                // 테스트용 참가자 데이터 생성
                val testParticipants = listOf(
                    Participant(
                        fullName = "홍길동",
                        phoneNumber = "010-1234-5678",
                        licenseNo = "L123456",
                        barcodeData = "TEST001",
                        eventId = event.id,
                        cmeCredits = 5.0
                    ),
                    Participant(
                        fullName = "김철수",
                        phoneNumber = "010-2345-6789",
                        licenseNo = "L234567",
                        barcodeData = "TEST002",
                        eventId = event.id,
                        cmeCredits = 7.5
                    ),
                    Participant(
                        fullName = "이영희",
                        phoneNumber = "010-3456-7890",
                        licenseNo = "L345678",
                        barcodeData = "TEST003",
                        eventId = event.id,
                        cmeCredits = 10.0
                    )
                )

                // 기존 테스트 데이터 삭제 후 새로 생성
                for (testParticipant in testParticipants) {
                    val existing = participantDao.getParticipantByBarcode(testParticipant.barcodeData)
                    if (existing == null) {
                        participantDao.insertParticipant(testParticipant)
                        println("📝 테스트 참가자 생성: ${testParticipant.fullName} (${testParticipant.barcodeData})")
                    }
                }

                _errorMessage.value = "테스트 데이터 생성 완료! 바코드: TEST001, TEST002, TEST003"

            } catch (e: Exception) {
                _errorMessage.value = "테스트 데이터 생성 실패: ${e.message}"
            }
        }
    }
}