package io.github.hehpollon.drivemood.verification

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityServiceInfo
import io.github.hehpollon.drivemood.DriveMoodApplication
import io.github.hehpollon.drivemood.MainActivity
import io.github.hehpollon.drivemood.data.*
import io.github.hehpollon.drivemood.domain.RhythmKind
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hehpollon.drivemood.ui.DriveMoodApp
import io.github.hehpollon.drivemood.ui.DayMoodColors
import io.github.hehpollon.drivemood.ui.NightMoodColors
import androidx.compose.ui.graphics.toArgb

/** Runs on the installed APK and real service graph; no replacement repository. */
class RuntimeChecks : Instrumentation() {
    private var action = "snapshot"
    private val repository get() = (targetContext.applicationContext as DriveMoodApplication).repository
    private var checks = 0
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); action = arguments?.getString("action") ?: action; start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val info = uiAutomation.serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            uiAutomation.serviceInfo = info
            when (action) {
                "snapshot" -> launch()
                "flow" -> flow()
                "live" -> live()
                "ui" -> vehicleUi()
                "rhythm-ui" -> rhythmUi()
                "route-ui" -> routeUi()
                "video" -> demoVideo()
                "verify-persistence" -> {
                    launch()
                    until("saved reports survive process restart") { repository.ui.value.records.any { it.isDemo && it.breathingCount == 1 } }
                    // Only the latest flow report matters; older interrupted records stay in device history.
                    val latest = repository.ui.value.records.filter { it.isDemo && it.breathingCount == 1 }.maxBy { it.endedAt }
                    check(!latest.interrupted) { "Restart turned the latest report into an interrupted record: $latest" }
                }
            }
            result.putString("screen", snapshot())
            result.putInt("checks", checks)
            result.putString("result", "PASS")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("result", "FAIL: ${error.stackTraceToString()}")
            result.putString("screen", runCatching { snapshot() }.getOrDefault("unavailable"))
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun launch() {
        uiAutomation.executeShellCommand("am start -n ${targetContext.packageName}/${MainActivity::class.java.name}").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
        until("real vehicle parked and app foreground") { repository.ui.value.canInteract && repository.ui.value.foreground }
    }
    private fun onMain(block: () -> Unit) = runOnMainSync(block)
    private fun until(label: String, timeout: Long = 12_000, predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (!predicate()) {
            if (android.os.SystemClock.elapsedRealtime() >= deadline) error("Timed out: $label; ${repository.ui.value}")
            Thread.sleep(100)
        }
        checks++
        sendStatus(0, Bundle().apply { putString("check", label) })
    }
    private fun flow() {
        launch()
        if (repository.ui.value.recording) onMain { repository.finishTrip() }
        onMain { repository.dismissArrival() }
        onMain { targetContext.startForegroundService(Intent(targetContext, TripService::class.java).setAction(TripService.START).putExtra("demo", true)) }
        until("foreground service starts recording") { repository.ui.value.recording && repository.ui.value.demo }
        until("steady replay uses measured rhythm", 12_000) { repository.ui.value.rhythm.kind == RhythmKind.STEADY }
        onMain { repository.startBreathing() }
        check(!repository.ui.value.breathing.active) { "Breathing started in simulated drive" }; checks++
        until("variation changes classification", 35_000) { repository.ui.value.rhythm.kind == RhythmKind.CHANGED }
        until("changed story rendered") { snapshot().contains("손의 움직임") }
        onMain { repository.setDemoScene(DemoScene.TURN) }
        until("turns excluded") { repository.ui.value.rhythm.kind == RhythmKind.MANEUVER && repository.ui.value.rhythm.score == null }
        onMain { repository.setDemoScene(DemoScene.DISCONNECTED) }
        until("missing signals have no score") { repository.ui.value.rhythm.kind == RhythmKind.NO_DATA && repository.ui.value.rhythm.score == null }
        onMain { repository.setDemoScene(DemoScene.PARKED); repository.startBreathing() }
        until("parked breathing starts") { repository.ui.value.breathing.active }
        Thread.sleep(3_300)
        check(!repository.ui.value.breathing.inhale) { "3-second exhale missing" }; checks++
        // Leaving the breathing page cancels the guide, while recording continues.
        targetContext.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        until("background cancels guide but keeps session") { !repository.ui.value.breathing.active && repository.ui.value.recording }
        Thread.sleep(5_500)
        check(repository.ui.value.hardware.signalsFresh(android.os.SystemClock.elapsedRealtime())) { "Background telemetry stopped" }; checks++
        launch()
        onMain { repository.closeBreathing(); repository.startBreathing() }
        until("full 30-second guide completes", 33_000) { repository.ui.value.breathing.completed }
        check(repository.ui.value.activeTrip?.breathingCount == 1) { "Completed breathing count wrong" }; checks++
        check(repository.ui.value.rhythm.score == null) { "Breathing fabricated a driving score" }; checks++
        until("completion screen rendered") { snapshot().contains("짧은 쉼") }
        onMain { repository.closeBreathing(); repository.setDemoScene(DemoScene.JOURNEY) }
        until("native-style arrival is gated on parking", 55_000) { !repository.ui.value.recording && repository.ui.value.arrivalReport != null }
        val report = repository.ui.value.arrivalReport!!
        check(report.isDemo && report.breathingCount == 1 && report.analyzedSeconds > 0 && report.changedSeconds > 0) { "Report metrics missing: $report" }; checks++
        until("arrival report rendered") { snapshot().contains("잘 도착하셨어요") }
        check(repository.ui.value.records.any { it.id == report.id }) { "Report not persisted" }; checks++
    }
    private fun live() {
        launch()
        onMain { repository.dismissArrival() }
        until("real recording control visible") { snapshot().contains("주행 기록 시작") }
        tap("주행 기록 시작")
        until("LIVE_READY") { repository.ui.value.recording && !repository.ui.value.demo }
        until("real SDK steady rhythm", 40_000) { repository.ui.value.rhythm.kind == RhythmKind.STEADY }
        until("optimized driving Activity visible") { snapshot().contains("주행 중") && !platformBlocked() }
        until("driving content is visible above platform surfaces") { drivingPixelsVisible() }
        check(nodes().none { it.isScrollable }) { "Live driving surface scrolls" }; checks++
        check(!snapshot().contains("30초 숨 고르기") && !snapshot().contains("조향각")) { "Parked tasks visible in motion" }; checks++
        capture("car-ui-live-driving")
        until("real SDK changed rhythm", 55_000) { repository.ui.value.rhythm.kind == RhythmKind.CHANGED }
        capture("car-ui-live-changed")
        until("real SDK parked", 25_000) { repository.ui.value.canInteract }
        launch()
        onMain { repository.finishTrip() }
        val report = repository.ui.value.arrivalReport!!
        check(!report.isDemo && report.analyzedSeconds > 0 && report.changedSeconds > 0 && report.brakePresses > 0) { "Actual SDK report missing metrics: $report" }; checks++
        until("real SDK report rendered") { snapshot().contains("잘 도착하셨어요") }
        onMain {
            repository.dismissArrival()
            targetContext.startForegroundService(Intent(targetContext, TripService::class.java).setAction(TripService.START).putExtra("demo", true))
        }
        until("demo ready for actual movement check") { repository.ui.value.recording && repository.ui.value.demo }
        onMain { repository.setDemoScene(DemoScene.PARKED); repository.startBreathing() }
        until("GUIDE_READY_FOR_MOVEMENT") { repository.ui.value.breathing.active }
        until("actual movement cancels demo and guide", 25_000) {
            val state = repository.ui.value
            !state.recording && !state.breathing.active && !state.canInteract && state.arrivalReport?.interrupted == true
        }
        until("real vehicle parked after restriction check", 25_000) { repository.ui.value.canInteract }
        launch()
    }
    private fun snapshot(): String {
        if (android.os.Build.VERSION.SDK_INT >= 33) uiAutomation.clearCache()
        val lines = mutableListOf<String>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.packageName?.toString() == targetContext.packageName) node.text?.toString()?.let { lines += it.replace('\n',' ') }
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        uiAutomation.windows.forEach { visit(it.root) }
        return lines.distinct().joinToString("\n")
    }

    private fun nodes(): List<android.view.accessibility.AccessibilityNodeInfo> {
        if(android.os.Build.VERSION.SDK_INT>=33) uiAutomation.clearCache()
        val result=mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if(node==null)return
            if(node.packageName?.toString()==targetContext.packageName) result+=node
            for(i in 0 until node.childCount)visit(node.getChild(i))
        }
        uiAutomation.windows.forEach {visit(it.root)}
        return result
    }
    private fun platformBlocked(): Boolean {
        if(android.os.Build.VERSION.SDK_INT>=33) uiAutomation.clearCache()
        var blocked=false
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if(node==null)return
            if(node.text?.toString()?.contains("주행 중에는 사용할 수 없습니다")==true)blocked=true
            for(i in 0 until node.childCount)visit(node.getChild(i))
        }
        uiAutomation.windows.forEach {visit(it.root)}
        return blocked
    }
    private fun drivingPixelsVisible(darkTheme: Boolean =
        targetContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    ): Boolean = backgroundVisible("주행 요약 화면", darkTheme)

    private fun backgroundVisible(description: String, darkTheme: Boolean): Boolean {
        val root=nodes().firstOrNull {it.contentDescription?.toString()==description} ?: return false
        val bounds=android.graphics.Rect();root.getBoundsInScreen(bounds)
        val screenshot=uiAutomation.takeScreenshot() ?: return false
        val x=(if(description=="주행 요약 화면") bounds.left+8 else bounds.right-8).coerceIn(0,screenshot.width-1)
        val y=(bounds.top+8).coerceIn(0,screenshot.height-1)
        val actual=screenshot.getPixel(x,y)
        screenshot.recycle()
        val expected=(if(darkTheme) NightMoodColors else DayMoodColors).background.toArgb()
        return listOf(0,8,16).all {shift -> kotlin.math.abs(((actual ushr shift) and 255)-((expected ushr shift) and 255))<=12}
    }
    private fun tap(label: String) {
        val deadline=android.os.SystemClock.elapsedRealtime()+5_000
        fun control(): android.view.accessibility.AccessibilityNodeInfo? = nodes()
            .filter {it.text?.toString()==label && it.isVisibleToUser}
            .firstNotNullOfOrNull {node ->
                var candidate: android.view.accessibility.AccessibilityNodeInfo?=node
                while(candidate!=null && !candidate.isClickable) candidate=candidate.parent
                candidate?.takeIf {it.isEnabled && it.isVisibleToUser}
            }
        var candidate=control()
        while(candidate==null && android.os.SystemClock.elapsedRealtime()<deadline) {
            Thread.sleep(50)
            candidate=control()
        }
        check(candidate!=null) {"Missing control $label"}
        check(candidate?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true) {"Could not tap $label"}
    }
    private fun touchTargets(label: String) {
        val density=targetContext.resources.displayMetrics.density
        val targets=nodes().filter {it.isClickable && it.isEnabled && it.isVisibleToUser}
        check(targets.isNotEmpty()) {"No controls to measure in $label"}
        targets.forEach {node ->
            val rect=android.graphics.Rect();node.getBoundsInScreen(rect)
            check(rect.width()/density>=112f && rect.height()/density>=112f) {"Small target in $label: ${node.text}, $rect, density=$density"}
        }
        checks++
        sendStatus(0,Bundle().apply {putString("check","$label: ${targets.size} controls at least 112dp")})
    }
    private fun capture(name: String) {
        Thread.sleep(300)
        val bitmap=uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        targetContext.cacheDir.resolve("$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    /** Drives the existing public demo flow for screen recording; no replacement UI/data. */
    private fun demoVideo() {
        launch()
        check(!repository.ui.value.recording) { "An existing trip is recording; leave it intact" }
        onMain { repository.dismissArrival(); repository.closeBreathing(); repository.dismissNotice() }
        until("video home ready") { snapshot().contains("주행 기록 시작") }
        val flag=targetContext.cacheDir.resolve("demo-video-start")
        flag.delete()
        videoMark("ready")
        until("screen recorder started",40_000) {flag.exists()}
        flag.delete()
        videoMark("home");Thread.sleep(7_000)
        tap("시연")
        until("video demo chooser") {snapshot().contains("여정 시연")}
        videoMark("chooser");Thread.sleep(1_500)
        tap("여정 시연")
        until("video demo recording") {repository.ui.value.recording && repository.ui.value.demo}
        until("video steady rhythm",12_000) {repository.ui.value.rhythm.kind==RhythmKind.STEADY}
        videoMark("steady");Thread.sleep(4_000)
        until("video changed rhythm",30_000) {repository.ui.value.rhythm.kind==RhythmKind.CHANGED}
        videoMark("changed");Thread.sleep(4_000)
        until("video parked",25_000) {
            repository.ui.value.vehicle.gear==io.github.hehpollon.drivemood.vehicle.Gear.PARK && snapshot().contains("기록 마치기")
        }
        videoMark("parked");Thread.sleep(4_000)
        tap("30초 숨 고르기")
        until("video breathing active") {repository.ui.value.breathing.active}
        videoMark("breathing")
        until("video full breathing completed",33_000) {repository.ui.value.breathing.completed}
        check(repository.ui.value.activeTrip?.breathingCount==1)
        videoMark("complete");Thread.sleep(3_000)
        tap("오늘의 여정으로 돌아가기")
        until("video after care home") {snapshot().contains("기록 마치기")}
        videoMark("aftercare");Thread.sleep(3_000)
        until("video next drive observed",35_000) {
            repository.ui.value.vehicle.gear==io.github.hehpollon.drivemood.vehicle.Gear.DRIVE && repository.ui.value.rhythm.kind==RhythmKind.STEADY
        }
        videoMark("nextdrive");Thread.sleep(5_000)
        until("video arrival report",35_000) {repository.ui.value.arrivalReport!=null && snapshot().contains("잘 도착하셨어요")}
        val report=repository.ui.value.arrivalReport!!
        check(report.isDemo && report.breathingCount==1 && report.analyzedSeconds>0 && report.changedSeconds>0)
        videoMark("report");Thread.sleep(7_000)
        nodes().firstOrNull {it.isScrollable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        Thread.sleep(700)
        videoMark("details");Thread.sleep(4_000)
        repeat(6) {
            if(nodes().none {it.text?.toString()=="돌아가기" && it.isVisibleToUser}) {
                nodes().firstOrNull {it.isScrollable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(400)
            }
        }
        tap("돌아가기")
        until("video returned home") {snapshot().contains("주행 기록 시작")}
        tap("기록")
        until("video saved history") {snapshot().contains("지나온 길의 리듬")}
        check(repository.ui.value.records.any {it.id==report.id})
        videoMark("history");Thread.sleep(7_000)
        tap("지금")
        videoMark("end");Thread.sleep(1_000)
    }
    private fun videoMark(scene: String) {
        val bounds=android.graphics.Rect()
        nodes().firstOrNull {it.contentDescription?.toString()=="Drive Mood 앱 화면"}?.getBoundsInScreen(bounds)
        val state=repository.ui.value
        sendStatus(0,Bundle().apply {
            putString("scene",scene);putLong("at_ms",android.os.SystemClock.elapsedRealtime())
            putString("crop","${bounds.left},${bounds.top},${bounds.width()},${bounds.height()}")
            putString("rhythm",state.rhythm.kind.name);putString("gear",state.vehicle.gear.name)
            putBoolean("demo",state.demo);putInt("breathing_remaining",state.breathing.remainingSeconds)
        })
    }
    /** Visual fixtures only; no sample, route, or record is written to the repository. */
    private fun rhythmUi() {
        val monitor=addMonitor(MainActivity::class.java.name,null,false)
        launch()
        val activity=monitor.lastActivity as? MainActivity ?: error("Activity not monitored")
        val original=repository.ui.value
        check(!original.recording) {"Keep an existing trip intact"}
        fun render(name: String, kind: RhythmKind, dark: Boolean=false, width: Int?=null, height: Int?=null, route: Boolean=false, recording: Boolean=true, motion: Boolean?=null) {
            val reading=io.github.hehpollon.drivemood.domain.RhythmReading(kind=kind)
            val routeState=if(route) original.route.copy(active=true,destination="서울역 공항철도 환승센터 제1주차장",
                remainingSeconds=3600,remainingMeters=42000,nextInstruction="300m 앞에서 오른쪽으로 이동") else original.route.copy(active=false)
            val fixture=original.copy(canInteract=false,moving=true,recording=recording,arrivalReport=null,
                rhythm=reading,route=routeState,breathing=io.github.hehpollon.drivemood.domain.BreathingState(),
                story=io.github.hehpollon.drivemood.domain.Stories.create(reading,original.vehicle,routeState,20,14,
                    io.github.hehpollon.drivemood.domain.BreathingState()))
            onMain {activity.setContent {
                androidx.compose.runtime.key(name) {
                    if(width!=null && height!=null) Box(Modifier.requiredSize(width.dp,height.dp)) {
                        DriveMoodApp(fixture,repository,onStartTrip={},onPermissions={},darkTheme=dark)
                    } else DriveMoodApp(fixture,repository,onStartTrip={},onPermissions={},darkTheme=dark)
                }
            }}
            val title=when(kind) {
                RhythmKind.STEADY -> "일정한 리듬"
                RhythmKind.CHANGED -> "리듬 변화"
                RhythmKind.MIXED -> "달라지는 리듬"
                RhythmKind.LEARNING -> "리듬 확인 중"
                RhythmKind.NO_DATA -> "연결 확인 중"
                else -> "길에 집중해요"
            }
            until("$name state and recording status visible") {
                snapshot().contains(title) && snapshot().contains(if(recording) "리듬 기록 중" else "주차 후 기록을 시작해요")
            }
            check(nodes().none {it.isScrollable}) {"$name scrolls"};checks++
            check(!snapshot().contains("30초 숨 고르기") && !snapshot().contains("조향각")) {"$name exposes parked tasks"};checks++
            if(route) until("$name next turn visible") {snapshot().contains("300m 앞에서 오른쪽으로 이동")}
            if(kind==RhythmKind.CHANGED) until("$name rest proposal visible") {snapshot().contains("안전하게 주차한 뒤 쉬어가요")}
            capture(name)
            if(motion!=null) verifyRhythmMotion(name,title,motion)
        }
        render("rhythm-ui-wide-steady",RhythmKind.STEADY,motion=true)
        render("rhythm-ui-wide-changed",RhythmKind.CHANGED)
        render("rhythm-ui-wide-learning",RhythmKind.LEARNING,recording=false)
        render("rhythm-ui-wide-no-data",RhythmKind.NO_DATA,motion=false)
        render("rhythm-ui-route-changed",RhythmKind.CHANGED,route=true)
        render("rhythm-ui-medium-mixed",RhythmKind.MIXED,width=960,height=1000)
        render("rhythm-ui-narrow-changed",RhythmKind.CHANGED,width=480,height=800,motion=true)
        render("rhythm-ui-night-steady",RhythmKind.STEADY,dark=true,motion=true)
        render("rhythm-ui-night-changed",RhythmKind.CHANGED,dark=true)
        check(repository.ui.value.records==original.records && !repository.ui.value.recording) {"Layout fixtures changed trip data"};checks++
        onMain {activity.setContent {
            val state by repository.ui.collectAsStateWithLifecycle()
            DriveMoodApp(state,repository,onStartTrip={},onPermissions={})
        }}
        removeMonitor(monitor)
    }

    /** Compares real rendered frames with a fixed classification: motion is independent of data changes. */
    private fun verifyRhythmMotion(name: String, title: String, moving: Boolean) {
        val all=nodes()
        val bounds=android.graphics.Rect().apply {
            all.first {it.contentDescription?.toString()=="주행 요약 화면"}.getBoundsInScreen(this)
        }
        val titleBounds=android.graphics.Rect().apply {all.first {it.text?.toString()==title}.getBoundsInScreen(this)}
        val first=uiAutomation.takeScreenshot() ?: error("Missing first motion frame")
        try {
            Thread.sleep(3_000)
            val second=uiAutomation.takeScreenshot() ?: error("Missing second motion frame")
            try {
                fun differences(rect: android.graphics.Rect): Int {
                    val region=android.graphics.Rect(rect)
                    check(region.intersect(0,0,first.width,first.height)) {"Empty capture region"}
                    val a=IntArray(region.width()*region.height());val b=IntArray(a.size)
                    first.getPixels(a,0,region.width(),region.left,region.top,region.width(),region.height())
                    second.getPixels(b,0,region.width(),region.left,region.top,region.width(),region.height())
                    return a.indices.count {a[it]!=b[it]}
                }
                val changed=differences(bounds)
                if(moving) {
                    check(changed>8) {"$name does not move while classification is unchanged"};checks++
                    check(changed<bounds.width()*bounds.height()*.02) {"$name changes too much of the screen: $changed pixels"};checks++
                } else {
                    check(changed==0) {"$name moves without observed rhythm: $changed pixels"};checks++
                }
                check(differences(titleBounds)==0) {"$name animates the text"};checks++
                listOf("before" to first,"after" to second).forEach {(suffix,frame) ->
                    targetContext.cacheDir.resolve("$name-motion-$suffix.png").outputStream().use {
                        frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                    }
                }
                sendStatus(0,Bundle().apply {putString("check","$name: $changed changed pixels in 3s; title fixed")})
            } finally {second.recycle()}
        } finally {first.recycle()}
    }

    /** Destination-card fixtures exercise layout and missing information without persisting routes. */
    private fun routeUi() {
        val monitor=addMonitor(MainActivity::class.java.name,null,false)
        launch()
        val activity=monitor.lastActivity as? MainActivity ?: error("Activity not monitored")
        val original=repository.ui.value
        check(!original.recording) {"Keep an existing trip intact"}
        val base=io.github.hehpollon.drivemood.navigation.RouteSnapshot(connected=true,active=true,
            destination="부산역",remainingSeconds=1680,remainingMeters=40400,nextInstruction="경로를 따라 이동")
        fun render(name: String, route: io.github.hehpollon.drivemood.navigation.RouteSnapshot=base,
            dark: Boolean=false, cardSize: Pair<Int,Int>?=null) {
            val reading=io.github.hehpollon.drivemood.domain.RhythmReading(kind=RhythmKind.STEADY)
            val fixture=original.copy(canInteract=false,moving=true,recording=true,arrivalReport=null,route=route,
                rhythm=reading,breathing=io.github.hehpollon.drivemood.domain.BreathingState(),
                story=io.github.hehpollon.drivemood.domain.Stories.create(reading,original.vehicle,route,20,14,
                    io.github.hehpollon.drivemood.domain.BreathingState()))
            onMain {activity.setContent {
                androidx.compose.runtime.key(name) {
                    if(cardSize==null) DriveMoodApp(fixture,repository,onStartTrip={},onPermissions={},darkTheme=dark)
                    else io.github.hehpollon.drivemood.ui.DriveMoodTheme(dark) {
                        Box(Modifier.requiredSize(cardSize.first.dp,cardSize.second.dp)) {
                            io.github.hehpollon.drivemood.ui.RouteSummary(fixture,true,{})
                        }
                    }
                }
            }}
            until("$name route card rendered") {nodes().any {it.contentDescription?.toString()=="경로 요약 카드"}}
            check(nodes().none {it.isScrollable}) {"$name scrolls"};checks++
            check(!snapshot().contains("30초 숨 고르기")) {"$name exposes parked tasks"};checks++
            capture(name)
        }
        render("route-ui-day")
        check(listOf("부산역","28분 남음","40.4 km","도착 예상","다음 안내").all {snapshot().contains(it)});checks++
        val eta=nodes().mapNotNull {it.text?.toString()}.firstOrNull {it.matches(Regex("\\d{2}:\\d{2}"))}
        check(eta!=null) {"Estimated arrival missing"};checks++
        render("route-ui-night",dark=true)
        until("night route screen background visible") {drivingPixelsVisible(true)}
        render("route-ui-long",base.copy(destination="서울역 공항철도 환승센터 제1주차장",
            remainingSeconds=5700,remainingMeters=120500,nextInstruction="300m 앞에서 오른쪽으로 이동"))
        check(snapshot().contains("95분 남음") && snapshot().contains("120.5 km"));checks++
        render("route-ui-missing",base.copy(destination="",remainingSeconds=null,remainingMeters=null,nextInstruction=null))
        check(snapshot().contains("시간 확인 중") && snapshot().contains("목적지 확인 중") &&
            snapshot().contains("경로 안내를 기다리고 있어요") && !snapshot().contains("0분 남음"));checks++
        render("route-ui-invalid",base.copy(remainingSeconds=-1,remainingMeters=-1))
        check(snapshot().contains("시간 확인 중") && !snapshot().contains("-1 km") &&
            nodes().none {it.text?.toString()?.matches(Regex("\\d{2}:\\d{2}"))==true});checks++
        render("route-ui-near",base.copy(remainingSeconds=45,remainingMeters=850))
        check(snapshot().contains("1분 남음") && snapshot().contains("850 m"));checks++
        render("route-ui-narrow",cardSize=360 to 800)
        check(snapshot().contains("도착 예상") && snapshot().contains("28분 남음"));checks++
        render("route-ui-narrow-short",cardSize=360 to 600)
        check(snapshot().contains("28분 남음") && snapshot().contains("40.4 km") && !snapshot().contains("도착 예상"));checks++
        render("route-ui-short",cardSize=520 to 420)
        check(snapshot().contains("28분 남음") && snapshot().contains("40.4 km") &&
            snapshot().contains("경로를 따라 이동") && !snapshot().contains("도착 예상"));checks++
        val cardBounds=android.graphics.Rect().apply {
            nodes().first {it.contentDescription?.toString()=="경로 요약 카드"}.getBoundsInScreen(this)
        }
        val instructionBounds=android.graphics.Rect().apply {
            nodes().first {it.text?.toString()=="경로를 따라 이동"}.getBoundsInScreen(this)
        }
        check(cardBounds.contains(instructionBounds)) {"Short card clips guidance"};checks++
        check(repository.ui.value.records==original.records && !repository.ui.value.recording);checks++
        onMain {activity.setContent {
            val state by repository.ui.collectAsStateWithLifecycle()
            DriveMoodApp(state,repository,onStartTrip={},onPermissions={})
        }}
        removeMonitor(monitor)
    }

    private fun vehicleUi() {
        val monitor=addMonitor(MainActivity::class.java.name,null,false)
        launch()
        val activity=monitor.lastActivity as? MainActivity ?: error("Activity not monitored")
        onMain {activity.setContent {
            val state by repository.ui.collectAsStateWithLifecycle()
            DriveMoodApp(state,repository,onStartTrip={},onPermissions={},darkTheme=false)
        }}
        until("parked primary actions visible") {snapshot().contains("주행 기록 시작")}
        until("day palette is rendered") {backgroundVisible("Drive Mood 앱 화면",false)}
        touchTargets("parked home")
        check(!snapshot().contains("조향각")) {"Engineering values leaked into home"};checks++
        capture("car-ui-home")
        val flags=android.content.pm.PackageManager.GET_META_DATA
        val parkedInfo=targetContext.packageManager.getActivityInfo(android.content.ComponentName(targetContext,MainActivity::class.java),flags)
        check(parkedInfo.metaData?.getBoolean("distractionOptimized")==true)
        checks++
        tap("시연")
        until("demo chooser visible") {snapshot().contains("여정 시연")}
        touchTargets("demo chooser")
        tap("닫기")
        tap("주행 화면")
        until("driving preview visible") {snapshot().contains("주차 중 미리보기")}
        check(nodes().none {it.isScrollable}) {"Driving view scrolls"};checks++
        check(!snapshot().contains("30초 숨 고르기")) {"Breathing action visible while driving"};checks++
        check(!snapshot().contains("직전 여정") && !snapshot().contains("첫 주행의 리듬")) {"Parked history summary visible while driving"};checks++
        touchTargets("driving preview")
        until("day driving pixels visible") {drivingPixelsVisible(false)}
        capture("car-ui-driving")
        tap("미리보기 닫기")
        until("return to parked home") {snapshot().contains("주행 기록 시작")}
        tap("쉼")
        until("rest page visible") {snapshot().contains("잠깐, 나에게 30초")}
        touchTargets("rest page")
        capture("car-ui-rest")
        tap("30초 숨 고르기")
        until("breathing guide visible") {snapshot().contains("나를 위한 30초")}
        capture("car-ui-breathing")
        tap("그만하고 돌아가기")
        until("return to rest page") {snapshot().contains("잠깐, 나에게 30초")}
        tap("기록")
        until("history page visible") {snapshot().contains("지나온 길의 리듬")}
        capture("car-ui-history")
        tap("지금")
        onMain {activity.setContent {
            val state by repository.ui.collectAsStateWithLifecycle()
            Box(Modifier.requiredSize(480.dp,800.dp)) {
                DriveMoodApp(state,repository,onStartTrip={},onPermissions={},darkTheme=false)
            }
        }}
        until("480dp primary actions visible") {snapshot().contains("주행 화면")}
        touchTargets("480dp home")
        capture("car-ui-narrow")
        tap("주행 화면")
        until("480dp driving preview visible") {snapshot().contains("주차 중 미리보기")}
        touchTargets("480dp driving preview")
        check(nodes().none {it.isScrollable}) {"Narrow driving view scrolls"};checks++
        capture("car-ui-narrow-driving")
        tap("미리보기 닫기")
        tap("쉼")
        until("480dp rest page visible") {snapshot().contains("잠깐, 나에게 30초")}
        touchTargets("480dp rest page")
        capture("car-ui-narrow-rest")
        onMain {activity.setContent {
            androidx.compose.runtime.key("medium-layout") {
                val state by repository.ui.collectAsStateWithLifecycle()
                Box(Modifier.requiredSize(960.dp,1000.dp)) {
                    DriveMoodApp(state,repository,onStartTrip={},onPermissions={},darkTheme=false)
                }
            }
        }}
        until("960dp primary actions visible") {snapshot().contains("주행 기록 시작")}
        touchTargets("960dp home")
        capture("car-ui-medium")
        // Layout fixture only: never inject a fake route into the repository or SDK.
        val destination="서울역 공항철도 환승센터 제1주차장"
        onMain {activity.setContent {
            androidx.compose.runtime.key("route-layout-fixture") {
                val state by repository.ui.collectAsStateWithLifecycle()
                val fixture=state.copy(route=state.route.copy(active=true,destination=destination,
                    remainingSeconds=3600,remainingMeters=42000,nextInstruction="300m 앞에서 오른쪽으로 이동"))
                DriveMoodApp(fixture,repository,onStartTrip={},onPermissions={},darkTheme=false)
            }
        }}
        until("route summary handles a long destination") {snapshot().contains(destination) && snapshot().contains("60분 남음")}
        touchTargets("route home fixture")
        capture("car-ui-route-fixture")
        tap("주행 화면")
        until("driving route summary visible") {snapshot().contains("300m 앞에서 오른쪽으로 이동")}
        check(nodes().none {it.isScrollable}) {"Route driving view scrolls"};checks++
        touchTargets("route driving fixture")
        capture("car-ui-route-driving-fixture")
        onMain {activity.setContent {
            val state by repository.ui.collectAsStateWithLifecycle()
            DriveMoodApp(state,repository,onStartTrip={},onPermissions={},darkTheme=true)
        }}
        until("night palette is rendered") {backgroundVisible("Drive Mood 앱 화면",true)}
        touchTargets("night home")
        capture("car-ui-night")
        tap("주행 화면")
        until("night driving pixels visible") {drivingPixelsVisible(true)}
        check(nodes().none {it.isScrollable}) {"Night driving view scrolls"};checks++
        capture("car-ui-night-driving")
        // Home-copy fixtures render copies only; they never create or persist user records.
        val fixtureEpoch=System.currentTimeMillis()
        val recent=io.github.hehpollon.drivemood.domain.TripRecord(
            id="home-copy-layout-fixture",startedAt=fixtureEpoch-1_860_000,endedAt=fixtureEpoch-60_000,
            elapsedSeconds=1800,drivingSeconds=1200,stableSeconds=480,changedSeconds=120,
            analyzedSeconds=600,averageScore=80,brakePresses=4,breathingCount=1,destination="집",isDemo=false)
        onMain {activity.setContent {
            androidx.compose.runtime.key("home-first-journey") {
                val state by repository.ui.collectAsStateWithLifecycle()
                DriveMoodApp(state.copy(records=emptyList(),recording=false,activeTrip=null,arrivalReport=null),
                    repository,onStartTrip={},onPermissions={},darkTheme=false)
            }
        }}
        until("first journey waits for real rhythm") {snapshot().contains("첫 주행의 리듬을 기다리고 있어요")}
        touchTargets("first journey home")
        capture("car-ui-home-first")
        onMain {activity.setContent {
            androidx.compose.runtime.key("home-recent-journey") {
                val state by repository.ui.collectAsStateWithLifecycle()
                DriveMoodApp(state.copy(records=listOf(recent),recording=false,activeTrip=null,arrivalReport=null),
                    repository,onStartTrip={},onPermissions={},darkTheme=false)
            }
        }}
        until("recent journey shows scoped summary and finish greeting") {
            val screen=snapshot()
            screen.contains("이번 여정도, 수고했어요") && screen.contains("분석 구간의 80%") &&
                screen.contains("분석 10분 0초 / 주행 20분 0초")
        }
        touchTargets("recent journey home")
        capture("car-ui-home-recent-fixture")
        onMain {activity.setContent {
            androidx.compose.runtime.key("home-long-drive") {
                val state by repository.ui.collectAsStateWithLifecycle()
                DriveMoodApp(state.copy(recording=true,activeTrip=recent.copy(drivingSeconds=5400,elapsedSeconds=5600),
                    lastDriving=null,arrivalReport=null),repository,onStartTrip={},onPermissions={},darkTheme=false)
            }
        }}
        until("long-drive parking uses current journey") {
            val screen=snapshot()
            screen.contains("긴 길, 잠깐 쉬어가요") && screen.contains("이번 여정") && screen.contains("분석 구간의 80%")
        }
        touchTargets("long drive parked home")
        capture("car-ui-home-long-drive-fixture")
        onMain {activity.setContent {
            androidx.compose.runtime.key("home-interrupted-missing") {
                val state by repository.ui.collectAsStateWithLifecycle()
                DriveMoodApp(state.copy(records=listOf(recent.copy(interrupted=true,analyzedSeconds=0,stableSeconds=0,averageScore=null)),
                    recording=false,activeTrip=null,arrivalReport=null),repository,onStartTrip={},onPermissions={},darkTheme=false)
            }
        }}
        until("interrupted journey discloses insufficient analysis") {
            val screen=snapshot()
            screen.contains("직전 여정 · 중단 기록") && screen.contains("리듬을 읽을 구간이 부족했어요") &&
                !screen.contains("분석 구간의") && !screen.contains("수고했어요")
        }
        touchTargets("interrupted journey home")
        capture("car-ui-home-insufficient-fixture")
        onMain {activity.setContent {
            androidx.compose.runtime.key("home-recent-narrow") {
                val state by repository.ui.collectAsStateWithLifecycle()
                Box(Modifier.requiredSize(480.dp,800.dp)) {
                    DriveMoodApp(state.copy(records=listOf(recent),recording=false,activeTrip=null,arrivalReport=null),
                        repository,onStartTrip={},onPermissions={},darkTheme=false)
                }
            }
        }}
        until("480dp home keeps finish greeting and analysis scope") {
            snapshot().contains("이번 여정도, 수고했어요") && snapshot().contains("직전 여정 · 분석 구간 중 80% 일정")
        }
        touchTargets("480dp recent journey home")
        capture("car-ui-home-recent-narrow-fixture")
        removeMonitor(monitor)
    }
}
