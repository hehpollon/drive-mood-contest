package io.github.hehpollon.drivemood.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

fun createVehicleDataSource(context: Context): VehicleDataSource = PleosVehicleSource(context.applicationContext)

private class PleosVehicleSource(private val context: Context) : VehicleDataSource {
    private val state = MutableStateFlow(VehicleSnapshot())
    override val snapshot = state.asStateFlow()
    @Volatile private var generation = 0
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var scope: CoroutineScope? = null
    private val cleanups = CopyOnWriteArrayList<() -> Unit>()
    private val unsupported = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var steeringReady = false
    @Volatile private var brakeReady = false

    override fun start() {
        if (vehicle != null) return
        val session = ++generation
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val sdk = Vehicle(context)
        vehicle = sdk
        unsupported.clear()
        state.value = VehicleSnapshot()
        runCatching {
            sdk.initialize()
            connectMotion(sdk, session)
            connectContext(sdk, session)
        }.onFailure { fail(session, "connection", it) }
        connectPlatform(session)
        scope?.launch {
            var seconds = 0
            while (isActive) {
                if (steeringReady) sdk.getSteeringWheel().getCurrentSteeringWheelAngle(
                    { value -> update(session, "steering") { it.copy(steeringAngleDegrees = value, steeringUpdatedAt = now()) } },
                    { fail(session, "steering", it) },
                )
                if (brakeReady) sdk.getBrake().getCurrentBrakePedalStatus(
                    { value -> update(session, "brake") { it.copy(brakePressed = value, brakeUpdatedAt = now()) } },
                    { fail(session, "brake", it) },
                )
                readGearFallback(sdk, session)
                readPlatformGear(session)
                if (seconds % 60 == 0 && "time" !in unsupported) sdk.getOdometer().getTripCurrentDrivingTime(
                    { value -> update(session, "time") { it.copy(sdkDrivingMinutes = value) } },
                    { fail(session, "time", it) },
                )
                seconds++
                delay(1_000)
            }
        }
    }

