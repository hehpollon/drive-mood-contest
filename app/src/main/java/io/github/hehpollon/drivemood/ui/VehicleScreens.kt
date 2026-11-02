package io.github.hehpollon.drivemood.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hehpollon.drivemood.data.DemoScene
import io.github.hehpollon.drivemood.data.DriveMoodState
import io.github.hehpollon.drivemood.domain.RhythmKind
import io.github.hehpollon.drivemood.domain.JourneyDigest
import io.github.hehpollon.drivemood.domain.ParkedContent
import io.github.hehpollon.drivemood.domain.ParkedContentFactory
import java.util.Calendar
import kotlinx.coroutines.delay

internal enum class CarSymbol { RHYTHM, HISTORY, REST, CONNECTION, VOLUME, MUTED, PLAY, PREVIEW }

@Composable
internal fun CarIcon(symbol: CarSymbol, color: Color = TextColor, modifier: Modifier = Modifier) {
    Canvas(modifier.size(32.dp)) {
        val s = size.minDimension
        val stroke = Stroke(2.3.dp.toPx(), cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x*s,y*s), Offset(x2*s,y2*s), stroke.width, StrokeCap.Round)
        when (symbol) {
            CarSymbol.RHYTHM -> {
                val path=Path().apply {
                    moveTo(.06f*s,.55f*s)
                    cubicTo(.26f*s,.55f*s,.22f*s,.25f*s,.4f*s,.25f*s)
                    cubicTo(.6f*s,.25f*s,.52f*s,.75f*s,.72f*s,.75f*s)
                    cubicTo(.86f*s,.75f*s,.85f*s,.5f*s,.96f*s,.5f*s)
                }
                drawPath(path,color,style=stroke)
            }
            CarSymbol.HISTORY -> { drawCircle(color, s*.4f, style=stroke); line(.5f,.24f,.5f,.5f); line(.5f,.5f,.72f,.63f) }
            CarSymbol.REST -> { drawCircle(color, s*.24f, style=stroke); drawCircle(color.copy(alpha=.6f),s*.43f,style=stroke) }
            CarSymbol.CONNECTION -> { drawRoundRect(color, Offset(.12f*s,.08f*s),Size(.76f*s,.84f*s),CornerRadius(.16f*s),style=stroke); line(.3f,.36f,.7f,.36f); line(.3f,.64f,.7f,.64f) }
            CarSymbol.PLAY -> { val path=Path().apply { moveTo(.28f*s,.15f*s); lineTo(.82f*s,.5f*s); lineTo(.28f*s,.85f*s); close() }; drawPath(path,color,style=stroke) }
            CarSymbol.PREVIEW -> { drawRoundRect(color,Offset(.08f*s,.18f*s),Size(.84f*s,.58f*s),CornerRadius(.08f*s),style=stroke);line(.38f,.92f,.62f,.92f) }
            CarSymbol.VOLUME, CarSymbol.MUTED -> {
                val path=Path().apply {moveTo(.1f*s,.38f*s);lineTo(.3f*s,.38f*s);lineTo(.52f*s,.18f*s);lineTo(.52f*s,.82f*s);lineTo(.3f*s,.62f*s);lineTo(.1f*s,.62f*s);close()};drawPath(path,color,style=stroke)
                if(symbol==CarSymbol.MUTED){line(.68f,.36f,.92f,.64f);line(.92f,.36f,.68f,.64f)}
                else drawArc(color,-55f,110f,false,Offset(.4f*s,.16f*s),Size(.55f*s,.68f*s),style=stroke)
            }
        }
    }
}

