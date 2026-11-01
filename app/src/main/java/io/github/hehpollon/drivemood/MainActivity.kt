package io.github.hehpollon.drivemood

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import io.github.hehpollon.drivemood.data.TripService
import io.github.hehpollon.drivemood.ui.DriveMoodApp

class MainActivity : ComponentActivity() {
    private val repository get() = (application as DriveMoodApplication).repository
    private var pendingTrip: Boolean? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repository.refreshConnection()
        val demo = pendingTrip
        pendingTrip = null
        if (demo != null) lifecycleScope.launch {
            val ready = withTimeoutOrNull(8_000) { repository.ui.first { it.canInteract } }
            if (ready != null) startTrip(demo) else repository.notice("주차 상태와 차량 연결을 확인한 뒤 다시 시작해 주세요.")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by repository.ui.collectAsStateWithLifecycle()
            DriveMoodApp(state, repository, onStartTrip = { demo ->
                if (missingPermissions().isNotEmpty()) { pendingTrip = demo; requestVehiclePermissions() }
                else startTrip(demo)
            }, onPermissions = ::requestVehiclePermissions)
        }
    }
    private fun startTrip(demo: Boolean) {
        runCatching { startForegroundService(Intent(this, TripService::class.java).setAction(TripService.START).putExtra("demo", demo)) }
            .onFailure { repository.notice("기록을 시작하지 못했어요. 앱을 다시 열고 시도해 주세요.") }
    }
    private fun missingPermissions() = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (BuildConfig.FLAVOR == "pleos") {
                add("android.car.permission.CAR_SPEED")
                add("android.car.permission.CAR_POWERTRAIN")
            }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    private fun requestVehiclePermissions() {
        val requested = missingPermissions()
        if (requested.isEmpty()) repository.refreshConnection() else permissions.launch(requested.toTypedArray())
    }
    override fun onResume() { super.onResume(); repository.setVisible(true) }
    override fun onPause() { repository.setVisible(false); super.onPause() }
}
