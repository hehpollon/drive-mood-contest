package io.github.hehpollon.drivemood.domain

data class BreathingState(
    val active: Boolean = false,
    val elapsedMillis: Long = 0,
    val completed: Boolean = false,
    val interrupted: Boolean = false,
) {
    val inhale: Boolean get() = elapsedMillis % 6_000 < 3_000
    val phaseProgress: Float get() = (elapsedMillis % 3_000) / 3_000f
    val remainingSeconds: Int get() = ((30_000 - elapsedMillis).coerceAtLeast(0) + 999).toInt() / 1_000
    val cycle: Int get() = (elapsedMillis / 6_000).toInt().coerceAtMost(4) + 1
}

class BreathingGuide {
    private var startedAt: Long? = null
    var state = BreathingState()
        private set

    fun start(now: Long, parked: Boolean): Boolean {
        if (!parked || state.active) return false
        startedAt = now
        state = BreathingState(active = true)
        return true
    }

    fun tick(now: Long, parked: Boolean): BreathingState {
        val start = startedAt ?: return state
        if (!state.active) return state
        if (!parked) {
            startedAt = null
            state = state.copy(active = false, interrupted = true)
        } else {
            val elapsed = (now - start).coerceIn(0, 30_000)
            state = BreathingState(active = elapsed < 30_000, elapsedMillis = elapsed, completed = elapsed >= 30_000)
            if (state.completed) startedAt = null
        }
        return state
    }

    fun cancel() {
        startedAt = null
        state = BreathingState()
    }
}
