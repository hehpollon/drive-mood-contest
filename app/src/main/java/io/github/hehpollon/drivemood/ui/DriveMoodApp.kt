package io.github.hehpollon.drivemood.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import io.github.hehpollon.drivemood.data.*
import io.github.hehpollon.drivemood.domain.*
import io.github.hehpollon.drivemood.vehicle.Gear
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun DriveMoodApp(state: DriveMoodState, repository: DriveMoodRepository, onStartTrip: (Boolean) -> Unit, onPermissions: () -> Unit, darkTheme: Boolean = isSystemInDarkTheme()) {
    var page by rememberSaveable { mutableStateOf("home") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var demoSheet by remember { mutableStateOf(false) }
    val selected=state.records.firstOrNull { it.id==selectedId }
    val arrival=state.arrivalReport
    // The launcher/router also has a driving-safe fallback: unknown is restricted.
    val restricted=!state.canInteract
    val demoDriving=state.demo && state.vehicle.gear in setOf(Gear.DRIVE,Gear.REVERSE)
    val driving=restricted || preview || demoDriving
    val breathing=state.breathing.active || state.breathing.completed || state.breathing.interrupted
    LaunchedEffect(restricted) { if(restricted) { page="home";selectedId=null;deleteId=null;preview=false;demoSheet=false } }
    LaunchedEffect(state.canInteract) { if(!state.canInteract) {preview=false;demoSheet=false;deleteId=null} }
    BackHandler(enabled=breathing || preview || demoSheet || selectedId!=null || page!="home" || arrival!=null) {
        when {
            demoSheet -> demoSheet=false
            preview -> preview=false
            breathing -> repository.closeBreathing()
            arrival!=null -> repository.dismissArrival()
            selectedId!=null -> selectedId=null
            else -> page="home"
        }
    }
    DriveMoodTheme(darkTheme) {
        Surface(Modifier.fillMaxSize().semantics { contentDescription="Drive Mood 앱 화면" },color=CanvasColor) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val compactRail=maxWidth<900.dp
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if(!driving && !breathing) NavigationRail(page,state.canInteract,state.demo,compact=compactRail,onPage={ next ->
                    page=next;selectedId=null;repository.dismissArrival()
                },onDemo={demoSheet=true})
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    VehicleToolbar(state,driving,preview,onExitPreview={preview=false},onDemo={demoSheet=true},
                        onPreview=if(!driving && !breathing && page=="home" && selected==null && arrival==null && state.canInteract) ({preview=true}) else null)
                    HorizontalDivider(color=SecondaryTextColor.copy(alpha=.15f))
                    if(state.notice!=null && !driving) Row(Modifier.fillMaxWidth().background(RaisedColor).padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(state.notice,fontSize=24.sp,lineHeight=36.sp,modifier=Modifier.weight(1f))
                        Spacer(Modifier.width(24.dp));MoodButton("닫기",secondary=true,onClick=repository::dismissNotice)
                    }
                    when {
                        driving -> DrivingSurface(state,repository::toggleDrivingVoice)
                        breathing -> BreathingScreen(state,repository::closeBreathing)
                        arrival!=null && state.canInteract -> ReportScreen(arrival,repository::dismissArrival)
                        selected!=null && state.canInteract -> ReportScreen(selected,onClose={selectedId=null},onDelete={deleteId=selected.id})
                        page=="history" && state.canInteract -> HistoryScreen(state.records,onSelect={selectedId=it})
                        page=="rest" && state.canInteract -> RestScreen(state,repository::startBreathing)
                        page=="connection" -> ConnectionScreen(state,repository,onPermissions)
                        else -> ParkedHome(state,onRecord={if(state.recording) repository.finishTrip() else onStartTrip(false)},
                            onBreathing=repository::startBreathing,onNavigation=repository::openNavigation)
                    }
                }
            }
            if(demoSheet && state.canInteract) DemoSheet(state,onStart={onStartTrip(true)},onScene=repository::setDemoScene,onClose={demoSheet=false})
            if(deleteId!=null && state.canInteract) AlertDialog(onDismissRequest={deleteId=null},title={Text("이 기록을 삭제할까요?",fontSize=32.sp)},
                text={Text("기기에 저장된 이 주행 기록이 삭제됩니다.",fontSize=24.sp)},
                confirmButton={MoodButton("삭제") {repository.deleteRecord(deleteId!!);deleteId=null;selectedId=null}},
                dismissButton={MoodButton("취소",secondary=true) {deleteId=null}})
            }
        }
    }
}