@Composable
internal fun NavigationRail(page: String, enabled: Boolean, demo: Boolean, compact: Boolean, onPage: (String)->Unit, onDemo: ()->Unit) {
    val colors=LocalMoodColors.current
    BoxWithConstraints(Modifier.width(if(compact) 144.dp else 176.dp).fillMaxHeight()) {
    val itemHeight=when { maxHeight>=1000.dp -> 144.dp; maxHeight>=720.dp -> 112.dp; else -> 96.dp }
    val itemGap=if(maxHeight>=1000.dp) 24.dp else 16.dp
    Column(Modifier.fillMaxSize().background(colors.navigation).padding(horizontal=12.dp,vertical=16.dp),
        horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(itemGap)) {
        listOf(Triple("home","지금",CarSymbol.RHYTHM), Triple("history","기록",CarSymbol.HISTORY),
            Triple("rest","쉼",CarSymbol.REST), Triple("connection","연결",CarSymbol.CONNECTION)).forEach { (id,label,icon) ->
            Surface(onClick={onPage(id)},enabled=enabled || id=="connection",shape=RoundedCornerShape(16.dp),
                color=if(page==id) colors.selectedNavigation else colors.navigation,contentColor=if(page==id) colors.onSelectedNavigation else SecondaryTextColor,
                modifier=Modifier.fillMaxWidth().height(itemHeight)) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    CarIcon(icon,if(page==id) colors.onSelectedNavigation else SecondaryTextColor,Modifier.size(if(compact) 40.dp else 44.dp))
                    Spacer(Modifier.height(12.dp)); Text(label,fontSize=if(compact) 26.sp else 28.sp,fontWeight=FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(onClick=onDemo,enabled=enabled,shape=RoundedCornerShape(16.dp),color=colors.navigation,
            modifier=Modifier.fillMaxWidth().height(itemHeight)) {
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                CarIcon(CarSymbol.PLAY,WarmOrange,Modifier.size(if(compact) 40.dp else 44.dp));Spacer(Modifier.height(12.dp));Text(if(demo) "장면" else "시연",fontSize=if(compact) 26.sp else 28.sp,color=WarmOrange)
            }
        }
    }
}
}

@Composable
internal fun VehicleToolbar(state: DriveMoodState, driving: Boolean, preview: Boolean, onExitPreview: ()->Unit, onDemo: ()->Unit, onPreview: (()->Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tinyHeader=maxWidth<450.dp
        val smallHeader=maxWidth<700.dp
        val stacked=maxWidth<900.dp && (preview || onPreview!=null || (driving && state.demo && state.canInteract))
        Column(Modifier.fillMaxWidth().padding(horizontal=32.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=80.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                if(!tinyHeader) CarIcon(CarSymbol.RHYTHM,RhythmBlue)
                Text("Drive Mood",fontSize=if(smallHeader) 24.sp else 32.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                if(preview) Badge("주차 중 미리보기",WarmOrange)
                else if(state.vehicle.isDemo) Badge("시연 데이터",WarmOrange)
                else Text(if(state.canInteract) "P · 주차 중" else if(state.moving) "주행 중" else "연결 확인 중",fontSize=24.sp,color=SecondaryTextColor)
                if(!stacked) {
                    if(preview) MoodButton("미리보기 닫기",secondary=true,onClick=onExitPreview)
                    else if(onPreview!=null) MoodButton("주행 화면",secondary=true,onClick=onPreview)
                    else if(driving && state.demo && state.canInteract) MoodButton("시연 장면",secondary=true,onClick=onDemo)
                }
            }
            if(stacked) {
                if(preview) MoodButton("미리보기 닫기",secondary=true,modifier=Modifier.fillMaxWidth(),onClick=onExitPreview)
                else if(onPreview!=null) MoodButton("주행 화면",secondary=true,modifier=Modifier.fillMaxWidth(),onClick=onPreview)
                else MoodButton("시연 장면",secondary=true,modifier=Modifier.fillMaxWidth(),onClick=onDemo)
            }
        }
    }
}

@Composable
private fun ParkedIntroduction(content: ParkedContent, compact: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val tiny = compact && maxHeight < 220.dp
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            if (!compact) {
                Text(content.kicker, fontSize = 24.sp,
                    color = RhythmBlue, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
            }
            Text(content.title, fontSize = if (tiny) 32.sp else if (compact) 40.sp else 72.sp,
                lineHeight = if (tiny) 42.sp else if (compact) 52.sp else 92.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(if (tiny) 12.dp else if (compact) 16.dp else 24.dp))
            Text(if (compact) content.digest.compactText else content.message,
                fontSize = if (compact) 24.sp else 32.sp, lineHeight = if (compact) 34.sp else 46.sp,
                color = SecondaryTextColor, maxLines = if (compact && !tiny) 3 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ParkedHome(state: DriveMoodState, onRecord: () -> Unit, onBreathing: () -> Unit, onNavigation: () -> Unit) {
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60_000) }
    }
    val hour = remember(nowMillis) { Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY) }
    val content = ParkedContentFactory.create(state.recording, state.activeTrip, state.records, state.lastDriving, nowMillis, hour)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1000.dp && maxHeight >= 720.dp
        val showCompactArt = maxWidth >= 600.dp && maxHeight >= 730.dp
        val showCompactIntroduction = maxHeight >= 440.dp
        Column(Modifier.fillMaxSize().padding(if (wide) 40.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ParkedIntroduction(content, false)
                        Spacer(Modifier.height(8.dp))
                        MoodButton(if (state.recording) "기록 마치기" else "주행 기록 시작", state.canInteract,
                            modifier = Modifier.fillMaxWidth(), prominent = true, onClick = onRecord)
                        MoodButton("30초 숨 고르기", state.canInteract, secondary = true,
                            modifier = Modifier.fillMaxWidth(), onClick = onBreathing)
                        Text(if (state.recording) "앱 기록 ${duration(state.activeTrip?.elapsedSeconds ?: 0)}"
                            else "도착하면 오늘의 리듬이 남아요", fontSize = 24.sp, color = SecondaryTextColor)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            JourneyLandscape(Modifier.fillMaxWidth().heightIn(max = 720.dp).aspectRatio(1.08f))
                        }
                        JourneyDigestSummary(content.digest)
                    }
                }
                HomeRouteStrip(state, onNavigation)
            } else {
                if (showCompactArt) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ParkedIntroduction(content, true, Modifier.weight(1f))
                    Box(Modifier.weight(.85f).aspectRatio(1f)) { JourneyLandscape() }
                } else if (showCompactIntroduction) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    ParkedIntroduction(content, true, Modifier.fillMaxWidth())
                } else Spacer(Modifier.weight(1f))
                MoodButton(if (state.recording) "기록 마치기" else "주행 기록 시작", state.canInteract,
                    modifier = Modifier.fillMaxWidth(), onClick = onRecord)
                MoodButton("30초 숨 고르기", state.canInteract, secondary = true,
                    modifier = Modifier.fillMaxWidth(), onClick = onBreathing)
            }
        }
    }
}

