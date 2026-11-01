package io.github.hehpollon.drivemood.navigation

import android.content.Context
import android.os.SystemClock
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

fun createRouteDataSource(context: Context): RouteDataSource = PleosRouteSource(context.applicationContext)

private class PleosRouteSource(context: Context) : RouteDataSource {
    private val sdk = NaviHelper(context)
    private val state = MutableStateFlow(RouteSnapshot())
    override val snapshot = state.asStateFlow()
    private var scope: CoroutineScope? = null
    @Volatile private var generation = 0
    private var listener: NaviHelperEventListener? = null

    override fun start() {
        if (scope != null) return
        val session = ++generation
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val events = object : NaviHelperEventListener {
            private fun change(transform: (RouteSnapshot) -> RouteSnapshot) {
                state.update { current -> if (session != generation) current else transform(current).copy(connected = true, updatedAt = SystemClock.elapsedRealtime(), issue = null) }
            }
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) = change {
                it.copy(active = true, destination = routeStartInfo.name.takeIf { name -> name.isNotBlank() }, remainingMeters = routeStartInfo.distance, remainingSeconds = routeStartInfo.duration)
            }
            override fun onDestinationInfo(destinationInfo: DestinationInfo) = change {
                it.copy(destination = destinationInfo.name.takeIf { name -> name.isNotBlank() }, remainingMeters = destinationInfo.remainDistance, remainingSeconds = destinationInfo.remainTime)
            }
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = change { it.copy(active = routeStateInfo.isRouting) }
            override fun onNavigationStatus(status: NavigationStatus) = change { it.copy(active = status == NavigationStatus.ROUTE_DRIVE) }
            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = change {
                it.copy(active = false, destination = destinationArrivedInfo.name.takeIf { name -> name.isNotBlank() }, remainingMeters = 0, remainingSeconds = 0, arrivalSequence = it.arrivalSequence + 1, maneuverNearby = false)
            }
            override fun onRouteCancelled() = change { RouteSnapshot(connected = true, arrivalSequence = it.arrivalSequence) }
            override fun onDestinationChanged() { refresh() }
            override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
                val next = tbtInfoList.firstOrNull()
                change {
                    it.copy(nextInstruction = next?.description, nextTurnMeters = next?.distance,
                        maneuverNearby = next != null && next.distance in 0.0..200.0 && !next.type.name.contains("STRAIGHT"))
                }
            }
            override fun onError(errorCode: NaviErrorCode) {
                if (session == generation) state.update { it.copy(issue = "내비게이션 정보를 다시 확인해 주세요") }
            }
        }
        listener = events
        sdk.addListener(events)
        sdk.initialize(onConnected = {
            if (session == generation) {
                state.update { it.copy(connected = true, issue = null) }
                refresh()
            }
        }, onFailed = {
            if (session == generation) state.update { it.copy(connected = false, issue = "내비게이션 연결을 기다리고 있어요") }
        })
        scope?.launch { while (isActive) { if (state.value.connected) refresh(); delay(10_000) } }
    }

    override fun refresh() {
        if (scope == null) return
        runCatching { sdk.getRouteStateInfo(); sdk.getDestinationInfo(); sdk.getTBTInfo() }
            .onFailure { state.update { it.copy(issue = "내비게이션 정보를 기다리고 있어요") } }
    }

    override fun openNavigation() {
        runCatching { sdk.requestNavigationScreen(NavigationScreenRequest.Foreground) }
            .onFailure { state.update { it.copy(issue = "차량의 기본 내비게이션을 열어 목적지를 설정해 주세요") } }
    }

    override fun stop() {
        ++generation
        scope?.cancel()
        scope = null
        listener?.let { sdk.removeListener(it) }
        listener = null
        sdk.release()
    }
}
