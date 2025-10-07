package com.example.qr.ui.monitoring

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.data.dao.EventDao
import com.example.qr.data.dao.ParticipantDao
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.server.MonitoringServer
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

    private val _recentScans = MutableLiveData<List<ScanRecordDao.ScanRecordWithParticipant>>()
    val recentScans: LiveData<List<ScanRecordDao.ScanRecordWithParticipant>> = _recentScans

    private val _serverStatus = MutableLiveData<ServerStatus>(ServerStatus.Stopped)
    val serverStatus: LiveData<ServerStatus> = _serverStatus

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

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
                    val scans = scanRecordDao.getRecentScansWithParticipants(activeEvent.id, 20).first()
                    val scanRecordsWithParticipant = scans
                    _recentScans.value = scanRecordsWithParticipant
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

    override fun onCleared() {
        super.onCleared()
        monitoringServer?.stopServer()
    }
}