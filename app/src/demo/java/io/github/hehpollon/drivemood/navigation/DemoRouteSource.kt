package io.github.hehpollon.drivemood.navigation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

fun createRouteDataSource(context: Context): RouteDataSource = DemoRouteSource()

private class DemoRouteSource : RouteDataSource {
    private val state = MutableStateFlow(RouteSnapshot(issue = "시연을 시작하면 샘플 경로를 보여드려요"))
    override val snapshot = state.asStateFlow()
    override fun start() = Unit
    override fun refresh() = Unit
    override fun openNavigation() = Unit
    override fun stop() = Unit
}
