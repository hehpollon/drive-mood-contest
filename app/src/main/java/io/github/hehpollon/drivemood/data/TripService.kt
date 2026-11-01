package io.github.hehpollon.drivemood.data

import android.app.*
import android.content.Intent
import android.os.IBinder
import io.github.hehpollon.drivemood.DriveMoodApplication
import io.github.hehpollon.drivemood.MainActivity
import io.github.hehpollon.drivemood.R

class TripService : Service() {
    private val repository get() = (application as DriveMoodApplication).repository
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("trip", "주행 기록", NotificationManager.IMPORTANCE_LOW).apply { description = "사용자가 시작한 차량 리듬 기록" })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { repository.finishTrip(fromNotification = true); stopSelf(); return START_NOT_STICKY }
        if (intent?.action != START) { stopSelf(); return START_NOT_STICKY }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, TripService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "trip").setSmallIcon(R.drawable.ic_drive_mood)
            .setContentTitle(if (intent.getBooleanExtra("demo", false)) "Drive Mood · 시연 기록 중" else "Drive Mood · 주행 기록 중")
            .setContentText("차량 리듬을 기기에 기록하고 있어요. 주차 후 확인해 주세요.")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "기록 종료", stop).build()).build()
        startForeground(24, notification)
        if (!repository.beginTrip(intent.getBooleanExtra("demo", false))) stopSelf()
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { repository.finishTrip(interrupted = true); stopSelf() }
    override fun onDestroy() { if (repository.ui.value.recording) repository.finishTrip(interrupted = true); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val START = "io.github.hehpollon.drivemood.START"
        const val STOP = "io.github.hehpollon.drivemood.STOP"
    }
}
