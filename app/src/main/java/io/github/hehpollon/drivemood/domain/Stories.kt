package io.github.hehpollon.drivemood.domain

import io.github.hehpollon.drivemood.navigation.RouteSnapshot
import io.github.hehpollon.drivemood.vehicle.VehicleSnapshot

data class MicroStory(val title: String, val message: String, val evidence: String)

object Stories {
    fun create(
        reading: RhythmReading,
        vehicle: VehicleSnapshot,
        route: RouteSnapshot,
        drivingMinutes: Int,
        hour: Int,
        breathing: BreathingState,
    ): MicroStory = when {
        breathing.completed -> MicroStory("조금 쉬어가는 시간", "잠깐 숨을 고르셨어요. 다음 주행에서 리듬을 다시 살펴볼게요.", "30초 숨 고르기 완료")
        reading.kind == RhythmKind.NO_DATA -> MicroStory("서두르지 않아도 괜찮아요", "차량 연결을 확인하고 있어요. 데이터가 모이면 함께 살펴볼게요.", "차량 데이터 확인 중")
        reading.kind == RhythmKind.MANEUVER -> MicroStory("길의 흐름을 따라", "방향을 바꾸는 구간이에요. 지금은 길에 집중해 주세요.", "회전·저속 구간 분석 제외")
        reading.kind == RhythmKind.CHANGED -> MicroStory(
            "손의 움직임이 조금 바빠졌어요",
            if (drivingMinutes >= 60) "긴 시간을 달려왔네요. 안전하게 주차한 뒤 잠깐 쉬어가도 좋아요." else "조향 보정이 이어지고 있어요. 쉬어갈 때 짧게 숨을 골라볼까요?",
            "최근 조향 변화 · 브레이크 패턴" + if (drivingMinutes >= 60) " · ${drivingMinutes}분 운전" else "",
        )
        route.active && route.remainingSeconds != null && route.remainingSeconds in 1..900 ->
            MicroStory("어느새 가까워진 도착", "${route.destination ?: "목적지"}까지 조금 남았어요. 마지막 구간도 편안한 리듬으로 함께해요.", "남은 경로 시간")
        vehicle.passengers != null && vehicle.passengers > 0 ->
            MicroStory("함께 가는 길", "함께 탄 사람들과 편안한 이동을 이어가고 있어요.", "좌석 점유 · 운전 리듬")
        hour >= 20 || hour < 6 -> MicroStory("조용한 밤의 리듬", "늦은 시간의 이동이네요. 주차 후에는 은은한 빛으로 쉬어가도 좋아요.", "현재 시간대 · 운전 리듬")
        reading.kind == RhythmKind.STEADY -> MicroStory("오늘따라 손이 편한 하루네요", "작은 움직임들이 일정한 리듬으로 이어지고 있어요.", "최근 30초 조향 · 브레이크")
        reading.kind == RhythmKind.PARKED -> MicroStory("여기서 잠깐, 나의 속도로", "출발 전에도, 도착한 뒤에도. 잠시 숨을 고를 수 있어요.", "주차 상태")
        else -> MicroStory("오늘의 리듬을 함께 읽어요", "지금의 움직임을 모으고 있어요. 당신의 길에 조용히 함께할게요.", "차량 데이터 수집 중")
    }
}
