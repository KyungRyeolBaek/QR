package com.example.qr.ui.monitoring

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.data.dao.EventDao
import com.example.qr.data.dao.ParticipantDao
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.data.entity.Event
import com.example.qr.data.entity.Participant
import com.example.qr.server.MonitoringServer
// TODO: Convert to CSV
// import com.example.qr.utils.ExcelReader
// import com.example.qr.utils.ExcelWriter
import com.example.qr.service.SmsService
import com.example.qr.ui.adapter.ParticipantWithSelection
import com.example.qr.ui.adapter.ParticipantScanInfo
import com.example.qr.data.entity.ScanType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.NetworkInterface

class MonitoringViewModel(
    private val eventDao: EventDao,
    private val participantDao: ParticipantDao,
    private val scanRecordDao: ScanRecordDao,
    private val context: android.content.Context
) : ViewModel() {

    data class Statistics(
        val totalParticipants: Int,
        val currentInside: Int,
        val totalVisits: Int
    )

    sealed class ServerStatus {
        object Stopped : ServerStatus()
        object Starting : ServerStatus()
        data class Running(val url: String) : ServerStatus()
        data class Error(val message: String) : ServerStatus()
    }

    private val _statistics = MutableLiveData<Statistics>()
    val statistics: LiveData<Statistics> = _statistics

    private val _recentScans = MutableLiveData<List<ParticipantScanInfo>>()
    val recentScans: LiveData<List<ParticipantScanInfo>> = _recentScans

    private val _serverStatus = MutableLiveData<ServerStatus>(ServerStatus.Stopped)
    val serverStatus: LiveData<ServerStatus> = _serverStatus

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    // Participant management
    private val _participants = MutableLiveData<List<ParticipantWithSelection>>()
    val participants: LiveData<List<ParticipantWithSelection>> = _participants

    private val _selectedParticipants = MutableLiveData<Set<Long>>(emptySet())
    val selectedParticipants: LiveData<Set<Long>> = _selectedParticipants

    private var allParticipants = listOf<ParticipantWithSelection>()
    private var currentStatusFilter = "all"

    private var monitoringServer: MonitoringServer? = null

    fun loadStatistics() {
        viewModelScope.launch {
            try {
                val activeEvent = eventDao.getActiveEvent()
                if (activeEvent != null) {
                    val totalParticipants = participantDao.getTotalParticipantCount().first()
                    val currentInside = scanRecordDao.getCurrentInsideCount(activeEvent.id).first()
                    val totalVisits = scanRecordDao.getTotalScanCount().first()

                    _statistics.value = Statistics(
                        totalParticipants = totalParticipants,
                        currentInside = currentInside,
                        totalVisits = totalVisits
                    )
                } else {
                    _statistics.value = Statistics(0, 0, 0)
                }
            } catch (e: Exception) {
                _message.value = "통계 로드 실패: ${e.message}"
            }
        }
    }

    fun loadRecentScans() {
        viewModelScope.launch {
            try {
                val activeEvent = eventDao.getActiveEvent()
                if (activeEvent != null) {
                    val participants = participantDao.getParticipantsByEvent(activeEvent.id).first()

                    // 참가자별로 최근 입장/퇴장 정보 계산
                    val participantScanInfoList = participants.map { participant ->
                        val scanRecords = scanRecordDao.getScanRecordsByParticipant(participant.id).first()
                        val sortedScans = scanRecords.sortedByDescending { it.scanTime }

                        val lastScan = sortedScans.firstOrNull()
                        val isCurrentlyInside = lastScan?.scanType == ScanType.ENTRY

                        // 가장 최근 입장 시간 찾기
                        val lastEntryTime = sortedScans.firstOrNull { it.scanType == ScanType.ENTRY }?.scanTime

                        // 가장 최근 퇴장 시간 찾기 (현재 입장중이 아닐 때만)
                        val lastExitTime = if (isCurrentlyInside) {
                            null
                        } else {
                            sortedScans.firstOrNull { it.scanType == ScanType.EXIT }?.scanTime
                        }

                        ParticipantScanInfo(
                            participantId = participant.id,
                            participantName = participant.fullName,
                            lastEntryTime = lastEntryTime,
                            lastExitTime = lastExitTime,
                            isCurrentlyInside = isCurrentlyInside
                        )
                    }

                    // 최근 활동한 참가자만 표시 (스캔 기록이 있는 참가자)
                    _recentScans.value = participantScanInfoList
                        .filter { it.lastEntryTime != null }
                        .sortedByDescending { it.lastEntryTime }
                        .take(20)
                } else {
                    _recentScans.value = emptyList()
                }
            } catch (e: Exception) {
                _message.value = "최근 스캔 기록 로드 실패: ${e.message}"
            }
        }
    }

    fun startWebServer() {
        viewModelScope.launch {
            try {
                _serverStatus.value = ServerStatus.Starting

                val port = 8080
                monitoringServer = MonitoringServer(
                    port = port,
                    participantDao = participantDao,
                    scanRecordDao = scanRecordDao,
                    eventDao = eventDao,
                    context = context
                )

                monitoringServer?.startServer()

                val ipAddress = getLocalIpAddress()
                val url = "http://$ipAddress:$port"
                _serverStatus.value = ServerStatus.Running(url)
                _message.value = "모니터링 서버가 시작되었습니다"

            } catch (e: Exception) {
                _serverStatus.value = ServerStatus.Error(e.message ?: "알 수 없는 오류")
                _message.value = "서버 시작 실패: ${e.message}"
            }
        }
    }

    fun stopWebServer() {
        viewModelScope.launch {
            try {
                monitoringServer?.stopServer()
                monitoringServer = null
                _serverStatus.value = ServerStatus.Stopped
                _message.value = "모니터링 서버가 중지되었습니다"
            } catch (e: Exception) {
                _message.value = "서버 중지 실패: ${e.message}"
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && !address.isLinkLocalAddress && address.hostAddress?.indexOf(':') ?: -1 < 0) {
                        return address.hostAddress ?: "localhost"
                    }
                }
            }
        } catch (e: Exception) {
            return "localhost"
        }
        return "localhost"
    }

    suspend fun uploadExcelFile(context: Context, uri: Uri) {
        try {
            _message.value = "CSV 파일 업로드 중..."

            // 활성 이벤트 확인 또는 생성
            var activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                val eventId = eventDao.insertEvent(
                    Event(
                        eventName = "이벤트",
                        eventDate = "2025-10-15",
                        description = "기본 이벤트",
                        isActive = true
                    )
                )
                activeEvent = eventDao.getEventById(eventId)
            }

            if (activeEvent != null) {
                val result = com.example.qr.utils.CsvReader.readParticipantsFromCsv(context, uri, activeEvent.id)
                result.fold(
                    onSuccess = { participants ->
                        // 중복 체크 (전화번호 기준)
                        val newParticipants = mutableListOf<Participant>()
                        val duplicates = mutableListOf<String>()

                        participants.forEach { participant ->
                            val existing = participantDao.getParticipantByPhone(activeEvent.id, participant.phoneNumber)
                            if (existing == null) {
                                newParticipants.add(participant)
                            } else {
                                duplicates.add(participant.fullName)
                            }
                        }

                        if (newParticipants.isNotEmpty()) {
                            val insertedIds = participantDao.insertParticipants(newParticipants)
                            val message = if (duplicates.isEmpty()) {
                                "성공: ${insertedIds.size}명의 참가자를 불러왔습니다"
                            } else {
                                "성공: ${insertedIds.size}명 추가, ${duplicates.size}명 중복 제외"
                            }
                            _message.value = message
                            loadStatistics() // 통계 새로고침
                            loadParticipantsWithFilter() // 참가자 목록 새로고침
                        } else {
                            _message.value = "모든 참가자가 이미 등록되어 있습니다"
                        }
                    },
                    onFailure = { exception ->
                        _message.value = "실패: ${exception.message}"
                    }
                )
            } else {
                _message.value = "이벤트 생성 실패"
            }
        } catch (e: Exception) {
            _message.value = "업로드 실패: ${e.message}"
        }
    }

    suspend fun downloadExcelData(context: Context) {
        // 현재 참가자 목록 다운로드 (중복 없이, 국문 이름 정렬)
        try {
            _message.value = "CSV 데이터 다운로드 중..."

            val activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                _message.value = "활성화된 이벤트가 없습니다"
                return
            }

            val participants = participantDao.getParticipantsByEvent(activeEvent.id).first()

            val result = com.example.qr.utils.CsvWriter.exportCurrentParticipants(
                context,
                activeEvent,
                participants
            )

            result.fold(
                onSuccess = { file ->
                    _message.value = "CSV 다운로드 완료: ${file.name}"
                    com.example.qr.utils.CsvWriter.shareCsvFile(context, file)
                },
                onFailure = { exception ->
                    _message.value = "다운로드 실패: ${exception.message}"
                }
            )
        } catch (e: Exception) {
            _message.value = "다운로드 실패: ${e.message}"
        }
    }

    suspend fun downloadDetailedExcel(context: Context) {
        // CSV 다운로드 - 모든 스캔 기록 포함
        try {
            _message.value = "CSV 데이터 준비 중..."

            val activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                _message.value = "활성화된 이벤트가 없습니다"
                return
            }

            // Get all participants with all their scan records
            val participants = participantDao.getParticipantsByEvent(activeEvent.id).first()
            val participantScans = participants.map { participant ->
                val scanRecords = scanRecordDao.getScanRecordsByParticipant(participant.id).first()
                participant to scanRecords
            }

            val result = com.example.qr.utils.CsvWriter.exportEventDataWithAllScans(
                context,
                activeEvent,
                participantScans
            )

            result.fold(
                onSuccess = { file ->
                    _message.value = "CSV 다운로드 완료: ${file.name}"
                    com.example.qr.utils.CsvWriter.shareCsvFile(context, file)
                },
                onFailure = { exception ->
                    _message.value = "다운로드 실패: ${exception.message}"
                }
            )
        } catch (e: Exception) {
            _message.value = "다운로드 실패: ${e.message}"
        }
    }

    suspend fun downloadExcelTemplate(context: Context) {
        try {
            _message.value = "템플릿 다운로드 중..."

            val result = com.example.qr.utils.CsvWriter.exportParticipantTemplate(context)
            result.fold(
                onSuccess = { file ->
                    _message.value = "템플릿 다운로드 완료: ${file.name}"
                    com.example.qr.utils.CsvWriter.shareCsvFile(context, file)
                },
                onFailure = { exception ->
                    _message.value = "템플릿 다운로드 실패: ${exception.message}"
                }
            )
        } catch (e: Exception) {
            _message.value = "템플릿 다운로드 실패: ${e.message}"
        }
    }

    suspend fun sendQrCodesToParticipants(context: Context, isResend: Boolean) {
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
            val prefs = context.getSharedPreferences("qr_settings", Context.MODE_PRIVATE)
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
        }
    }

    suspend fun sendQrCodesToSelectedParticipants(context: Context, isResend: Boolean) {
        try {
            val selectedIds = _selectedParticipants.value ?: emptySet()
            if (selectedIds.isEmpty()) {
                _message.value = "선택된 참가자가 없습니다."
                return
            }

            val activeEvent = eventDao.getActiveEvent()
            if (activeEvent == null) {
                _message.value = "활성화된 이벤트가 없습니다."
                return
            }

            // Get selected participants
            val allParticipants = participantDao.getParticipantsByEvent(activeEvent.id).first()
            val selectedParticipants = allParticipants.filter { selectedIds.contains(it.id) }

            if (selectedParticipants.isEmpty()) {
                _message.value = "선택된 참가자를 찾을 수 없습니다."
                return
            }

            val smsService = SmsService(context)

            // SMS 권한 확인
            if (!smsService.hasSmsPermission()) {
                _message.value = "SMS 권한이 필요합니다. 설정에서 권한을 허용해주세요."
                return
            }

            // 저장된 템플릿 불러오기
            val prefs = context.getSharedPreferences("qr_settings", Context.MODE_PRIVATE)
            val defaultTemplate = SmsService.DEFAULT_MESSAGE_TEMPLATE
            val defaultResendTemplate = SmsService.RESEND_MESSAGE_TEMPLATE

            // 메시지 템플릿 선택
            val messageTemplate = if (isResend) {
                prefs.getString("resend_template", defaultResendTemplate) ?: defaultResendTemplate
            } else {
                prefs.getString("default_template", defaultTemplate) ?: defaultTemplate
            }

            _message.value = "선택된 참가자에게 QR 코드 발송 시작... (${selectedParticipants.size}명)"

            // 참가자 정보 변환
            val participantInfos = selectedParticipants.map { participant ->
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
                    // Clear selection after sending
                    clearParticipantSelection()
                }
            )

        } catch (e: Exception) {
            _message.value = "QR 코드 발송 실패: ${e.message}"
        }
    }

    // Participant Management Functions
    fun loadParticipantsWithFilter(statusFilter: String = "all") {
        viewModelScope.launch {
            try {
                currentStatusFilter = statusFilter

                val activeEvent = eventDao.getActiveEvent()
                if (activeEvent == null) {
                    _participants.value = emptyList()
                    allParticipants = emptyList()
                    return@launch
                }

                val basicParticipants = participantDao.getParticipantsByEvent(activeEvent.id).first()

                val participantsWithInfo = basicParticipants.map { participant ->
                    val scanRecords = scanRecordDao.getScanRecordsByParticipant(participant.id).first()
                    val lastScan = scanRecords.maxByOrNull { it.scanTime }
                    val isInside = lastScan?.scanType == ScanType.ENTRY

                    ParticipantWithSelection(
                        participant = participant,
                        isSelected = _selectedParticipants.value?.contains(participant.id) == true,
                        scanCount = scanRecords.size,
                        lastScanTime = lastScan?.scanTime,
                        isCurrentlyInside = isInside
                    )
                }

                allParticipants = participantsWithInfo
                applyStatusFilter()

            } catch (e: Exception) {
                _message.value = "참가자 목록 로드 실패: ${e.message}"
                _participants.value = emptyList()
            }
        }
    }

    private fun applyStatusFilter() {
        val filteredParticipants = when (currentStatusFilter) {
            "inside" -> allParticipants.filter { it.isCurrentlyInside }
            "exited" -> allParticipants.filter { !it.isCurrentlyInside && it.scanCount > 0 }
            "never" -> allParticipants.filter { it.scanCount == 0 }
            else -> allParticipants
        }

        _participants.value = filteredParticipants
    }

    fun searchParticipants(query: String) {
        if (query.isBlank()) {
            applyStatusFilter()
            return
        }

        val filtered = allParticipants.filter { item ->
            item.participant.fullName.contains(query, ignoreCase = true) ||
                    item.participant.phoneNumber.contains(query) ||
                    item.participant.licenseNo.contains(query, ignoreCase = true)
        }

        _participants.value = filtered
    }

    fun toggleParticipantSelection(participantId: Long, isSelected: Boolean) {
        val currentSelected = _selectedParticipants.value ?: emptySet()
        _selectedParticipants.value = if (isSelected) {
            currentSelected + participantId
        } else {
            currentSelected - participantId
        }

        _participants.value = _participants.value?.map { item ->
            if (item.participant.id == participantId) {
                item.copy(isSelected = isSelected)
            } else {
                item
            }
        }
    }

    fun toggleSelectAllParticipants() {
        val currentList = _participants.value ?: return
        val selectedIds = _selectedParticipants.value ?: emptySet()

        // If all are selected, deselect all. Otherwise, select all
        val allIds = currentList.map { it.participant.id }.toSet()
        val allSelected = selectedIds.containsAll(allIds) && allIds.isNotEmpty()

        if (allSelected) {
            // Deselect all
            _selectedParticipants.value = emptySet()
            _participants.value = currentList.map { it.copy(isSelected = false) }
        } else {
            // Select all
            _selectedParticipants.value = allIds
            _participants.value = currentList.map { it.copy(isSelected = true) }
        }
    }

    fun clearParticipantSelection() {
        _selectedParticipants.value = emptySet()
        _participants.value = _participants.value?.map { it.copy(isSelected = false) }
    }

    fun deleteSelectedParticipants() {
        viewModelScope.launch {
            try {
                val selectedIds = _selectedParticipants.value ?: emptySet()
                if (selectedIds.isEmpty()) {
                    _message.value = "선택된 참가자가 없습니다"
                    return@launch
                }

                var deletedCount = 0
                selectedIds.forEach { participantId ->
                    try {
                        scanRecordDao.deleteScanRecordsByParticipant(participantId)
                        val count = participantDao.deleteParticipant(participantId)
                        if (count > 0) deletedCount++
                    } catch (e: Exception) {
                        println("참가자 삭제 실패 (ID: $participantId): ${e.message}")
                    }
                }

                _message.value = "${deletedCount}명의 참가자가 삭제되었습니다"
                clearParticipantSelection()
                loadParticipantsWithFilter(currentStatusFilter)
                loadStatistics() // Refresh statistics

            } catch (e: Exception) {
                _message.value = "삭제 실패: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitoringServer?.stopServer()
    }
}