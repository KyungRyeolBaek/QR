package com.example.qr.ui.monitoring

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.api.ParticipantSyncData
import com.example.qr.api.SyncEvent
import com.example.qr.api.SyncMessage
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
import com.example.qr.api.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.NetworkInterface
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

    sealed class ConnectionStatus {
        object Disconnected : ConnectionStatus()
        object Connecting : ConnectionStatus()
        data class Connected(val masterUrl: String) : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }

    private val _statistics = MutableLiveData<Statistics>()
    val statistics: LiveData<Statistics> = _statistics

    private val _recentScans = MutableLiveData<List<ParticipantScanInfo>>()
    val recentScans: LiveData<List<ParticipantScanInfo>> = _recentScans

    private val _serverStatus = MutableLiveData<ServerStatus>(ServerStatus.Stopped)
    val serverStatus: LiveData<ServerStatus> = _serverStatus

    private val _connectionStatus = MutableLiveData<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus

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
    private var refreshJob: Job? = null
    private var masterServerUrl: String = ""

    // WebSocket 클라이언트
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private var reconnectJob: Job? = null

    // 연결 안정성 추적
    private var connectionStartTime: Long = 0
    private var isStableConnection: Boolean = false
    private var consecutiveFastFailures: Int = 0
    private val minConnectionTime: Long = 5000  // 5초
    private val maxConsecutiveFastFailures: Int = 3

    fun loadStatistics() {
        viewModelScope.launch {
            try {
                // 클라이언트 모드인지 확인
                if (isClientMode() && _connectionStatus.value is ConnectionStatus.Connected) {
                    // 마스터 서버에서 데이터 가져오기
                    loadStatisticsFromMaster()
                } else {
                    // 로컬 DB에서 데이터 가져오기
                    loadStatisticsFromLocal()
                }
            } catch (e: Exception) {
                _message.value = "통계 로드 실패: ${e.message}"
            }
        }
    }

    private suspend fun loadStatisticsFromLocal() {
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
    }

    private suspend fun loadStatisticsFromMaster() {
        try {
            val api = RetrofitClient.createApi(masterServerUrl)
            val response = api.getStats()

            if (response.isSuccessful && response.body() != null) {
                val stats = response.body()!!
                _statistics.value = Statistics(
                    totalParticipants = stats.totalParticipants,
                    currentInside = stats.currentInside,
                    totalVisits = stats.totalVisits
                )
            } else {
                throw Exception("서버 응답 실패: ${response.code()}")
            }
        } catch (e: Exception) {
            println("❌ 마스터 서버에서 통계 로드 실패: ${e.message}")
            throw e
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

                val port = 8443  // HTTPS 포트
                monitoringServer = MonitoringServer(
                    port = port,
                    participantDao = participantDao,
                    scanRecordDao = scanRecordDao,
                    eventDao = eventDao,
                    context = context
                )

                monitoringServer?.startServer()

                val ipAddress = getLocalIpAddress()
                val url = "https://$ipAddress:$port"  // HTTPS 프로토콜 사용
                _serverStatus.value = ServerStatus.Running(url)
                _message.value = "모니터링 서버가 시작되었습니다 (HTTPS)"

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

                // 클라이언트 모드인지 확인
                if (isClientMode() && _connectionStatus.value is ConnectionStatus.Connected) {
                    // 마스터 서버에서 데이터 가져오기
                    loadParticipantsFromMaster()
                } else {
                    // 로컬 DB에서 데이터 가져오기
                    loadParticipantsFromLocal()
                }

                applyStatusFilter()

            } catch (e: Exception) {
                _message.value = "참가자 목록 로드 실패: ${e.message}"
                _participants.value = emptyList()
            }
        }
    }

    private suspend fun loadParticipantsFromLocal() {
        val activeEvent = eventDao.getActiveEvent()
        if (activeEvent == null) {
            _participants.value = emptyList()
            allParticipants = emptyList()
            return
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
    }

    private suspend fun loadParticipantsFromMaster() {
        try {
            val api = RetrofitClient.createApi(masterServerUrl)
            val response = api.getParticipants()

            if (response.isSuccessful && response.body() != null) {
                val participantsResponse = response.body()!!

                val participantsWithInfo = participantsResponse.participants.map { apiParticipant ->
                    val participant = com.example.qr.data.entity.Participant(
                        id = apiParticipant.id,
                        eventId = 0, // Not needed for display
                        fullName = apiParticipant.fullName,
                        phoneNumber = apiParticipant.phoneNumber,
                        licenseNo = apiParticipant.licenseNo,
                        barcodeData = apiParticipant.barcodeData
                    )

                    ParticipantWithSelection(
                        participant = participant,
                        isSelected = _selectedParticipants.value?.contains(apiParticipant.id) == true,
                        scanCount = apiParticipant.scanCount,
                        lastScanTime = apiParticipant.lastScanTime,
                        isCurrentlyInside = apiParticipant.isCurrentlyInside
                    )
                }

                allParticipants = participantsWithInfo
            } else {
                throw Exception("서버 응답 실패: ${response.code()}")
            }
        } catch (e: Exception) {
            println("❌ 마스터 서버에서 참가자 목록 로드 실패: ${e.message}")
            throw e
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

    // Client mode functions
    fun connectToMasterServer(masterUrl: String) {
        viewModelScope.launch {
            try {
                _connectionStatus.value = ConnectionStatus.Connecting

                // URL 정규화
                val normalizedUrl = normalizeMasterUrl(masterUrl)
                masterServerUrl = normalizedUrl

                println("🔧 [URL] 정규화: $masterUrl → $normalizedUrl")

                // Health check 먼저 (REST API)
                val api = RetrofitClient.createApi(normalizedUrl)
                val response = api.healthCheck()

                if (!response.isSuccessful) {
                    throw Exception("서버 응답 실패: ${response.code()}")
                }

                // WebSocket 연결
                connectWebSocket(normalizedUrl)

            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error(e.message ?: "연결 실패")
                _message.value = "서버 연결 실패: ${e.message}"
            }
        }
    }

    /**
     * 마스터 서버 URL 정규화
     * - 프로토콜 없으면 https:// 추가
     * - http://는 https://로 변경
     * - 포트 없으면 :8443 추가
     */
    private fun normalizeMasterUrl(masterIp: String): String {
        if (masterIp.isBlank()) return ""

        var url = masterIp.trim()

        // 프로토콜 추가 (없으면)
        if (!url.startsWith("http")) {
            url = "https://$url"
        } else if (url.startsWith("http://")) {
            url = url.replace("http://", "https://")
        }

        // 포트 추가 (없으면)
        // 이미 포트가 있는지 확인: https://192.168.0.10:8443 형태
        val hasPort = url.substringAfter("://").contains(":")
        if (!hasPort) {
            url = "$url:8443"
        }

        return url
    }

    private fun connectWebSocket(masterUrl: String) {
        try {
            // 중복 연결 방지
            if (webSocket != null) {
                println("⚠️ 이미 WebSocket 연결이 존재합니다 - 중복 연결 방지")
                return
            }

            // WebSocket URL 생성 (https → wss)
            val wsUrl = masterUrl.replace("https://", "wss://").replace("http://", "ws://") + "/ws/sync"
            println("🔌 WebSocket 연결 시도: $wsUrl")

            // 자체 서명 인증서를 허용하는 TrustManager
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // SSL Context 생성
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }  // 모든 호스트명 허용
                // pingInterval 제거 - NanoWSD 호환성 문제로 애플리케이션 레벨 JSON Ping/Pong 사용
                .connectTimeout(30, TimeUnit.SECONDS)  // 대량 데이터 전송을 위해 30초로 증가
                .readTimeout(0, TimeUnit.SECONDS)  // WebSocket은 timeout 없음
                .writeTimeout(30, TimeUnit.SECONDS)  // 쓰기 타임아웃도 30초로 설정
                .build()

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    println("✅ WebSocket 연결 성공")

                    // 연결 시간 기록
                    connectionStartTime = System.currentTimeMillis()
                    isStableConnection = false  // 초기 데이터 수신 완료 후 true로 설정

                    reconnectAttempts = 0
                    reconnectJob?.cancel()

                    viewModelScope.launch {
                        _connectionStatus.value = ConnectionStatus.Connected(masterUrl)
                        _message.value = "실시간 동기화 연결 중..."
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    println("📩 WebSocket 메시지 수신: ${text.take(100)}...")

                    viewModelScope.launch {
                        handleSyncEvent(text)
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // Binary message (사용 안 함)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    println("🔌 WebSocket 종료 중:")
                    println("   종료 코드: $code")
                    println("   종료 이유: $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val connectionDuration = System.currentTimeMillis() - connectionStartTime
                    val wasFastFailure = connectionDuration < minConnectionTime

                    println("🔌 WebSocket 연결 종료:")
                    println("   종료 코드: $code")
                    println("   종료 이유: $reason")
                    println("   연결 유지 시간: ${connectionDuration}ms")
                    println("   안정적인 연결이었음: $isStableConnection")
                    println("   빠른 실패: $wasFastFailure")
                    println("   현재 상태: ${_connectionStatus.value}")

                    viewModelScope.launch {
                        // 정상 종료 코드(1000, 1001)는 재연결하지 않음
                        if (code == 1000 || code == 1001) {
                            println("✅ 정상 종료 - 재연결하지 않음 (코드: $code)")
                            _connectionStatus.value = ConnectionStatus.Disconnected
                            _message.value = "서버 연결이 종료되었습니다"
                            this@MonitoringViewModel.webSocket = null
                            return@launch
                        }

                        if (_connectionStatus.value is ConnectionStatus.Connected) {
                            // 빠른 실패 추적
                            if (wasFastFailure) {
                                consecutiveFastFailures++
                                println("⚠️ 연속 빠른 실패 횟수: $consecutiveFastFailures/$maxConsecutiveFastFailures")
                            }

                            _connectionStatus.value = ConnectionStatus.Error("연결 끊김: $reason")
                            this@MonitoringViewModel.webSocket = null

                            // 재연결 조건 확인
                            if (consecutiveFastFailures >= maxConsecutiveFastFailures) {
                                println("❌ 연속 빠른 실패 횟수 초과 - 재연결 중지")
                                _message.value = "서버 연결 불안정 - 재연결 중지. 서버 상태를 확인해주세요."
                            } else if (wasFastFailure && !isStableConnection) {
                                println("⚠️ 안정적인 연결 확립 전 빠른 실패 - 재연결 시도")
                                _message.value = "연결이 끊어졌습니다 (${connectionDuration}ms)"
                                attemptReconnect()
                            } else if (isStableConnection) {
                                println("🔄 안정적인 연결이 끊어짐 - 재연결 시도")
                                _message.value = "연결이 끊어졌습니다 (코드: $code)"
                                consecutiveFastFailures = 0  // 안정적인 연결이었으므로 리셋
                                attemptReconnect()
                            } else {
                                println("⚠️ 재연결 조건 불충족")
                                _message.value = "연결 실패 - 서버를 확인해주세요"
                            }
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val connectionDuration = System.currentTimeMillis() - connectionStartTime
                    val wasFastFailure = connectionDuration < minConnectionTime

                    println("❌ WebSocket 연결 실패:")
                    println("   예외 타입: ${t.javaClass.simpleName}")
                    println("   예외 메시지: ${t.message}")
                    println("   HTTP 응답: ${response?.code} ${response?.message}")
                    println("   연결 유지 시간: ${connectionDuration}ms")
                    println("   안정적인 연결이었음: $isStableConnection")
                    println("   빠른 실패: $wasFastFailure")
                    println("   현재 상태: ${_connectionStatus.value}")
                    println("   Stack Trace:")
                    t.printStackTrace()

                    val errorMessage = when {
                        t.message?.contains("Trust anchor") == true -> "SSL 인증서 오류"
                        t.message?.contains("404") == true -> "서버 엔드포인트를 찾을 수 없음"
                        t.message?.contains("timeout") == true -> "연결 시간 초과"
                        t.message?.contains("refused") == true -> "서버 연결 거부"
                        else -> t.message ?: "알 수 없는 오류"
                    }

                    viewModelScope.launch {
                        // 빠른 실패 추적
                        if (wasFastFailure) {
                            consecutiveFastFailures++
                            println("⚠️ 연속 빠른 실패 횟수: $consecutiveFastFailures/$maxConsecutiveFastFailures")
                        }

                        _connectionStatus.value = ConnectionStatus.Error(errorMessage)
                        this@MonitoringViewModel.webSocket = null

                        // 재연결 조건 확인
                        if (consecutiveFastFailures >= maxConsecutiveFastFailures) {
                            println("❌ 연속 빠른 실패 횟수 초과 - 재연결 중지")
                            _message.value = "연결 실패: $errorMessage\n서버 연결이 불안정합니다. 서버 상태를 확인해주세요."
                        } else if (wasFastFailure && !isStableConnection) {
                            println("⚠️ 안정적인 연결 확립 전 빠른 실패 - 재연결 시도")
                            _message.value = "연결 실패: $errorMessage"
                            attemptReconnect()
                        } else if (isStableConnection) {
                            println("🔄 안정적인 연결이 실패함 - 재연결 시도")
                            _message.value = "연결 실패: $errorMessage"
                            consecutiveFastFailures = 0  // 안정적인 연결이었으므로 리셋
                            attemptReconnect()
                        } else {
                            println("⚠️ 재연결 조건 불충족")
                            _message.value = "연결 실패: $errorMessage\n서버를 확인해주세요"
                        }
                    }
                }
            })

        } catch (e: Exception) {
            println("❌ WebSocket 연결 생성 실패: ${e.message}")
            _connectionStatus.value = ConnectionStatus.Error(e.message ?: "연결 실패")
        }
    }

    private suspend fun handleSyncEvent(json: String) {
        try {
            // SyncMessage로 파싱하여 타입 확인
            val syncMessage = gson.fromJson(json, SyncMessage::class.java)

            // 타입별로 data를 적절한 클래스로 재파싱
            when (syncMessage.type) {
                "ConnectionEstablished" -> {
                    val dataJson = gson.toJson(syncMessage.data)
                    val event = gson.fromJson(dataJson, SyncEvent.ConnectionEstablished::class.java)
                    handleConnectionEstablished(event)
                }
                "ScanRecorded" -> {
                    val dataJson = gson.toJson(syncMessage.data)
                    val event = gson.fromJson(dataJson, SyncEvent.ScanRecorded::class.java)
                    handleScanRecorded(event)
                }
                "StatsUpdated" -> {
                    val dataJson = gson.toJson(syncMessage.data)
                    val event = gson.fromJson(dataJson, SyncEvent.StatsUpdated::class.java)
                    handleStatsUpdated(event)
                }
                "Ping" -> {
                    println("🏓 Ping 수신 - Pong 전송")
                    webSocket?.send(gson.toJson(SyncMessage.wrap(SyncEvent.Pong())))
                }
                "Pong" -> {
                    println("🏓 Pong 수신")
                }
                "Error" -> {
                    val dataJson = gson.toJson(syncMessage.data)
                    val event = gson.fromJson(dataJson, SyncEvent.Error::class.java)
                    println("❌ 서버 에러 수신: ${event.message}")
                    _message.value = "서버 오류: ${event.message}"
                }
                else -> {
                    println("⚠️ 알 수 없는 이벤트 타입: ${syncMessage.type}")
                }
            }
        } catch (e: Exception) {
            println("❌ 이벤트 처리 실패:")
            println("   예외 타입: ${e.javaClass.simpleName}")
            println("   예외 메시지: ${e.message}")
            println("   Stack Trace:")
            e.printStackTrace()
        }
    }

    private suspend fun handleConnectionEstablished(event: SyncEvent.ConnectionEstablished) {
        println("✅ 초기 데이터 수신: ${event.participants.size}명")

        // 통계 업데이트
        _statistics.value = Statistics(
            totalParticipants = event.totalParticipants,
            currentInside = event.currentInside,
            totalVisits = event.totalVisits
        )

        // 참가자 목록 업데이트
        allParticipants = event.participants.map { syncData ->
            val participant = Participant(
                id = syncData.id,
                eventId = 0,
                fullName = syncData.fullName,
                phoneNumber = syncData.phoneNumber,
                licenseNo = syncData.licenseNo,
                barcodeData = syncData.barcodeData
            )

            ParticipantWithSelection(
                participant = participant,
                isSelected = false,
                scanCount = syncData.scanCount,
                lastScanTime = syncData.lastScanTime,
                isCurrentlyInside = syncData.isCurrentlyInside
            )
        }

        applyStatusFilter()

        // 초기 데이터 수신 완료 - 안정적인 연결로 표시
        isStableConnection = true
        consecutiveFastFailures = 0  // 성공 시 빠른 실패 카운트 리셋
        _message.value = "실시간 동기화 연결됨"
        println("🟢 안정적인 연결 확립: ${event.participants.size}명 동기화 완료")
    }

    private suspend fun handleScanRecorded(event: SyncEvent.ScanRecorded) {
        println("🔔 스캔 이벤트: ${event.participantName} - ${event.scanType}")

        // 통계 및 참가자 목록 새로고침
        loadStatistics()
        loadParticipantsWithFilter(currentStatusFilter)
    }

    private suspend fun handleStatsUpdated(event: SyncEvent.StatsUpdated) {
        println("📊 통계 업데이트: ${event.currentInside}명 입장 중")

        _statistics.value = Statistics(
            totalParticipants = event.totalParticipants,
            currentInside = event.currentInside,
            totalVisits = event.totalVisits
        )
    }

    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            println("❌ 최대 재연결 시도 횟수 초과")
            _message.value = "재연결 실패: 최대 시도 횟수 초과"
            return
        }

        reconnectAttempts++
        val delay = minOf(1000L * (1 shl (reconnectAttempts - 1)), 30000L)  // Exponential backoff (최대 30초)

        println("🔄 재연결 시도 $reconnectAttempts/$maxReconnectAttempts (${delay}ms 후)")
        _message.value = "재연결 시도 중... ($reconnectAttempts/$maxReconnectAttempts)"

        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(delay)
            if (masterServerUrl.isNotEmpty()) {
                connectWebSocket(masterServerUrl)
            }
        }
    }

    fun disconnectFromMasterServer() {
        viewModelScope.launch {
            try {
                // WebSocket 연결 종료
                webSocket?.close(1000, "User disconnect")
                webSocket = null

                // 재연결 작업 취소
                reconnectJob?.cancel()
                reconnectJob = null
                reconnectAttempts = 0

                // 연결 추적 변수 리셋
                connectionStartTime = 0
                isStableConnection = false
                consecutiveFastFailures = 0

                _connectionStatus.value = ConnectionStatus.Disconnected
                masterServerUrl = ""
                _message.value = "서버 연결이 해제되었습니다"

                // 로컬 데이터로 복귀
                loadStatistics()
                loadParticipantsWithFilter()
            } catch (e: Exception) {
                _message.value = "연결 해제 실패: ${e.message}"
            }
        }
    }

    private fun isClientMode(): Boolean {
        val prefs = context.getSharedPreferences("device_settings", Context.MODE_PRIVATE)
        return !prefs.getBoolean("is_master_mode", true)
    }

    override fun onCleared() {
        super.onCleared()
        monitoringServer?.stopServer()

        // WebSocket 정리
        webSocket?.close(1000, "ViewModel cleared")
        webSocket = null
        reconnectJob?.cancel()
    }
}