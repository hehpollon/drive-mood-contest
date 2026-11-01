package io.github.hehpollon.drivemood.domain

import org.junit.Assert.*
import org.junit.Test

class ParkedContentTest {
    private val now = 1_800_000_000_000L
    private fun trip(id: String = "real", endedAt: Long = now - 60_000) = TripRecord(
        id, endedAt - 1_800_000, endedAt, 1800, 1200, 480, 120, 600, 80, 4, 0, "집", false,
    )
    private fun content(records: List<TripRecord> = emptyList(), active: TripRecord? = null,
        recording: Boolean = active != null, reading: RhythmReading? = null, hour: Int = 9,
        at: Long = now) = ParkedContentFactory.create(recording, active, records, reading, at, hour)

    @Test fun latestRealJourneyWinsOverNewerDemoAndListOrder() {
        val newest = trip()
        val older = trip("older", now - 3_600_000)
        val demo = trip("demo", now).copy(isDemo = true)
        val result = content(listOf(older, demo, newest))
        assertEquals(newest, result.digest.record)
        assertTrue(result.title.contains("수고했어요"))
        assertTrue(result.digest.message.contains("80%")) // 480 / 600, not 480 / 1200.
    }

    @Test fun noRealJourneyNeverUsesDemoAsPersonalHistory() {
        val result = content(listOf(trip().copy(isDemo = true)))
        assertNull(result.digest.record)
        assertTrue(result.digest.message.contains("첫 주행"))
        assertFalse(result.title.contains("수고했어요"))
    }

    @Test fun currentSessionOverridesPreviousAndLabelsDemoExplicitly() {
        val active = trip("current").copy(isDemo = true, stableSeconds = 300)
        val result = content(listOf(trip()), active)
        assertEquals(active, result.digest.record)
        assertTrue(result.digest.current)
        assertTrue(result.digest.label.contains("시연"))
        assertTrue(result.kicker.contains("시연"))
        assertTrue(result.digest.message.contains("50%"))
        assertFalse(result.title.contains("수고했어요"))
    }

    @Test fun missingOrInvalidAnalysisNeverBecomesAPercentage() {
        val missing = trip().copy(analyzedSeconds = 0, stableSeconds = 0, averageScore = null)
        val invalid = trip().copy(stableSeconds = 900)
        for (record in listOf(missing, invalid)) {
            val result = content(listOf(record))
            assertFalse(result.digest.message.contains("%"))
            assertTrue(result.digest.message.contains("부족"))
        }
    }

    @Test fun justStartedRecordingDoesNotReuseOldRhythmOrPretendThereWasADrive() {
        val active = trip().copy(drivingSeconds = 0, analyzedSeconds = 0, stableSeconds = 0)
        val result = content(listOf(trip()), active, reading = RhythmReading(RhythmKind.CHANGED, 20))
        assertTrue(result.title.contains("출발"))
        assertTrue(result.digest.message.contains("출발하면"))
        assertFalse(result.digest.message.contains("%"))
    }

    @Test fun longDriveUsesObservedDrivingTimeInsteadOfParkedElapsedTime() {
        val waiting = trip().copy(elapsedSeconds = 9000, drivingSeconds = 1200)
        assertFalse(content(active = waiting).title.contains("긴 길"))
        assertTrue(content(active = waiting.copy(drivingSeconds = 3600)).title.contains("긴 길"))
        assertTrue(content(active = waiting, reading = RhythmReading(RhythmKind.CHANGED, 20)).title.contains("숨을 골라요"))
    }

    @Test fun interruptedRecordIsIdentifiedAndNeverReceivesArrivalGreeting() {
        val interrupted = trip().copy(interrupted = true)
        val result = content(listOf(interrupted))
        assertTrue(result.digest.label.contains("중단"))
        assertFalse(result.title.contains("수고했어요"))
        assertEquals(interrupted, result.digest.record)
    }

    @Test fun finishGreetingExpiresAndDoesNotAcceptFutureTimestamps() {
        val record = trip(endedAt = now)
        assertTrue(content(listOf(record), at = now + 1_800_000).title.contains("수고했어요"))
        assertFalse(content(listOf(record), at = now + 1_800_001).title.contains("수고했어요"))
        assertFalse(content(listOf(record), at = now - 1).title.contains("수고했어요"))
        assertTrue(content(listOf(record), at = now + 3_600_000).digest.message.contains("80%"))
    }

    @Test fun timeOfDayChangesDepartureGreetingAndBreathingNeverChangesMeasuredSummary() {
        assertTrue(content(hour = 8).title.contains("오늘도"))
        assertTrue(content(hour = 14).title.contains("함께해요"))
        assertTrue(content(hour = 23).title.contains("늦은 길"))
        val record = trip()
        assertEquals(content(listOf(record)).digest.message,
            content(listOf(record.copy(breathingCount = 5))).digest.message)
    }
}
