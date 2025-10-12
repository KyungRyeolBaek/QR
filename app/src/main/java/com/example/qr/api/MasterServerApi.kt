package com.example.qr.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 마스터 서버 API 인터페이스
 */
interface MasterServerApi {

    /**
     * 헬스체크 - 서버 연결 상태 확인
     */
    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    /**
     * 이벤트 정보 조회 - 활성 이벤트 정보 가져오기
     */
    @GET("/api/event")
    suspend fun getEvent(): Response<EventResponse>

    /**
     * 스캔 기록 - 바코드 스캔 데이터 전송
     */
    @POST("/api/scan")
    suspend fun recordScan(@Body request: ScanRequest): Response<ScanResponse>

    /**
     * 통계 조회 - 총 등록자, 현재 입장, 총 방문 수
     */
    @GET("/api/stats")
    suspend fun getStats(): Response<StatsResponse>

    /**
     * 참가자 목록 조회 - 전체 참가자 목록과 스캔 정보
     */
    @GET("/api/participants")
    suspend fun getParticipants(): Response<ParticipantsResponse>
}

/**
 * 헬스체크 응답
 */
data class HealthResponse(
    val status: String,
    val timestamp: Long
)

/**
 * 이벤트 정보 응답
 */
data class EventResponse(
    val id: Long,
    val eventName: String,
    val eventDate: String,
    val description: String,
    val backgroundColor: String,
    val textColor: String
)

/**
 * 스캔 요청
 */
data class ScanRequest(
    val barcodeData: String
)

/**
 * 스캔 응답
 */
data class ScanResponse(
    val success: Boolean,
    val message: String? = null,
    val participant: ParticipantResponse? = null,
    val scanType: String? = null,  // "ENTRY" or "EXIT"
    val scanTime: Long? = null,
    val scanStats: ScanStatsResponse? = null
)

/**
 * 스캔 통계 응답
 */
data class ScanStatsResponse(
    val firstScanTime: Long?,
    val lastScanTime: Long?,
    val totalScans: Int,
    val isCurrentlyInside: Boolean
)

/**
 * 참가자 응답
 */
data class ParticipantResponse(
    val id: Long,
    val fullName: String,
    val phoneNumber: String,
    val organization: String,
    val licenseNo: String,
    val barcodeData: String
)

/**
 * 통계 응답
 */
data class StatsResponse(
    val totalParticipants: Int,
    val currentInside: Int,
    val totalVisits: Int
)

/**
 * 참가자 목록 응답
 */
data class ParticipantsResponse(
    val participants: List<ParticipantWithScanInfoResponse>
)

/**
 * 참가자 + 스캔 정보 응답
 */
data class ParticipantWithScanInfoResponse(
    val id: Long,
    val fullName: String,
    val phoneNumber: String,
    val licenseNo: String,
    val barcodeData: String,
    val scanCount: Int,
    val lastScanTime: Long?,
    val isCurrentlyInside: Boolean
)
