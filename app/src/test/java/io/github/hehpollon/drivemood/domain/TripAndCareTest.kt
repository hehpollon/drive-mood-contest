package io.github.hehpollon.drivemood.domain

import io.github.hehpollon.drivemood.navigation.RouteSnapshot
import io.github.hehpollon.drivemood.vehicle.*
import org.junit.Assert.*
import org.junit.Test

class TripAndCareTest {
    @Test fun breathingHasFiveCompleteCyclesAndRequiresParking() {
        val guide = BreathingGuide()
        assertFalse(guide.start(0, parked = false))
        assertTrue(guide.start(0, parked = true))
        assertTrue(guide.tick(2_999, true).inhale)
        assertFalse(guide.tick(3_000, true).inhale)
        assertTrue(guide.tick(29_999, true).active)
        val completed = guide.tick(30_000, true)
        assertTrue(completed.completed)
        assertFalse(completed.active)
        assertEquals(0, completed.remainingSeconds)
        assertEquals(5, completed.cycle)
    }
    @Test fun movementInterruptsAndNeverCountsAsCompletedBreathing() {
        val guide = BreathingGuide()
        guide.start(1_000, true)
        val state = guide.tick(8_000, false)
        assertTrue(state.interrupted)
        assertFalse(state.completed)
        assertFalse(guide.tick(40_000, true).completed)
    }
    private fun vehicle(now: Long, brake: Boolean = false, fresh: Boolean = true, gear: Gear = Gear.DRIVE) = VehicleSnapshot(
        steeringAngleDegrees = 1f, brakePressed = brake, gear = gear, gearUpdatedAt = now, uxRestricted = false,
        steeringUpdatedAt = if (fresh) now else -100_000, brakeUpdatedAt = if (fresh) now else -100_000)
    @Test fun missingDataDoesNotTurnIntoHealthyScoreOrAnalyzedTime() {
        val trip = TripAccumulator("test", 1_000, 0, false)
        trip.observe(1_000, vehicle(1_000, fresh = false), RhythmReading(RhythmKind.NO_DATA))
        val record = trip.snapshot(1_000, 2_000)
        assertEquals(1L, record.drivingSeconds)
        assertEquals(0L, record.analyzedSeconds)
        assertNull(record.averageScore)
        assertNull(record.steadyPercent)
    }
    @Test fun reportCountsBrakeEdgesAndOnlyAnalyzableMovingTime() {
        val trip = TripAccumulator("test", 1_000, 0, false)
        val steady = RhythmReading(RhythmKind.STEADY, score = 90)
        trip.observe(1_000, vehicle(1_000), steady)
        trip.observe(2_000, vehicle(2_000, brake = true), steady)
        trip.observe(3_000, vehicle(3_000, brake = true), steady)
        trip.observe(4_000, vehicle(4_000, gear = Gear.PARK), RhythmReading(RhythmKind.PARKED))
        trip.completeBreathing(34_000)
        val record = trip.snapshot(34_000, 35_000)
        assertEquals(1, record.brakePresses)
        assertEquals(3L, record.drivingSeconds)
        assertEquals(3L, record.analyzedSeconds)
        assertEquals(100, record.coveragePercent)
        assertEquals(90, record.averageScore)
        assertEquals(1, record.breathingCount)
    }
    @Test fun longUnobservedGapIsNotAllCountedAsAnalyzed() {
        val trip = TripAccumulator("test", 0, 0, false)
        trip.observe(20_000, vehicle(20_000), RhythmReading(RhythmKind.STEADY, 100))
        val record = trip.snapshot(20_000, 20_000)
        assertEquals(2L, record.analyzedSeconds)
        assertEquals(10, record.coveragePercent)
    }
    @Test fun parkingRequiresFreshGearAndRespectsActualUxRestriction() {
        assertTrue(vehicle(1_000, gear = Gear.PARK).isParked(2_000))
        assertFalse(vehicle(1_000, gear = Gear.PARK).isParked(8_000))
        assertFalse(vehicle(1_000, gear = Gear.PARK).copy(uxRestricted = true).isParked(2_000))
    }
    @Test fun storyPriorityUsesMeasuredChangeBeforeRouteOrTimeOfDay() {
        val story = Stories.create(RhythmReading(RhythmKind.CHANGED, 30), vehicle(1_000),
            RouteSnapshot(active = true, remainingSeconds = 200), 92, 23, BreathingState())
        assertTrue(story.message.contains("긴 시간"))
        assertTrue(story.evidence.contains("92분"))
        val missing = Stories.create(RhythmReading(RhythmKind.NO_DATA), vehicle(1_000), RouteSnapshot(), 0, 23, BreathingState())
        assertTrue(missing.message.contains("연결"))
    }
}
