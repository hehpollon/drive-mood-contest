package io.github.hehpollon.drivemood.vehicle

import kotlinx.coroutines.flow.StateFlow

enum class Gear(val label: String) { PARK("P"), DRIVE("D"), REVERSE("R"), NEUTRAL("N"), UNKNOWN("—") }

data class VehicleSnapshot(
    val status: String = "차량에 연결하고 있어요",
    val isDemo: Boolean = false,
    val steeringAngleDegrees: Float? = null,
    val steeringUpdatedAt: Long = 0L,
    val brakePressed: Boolean? = null,
    val brakeUpdatedAt: Long = 0L,
    val gear: Gear = Gear.UNKNOWN,
    val gearUpdatedAt: Long = 0L,
    val speedKph: Float? = null,
    val sdkDrivingMinutes: Int? = null,
    val turnSignalActive: Boolean? = null,
    val cabinTemperatureC: Float? = null,
    val targetTemperatureC: Float? = null,
    val ambientBrightnessPercent: Int? = null,
    val ambientColorArgb: Int? = null,
    val passengers: Int? = null,
    val uxRestricted: Boolean? = null,
    val issues: Map<String, String> = emptyMap(),
) {
    fun isParked(now: Long): Boolean = gear == Gear.PARK && now - gearUpdatedAt in 0..5_000 &&
        (speedKph == null || speedKph < 0.5f) && uxRestricted == false
    fun signalsFresh(now: Long): Boolean = steeringAngleDegrees?.isFinite() == true && brakePressed != null &&
        now - steeringUpdatedAt in 0..5_000 && now - brakeUpdatedAt in 0..5_000
}

interface VehicleDataSource {
    val snapshot: StateFlow<VehicleSnapshot>
    fun start()
    fun stop()
}
