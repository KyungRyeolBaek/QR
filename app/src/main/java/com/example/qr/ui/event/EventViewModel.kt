package com.example.qr.ui.event

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.data.dao.EventDao
import com.example.qr.data.dao.ParticipantDao
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.data.entity.Event
import com.example.qr.data.entity.Participant
import com.example.qr.data.entity.ParticipantWithScanInfo
import com.example.qr.data.entity.ScanRecord
import com.example.qr.data.entity.ScanType
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

    // 마지막 스캔 시간 저장 (연속 스캔 방지)
    private var lastScanTime: Long = 0
    private val SCAN_DELAY_MS = 1500L // 1.5초 딜레이
    private var isScanning = false // 스캔 진행 중 플래그

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
                // 스캔 진행 중이면 즉시 차단
                if (isScanning) {
                    println("🚫 [SCAN_BLOCKED] 스캔 처리 중 - 비활성화 상태")
                    com.example.qr.utils.CrashLogger.log("🚫 스캔 처리 중 - 차단")
                    return@launch
                }

                // 연속 스캔 방지: 1.5초 이내 재스캔 차단
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastScanTime < SCAN_DELAY_MS) {
                    println("⏱️ [SCAN_BLOCKED] 연속 스캔 차단: ${currentTime - lastScanTime}ms 경과")
                    com.example.qr.utils.CrashLogger.log("⏱️ 연속 스캔 차단: 딜레이 ${SCAN_DELAY_MS}ms 미만")
                    return@launch
                }

                // 스캔 시작: flag와 시간 업데이트
                isScanning = true
                lastScanTime = currentTime

                println("🔍 [SCAN_START] QR 코드 스캔 시작: $barcodeData")
                com.example.qr.utils.CrashLogger.log("🔍 [SCAN_START] QR 코드 스캔 시작: $barcodeData")

                // ===== STEP 1: 이벤트 확인만 실행 =====
                val event = _activeEvent.value
                com.example.qr.utils.CrashLogger.log("이벤트 확인 중...")
                if (event == null) {
                    println("❌ [SCAN_ERROR] 활성화된 이벤트 없음")
                    com.example.qr.utils.CrashLogger.log("❌ 활성화된 이벤트 없음")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _errorMessage.value = "활성화된 이벤트가 없습니다."
                    }
                    return@launch
                }
                println("✅ [SCAN_EVENT] 이벤트 확인: ${event.eventName} (ID: ${event.id})")
                com.example.qr.utils.CrashLogger.log("✅ 이벤트: ${event.eventName}")

                // QR 코드로 참가자 찾기
                println("🔍 [SCAN_SEARCH] 참가자 검색 중...")
                com.example.qr.utils.CrashLogger.log("🔍 참가자 검색 중...")
                val participant = findParticipantByBarcode(barcodeData)
                com.example.qr.utils.CrashLogger.log("검색 완료")
                if (participant == null) {
                    println("❌ [SCAN_ERROR] 등록되지 않은 QR 코드: $barcodeData")
                    com.example.qr.utils.CrashLogger.log("❌ 등록되지 않은 QR 코드: $barcodeData")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _errorMessage.value = "등록되지 않은 QR 코드입니다: $barcodeData"
                    }
                    return@launch
                }
                println("✅ [SCAN_FOUND] 참가자 발견: ${participant.fullName} (ID: ${participant.id})")
                com.example.qr.utils.CrashLogger.log("✅ 참가자: ${participant.fullName}")

                // 스캔 기록 추가
                println("🔍 [SCAN_TYPE] 스캔 타입 결정 중...")
                com.example.qr.utils.CrashLogger.log("🔍 스캔 타입 결정 중...")
                val scanType = determineScanType(participant, event.id)
                println("✅ [SCAN_TYPE] 스캔 타입: $scanType")
                com.example.qr.utils.CrashLogger.log("✅ 스캔 타입: $scanType")

                val scanRecord = ScanRecord(
                    participantId = participant.id,
                    eventId = event.id,
                    scanTime = System.currentTimeMillis(),
                    scanType = scanType
                )

                println("💾 [SCAN_DB] 스캔 기록 저장 중...")
                com.example.qr.utils.CrashLogger.log("💾 DB 저장 중...")
                val recordId = scanRecordDao.insertScanRecord(scanRecord)
                println("✅ [SCAN_DB] 스캔 기록 저장 완료 (ID: $recordId)")
                com.example.qr.utils.CrashLogger.log("✅ DB 저장 완료 (ID: $recordId)")

                // 참가자 정보와 스캔 정보 업데이트
                println("📊 [SCAN_INFO] 참가자 상세 정보 로드 중...")
                com.example.qr.utils.CrashLogger.log("📊 상세 정보 로드 중...")
                val participantWithScanInfo = createParticipantWithScanInfo(participant, event.id)
                println("✅ [SCAN_INFO] 참가자 정보 로드 완료")
                com.example.qr.utils.CrashLogger.log("✅ 정보 로드 완료")

                // 메인 스레드에서 LiveData 업데이트 보장
                println("🔄 [SCAN_UI] UI 업데이트 중...")
                com.example.qr.utils.CrashLogger.log("🔄 UI 업데이트 중...")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    // 참가자 정보 업데이트 먼저
                    _currentParticipant.value = participantWithScanInfo
                    com.example.qr.utils.CrashLogger.log("currentParticipant 업데이트됨")

                    println("✅ [SCAN_COMPLETE] QR 코드 스캔 완료!")
                    com.example.qr.utils.CrashLogger.log("✅ 스캔 완료!")
                }

                // 별도 코루틴으로 애니메이션 트리거 및 스캔 재활성화
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    kotlinx.coroutines.delay(100)  // UI 업데이트 완료 대기
                    _scanSuccess.value = true
                    com.example.qr.utils.CrashLogger.log("scanSuccess 트리거됨")
                    kotlinx.coroutines.delay(50)
                    _scanSuccess.value = false  // 이벤트 소비 후 리셋
                    com.example.qr.utils.CrashLogger.log("scanSuccess 리셋됨")

                    // 1.5초 대기 후 스캔 재활성화
                    kotlinx.coroutines.delay(SCAN_DELAY_MS)
                    isScanning = false
                    println("✅ [SCAN_READY] 스캔 준비 완료 - 다음 스캔 가능")
                    com.example.qr.utils.CrashLogger.log("✅ 스캔 준비 완료")
                }

            } catch (e: Exception) {
                // 에러 발생 시 스캔 플래그 해제
                isScanning = false

                println("❌❌❌ [SCAN_ERROR] 스캔 처리 실패!")
                println("에러 타입: ${e.javaClass.simpleName}")
                println("에러 메시지: ${e.message}")
                println("스택 트레이스:")
                e.printStackTrace()

                // 파일에도 로그
                com.example.qr.utils.CrashLogger.log("❌❌❌ [SCAN_ERROR] 스캔 처리 실패!")
                com.example.qr.utils.CrashLogger.log("에러 타입: ${e.javaClass.simpleName}")
                com.example.qr.utils.CrashLogger.log("에러 메시지: ${e.message}")
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                com.example.qr.utils.CrashLogger.log(sw.toString())

                // 메인 스레드에서 에러 메시지 업데이트
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _errorMessage.value = "스캔 처리 실패: ${e.javaClass.simpleName}: ${e.message}"
                }

                // 에러 발생시에도 참가자 정보는 표시 (스캔 기록 없이)
                try {
                    println("🔍 [SCAN_RECOVERY] 기본 참가자 정보 로드 시도...")
                    val participant = findParticipantByBarcode(barcodeData)
                    if (participant != null) {
                        println("✅ [SCAN_RECOVERY] 참가자 발견: ${participant.fullName}")
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
                    } else {
                        println("❌ [SCAN_RECOVERY] 참가자 찾을 수 없음")
                    }
                } catch (secondaryError: Exception) {
                    println("❌❌ [SCAN_RECOVERY] 기본 참가자 정보 로드도 실패!")
                    println("에러: ${secondaryError.message}")
                    secondaryError.printStackTrace()
                }
            }
        }
    }

    private suspend fun findParticipantByBarcode(barcodeData: String): Participant? {
        val event = _activeEvent.value ?: return null

        // 1. 직접 QR 코드 데이터로 정확히 매칭
        var participant = participantDao.getParticipantByBarcode(barcodeData)
        if (participant != null) {
            println("✅ [SEARCH] QR 코드로 직접 매칭: $barcodeData")
            return participant
        }

        // 2. 하이픈(-) ↔ 언더바(_) 변환 후 매칭
        // QR-12342 → QR_12342 또는 QR_12342 → QR-12342
        val alternativeCode = when {
            barcodeData.contains("-") -> barcodeData.replace("-", "_")
            barcodeData.contains("_") -> barcodeData.replace("_", "-")
            else -> null
        }
        if (alternativeCode != null) {
            participant = participantDao.getParticipantByBarcode(alternativeCode)
            if (participant != null) {
                println("✅ [SEARCH] 하이픈/언더바 변환 후 매칭: $barcodeData → $alternativeCode")
                return participant
            }
        }

        // 3. JSON 형태의 QR 코드인 경우 파싱
        if (barcodeData.startsWith("{") && barcodeData.contains("\"code\"")) {
            try {
                val codePattern = "\"code\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val matchResult = codePattern.find(barcodeData)
                val code = matchResult?.groupValues?.get(1)

                if (code != null) {
                    participant = participantDao.getParticipantByBarcode(code)
                    if (participant != null) {
                        println("✅ [SEARCH] JSON 파싱 후 매칭: $code")
                        return participant
                    }
                }
            } catch (e: Exception) {
                println("⚠️ [SEARCH] JSON 파싱 실패: ${e.message}")
            }
        }

        // 4. QR 코드가 면허번호 그 자체일 경우 (직접 입력된 경우)
        participant = participantDao.getParticipantByLicense(event.id, barcodeData)
        if (participant != null) {
            println("✅ [SEARCH] 면허번호로 매칭: $barcodeData")
            return participant
        }

        // 5. QR_ 또는 QR- prefix 제거 후 면허번호로 매칭 시도
        if (barcodeData.startsWith("QR_") || barcodeData.startsWith("QR-")) {
            val licenseNo = barcodeData.substring(3) // "QR_" 또는 "QR-" 제거
            participant = participantDao.getParticipantByLicense(event.id, licenseNo)
            if (participant != null) {
                println("✅ [SEARCH] QR prefix 제거 후 면허번호로 매칭: $licenseNo")
                return participant
            }
        }

        println("❌ [SEARCH] 모든 검색 방법 실패: $barcodeData")
        return null
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

            // 최근(마지막) 입장 시간을 가져옴 (첫번째가 아닌 최근 입장)
            val lastEntry = scanRecordDao.getLastScanByType(participant.id, eventId, ScanType.ENTRY)
            val lastScan = scanRecordDao.getLastScanRecord(participant.id, eventId)
            val totalScans = scanRecordDao.getScanRecordCountByParticipant(participant.id, eventId)

            // 현재 내부에 있는지 확인 (마지막 스캔이 입장인지)
            val isCurrentlyInside = lastScan?.scanType == ScanType.ENTRY

            println("📊 [SCAN_INFO] 로드 완료:")
            println("   최근 입장: ${lastEntry?.scanTime?.let { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "없음"}")
            println("   마지막 스캔: ${lastScan?.scanTime?.let { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "없음"}")
            println("   총 스캔 횟수: $totalScans")
            println("   현재 상태: ${if (isCurrentlyInside) "입장중" else "퇴장완료"}")

            ParticipantWithScanInfo(
                participant = participant,
                firstScanTime = lastEntry?.scanTime,  // 최근 입장 시간
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

    fun updateEventInfo(eventId: Long, newTitle: String, newDate: String) {
        viewModelScope.launch {
            try {
                val event = eventDao.getEventById(eventId)
                if (event != null) {
                    val updatedEvent = event.copy(
                        eventName = newTitle,
                        eventDate = newDate
                    )
                    eventDao.updateEvent(updatedEvent)
                    _activeEvent.value = updatedEvent
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _errorMessage.value = "이벤트를 찾을 수 없습니다"
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _errorMessage.value = "이벤트 업데이트 실패: ${e.message}"
                }
            }
        }
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

                _errorMessage.value = "테스트 데이터 생성 완료! QR 코드: TEST001, TEST002, TEST003"

            } catch (e: Exception) {
                _errorMessage.value = "테스트 데이터 생성 실패: ${e.message}"
            }
        }
    }
}