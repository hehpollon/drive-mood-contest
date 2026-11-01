package io.github.hehpollon.drivemood.data

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.hehpollon.drivemood.domain.*
import io.github.hehpollon.drivemood.navigation.*
import io.github.hehpollon.drivemood.vehicle.*
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DriveMoodState(
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val hardware: VehicleSnapshot = VehicleSnapshot(),
    val route: RouteSnapshot = RouteSnapshot(),
    val rhythm: RhythmReading = RhythmReading(),
    val lastDriving: RhythmReading? = null,
    val wave: List<WavePoint> = emptyList(),
    val story: MicroStory = MicroStory("당신의 길에, 조용히 함께", "오늘의 운전 리듬을 함께 읽어요.", "차량 연결 대기"),
    val recording: Boolean = false,
    val demo: Boolean = false,
    val demoScene: DemoScene = DemoScene.JOURNEY,
    val canInteract: Boolean = false,
    val foreground: Boolean = false,
    val moving: Boolean = false,
    val breathing: BreathingState = BreathingState(),
    val activeTrip: TripRecord? = null,
    val records: List<TripRecord> = emptyList(),
    val arrivalReport: TripRecord? = null,
    val voiceEnabled: Boolean = true,
    val voiceAvailable: Boolean = false,
    val notice: String? = null,
)

