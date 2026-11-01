package io.github.hehpollon.drivemood

import android.app.Application
import io.github.hehpollon.drivemood.data.DriveMoodRepository

class DriveMoodApplication : Application() {
    val repository by lazy { DriveMoodRepository(this) }
}
