package com.example.qr.ui.participant

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qr.data.dao.EventDao
import com.example.qr.data.dao.ParticipantDao
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.data.entity.ParticipantWithScanInfo
import com.example.qr.data.entity.ScanType
import com.example.qr.ui.adapter.ParticipantWithSelection
import com.example.qr.utils.CsvWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ParticipantManagementViewModel(
    private val participantDao: ParticipantDao,
    private val scanRecordDao: ScanRecordDao,
    private val eventDao: EventDao
) : ViewModel() {

    private val _participants = MutableLiveData<List<ParticipantWithSelection>>()
    val participants: LiveData<List<ParticipantWithSelection>> = _participants

    private val _selectedParticipants = MutableLiveData<Set<Long>>(emptySet())
    val selectedParticipants: LiveData<Set<Long>> = _selectedParticipants

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var allParticipants = listOf<ParticipantWithSelection>()
    private var currentStatus = "all"

    fun loadParticipants(statusFilter: String = "all") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                currentStatus = statusFilter

                val activeEvent = eventDao.getActiveEvent()
                if (activeEvent == null) {
                    _participants.value = emptyList()
                    allParticipants = emptyList()
                    _message.value = "활성화된 이벤트가 없습니다"
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

                // Apply status filter
                val filteredParticipants = when (statusFilter) {
                    "inside" -> participantsWithInfo.filter { it.isCurrentlyInside }
                    "outside" -> participantsWithInfo.filter { !it.isCurrentlyInside && it.scanCount > 0 }
                    "never" -> participantsWithInfo.filter { it.scanCount == 0 }
                    else -> participantsWithInfo
                }

                _participants.value = filteredParticipants

            } catch (e: Exception) {
                _message.value = "참가자 목록 로드 실패: ${e.message}"
                _participants.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchParticipants(query: String) {
        if (query.isBlank()) {
            loadParticipants(currentStatus)
            return
        }

        val filtered = allParticipants.filter { item ->
            item.participant.fullName.contains(query, ignoreCase = true) ||
                    item.participant.phoneNumber.contains(query) ||
                    item.participant.licenseNo.contains(query, ignoreCase = true)
        }

        _participants.value = filtered
    }

    fun toggleSelection(participantId: Long, isSelected: Boolean) {
        val currentSelected = _selectedParticipants.value ?: emptySet()
        _selectedParticipants.value = if (isSelected) {
            currentSelected + participantId
        } else {
            currentSelected - participantId
        }

        // Update participant list
        _participants.value = _participants.value?.map { item ->
            if (item.participant.id == participantId) {
                item.copy(isSelected = isSelected)
            } else {
                item
            }
        }
    }

    fun selectAll() {
        val currentList = _participants.value ?: return
        val allIds = currentList.map { it.participant.id }.toSet()
        _selectedParticipants.value = allIds

        _participants.value = currentList.map { it.copy(isSelected = true) }
    }

    fun clearSelection() {
        _selectedParticipants.value = emptySet()
        _participants.value = _participants.value?.map { it.copy(isSelected = false) }
    }

    fun deleteSelectedParticipants() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val selectedIds = _selectedParticipants.value ?: emptySet()
                if (selectedIds.isEmpty()) {
                    _message.value = "선택된 참가자가 없습니다"
                    return@launch
                }

                var deletedCount = 0
                selectedIds.forEach { participantId ->
                    try {
                        // Delete scan records first
                        scanRecordDao.deleteScanRecordsByParticipant(participantId)
                        // Then delete participant
                        val count = participantDao.deleteParticipant(participantId)
                        if (count > 0) deletedCount++
                    } catch (e: Exception) {
                        println("참가자 삭제 실패 (ID: $participantId): ${e.message}")
                    }
                }

                _message.value = "${deletedCount}명의 참가자가 삭제되었습니다"
                clearSelection()
                loadParticipants(currentStatus)

            } catch (e: Exception) {
                _message.value = "삭제 실패: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteParticipant(participantId: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Delete scan records first
                scanRecordDao.deleteScanRecordsByParticipant(participantId)
                // Then delete participant
                val count = participantDao.deleteParticipant(participantId)

                if (count > 0) {
                    _message.value = "참가자가 삭제되었습니다"
                    loadParticipants(currentStatus)
                } else {
                    _message.value = "참가자를 찾을 수 없습니다"
                }

            } catch (e: Exception) {
                _message.value = "삭제 실패: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportParticipantsToCsv(
        context: Context,
        eventId: Long,
        callback: (Result<File>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val event = withContext(Dispatchers.IO) {
                    eventDao.getEventById(eventId)
                }

                if (event == null) {
                    callback(Result.failure(Exception("이벤트를 찾을 수 없습니다")))
                    return@launch
                }

                val participantsWithScanInfo = withContext(Dispatchers.IO) {
                    val basicParticipants = participantDao.getParticipantsByEvent(eventId).first()
                    basicParticipants.map { participant ->
                        val scanRecords = scanRecordDao.getScanRecordsByParticipant(participant.id).first()
                        val lastScan = scanRecords.maxByOrNull { it.scanTime }
                        val firstScan = scanRecords.minByOrNull { it.scanTime }
                        val isInside = lastScan?.scanType == ScanType.ENTRY

                        ParticipantWithScanInfo(
                            participant = participant,
                            firstScanTime = firstScan?.scanTime,
                            lastScanTime = lastScan?.scanTime,
                            lastScanType = lastScan?.scanType,
                            totalScans = scanRecords.size,
                            isCurrentlyInside = isInside
                        )
                    }
                }

                val result = withContext(Dispatchers.IO) {
                    CsvWriter.exportEventData(context, event, participantsWithScanInfo)
                }

                callback(result)

            } catch (e: Exception) {
                callback(Result.failure(e))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