/** Serializes SDK consumption and session state on the main dispatcher. */
class DriveMoodRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vehicleSource = createVehicleDataSource(context)
    private val routeSource = createRouteDataSource(context)
    private val store = TripStore(context)
    private val voice = VoiceGuide(context)
    private val guide = BreathingGuide()
    private var engine = RhythmEngine()
    private var accumulator: TripAccumulator? = null
    private var replay: DemoReplay? = null
    private var visible = false
    private var connected = false
    private var checkpointAt = 0L
    private var arrivalBaseline = 0L
    private var pendingArrival = false
    private var lastDriving: RhythmReading? = null
    private var lastVoiceAt = -600_000L
    private var lastPhase = -1L
    private val state = MutableStateFlow(DriveMoodState())
    val ui = state.asStateFlow()

    init {
        store.recover()
        state.value = state.value.copy(records = store.records(), voiceEnabled = store.voiceEnabled())
        scope.launch { while (isActive) { if (connected) tick(); delay(200) } }
    }

    fun setVisible(value: Boolean) {
        visible = value
        state.value = state.value.copy(foreground = value)
        if (!value) { guide.cancel(); voice.stop(); checkpoint() }
        reconcileConnection()
        if (value && connected) tick()
    }

    private fun reconcileConnection() {
        val needed = visible || accumulator != null
        if (needed && !connected) {
            connected = true
            vehicleSource.start(); routeSource.start()
        } else if (!needed && connected) {
            connected = false
            vehicleSource.stop(); routeSource.stop()
        }
    }

    fun refreshConnection() {
        if (!state.value.canInteract && state.value.moving) return
        vehicleSource.stop(); routeSource.stop(); connected = false
        reconcileConnection()
        tick()
    }

    fun beginTrip(demo: Boolean): Boolean {
        if (accumulator != null) return true
        val now = clock()
        if (!vehicleSource.snapshot.value.isParked(now)) { notice("주차 상태가 확인되면 기록을 시작할 수 있어요."); return false }
        replay = if (demo) DemoReplay(now) else null
        engine = RhythmEngine(if (demo) 5 else 12)
        guide.cancel(); lastDriving = null; pendingArrival = false; lastPhase = -1
        arrivalBaseline = if (demo) 0 else routeSource.snapshot.value.arrivalSequence
        accumulator = TripAccumulator(UUID.randomUUID().toString(), System.currentTimeMillis(), now, demo || vehicleSource.snapshot.value.isDemo)
        checkpointAt = now
        state.value = state.value.copy(arrivalReport = null, notice = null)
        reconcileConnection(); tick(); checkpoint()
        return true
    }

    fun finishTrip(interrupted: Boolean = false, fromNotification: Boolean = false) {
        if (!interrupted && !fromNotification && !state.value.canInteract) { notice("주차 후 기록을 마칠 수 있어요."); return }
        val active = accumulator ?: return
        val record = active.snapshot(clock(), System.currentTimeMillis(), interrupted || (fromNotification && !vehicleSource.snapshot.value.isParked(clock())))
        store.save(record)
        accumulator = null; replay = null; pendingArrival = false
        guide.cancel(); voice.stop(); engine = RhythmEngine()
        state.value = state.value.copy(recording = false, demo = false, activeTrip = null, breathing = guide.state,
            records = store.records(), arrivalReport = record)
        context.stopService(Intent(context, TripService::class.java))
        if (connected) tick()
        reconcileConnection()
    }

    fun setDemoScene(scene: DemoScene) {
        if (!vehicleSource.snapshot.value.isParked(clock())) return
        replay?.scene = scene
        guide.cancel(); voice.stop(); engine = RhythmEngine(5)
        tick()
    }

    fun startBreathing() {
        val now = clock()
        if (guide.start(now, visible && state.value.canInteract && state.value.vehicle.isParked(now))) {
            state.value = state.value.copy(notice = null)
            lastPhase = -1; tick()
        }
        else notice("안전하게 주차한 뒤 숨을 고를 수 있어요.")
    }
    fun closeBreathing() { guide.cancel(); voice.stop(); tick() }
    fun dismissArrival() { state.value = state.value.copy(arrivalReport = null) }
    fun deleteRecord(id: String) { if (state.value.canInteract) { store.delete(id); state.value = state.value.copy(records = store.records()) } }
    fun setVoice(value: Boolean) {
        if (!state.value.canInteract) return
        updateVoice(value)
    }
    /** A single-step guidance mute control; never opens setup or starts a dialog. */
    fun toggleDrivingVoice() {
        if (!state.value.voiceAvailable) return
        updateVoice(!state.value.voiceEnabled)
    }
    private fun updateVoice(value: Boolean) {
        store.setVoice(value)
        if (!value) voice.stop()
        state.value = state.value.copy(voiceEnabled = value)
    }
    fun openNavigation() { if (state.value.canInteract && replay == null) routeSource.openNavigation() }
    fun refreshNavigation() { if (state.value.canInteract) routeSource.refresh() }
    fun notice(value: String) { state.value = state.value.copy(notice = value) }
    fun dismissNotice() { state.value = state.value.copy(notice = null) }

    private fun tick() {
        val now = clock()
        val hardware = vehicleSource.snapshot.value
        // Simulation cannot override the actual vehicle restrictions.
        if (replay != null && !hardware.isParked(now)) {
            notice("실제 차량의 주차 상태가 해제되어 시연 기록을 중단했어요.")
            finishTrip(interrupted = true); return
        }
        val vehicle = replay?.vehicle(now) ?: hardware
        val route = replay?.route(now) ?: routeSource.snapshot.value
        val canInteract = hardware.isParked(now)
        val moving = hardware.uxRestricted == true || hardware.gear in setOf(Gear.DRIVE, Gear.REVERSE) || (hardware.speedKph ?: 0f) >= .5f
        if (!vehicle.isParked(now) && guide.state.completed) guide.cancel()
        val reading = engine.observe(RhythmSample(now, vehicle.steeringAngleDegrees, vehicle.brakePressed,
            vehicle.gear, vehicle.speedKph,
            vehicle.turnSignalActive == true || (route.maneuverNearby && now - route.updatedAt in 0..20_000),
            vehicle.signalsFresh(now) && now - vehicle.gearUpdatedAt in 0..5_000))
        if (reading.score != null) lastDriving = reading
        val before = guide.state
        val breathing = guide.tick(now, canInteract && vehicle.isParked(now) && visible)
        if (!before.completed && breathing.completed) {
            accumulator?.completeBreathing(now)
            if (state.value.voiceEnabled) voice.speak("잠깐 숨을 고르셨어요. 다음 주행에서 리듬을 다시 살펴볼게요.")
            checkpoint()
        }
        if (breathing.active && state.value.voiceEnabled) {
            val phase = breathing.elapsedMillis / 3_000
            if (phase != lastPhase) { lastPhase = phase; voice.speak(if (breathing.inhale) "천천히 들이쉬고" else "편안하게 내쉬어요") }
        }
        if (breathing.interrupted && !before.interrupted) voice.stop()
        accumulator?.let { active ->
            active.observe(now, vehicle, reading)
            if (route.destination != null) active.destination = route.destination
            if (route.arrivalSequence > arrivalBaseline) pendingArrival = true
        }
        val trip = accumulator?.snapshot(now, System.currentTimeMillis())
        val story = Stories.create(reading, vehicle, route, vehicle.sdkDrivingMinutes ?: ((trip?.drivingSeconds ?: 0) / 60).toInt(),
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY), breathing)
        if (accumulator != null && reading.kind == RhythmKind.CHANGED && now - lastVoiceAt >= 600_000 && state.value.voiceEnabled && !breathing.active) {
            voice.speak(story.message); lastVoiceAt = now
        }
        state.value = state.value.copy(vehicle = vehicle, hardware = hardware, route = route, rhythm = reading,
            lastDriving = lastDriving, wave = engine.points, story = story, recording = accumulator != null,
            demo = replay != null, demoScene = replay?.scene ?: DemoScene.JOURNEY,
            canInteract = canInteract, moving = moving, breathing = breathing, activeTrip = trip, voiceAvailable = voice.available)
        if (accumulator != null && now - checkpointAt >= 15_000) { checkpointAt = now; checkpoint() }
        // Wait for parking as well as the native navigation arrival event.
        if (pendingArrival && canInteract && vehicle.isParked(now) && !breathing.active) finishTrip()
    }

    private fun checkpoint() { accumulator?.let { store.checkpoint(it.snapshot(clock(), System.currentTimeMillis())) } }
    private fun clock() = SystemClock.elapsedRealtime()
}
