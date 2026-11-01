package io.github.hehpollon.drivemood.ui

import io.github.hehpollon.drivemood.domain.RhythmKind

/** Brief driving copy: reading priority is state, then one supporting sentence. */
internal data class DrivingContent(val title: String, val message: String, val changed: Boolean = false)

internal fun drivingContent(kind: RhythmKind): DrivingContent = when (kind) {
    RhythmKind.STEADY -> DrivingContent("일정한 리듬", "편안한 흐름을 이어가고 있어요")
    RhythmKind.CHANGED -> DrivingContent("리듬 변화", "손의 움직임이 바빠졌어요", changed = true)
    RhythmKind.MIXED -> DrivingContent("달라지는 리듬", "움직임에 변화가 있어요")
    RhythmKind.MANEUVER -> DrivingContent("길에 집중해요", "회전·저속 구간은 분석에서 쉬어가요")
    RhythmKind.NO_DATA -> DrivingContent("연결 확인 중", "차량 신호를 기다리고 있어요")
    RhythmKind.PARKED -> DrivingContent("잠시 쉬는 중", "출발 전, 나의 속도로 준비해요")
    RhythmKind.LEARNING -> DrivingContent("리듬 확인 중", "차량 움직임을 모으고 있어요")
}
