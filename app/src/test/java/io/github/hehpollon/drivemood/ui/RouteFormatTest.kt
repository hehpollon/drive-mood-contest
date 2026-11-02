package io.github.hehpollon.drivemood.ui

import io.github.hehpollon.drivemood.navigation.RouteSnapshot
import org.junit.Assert.*
import org.junit.Test

class RouteFormatTest {
    @Test fun nearDistanceUsesMetersAndPartialMinutesRoundUp() {
        val route = RouteSnapshot(active = true, remainingMeters = 500, remainingSeconds = 61)
        assertEquals("500 m", route.distanceText())
        assertEquals(2L, route.remainingMinutes())
        assertEquals("41.2 km", route.copy(remainingMeters = 41_234).distanceText())
    }
    @Test fun negativeMissingOrInactiveValuesStayUnconfirmed() {
        listOf(RouteSnapshot(active = true, remainingMeters = -50, remainingSeconds = -120),
            RouteSnapshot(active = true), RouteSnapshot(active = false, remainingMeters = 900, remainingSeconds = 300)).forEach {
            assertNull(it.distanceText())
            assertNull(it.remainingMinutes())
        }
    }
}
