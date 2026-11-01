package io.github.hehpollon.drivemood.domain

data class TripEvent(val offsetSeconds: Long, val kind: String, val message: String)

data class TripRecord(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val elapsedSeconds: Long,
    val drivingSeconds: Long,
    val stableSeconds: Long,
    val changedSeconds: Long,
    val analyzedSeconds: Long,
    val averageScore: Int?,
    val brakePresses: Int,
    val breathingCount: Int,
    val destination: String?,
    val isDemo: Boolean,
    val interrupted: Boolean = false,
    val events: List<TripEvent> = emptyList(),
    val scores: List<Int> = emptyList(),
) {
    val coveragePercent: Int get() = if (drivingSeconds == 0L) 0 else ((analyzedSeconds * 100) / drivingSeconds).toInt().coerceIn(0, 100)
    val steadyPercent: Int? get() = if (analyzedSeconds == 0L) null else ((stableSeconds * 100) / analyzedSeconds).toInt().coerceIn(0, 100)
    val closingStory: String get() = when {
        interrupted -> "기록이 중단되기 전까지의 리듬을 남겨두었어요."
        averageScore == null -> "잘 도착하셨어요. 이번에는 리듬을 분석할 주행 구간이 충분하지 않았어요."
        changedSeconds > 0 -> "중간중간 리듬이 흔들렸지만, 잘 도착하셨어요. 오늘의 길도 수고하셨어요."
        else -> "자신만의 리듬을 이어오며 도착하셨어요. 오늘의 길도 수고하셨어요."
    }
}
