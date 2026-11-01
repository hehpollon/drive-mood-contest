package io.github.hehpollon.drivemood.domain

import io.github.hehpollon.drivemood.vehicle.Gear
import io.github.hehpollon.drivemood.vehicle.VehicleSnapshot
import kotlin.math.roundToInt

class TripAccumulator(
    val id: String,
    val startedAt: Long,
    val startedMonotonic: Long,
    val isDemo: Boolean,
) {
    private var lastTick = startedMonotonic
    private var drivingMillis = 0L
    private var stableMillis = 0L
    private var changedMillis = 0L
    private var analyzedMillis = 0L
    private var weightedScores = 0L
    private var brakePresses = 0
    private var lastBrake: Boolean? = null
    private var lastKind: RhythmKind? = null
    private var lastScoreAt = startedMonotonic
    private var scoreInterval = 5_000L
    private val scores = mutableListOf<Int>()
    private val events = mutableListOf<TripEvent>()
    var breathingCount = 0
        private set
    var destination: String? = null

    fun observe(now: Long, vehicle: VehicleSnapshot, reading: RhythmReading) {
        val delta = (now - lastTick).coerceAtLeast(0)
        lastTick = now
        val moving = vehicle.gear in setOf(Gear.DRIVE, Gear.REVERSE) && now - vehicle.gearUpdatedAt in 0..5_000
        if (!moving) { lastBrake = null; return }
        drivingMillis += delta
        val accounted = delta.coerceAtMost(2_000)
        if (vehicle.signalsFresh(now) && reading.score != null) {
            analyzedMillis += accounted
            weightedScores += reading.score * accounted
            if (reading.kind == RhythmKind.STEADY) stableMillis += accounted
            if (reading.kind in setOf(RhythmKind.CHANGED, RhythmKind.MIXED)) changedMillis += accounted
            if (reading.kind != lastKind) {
                addEvent(now, "rhythm", reading.kind.label)
                lastKind = reading.kind
            }
            if (now - lastScoreAt >= scoreInterval) {
                scores += reading.score
                lastScoreAt = now
                if (scores.size >= 120) {
                    val compacted = scores.chunked(2).map { it.average().roundToInt() }
                    scores.clear(); scores.addAll(compacted); scoreInterval *= 2
                }
            }
        }
        if (vehicle.signalsFresh(now)) {
            if (lastBrake == false && vehicle.brakePressed == true) brakePresses++
            lastBrake = vehicle.brakePressed
        } else lastBrake = null
    }

    fun completeBreathing(now: Long) {
        breathingCount++
        addEvent(now, "breathing", "30초 숨 고르기 완료")
    }

    private fun addEvent(now: Long, kind: String, message: String) {
        events += TripEvent(((now - startedMonotonic) / 1_000).coerceAtLeast(0), kind, message)
        if (events.size > 30) events.removeAt(0)
    }

    fun snapshot(now: Long, epoch: Long, interrupted: Boolean = false): TripRecord = TripRecord(
        id, startedAt, epoch, ((now - startedMonotonic) / 1_000).coerceAtLeast(0),
        drivingMillis / 1_000, stableMillis / 1_000, changedMillis / 1_000, analyzedMillis / 1_000,
        if (analyzedMillis == 0L) null else (weightedScores.toDouble() / analyzedMillis).roundToInt(),
        brakePresses, breathingCount, destination, isDemo, interrupted, events.toList(), scores.toList(),
    )
}