    private fun connectMotion(sdk: Vehicle, session: Int) {
        val steering = sdk.getSteeringWheel()
        steering.checkSteeringWheelAngleCapabilities({ capability, _ ->
            if (session != generation) return@checkSteeringWheelAngleCapabilities
            if (!capability.accessType.readable()) { fail(session, "steering", UnsupportedOperationException()); return@checkSteeringWheelAngleCapabilities }
            steeringReady = true
            val listener = object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) = update(session, "steering") {
                    it.copy(steeringAngleDegrees = angle, steeringUpdatedAt = now())
                }
                override fun onFailed(e: Exception) = fail(session, "steering", e)
            }
            steering.registerSteeringWheelAngle(listener)
            cleanups += { steering.unregisterSteeringWheelAngle(listener) }
        }, { fail(session, "steering", it) })
        val brake = sdk.getBrake()
        brake.checkBrakePedalStatusCapabilities({ capability ->
            if (session != generation) return@checkBrakePedalStatusCapabilities
            if (!capability.accessType.readable()) { fail(session, "brake", UnsupportedOperationException()); return@checkBrakePedalStatusCapabilities }
            brakeReady = true
            val listener = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(status: Boolean?) = update(session, "brake") {
                    it.copy(brakePressed = status, brakeUpdatedAt = now())
                }
                override fun onFailed(e: Exception) = fail(session, "brake", e)
            }
            brake.registerBrakePedalStatus(listener)
            cleanups += { brake.unregisterBrakePedalStatus(listener) }
        }, { fail(session, "brake", it) })
        val turn = sdk.getTurnSignal()
        turn.checkTurnSignalStateCapabilities({ config ->
            if (session != generation || !config.accessType.readable()) return@checkTurnSignalStateCapabilities
            val listener = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = update(session, "turn") {
                    it.copy(turnSignalActive = state?.let { value -> value != TurnSignalValue.OFF })
                }
                override fun onFailed(e: Exception) = fail(session, "turn", e)
            }
            turn.registerTurnSignalState(listener)
            turn.getCurrentTurnSignalState(listener::onTurnSignalStateUpdated, listener::onFailed)
            cleanups += { turn.unregisterTurnSignalState(listener) }
        }, { fail(session, "turn", it) })
    }

    @Suppress("DEPRECATION")
    private fun readGearFallback(sdk: Vehicle, session: Int) {
        sdk.getGear().getCurrentGearSelectionState({ value ->
            val gear = when (value?.name) {
                "GEAR_PARK" -> Gear.PARK
                "GEAR_DRIVE", "GEAR_1", "GEAR_2", "GEAR_3", "GEAR_4", "GEAR_5", "GEAR_6", "GEAR_7", "GEAR_8", "GEAR_9" -> Gear.DRIVE
                "GEAR_REVERSE" -> Gear.REVERSE
                "GEAR_NEUTRAL" -> Gear.NEUTRAL
                else -> Gear.UNKNOWN
            }
            if (gear != Gear.UNKNOWN) update(session, "gear") { it.copy(gear = gear, gearUpdatedAt = now()) }
        }, { if (state.value.gear == Gear.UNKNOWN) fail(session, "gear", it) })
    }

    // GEAR_SELECTION is on-change only; polling keeps the 5-second freshness check independent of the deprecated SDK getter.
    private fun readPlatformGear(session: Int) {
        val manager = properties ?: return
        runCatching { manager.getProperty<Any>(VehiclePropertyIds.GEAR_SELECTION, 0) }.onSuccess { onPlatformGear(session, it) }
    }

    private fun onPlatformGear(session: Int, value: CarPropertyValue<*>) {
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
        val gear = when (value.value as? Int) {
            android.car.VehicleGear.GEAR_PARK -> Gear.PARK
            android.car.VehicleGear.GEAR_DRIVE -> Gear.DRIVE
            android.car.VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            android.car.VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            else -> Gear.UNKNOWN
        }
        update(session, "gear") { it.copy(gear = gear, gearUpdatedAt = now()) }
    }

    private fun connectPlatform(session: Int) {
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { readyCar, ready ->
                if (session != generation) return@createCar
                if (!ready) {
                    properties = null
                    update(session, "ux") { it.copy(uxRestricted = true, gear = Gear.UNKNOWN, gearUpdatedAt = 0) }
                    return@createCar
                }
                runCatching {
                    val manager = readyCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    restrictions = manager
                    update(session, "ux") { it.copy(uxRestricted = manager.currentCarUxRestrictions.isRequiresDistractionOptimization) }
                    manager.registerListener { value -> update(session, "ux") { it.copy(uxRestricted = value.isRequiresDistractionOptimization) } }
                }.onFailure { fail(session, "ux", it) }
                runCatching {
                    val manager = readyCar.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    properties = manager
                    val callback = object : CarPropertyManager.CarPropertyEventCallback {
                        override fun onChangeEvent(value: CarPropertyValue<*>) {
                            if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
                            when (value.propertyId) {
                                VehiclePropertyIds.PERF_VEHICLE_SPEED -> update(session, "speed") { it.copy(speedKph = (value.value as? Float)?.let { speed -> kotlin.math.abs(speed * 3.6f) }) }
                                VehiclePropertyIds.GEAR_SELECTION -> onPlatformGear(session, value)
                            }
                        }
                        override fun onErrorEvent(propertyId: Int, areaId: Int) {
                            if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) update(session, "speed") { it.copy(speedKph = null) }
                        }
                    }
                    for (property in listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.PERF_VEHICLE_SPEED)) {
                        runCatching {
                            manager.registerCallback(callback, property, CarPropertyManager.SENSOR_RATE_NORMAL)
                            callback.onChangeEvent(manager.getProperty<Any>(property, 0))
                        }.onFailure { fail(session, if (property == VehiclePropertyIds.GEAR_SELECTION) "gear" else "speed", it) }
                    }
                    cleanups += { manager.unregisterCallback(callback) }
                }.onFailure { fail(session, "speed", it) }
            }
        } catch (error: Exception) { fail(session, "ux", error) }
    }

    private fun connectContext(sdk: Vehicle, session: Int) {
        val hvac = sdk.getHVAC()
        hvac.checkHvacTemperatureCurrentCapabilities({ config, areas ->
            if (session != generation || !config.accessType.readable()) return@checkHvacTemperatureCurrentCapabilities
            val area = areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.firstOrNull() ?: return@checkHvacTemperatureCurrentCapabilities
            scope?.launch {
                while (isActive) {
                    hvac.getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS,
                        { value -> update(session, "climate") { it.copy(cabinTemperatureC = value) } }, { fail(session, "climate", it) })
                    delay(10_000)
                }
            }
        }, { fail(session, "climate", it) })
        hvac.checkHvacTemperatureCapabilities({ config, areas, _ ->
            if (session != generation || !config.accessType.readable()) return@checkHvacTemperatureCapabilities
            val area = areas.keys.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.keys.firstOrNull() ?: return@checkHvacTemperatureCapabilities
            scope?.launch {
                while (isActive) {
                    hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS,
                        { value -> update(session, "climateSet") { it.copy(targetTemperatureC = value) } }, { fail(session, "climateSet", it) })
                    delay(10_000)
                }
            }
        }, { fail(session, "climateSet", it) })
        val light = sdk.getLight()
        light.checkAmbientLightBrightnessCapabilities({ config, areas ->
            if (session != generation || !config.accessType.readable()) return@checkAmbientLightBrightnessCapabilities
            val area = areas.keys.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.keys.firstOrNull() ?: return@checkAmbientLightBrightnessCapabilities
            val bounds = areas[area]
            scope?.launch {
                while (isActive) {
                    light.getAmbientLightBrightness(area, { value ->
                        val minimum = bounds?.minValue ?: 0
                        val maximum = bounds?.maxValue ?: 100
                        val percent = value?.let { brightness -> if (maximum > minimum) ((brightness - minimum) * 100 / (maximum - minimum)).coerceIn(0, 100) else null }
                        update(session, "light") { it.copy(ambientBrightnessPercent = percent) }
                    }, { fail(session, "light", it) })
                    light.getAmbientLightBrightnessColor(area,
                        { value -> update(session, "color") { it.copy(ambientColorArgb = value?.or(0xFF000000.toInt())) } }, { fail(session, "color", it) })
                    delay(10_000)
                }
            }
        }, { fail(session, "light", it) })
        val seat = sdk.getSeat()
        seat.checkSeatOccupancyCapabilities({ config, areas ->
            if (session != generation || !config.accessType.readable()) return@checkSeatOccupancyCapabilities
            val passengerAreas = areas.filter { SeatValue.ROW_1_LEFT !in it.values }
            if (passengerAreas.isEmpty()) return@checkSeatOccupancyCapabilities
            scope?.launch {
                while (isActive) {
                    val occupancy = java.util.concurrent.ConcurrentHashMap<SeatArea, Boolean>()
                    passengerAreas.forEach { area ->
                        seat.getCurrentSeatOccupancy(area, { occupied ->
                            if (occupied != null) occupancy[area] = occupied
                            if (occupancy.size == passengerAreas.size) update(session, "seats") { it.copy(passengers = occupancy.values.count { value -> value }) }
                        }, { fail(session, "seats", it) })
                    }
                    delay(15_000)
                }
            }
        }, { fail(session, "seats", it) })
    }

    private fun update(session: Int, key: String, transform: (VehicleSnapshot) -> VehicleSnapshot) {
        state.update { current -> if (session != generation) current else transform(current).copy(status = "차량 연결", issues = current.issues - key) }
    }

    private fun fail(session: Int, key: String, error: Throwable) {
        if (error.message == "ERROR_NOT_SUPPORTED_PROPERTY" || error is UnsupportedOperationException) unsupported += key
        val label = when (key) {
            "time" -> "차량 운전 시간"
            "climate", "climateSet" -> "공조 정보"
            "light", "color" -> "앰비언트 정보"
            "seats" -> "좌석 점유"
            "turn" -> "방향지시등"
            "speed" -> "차량 속도"
            "gear" -> "기어 상태"
            "ux" -> "주행 제한 정보"
            else -> "차량 신호"
        }
        state.update { current ->
            if (session != generation) current else current.copy(
                steeringUpdatedAt = if (key == "steering") 0 else current.steeringUpdatedAt,
                steeringAngleDegrees = if (key == "steering") null else current.steeringAngleDegrees,
                brakeUpdatedAt = if (key == "brake") 0 else current.brakeUpdatedAt,
                brakePressed = if (key == "brake") null else current.brakePressed,
                cabinTemperatureC = if (key == "climate") null else current.cabinTemperatureC,
                targetTemperatureC = if (key == "climateSet") null else current.targetTemperatureC,
                ambientBrightnessPercent = if (key == "light") null else current.ambientBrightnessPercent,
                ambientColorArgb = if (key == "color") null else current.ambientColorArgb,
                passengers = if (key == "seats") null else current.passengers,
                turnSignalActive = if (key == "turn") null else current.turnSignalActive,
                uxRestricted = if (key == "ux") true else current.uxRestricted,
                issues = current.issues + (key to if (key in unsupported) "$label · 현재 환경에서 미제공" else "$label · 연결 또는 조회 권한 확인 필요"),
            )
        }
    }

    override fun stop() {
        ++generation
        scope?.cancel()
        scope = null
        cleanups.forEach { runCatching(it) }
        cleanups.clear()
        runCatching { restrictions?.unregisterListener() }
        runCatching { car?.disconnect() }
        runCatching { vehicle?.release() }
        car = null
        vehicle = null
        properties = null
        restrictions = null
        steeringReady = false
        brakeReady = false
        state.value = VehicleSnapshot(status = "차량 연결 대기")
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun AccessType.readable() = this == AccessType.READ || this == AccessType.READ_WRITE
}
