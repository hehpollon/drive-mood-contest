package io.github.hehpollon.drivemood.navigation

import kotlinx.coroutines.flow.StateFlow

data class RouteSnapshot(
    val connected: Boolean = false,
    val active: Boolean = false,
    val destination: String? = null,
    val remainingMeters: Int? = null,
    val remainingSeconds: Int? = null,
    val nextInstruction: String? = null,
    val nextTurnMeters: Double? = null,
    val maneuverNearby: Boolean = false,
    val arrivalSequence: Long = 0,
    val updatedAt: Long = 0,
    val issue: String? = null,
)

interface RouteDataSource {
    val snapshot: StateFlow<RouteSnapshot>
    fun start()
    fun refresh()
    fun openNavigation()
    fun stop()
}
