package com.example.qr.api

import com.example.qr.data.entity.Participant
import com.example.qr.data.entity.ScanRecord
import com.example.qr.data.entity.ScanType

/**
 * WebSocket 동기화 이벤트
 * 마스터-클라이언트 간 실시간 데이터 동기화를 위한 이벤트 타입
 */
sealed class SyncEvent {
    /**
     * 연결 성공 - 초기 데이터 전송
     */
    data class ConnectionEstablished(
        val totalParticipants: Int,
        val currentInside: Int,
        val totalVisits: Int,
        val participants: List<ParticipantSyncData>,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * 스캔 기록 추가
     */
    data class ScanRecorded(
        val participantId: Long,
        val participantName: String,
        val scanType: ScanType,
        val scanTime: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * 참가자 추가
     */
    data class ParticipantAdded(
        val participant: ParticipantSyncData,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * 참가자 삭제
     */
    data class ParticipantDeleted(
        val participantId: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * 여러 참가자 삭제
     */
    data class ParticipantsDeleted(
        val participantIds: List<Long>,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * 통계 업데이트
     */
    data class StatsUpdated(
        val totalParticipants: Int,
        val currentInside: Int,
        val totalVisits: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * Ping (연결 확인)
     */
    data class Ping(
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * Pong (Ping 응답)
     */
    data class Pong(
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()

    /**
     * 에러 메시지
     */
    data class Error(
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncEvent()
}

/**
 * 동기화용 참가자 데이터
 */
data class ParticipantSyncData(
    val id: Long,
    val fullName: String,
    val phoneNumber: String,
    val licenseNo: String,
    val barcodeData: String,
    val scanCount: Int,
    val lastScanTime: Long?,
    val isCurrentlyInside: Boolean
)

/**
 * WebSocket 메시지 래퍼 - 타입 정보 포함
 */
data class SyncMessage(
    val type: String,  // "ConnectionEstablished", "ScanRecorded", etc.
    val data: Any      // 실제 이벤트 데이터
) {
    companion object {
        fun wrap(event: SyncEvent): SyncMessage {
            val type = when (event) {
                is SyncEvent.ConnectionEstablished -> "ConnectionEstablished"
                is SyncEvent.ScanRecorded -> "ScanRecorded"
                is SyncEvent.ParticipantAdded -> "ParticipantAdded"
                is SyncEvent.ParticipantDeleted -> "ParticipantDeleted"
                is SyncEvent.ParticipantsDeleted -> "ParticipantsDeleted"
                is SyncEvent.StatsUpdated -> "StatsUpdated"
                is SyncEvent.Ping -> "Ping"
                is SyncEvent.Pong -> "Pong"
                is SyncEvent.Error -> "Error"
            }
            return SyncMessage(type, event)
        }
    }
}
