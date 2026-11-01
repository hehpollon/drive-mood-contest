package io.github.hehpollon.drivemood.domain

data class JourneyDigest(
    val label: String,
    val message: String,
    val record: TripRecord?,
    val current: Boolean,
    val steadyPercent: Int? = null,
) {
    val compactText: String get() = when {
        record == null -> message
        steadyPercent != null -> "$label · 분석 구간 중 $steadyPercent% 일정"
        else -> "$label · $message"
    }
}

data class ParkedContent(
    val title: String,
    val message: String,
    val kicker: String,
    val digest: JourneyDigest,
)

/** Parked-screen copy only. Never changes classification, scores, or parking restrictions. */
object ParkedContentFactory {
    // A UI greeting window, unrelated to fatigue or any vehicle safety threshold.
    private const val RECENT_FINISH_MILLIS = 30 * 60 * 1_000L

    fun create(
        recording: Boolean,
        activeTrip: TripRecord?,
        records: List<TripRecord>,
        lastDriving: RhythmReading?,
        nowMillis: Long,
        hour: Int,
    ): ParkedContent {
        val previous = records.filterNot { it.isDemo }.maxByOrNull { it.endedAt }
        val current = if (recording) activeTrip else null
        val digest = digest(current ?: previous, current != null)
        val kicker = when {
            recording && current?.isDemo == true -> "시연 중인 여정"
            recording -> "오늘의 여정 · 기록 중"
            else -> "오늘의 여정"
        }
        val (title, message) = when {
            recording && (current?.drivingSeconds ?: 0) >= 3_600 ->
                "긴 길,\n잠깐 쉬어가요" to "한 시간 이상 달렸어요.\n주차한 동안 짧게 쉬어가요."
            recording && (current?.analyzedSeconds ?: 0) > 0 && lastDriving?.kind == RhythmKind.CHANGED ->
                "잠깐,\n숨을 골라요" to "직전 주행에서 조향 변화가 이어졌어요.\n주차한 동안 쉬어가도 좋아요."
            recording && (current?.drivingSeconds ?: 0) > 0 ->
                "여기서 잠깐,\n나의 속도로" to "다음 길을 이어가기 전,\n짧은 여유를 가져보세요."
            recording ->
                "출발도,\n나의 속도로" to "기록을 시작했어요.\n출발하면 리듬을 함께 살펴볼게요."
            previous != null && !previous.interrupted && previous.drivingSeconds > 0 &&
                nowMillis - previous.endedAt in 0..RECENT_FINISH_MILLIS ->
                "이번 여정도,\n수고했어요" to "방금 남긴 여정의 리듬을\n함께 살펴보세요."
            hour >= 20 || hour < 6 ->
                "늦은 길도,\n나의 속도로" to "오늘의 길에도 조용히 함께할게요."
            hour < 11 ->
                "오늘도,\n나의 속도로" to "조향과 브레이크의 흐름을\n함께 살펴볼게요."
            else ->
                "오늘의 길을,\n함께해요" to "조향과 브레이크의 흐름을\n함께 살펴볼게요."
        }
        return ParkedContent(title, message, kicker, digest)
    }

    private fun digest(record: TripRecord?, current: Boolean): JourneyDigest {
        if (record == null) return JourneyDigest("나의 첫 여정", "첫 주행의 리듬을 기다리고 있어요", null, false)
        val label = when {
            current && record.isDemo -> "이번 시연 여정"
            current -> "이번 여정"
            record.interrupted -> "직전 여정 · 중단 기록"
            else -> "직전 여정"
        }
        val observed = record.drivingSeconds > 0 && record.analyzedSeconds in 1..record.drivingSeconds &&
            record.stableSeconds in 0..record.analyzedSeconds
        val message = when {
            observed -> "분석 구간의 ${record.steadyPercent}%가 일정한 리듬이었어요"
            current && record.drivingSeconds == 0L -> "출발하면 리듬을 살펴볼게요"
            else -> "리듬을 읽을 구간이 부족했어요"
        }
        return JourneyDigest(label, message, record, current, record.steadyPercent.takeIf { observed })
    }
}
