package com.example.qr.data.entity

data class ParticipantWithScanInfo(
    val participant: Participant,
    val firstScanTime: Long?,  // 최근(마지막) 입장 시간 (재입장 시 업데이트됨)
    val lastScanTime: Long?,   // 마지막 스캔 시간
    val lastScanType: ScanType?, // 마지막 스캔 타입 (입장/퇴장)
    val totalScans: Int,       // 총 스캔 횟수
    val isCurrentlyInside: Boolean // 현재 내부에 있는지 여부
) {
    // 퇴장 시간 (현재 입장중이면 null, 퇴장했으면 퇴장 시간)
    val exitTime: Long?
        get() = if (isCurrentlyInside) null else lastScanTime

    // 체류 시간 계산 (밀리초)
    val durationTime: Long?
        get() = when {
            firstScanTime == null -> null
            isCurrentlyInside -> {
                // 현재 입장중인 경우: 현재 시간 - 최근 입장 시간
                System.currentTimeMillis() - firstScanTime
            }
            else -> {
                val exit = exitTime
                if (exit != null && firstScanTime != null) {
                    // 퇴장완료인 경우: 퇴장 시간 - 입장 시간
                    exit - firstScanTime
                } else {
                    null
                }
            }
        }

    // 체류 시간을 시:분:초 형식으로 반환
    val formattedDuration: String
        get() = durationTime?.let { duration ->
            val hours = duration / (1000 * 60 * 60)
            val minutes = (duration % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (duration % (1000 * 60)) / 1000
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } ?: "00:00:00"

    // 현재 상태 텍스트
    val statusText: String
        get() = when {
            firstScanTime == null -> "미입장"
            isCurrentlyInside -> "입장중"
            else -> "퇴장완료"
        }
}