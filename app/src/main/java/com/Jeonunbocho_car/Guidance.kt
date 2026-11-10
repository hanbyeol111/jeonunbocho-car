package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import kotlin.math.roundToInt

const val NO_PRACTICE_AHEAD = "다음 연습 구간 없음"

// 일반 좌·우회전과 직진은 기본 내비가 안내하므로 연습 상황만 고른다.
fun TurnType.toPracticeElement(): PracticeElement? = when {
    this == TurnType.TURN_UTURN -> PracticeElement.U_TURN
    name.startsWith("TURN_ENTER_ROUNDABOUT") -> PracticeElement.ROUNDABOUT
    name.startsWith("TURN_ENTER_IC") || name.startsWith("TURN_EXIT_IC") -> PracticeElement.HIGHWAY_IC
    this == TurnType.TURN_DEVIDE_MERGE -> PracticeElement.MERGE_SPLIT
    this == TurnType.TURN_TUNNEL -> PracticeElement.TUNNEL
    name.startsWith("TURN_ENTER_UNDER") || name.startsWith("TURN_ENTER_OVER") ->
        PracticeElement.OVERPASS_UNDERPASS
    this == TurnType.TURN_SHARP_R || this == TurnType.TURN_SHARP_L -> PracticeElement.SHARP_TURN
    this == TurnType.TURN_KEEP_R || this == TurnType.TURN_KEEP_L -> PracticeElement.KEEP_LANE
    else -> null
}

fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()}m" else "%.1fkm".format(meters / 1000)

data class NextPractice(val distance: String, val element: PracticeElement)

// TBT 목록은 경로 전체이고 distance는 이전 안내 지점부터의 구간 거리다(에뮬레이터 로그로 확인).
// 그래서 순서대로 더하다가 처음 만나는 연습 상황을 고른다.
fun nextPractice(tbtList: List<TBTInfo>): NextPractice? {
    var fromHere = 0.0
    for (tbt in tbtList) {
        fromHere += tbt.distance
        tbt.type.toPracticeElement()?.let { return NextPractice(formatDistance(fromHere), it) }
    }
    return null
}
