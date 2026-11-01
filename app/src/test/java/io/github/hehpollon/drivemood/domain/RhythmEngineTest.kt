package io.github.hehpollon.drivemood.domain

import io.github.hehpollon.drivemood.vehicle.Gear
import org.junit.Assert.*
import org.junit.Test

class RhythmEngineTest {
    private fun sample(index: Int, angle: Float = 0f, brake: Boolean? = false, gear: Gear = Gear.DRIVE, speed: Float = 80f, fresh: Boolean = true, maneuver: Boolean = false) =
        RhythmSample(index * 1_000L, angle, brake, gear, speed, maneuver, fresh)

    @Test fun steadySignalsNeedWarmupAndProduceMeasuredScore() {
        val engine = RhythmEngine()
        repeat(11) { assertNull(engine.observe(sample(it, .1f)).score) }
        assertEquals(RhythmKind.STEADY, engine.observe(sample(11, .1f)).kind)
        assertEquals(100, engine.reading.score)
    }
    @Test fun changeRequiresPersistenceAndCannotRecoverFromBreathingOrOneQuietSample() {
        val engine = RhythmEngine(5)
        repeat(18) { engine.observe(sample(it, if (it % 2 == 0) 7f else -7f)) }
        assertEquals(RhythmKind.CHANGED, engine.reading.kind)
        assertEquals(RhythmKind.CHANGED, engine.observe(sample(18)).kind)
        for (i in 19..65) engine.observe(sample(i))
        assertEquals(RhythmKind.STEADY, engine.reading.kind)
        assertEquals(100, engine.reading.score)
    }
    @Test fun turnsLowSpeedAndParkingHaveNoScore() {
        listOf(sample(0, 25f), sample(0, maneuver = true), sample(0, speed = 5f)).forEach {
            val reading = RhythmEngine(2).observe(it)
            assertEquals(RhythmKind.MANEUVER, reading.kind)
            assertNull(reading.score)
        }
        assertEquals(RhythmKind.PARKED, RhythmEngine().observe(sample(0, gear = Gear.PARK)).kind)
    }
    @Test fun missingAndStaleSignalsProduceGapAndRestartWarmup() {
        val engine = RhythmEngine(3)
        repeat(3) { engine.observe(sample(it)) }
        assertNotNull(engine.reading.score)
        assertEquals(RhythmKind.NO_DATA, engine.observe(sample(3, fresh = false)).kind)
        assertNull(engine.points.last().angle)
        assertEquals(RhythmKind.LEARNING, engine.observe(sample(4)).kind)
        assertEquals(RhythmKind.NO_DATA, engine.observe(sample(5, brake = null)).kind)
    }
    @Test fun schedulingGapDoesNotJoinTwoUnrelatedDrivingWindows() {
        val engine = RhythmEngine(3)
        repeat(3) { engine.observe(sample(it)) }
        assertEquals(RhythmKind.LEARNING, engine.observe(sample(20)).kind)
        assertTrue(engine.points.any { it.angle == null })
    }
    @Test fun subSecondCallsDoNotSpeedUpWarmup() {
        val engine = RhythmEngine(3)
        repeat(20) { engine.observe(RhythmSample(it * 20L, 0f, false, Gear.DRIVE)) }
        assertNull(engine.reading.score)
        assertEquals(1, engine.points.size)
    }
}