@Composable
internal fun EnvironmentComparison(state: DriveMoodState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CurrentCabinCard(state, Modifier.fillMaxWidth())
            SuggestedCabinCard(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CurrentCabinCard(state, Modifier.weight(1f))
            SuggestedCabinCard(Modifier.weight(1f))
        }
    }
    Text("온도와 빛은 제안이에요.\n원하는 상태로 직접 조절해 주세요.",
        color = SecondaryTextColor, fontSize = 24.sp, lineHeight = 34.sp)
}

@Composable
private fun CurrentCabinCard(state: DriveMoodState, modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    Column(modifier.background(colors.calm, RoundedCornerShape(20.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("지금의 실내", fontSize = 24.sp, color = SecondaryTextColor)
        Text(temperature(state.vehicle.cabinTemperatureC), fontSize = 36.sp, fontWeight = FontWeight.Medium)
        Text("설정 ${temperature(state.vehicle.targetTemperatureC)}", fontSize = 24.sp, color = SecondaryTextColor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).background(state.vehicle.ambientColorArgb?.let { Color(it) } ?: SecondaryTextColor, RoundedCornerShape(50)))
            Text(state.vehicle.ambientBrightnessPercent?.let { "조명 $it%" } ?: "조명 미제공", fontSize = 24.sp, color = SecondaryTextColor)
        }
    }
}

@Composable
private fun SuggestedCabinCard(modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    Column(modifier.background(colors.suggestion, RoundedCornerShape(20.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("쉬어갈 때의 제안", fontSize = 24.sp, color = WarmOrange)
        Text("22°C", fontSize = 36.sp, fontWeight = FontWeight.Medium)
        Text("편안한 온도로", fontSize = 24.sp, color = SecondaryTextColor)
        Text("따뜻한 빛 · 30%", fontSize = 24.sp, color = WarmOrange)
    }
}

@Composable
private fun BreathingScreen(state: DriveMoodState, onClose: () -> Unit) {
    val colors = LocalMoodColors.current
    val guide = state.breathing
    val complete = guide.completed
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1200.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow(if (complete) "30초의 짧은 쉼" else "나를 위한 30초", WarmOrange)
            Text(when { complete -> "나에게 건넨, 짧은 쉼"; guide.interrupted -> "숨 고르기를 잠시 멈췄어요"; guide.inhale -> "천천히 들이쉬어요"; else -> "편안하게 내쉬어요" }, fontSize = if (wide) 36.sp else 28.sp, fontWeight = FontWeight.Medium)
            Text(if (complete) "다음 주행에서 리듬을 다시 살펴볼게요." else if (guide.interrupted) "주차 상태에서 다시 시작해 주세요." else "편안한 만큼만 호흡해 주세요.", color = SecondaryTextColor, fontSize = 24.sp)
            Box(Modifier.size(if (wide) 310.dp else 250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = "${guide.remainingSeconds}초 남음, ${guide.cycle}번째 호흡" }) {
                    val fraction = if (complete || guide.interrupted) .5f else if (guide.inhale) guide.phaseProgress else 1 - guide.phaseProgress
                    val radius = size.minDimension * (.27f + fraction * .12f)
                    drawCircle(colors.breathing, radius)
                    drawCircle(colors.primary, radius, style = Stroke(2.dp.toPx()))
                    drawCircle(colors.outline.copy(alpha = .35f), size.minDimension * .46f, style = Stroke(1.dp.toPx()))
                    drawArc(colors.warm, -90f, 360 * (guide.elapsedMillis / 30_000f), false, Offset(size.width * .04f, size.height * .04f), androidx.compose.ui.geometry.Size(size.width * .92f, size.height * .92f), style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (complete) "완료" else "${guide.remainingSeconds}", fontSize = 48.sp, fontWeight = FontWeight.Light)
                    Text(if (complete) "30초 숨 고르기" else "${guide.cycle} / 5 번째 호흡", color = SecondaryTextColor, fontSize = 24.sp)
                }
            }
            Row(Modifier.widthIn(max = 1280.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                MoodPanel(Modifier.weight(1f)) {
                    Text("움직임과 호흡", fontSize = 28.sp)
                    RhythmWave(state.wave, RhythmBlue, breath = guide.elapsedMillis / 6_000f)
                    Text("실선 · 차량 조향     점선 · 호흡 가이드", fontSize = 24.sp, color = SecondaryTextColor)
                }
                if (wide) MoodPanel(Modifier.weight(1f)) { EnvironmentComparison(state) }
            }
            if (!wide) EnvironmentComparison(state)
            MoodButton(if (complete || guide.interrupted) "오늘의 여정으로 돌아가기" else "그만하고 돌아가기", secondary = !complete, onClick = onClose)
        }
    }
}