@Composable
private fun JourneyDigestSummary(digest: JourneyDigest) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(digest.label + if (!digest.current && digest.record != null) " · ${dateTime(digest.record.endedAt)}" else "",
            fontSize = 22.sp, color = SecondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(digest.message, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        digest.record?.let { record ->
            Text(if (digest.current && record.drivingSeconds == 0L) "주차 중에는 주행 리듬을 분석하지 않아요"
                else "분석 ${duration(record.analyzedSeconds)} / 주행 ${duration(record.drivingSeconds)}",
                fontSize = 24.sp, lineHeight = 34.sp, color = SecondaryTextColor)
        }
    }
}

@Composable
private fun HomeRouteStrip(state: DriveMoodState, onNavigation: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(color = SecondaryTextColor.copy(alpha = .18f))
        Row(Modifier.fillMaxWidth().heightIn(min = 112.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.route.active) {
                    Text(state.route.destination?.takeIf { it.isNotBlank() } ?: "목적지 확인 중", fontSize = 28.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(state.route.remainingMinutes()?.let { "${it}분 남음" },
                        state.route.distanceText()).joinToString(" · ").ifBlank { "경로 정보를 확인하고 있어요" },
                        fontSize = 24.sp, color = SecondaryTextColor)
                } else Text(if (state.vehicle.isDemo) "시연에서 가상의 여정을 함께 살펴봐요"
                    else "목적지를 정하면 경로도 함께 보여요", fontSize = 24.sp, color = SecondaryTextColor)
            }
            if (!state.vehicle.isDemo) MoodButton("차량 내비 열기", state.canInteract && state.route.connected,
                secondary = true, onClick = onNavigation)
        }
    }
}

