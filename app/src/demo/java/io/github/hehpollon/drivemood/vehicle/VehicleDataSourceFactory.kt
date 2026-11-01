package io.github.hehpollon.drivemood.vehicle

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

fun createVehicleDataSource(context: Context): VehicleDataSource = DemoVehicleDataSource()

private class DemoVehicleDataSource : VehicleDataSource {
    private val state = MutableStateFlow(VehicleSnapshot(status = "샘플 차량", isDemo = true))
    private var scope: CoroutineScope? = null
    override val snapshot = state.asStateFlow()
    override fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope?.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                state.value = VehicleSnapshot(
                    status = "샘플 차량", isDemo = true, steeringAngleDegrees = 0f,
                    steeringUpdatedAt = now, brakePressed = false, brakeUpdatedAt = now,
                    gear = Gear.PARK, gearUpdatedAt = now, speedKph = 0f,
                    cabinTemperatureC = 23f, targetTemperatureC = 22f,
                    ambientBrightnessPercent = 70, ambientColorArgb = 0xFFB9D8EF.toInt(),
                    passengers = 0, uxRestricted = false,
                )
                delay(1_000)
            }
        }
    }
    override fun stop() { scope?.cancel(); scope = null }
}