@Composable
private fun HistoryScreen(records: List<TripRecord>, onSelect: (String) -> Unit) {
    val weekStart = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis }
    val week = records.filter { !it.isDemo && it.startedAt >= weekStart }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Eyebrow("나의 주행 기록")
        Text("지나온 길의 리듬", fontSize = 34.sp, fontWeight = FontWeight.Medium)
        MoodPanel(Modifier.fillMaxWidth()) {
            Text("최근 7일", fontSize = 24.sp, color = SecondaryTextColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("기록한 여정", "${week.size}회", Modifier.weight(1f))
                Metric("운전 시간", duration(week.sumOf { it.drivingSeconds }), Modifier.weight(1f))
                Metric("숨 고르기", "${week.sumOf { it.breathingCount }}회", Modifier.weight(1f))
            }
            Text("시연 기록은 주간 합계에서 제외해요.", fontSize = 24.sp, color = SecondaryTextColor)
        }
        if (records.isEmpty()) MoodPanel(Modifier.fillMaxWidth()) {
            Text("아직 남겨진 여정이 없어요", fontSize = 24.sp)
            Text("첫 주행을 기록하면 이곳에 오늘의 리듬이 쌓여요.", fontSize = 24.sp, color = SecondaryTextColor)
        }
        records.forEach { record ->
            MoodPanel(Modifier.fillMaxWidth().heightIn(min = 144.dp).clickable { onSelect(record.id) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dateTime(record.startedAt), fontSize = 24.sp, color = SecondaryTextColor)
                        Text(record.destination ?: "오늘의 여정", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        Text("운전 ${duration(record.drivingSeconds)} · 분석 ${record.coveragePercent}% · 숨 고르기 ${record.breathingCount}회", color = SecondaryTextColor, fontSize = 24.sp)
                    }
                    Badge(if (record.isDemo && record.interrupted) "시연 · 중단" else if (record.isDemo) "시연" else if (record.interrupted) "중단 기록" else "주행", if (record.isDemo || record.interrupted) WarmOrange else RhythmBlue)
                    Text("›", fontSize = 30.sp, color = SecondaryTextColor)
                }
            }
        }
        Text("최근 60개의 기록을 이 기기에 보관해요.", fontSize = 24.sp, color = SecondaryTextColor)
    }
}

@Composable
private fun ReportScreen(record: TripRecord, onClose: () -> Unit, onDelete: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("도착 리포트")
            if (record.isDemo) Badge("시연 리포트", WarmOrange) else if (record.interrupted) Badge("중단된 기록", WarmOrange)
        }
        Text(if (record.interrupted) "여기까지의 기록을 남겼어요" else "${record.destination?.let { "$it, " } ?: ""}잘 도착하셨어요", fontSize = 34.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium)
        Text(record.closingStory, fontSize = 28.sp, lineHeight = 33.sp, color = SecondaryTextColor)
        Text("${dateTime(record.startedAt)} → ${clockTime(record.endedAt)}", fontSize = 24.sp, color = SecondaryTextColor)
        MoodPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("운전 시간", duration(record.drivingSeconds), Modifier.weight(1f))
                Metric("일정한 리듬", record.steadyPercent?.let { "$it%" } ?: "분석 부족", Modifier.weight(1f), "분석된 구간 기준")
                Metric("숨 고르기", "${record.breathingCount}회", Modifier.weight(1f))
            }
            HorizontalDivider(color = SecondaryTextColor.copy(alpha = .15f))
            Text("여정의 리듬 변화", fontSize = 28.sp)
            if (record.scores.isEmpty()) Text("분석 가능한 주행 구간이 충분하지 않았어요.", color = SecondaryTextColor, fontSize = 24.sp) else ScoreChart(record.scores)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("출발", fontSize = 24.sp, color = SecondaryTextColor)
                Text("도착", fontSize = 24.sp, color = SecondaryTextColor)
            }
            Text("분석한 구간 ${duration(record.analyzedSeconds)} / 운전 ${duration(record.drivingSeconds)} (${record.coveragePercent}%) · 브레이크 ${record.brakePresses}회", fontSize = 24.sp, color = SecondaryTextColor)
            Text("회전·저속·연결이 끊긴 구간은 제외해요. 차트는 분석 구간 순서이며 건강·안전 점수가 아니에요.", fontSize = 24.sp, lineHeight = 32.sp, color = SecondaryTextColor)
        }
        if (record.events.isNotEmpty()) MoodPanel(Modifier.fillMaxWidth()) {
            Eyebrow("여정의 순간들")
            record.events.forEach { event ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("+${duration(event.offsetSeconds)}", modifier = Modifier.width(145.dp), fontSize = 24.sp, color = SecondaryTextColor)
                    Text(event.message, fontSize = 24.sp, color = if (event.kind == "breathing") WarmOrange else TextColor)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MoodButton("돌아가기", onClick = onClose)
            if (onDelete != null) MoodButton("이 기록 삭제", secondary = true, onClick = onDelete)
        }
    }
}