@Composable
private fun DrivingRhythmSummary(state: DriveMoodState, compact: Boolean, modifier: Modifier = Modifier) {
    val content = drivingContent(state.rhythm.kind)
    val colors = LocalMoodColors.current
    BoxWithConstraints(modifier.background(if (content.changed) colors.suggestion else colors.hero, RoundedCornerShape(40.dp))) {
        val sideBySide = maxWidth >= 1120.dp && maxHeight >= 420.dp
        val tiny = maxHeight < 340.dp
        val showScene = !sideBySide && maxWidth >= 550.dp && maxHeight >= 620.dp
        val showMessage = maxHeight >= 240.dp
        val padding = if (tiny) 24.dp else if (compact) 32.dp else 48.dp
        if (sideBySide) {
            Row(Modifier.fillMaxSize().padding(padding), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                DrivingRhythmCopy(state, false, false, true, Modifier.weight(1.05f))
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    RhythmIllustration(state.rhythm.kind, Modifier.fillMaxWidth().aspectRatio(1.05f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding),
                verticalArrangement = if (showScene) Arrangement.Top else Arrangement.Center) {
                DrivingRhythmCopy(state, compact, tiny, showMessage, Modifier.fillMaxWidth(), animateMark = !showScene)
                if (showScene) {
                    Spacer(Modifier.height(16.dp))
                    RhythmIllustration(state.rhythm.kind, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DrivingRhythmCopy(state: DriveMoodState, compact: Boolean, tiny: Boolean, showMessage: Boolean, modifier: Modifier = Modifier, animateMark: Boolean = false) {
    val content = drivingContent(state.rhythm.kind)
    val showStory = state.rhythm.kind !in setOf(RhythmKind.NO_DATA, RhythmKind.LEARNING)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (tiny) 16.dp else 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RhythmIllustration(state.rhythm.kind, Modifier.size(if (tiny) 64.dp else 88.dp, if (tiny) 32.dp else 40.dp), markOnly = true, animate = animateMark)
            if (!tiny || !compact) Text("지금의 운전 리듬", fontSize = 24.sp, color = SecondaryTextColor)
        }
        Text(content.title, fontSize = if (tiny) 32.sp else if (compact) 40.sp else 64.sp,
            lineHeight = if (tiny) 42.sp else if (compact) 54.sp else 84.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (showMessage) {
            Text(if (showStory) state.story.title.takeIf { it.isNotBlank() } ?: content.message else content.message,
                fontSize = if (tiny) 24.sp else if (compact) 28.sp else 32.sp,
                lineHeight = if (tiny) 34.sp else 44.sp, color = SecondaryTextColor,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (content.changed) Text("안전하게 주차한 뒤 쉬어가요", fontSize = if (tiny) 24.sp else 28.sp,
                lineHeight = if (tiny) 34.sp else 40.sp, color = WarmOrange, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DrivingSurface(state: DriveMoodState, onVoice: ()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription="주행 요약 화면" }) {
        val narrowFooter=maxWidth<700.dp
        val wide=maxWidth>=1000.dp && maxHeight>=620.dp
        Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            if(wide && state.route.active) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(32.dp)) {
                DrivingRhythmSummary(state,false,Modifier.weight(1.6f).fillMaxHeight())
                RouteSummary(state,true,{},Modifier.weight(1f).fillMaxHeight())
            } else DrivingRhythmSummary(state,!wide,Modifier.weight(1f).fillMaxWidth())
            if(narrowFooter) Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                GuidanceControl(state,onVoice)
                DrivingRecordingStatus(state.recording)
            } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(32.dp),verticalAlignment=Alignment.CenterVertically) {
                GuidanceControl(state,onVoice)
                Spacer(Modifier.weight(1f))
                DrivingRecordingStatus(state.recording)
            }
        }
    }
}

@Composable
private fun DrivingRecordingStatus(recording: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recording) Box(Modifier.size(10.dp).background(RhythmBlue, RoundedCornerShape(50)))
        Text(if (recording) "리듬 기록 중" else "주차 후 기록을 시작해요", fontSize = 24.sp, color = SecondaryTextColor)
    }
}

@Composable
private fun GuidanceControl(state: DriveMoodState, onVoice: ()->Unit) {
    if(state.voiceAvailable) Surface(onClick=onVoice,color=RaisedColor,shape=RoundedCornerShape(20.dp),modifier=Modifier.heightIn(min=112.dp).widthIn(min=320.dp)) {
        Row(Modifier.padding(horizontal=28.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            CarIcon(if(state.voiceEnabled) CarSymbol.VOLUME else CarSymbol.MUTED,RhythmBlue,Modifier.size(40.dp))
            Text(if(state.voiceEnabled) "안내 음소거" else "음성 안내 켜기",fontSize=32.sp)
        }
    } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        CarIcon(CarSymbol.MUTED,SecondaryTextColor);Text("화면으로 안내",fontSize=24.sp,color=SecondaryTextColor)
    }
}

@Composable
internal fun RestScreen(state: DriveMoodState, onBreathing: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp && maxHeight >= 800.dp
        val narrowCopy = maxWidth < 450.dp
        if (wide) Row(Modifier.fillMaxSize().padding(40.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                RestIntroduction()
                Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).aspectRatio(1.8f)) { JourneyLandscape() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                EnvironmentComparison(state)
                MoodButton("30초 숨 고르기", state.canInteract, modifier = Modifier.fillMaxWidth(), prominent = true, onClick = onBreathing)
                Text("주차한 동안 사용할 수 있어요", fontSize = 24.sp, color = SecondaryTextColor)
            }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            RestIntroduction(compact = true, narrow = narrowCopy)
            MoodButton("30초 숨 고르기", state.canInteract, modifier = Modifier.fillMaxWidth(), prominent = true, onClick = onBreathing)
            EnvironmentComparison(state)
            Text("주차한 동안 사용할 수 있어요", fontSize = 24.sp, color = SecondaryTextColor)
        }
    }
}

@Composable
private fun RestIntroduction(compact: Boolean = false, narrow: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("나를 위한 짧은 쉼", fontSize = 24.sp, color = WarmOrange, fontWeight = FontWeight.Medium)
        Text(if (narrow) "잠깐,\n나에게 30초" else "잠깐, 나에게 30초", fontSize = if (compact) 40.sp else 56.sp,
            lineHeight = if (compact) 54.sp else 76.sp, fontWeight = FontWeight.SemiBold)
        Text(if (narrow) "3초 들이쉬고,\n3초 내쉬어요.\n편안한 만큼,\n다섯 번 호흡해요."
            else "3초 들이쉬고, 3초 내쉬어요.\n편안한 만큼 다섯 번 호흡해요.",
            fontSize = 28.sp, lineHeight = 44.sp, color = SecondaryTextColor)
    }
}

@Composable
internal fun DemoSheet(state: DriveMoodState, onStart: ()->Unit, onScene: (DemoScene)->Unit, onClose: ()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onClose,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.widthIn(max=920.dp).fillMaxWidth().heightIn(max=900.dp).background(SurfaceColor,RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState()).padding(40.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Text(if(state.demo) "시연 장면" else "여정 시연",fontSize=36.sp,fontWeight=FontWeight.Medium)
            Text("차량은 주차해 두고 확인해 주세요",fontSize=24.sp,color=SecondaryTextColor)
            if(state.demo) DemoScene.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    row.forEach { scene -> MoodButton(scene.label,secondary=state.demoScene!=scene,modifier=Modifier.weight(1f)) { onScene(scene);onClose() } }
                }
            } else if(!state.recording) MoodButton("여정 시연",modifier=Modifier.fillMaxWidth()) { onStart();onClose() }
            else Text("지금 기록을 마친 뒤 시연할 수 있어요",fontSize=24.sp,color=SecondaryTextColor)
            MoodButton("닫기",secondary=true,modifier=Modifier.fillMaxWidth(),onClick=onClose)
        }
    }
}
