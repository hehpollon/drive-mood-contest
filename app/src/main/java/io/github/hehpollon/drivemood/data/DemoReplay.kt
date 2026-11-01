package io.github.hehpollon.drivemood.data

import io.github.hehpollon.drivemood.navigation.RouteSnapshot
import io.github.hehpollon.drivemood.vehicle.Gear
import io.github.hehpollon.drivemood.vehicle.VehicleSnapshot
import kotlin.math.sin

enum class DemoScene(val label: String) { JOURNEY("전체 여정"), STEADY("안정 리듬"), CHANGED("리듬 변화"), TURN("회전 구간"), DISCONNECTED("연결 끊김"), PARKED("주차·숨 고르기") }

class DemoReplay(val startedAt: Long) {
    var scene = DemoScene.JOURNEY
    fun vehicle(now: Long): VehicleSnapshot {
        val seconds = (now - startedAt) / 1_000.0
        val stage = if (scene != DemoScene.JOURNEY) scene else when {
            seconds < 14 -> DemoScene.STEADY
            seconds < 34 -> DemoScene.CHANGED
            seconds < 80 -> DemoScene.PARKED
            seconds < 110 -> DemoScene.STEADY
            else -> DemoScene.PARKED
        }
        val moving = stage != DemoScene.PARKED
        val angle = when (stage) {
            DemoScene.CHANGED -> if (seconds.toInt() % 2 == 0) 7f else -7f
            DemoScene.TURN -> (sin(seconds / 4) * 22).toFloat()
            DemoScene.PARKED -> 0f
            else -> (sin(seconds / 3) * 0.35).toFloat()
        }
        return VehicleSnapshot(
            status = "시연 데이터", isDemo = true,
            steeringAngleDegrees = angle.takeUnless { stage == DemoScene.DISCONNECTED },
            steeringUpdatedAt = if (stage == DemoScene.DISCONNECTED) 0 else now,
            brakePressed = (stage == DemoScene.CHANGED && seconds.toInt() % 5 == 0).takeUnless { stage == DemoScene.DISCONNECTED },
            brakeUpdatedAt = if (stage == DemoScene.DISCONNECTED) 0 else now,
            gear = if (moving) Gear.DRIVE else Gear.PARK, gearUpdatedAt = now,
            speedKph = if (moving) 80f else 0f, sdkDrivingMinutes = 92,
            turnSignalActive = stage == DemoScene.TURN, cabinTemperatureC = 24f, targetTemperatureC = 23f,
            ambientBrightnessPercent = 80, ambientColorArgb = 0xFF9ECBE0.toInt(), passengers = 0, uxRestricted = false,
        )
    }

    fun route(now: Long): RouteSnapshot {
        val elapsed = (now - startedAt) / 1_000
        val arrived = scene == DemoScene.JOURNEY && elapsed >= 110
        return RouteSnapshot(
            connected = true, active = !arrived, destination = "부산역",
            remainingMeters = if (arrived) 0 else (42_000 - elapsed * 120).toInt().coerceAtLeast(1_000),
            remainingSeconds = if (arrived) 0 else (1_800 - elapsed * 10).toInt().coerceAtLeast(60),
            nextInstruction = if (scene == DemoScene.TURN) "앞에서 우회전" else "경로를 따라 이동",
            nextTurnMeters = if (scene == DemoScene.TURN) 100.0 else 5_000.0,
            maneuverNearby = scene == DemoScene.TURN, arrivalSequence = if (arrived) 1 else 0, updatedAt = now,
        )
    }
}