@Composable
private fun ConnectionScreen(state: DriveMoodState, repository: DriveMoodRepository, onPermissions: () -> Unit) {
    val v = state.hardware
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Eyebrow("차량 연결과 음성 안내")
        Text("연결과 안내", fontSize = 34.sp, fontWeight = FontWeight.Medium)
        MoodPanel(Modifier.fillMaxWidth()) {
            Text(if (v.isDemo) "일반 기기용 시연 환경" else "Pleos 차량 환경", fontSize = 25.sp)
            ConnectionRow("조향각", v.steeringAngleDegrees?.let { "%.1f°".format(it) } ?: "미수신")
            ConnectionRow("브레이크", v.brakePressed?.let { if (it) "밟음" else "해제" } ?: "미수신")
            ConnectionRow("기어 / 속도", "${v.gear.label} / ${v.speedKph?.let { "%.0f km/h".format(it) } ?: "속도 미제공"}")
            ConnectionRow("차량 운전 시간", v.sdkDrivingMinutes?.let { "${it}분" } ?: "미제공 · 앱 기록 시간 사용")
            ConnectionRow("동승 좌석", v.passengers?.let { "${it}석 점유" } ?: "미제공")
            ConnectionRow("내비게이션", if (state.route.connected && !state.demo) "연결됨" else "연결 대기")
            ConnectionRow("주행 중 조작 제한", when (v.uxRestricted) { true -> "제한 중"; false -> "해제"; null -> "확인 중" })
            v.issues.values.distinct().forEach { Text(it, fontSize = 24.sp, color = WarmOrange) }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MoodButton("권한 확인", onClick = onPermissions)
                MoodButton("다시 연결", secondary = true, onClick = repository::refreshConnection)
            }
        }
        MoodPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("음성으로 조용히 안내", fontSize = 28.sp)
                    Text(if (state.voiceAvailable) "기기의 한국어 음성 엔진을 사용해요." else "한국어 음성 엔진이 없으면 화면으로 안내해요.", fontSize = 24.sp, color = SecondaryTextColor)
                }
                Switch(state.voiceEnabled, repository::setVoice, modifier = Modifier.size(80.dp), enabled = state.canInteract)
            }
            Text("리듬 변화 제안은 10분에 한 번 이하로 안내해요. 호흡 음성은 주차 상태에서만 재생해요.", fontSize = 24.sp, color = SecondaryTextColor)
        }
        Text("차량 데이터와 기록은 앱 내부에 보관해요. 서버로 보내지 않아요. 기기의 내비게이션과 음성 엔진은 해당 서비스의 설정을 따릅니다.", fontSize = 24.sp, lineHeight = 36.sp, color = SecondaryTextColor)
        Text("Drive Mood ${io.github.hehpollon.drivemood.BuildConfig.VERSION_NAME} · 운전 리듬 프로토타입", fontSize = 24.sp, color = SecondaryTextColor)
    }
}

@Composable
private fun ConnectionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = SecondaryTextColor, fontSize = 24.sp)
        Text(value, modifier = Modifier.weight(1.5f), fontSize = 24.sp)
    }
}
