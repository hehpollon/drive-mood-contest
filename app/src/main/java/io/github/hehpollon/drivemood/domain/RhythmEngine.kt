package io.github.hehpollon.drivemood.domain

import io.github.hehpollon.drivemood.vehicle.Gear
import kotlin.math.abs
import kotlin.math.roundToInt

enum class RhythmKind(val label: String) {
    LEARNING("리듬을 알아가는 중"), STEADY("일정한 리듬"), MIXED("변화가 있는 리듬"),
    CHANGED("변화가 커진 리듬"), MANEUVER("회전 구간"), NO_DATA("데이터 확인 중"), PARKED("잠시 쉬는 중"),
}

data class RhythmSample(
    val at: Long,
    val angle: Float?,
    val brake: Boolean?,
    val gear: Gear,
    val speedKph: Float? = null,
    val maneuver: Boolean = false,
    val fresh: Boolean = true,
)

data class WavePoint(val at: Long, val angle: Float?, val braking: Boolean)

data class RhythmReading(
    val kind: RhythmKind = RhythmKind.LEARNING,
    val score: Int? = null,
    val samples: Int = 0,
    val meanChange: Float = 0f,
    val reversals: Int = 0,
    val brakePresses: Int = 0,
    val reason: String = "움직임이 쌓이면 오늘의 리듬을 보여드려요.",
)

/** Transparent prototype heuristic. Describes signal variation, not fatigue or safety. */
class RhythmEngine(private val warmupSamples: Int = 12) {
    private val window = ArrayDeque<RhythmSample>()
    private val wave = ArrayDeque<WavePoint>()
    private var lastAt = Long.MIN_VALUE
    private var highChangeStreak = 0
    private var recoveryStreak = 0
    private var changed = false
    var reading = RhythmReading()
        private set
    val points: List<WavePoint> get() = wave.toList()

    fun observe(sample: RhythmSample): RhythmReading {
        if (lastAt != Long.MIN_VALUE && sample.at - lastAt < 1_000) return reading
        if (lastAt != Long.MIN_VALUE && sample.at - lastAt > 5_000) {
            window.clear()
            highChangeStreak = 0
            recoveryStreak = 0
            changed = false
            wave.addLast(WavePoint(sample.at - 1, null, false))
        }
        lastAt = sample.at
        wave.addLast(WavePoint(sample.at, sample.angle.takeIf { sample.fresh }, sample.brake == true))
        while (wave.size > 90) wave.removeFirst()
        val excluded = when {
            !sample.fresh || sample.angle?.isFinite() != true || sample.brake == null ->
                RhythmReading(RhythmKind.NO_DATA, reason = "새 차량 데이터를 기다리고 있어요.")
            sample.gear == Gear.PARK -> RhythmReading(RhythmKind.PARKED, reason = "주차 상태에서는 주행 리듬을 판정하지 않아요.")
            sample.gear !in setOf(Gear.DRIVE, Gear.REVERSE) ->
                RhythmReading(RhythmKind.NO_DATA, reason = "주행 상태가 확인되면 리듬을 살펴볼게요.")
            sample.maneuver || abs(sample.angle) > 18f ->
                RhythmReading(RhythmKind.MANEUVER, reason = "회전과 방향 전환 구간은 분석에서 잠시 제외해요.")
            sample.speedKph != null && sample.speedKph < 10f ->
                RhythmReading(RhythmKind.MANEUVER, reason = "저속·정체 구간은 리듬 점수에 반영하지 않아요.")
            else -> null
        }
        if (excluded != null) {
            window.clear()
            highChangeStreak = 0
            recoveryStreak = 0
            changed = false
            reading = excluded
            return reading
        }
        window.addLast(sample)
        while (window.isNotEmpty() && sample.at - window.first().at > 30_000) window.removeFirst()
        if (window.size < warmupSamples) {
            reading = RhythmReading(samples = window.size, reason = "비슷한 주행 조건의 움직임을 모으고 있어요.")
            return reading
        }
        val values = window.toList()
        val deltas = values.zipWithNext { a, b -> b.angle!! - a.angle!! }
        val mean = deltas.map { abs(it) }.average().toFloat()
        val meaningful = deltas.filter { abs(it) >= 1.2f }
        val reversals = meaningful.zipWithNext().count { (a, b) -> a * b < 0f }
        val presses = values.zipWithNext().count { (a, b) -> a.brake == false && b.brake == true }
        val score = (100 - mean * 6f - reversals * 2.5f - presses * 2f).roundToInt().coerceIn(0, 100)
        highChangeStreak = if (score < 55) highChangeStreak + 1 else 0
        recoveryStreak = if (score >= 72) recoveryStreak + 1 else 0
        if (highChangeStreak >= 3) changed = true
        if (recoveryStreak >= 6) changed = false
        val kind = when {
            changed -> RhythmKind.CHANGED
            score < 72 -> RhythmKind.MIXED
            else -> RhythmKind.STEADY
        }
        reading = RhythmReading(
            kind, score, values.size, mean, reversals, presses,
            when (kind) {
                RhythmKind.CHANGED -> "최근 30초 동안 조향 보정과 리듬 변화가 이어졌어요."
                RhythmKind.MIXED -> "조향과 브레이크 움직임에 변화가 있어요."
                else -> "최근 움직임이 일정하게 이어지고 있어요."
            },
        )
        return reading
    }
}
