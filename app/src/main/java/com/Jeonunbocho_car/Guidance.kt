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

// 고속도로 IC는 진입과 진출을 나눠서 말한다.
fun practiceTitle(type: TurnType): String = when {
    type.name.startsWith("TURN_ENTER_IC") -> "고속도로 진입"
    type.name.startsWith("TURN_EXIT_IC") -> "고속도로 진출"
    else -> type.toPracticeElement()?.label ?: type.name
}

fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()}m" else "%.1fkm".format(meters / 1000)

// 시안 캔버스 크기(dp). 창이 이보다 크면 화면 전체를 키운다.
const val DESIGN_WIDTH_DP = 600
const val DESIGN_HEIGHT_DP = 800

// 가로·세로 중 덜 남는 쪽에 맞춘다. 창이 시안보다 작으면 줄이지 않는다(1).
fun designScale(widthDp: Int, heightDp: Int): Float =
    maxOf(1f, minOf(widthDp.toFloat() / DESIGN_WIDTH_DP, heightDp.toFloat() / DESIGN_HEIGHT_DP))

// 큰 숫자와 작은 단위를 따로 그리기 위해 "1.2km" → ("1.2", "km")
fun splitUnit(distance: String): Pair<String, String> =
    if (distance.endsWith("km")) distance.dropLast(2) to "km" else distance.removeSuffix("m") to "m"

// 앞 글자에 받침이 있으면 "이", 없으면 "가" (한글이 아니면 "이")
fun subjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return "이"
    if (last !in '가'..'힣') return "이"
    return if ((last - '가') % 28 == 0) "가" else "이"
}

private fun side(type: TurnType): String? = when {
    type.name.endsWith("_R") -> "오른쪽"
    type.name.endsWith("_L") || type == TurnType.TURN_LEFT || type == TurnType.TURN_UTURN -> "왼쪽"
    type == TurnType.TURN_RIGHT -> "오른쪽"
    else -> null
}

// 주행 중 코칭 문구를 가까워지는 순서(준비 → 확인 → 실행)로 둔다. 화면에는 지금 단계 한 줄만 보여 준다.
// 근거: 도로교통법 제14·18·19·22·25조의2·31·37·38·62·65조. 유턴의 '왼쪽 차선' 준비는 차선 준비와 같은 참고 안내다.
// 방향지시등은 주행 중에 안내하지 않고 결과 화면에서만 알려준다.
fun TurnType.coaching(): List<String> = when {
    this == TurnType.TURN_TUNNEL -> listOf("전조등을 켜요", "터널 안에서는 앞지르지 않아요")
    name.startsWith("TURN_ENTER_UNDER") -> listOf("경로는 지하차도로 들어가요", "옆길로 빠지지 않게 차선을 유지해요")
    name.startsWith("TURN_ENTER_OVER") -> listOf("경로는 고가도로로 올라가요", "옆길로 빠지지 않게 차선을 유지해요")
    this == TurnType.TURN_SHARP_R || this == TurnType.TURN_SHARP_L ->
        listOf("속도를 줄여 천천히 가요", "커브 안에서는 차선을 유지해요")
    this == TurnType.TURN_KEEP_R || this == TurnType.TURN_KEEP_L ->
        listOf("${side(this)} 길로 가요", "실선 구간에서는 차선을 바꾸지 않아요")
    name.startsWith("TURN_ENTER_ROUNDABOUT") ->
        listOf("진입 전 속도를 줄여요", "안에서 도는 차가 먼저예요", "반시계 방향으로 돌아요")
    this == TurnType.TURN_UTURN ->
        listOf("왼쪽 차선으로 미리 옮겨 두세요", "유턴 표지·신호를 확인해요", "맞은편 차가 없을 때 돌아요")
    this == TurnType.TURN_DEVIDE_MERGE -> listOf("옆 차선 차를 먼저 확인해요", "방해되면 기다렸다가 들어가요")
    name.startsWith("TURN_ENTER_IC") -> listOf("본선을 달리는 차가 먼저예요")
    name.startsWith("TURN_EXIT_IC") -> listOf("출구 차선으로 미리 옮겨요", "지나쳤으면 다음 출구로 가요")
    else -> emptyList()
}

// shortcut: 단계 거리(준비·확인·실행)는 근거 없이 정한 값이다. 실제 주행으로 확인한 뒤 조정한다.
private val ROAD_STAGES_M = listOf(300.0, 100.0, 30.0)
private val HIGHWAY_STAGES_M = listOf(1000.0, 300.0, 100.0)

// index: 0부터. total: 이 상황의 단계 수(1~3).
data class CoachStage(val index: Int, val total: Int, val label: String, val text: String)

private fun stageLabels(total: Int) = when (total) {
    1 -> listOf("지금")
    2 -> listOf("준비", "실행")
    else -> listOf("준비", "확인", "실행")
}

// 가까워질수록 다음 단계로 넘어간다. 첫 단계 거리보다 멀면 첫 단계를 보여 준다.
fun coachStage(lines: List<String>, meters: Double, highway: Boolean): CoachStage? {
    if (lines.isEmpty()) return null
    val limits = (if (highway) HIGHWAY_STAGES_M else ROAD_STAGES_M).take(lines.size)
    val index = limits.indexOfLast { meters <= it }.coerceAtLeast(0)
    return CoachStage(index, lines.size, stageLabels(lines.size)[index], lines[index])
}

// 내비가 주는 방면 글자(예: "기업지원허브 방면"). 비었거나 도착 안내면 보여 주지 않는다.
fun signText(description: String): String? =
    description.trim().takeIf { it.isNotEmpty() && it != "도착" }

// 화면에 그릴 회전 아이콘(Material Icons). 직진·터널·고가도로처럼 맞는 아이콘이 없으면 null.
fun TurnType.iconRes(): Int? = when {
    this == TurnType.TURN_LEFT -> R.drawable.ic_turn_left
    this == TurnType.TURN_RIGHT -> R.drawable.ic_turn_right
    this == TurnType.TURN_SHARP_L -> R.drawable.ic_turn_sharp_left
    this == TurnType.TURN_SHARP_R -> R.drawable.ic_turn_sharp_right
    this == TurnType.TURN_UTURN -> R.drawable.ic_u_turn_left
    name.startsWith("TURN_ENTER_ROUNDABOUT") -> R.drawable.ic_roundabout_right
    this == TurnType.TURN_KEEP_L -> R.drawable.ic_fork_left
    this == TurnType.TURN_KEEP_R -> R.drawable.ic_fork_right
    this == TurnType.TURN_DEVIDE_MERGE -> R.drawable.ic_merge
    (name.startsWith("TURN_ENTER_IC") || name.startsWith("TURN_EXIT_IC")) && name.endsWith("_L") -> R.drawable.ic_ramp_left
    name.startsWith("TURN_ENTER_IC") || name.startsWith("TURN_EXIT_IC") -> R.drawable.ic_ramp_right
    else -> null
}

// shortcut: 크게 보여주는 거리는 근거 없이 정한 값이다. 실제 주행으로 확인한 뒤 조정한다.
private const val NEAR_ROAD_M = 300.0
private const val NEAR_HIGHWAY_M = 1000.0

private data class Ahead(val index: Int, val meters: Double, val type: TurnType)

// TBT 목록은 경로 전체이고 distance는 이전 안내 지점부터의 구간 거리다(에뮬레이터 로그로 확인).
// 그래서 순서대로 더하다가 처음 고른 지점과 그 지점까지의 거리를 돌려준다.
private fun firstAhead(tbtList: List<TBTInfo>, pick: (TurnType) -> Boolean): Ahead? {
    var fromHere = 0.0
    tbtList.forEachIndexed { index, tbt ->
        fromHere += tbt.distance
        if (pick(tbt.type)) return Ahead(index, fromHere, tbt.type)
    }
    return null
}

data class NextPractice(
    val index: Int,
    val meters: Double,
    val type: TurnType,
    val element: PracticeElement,
    val coaching: List<String>,
    val sign: String? = null,
) {
    val distance: String get() = formatDistance(meters)

    val title: String get() = practiceTitle(type)
    val isNear: Boolean
        get() = meters <= if (element == PracticeElement.HIGHWAY_IC) NEAR_HIGHWAY_M else NEAR_ROAD_M
    val stage: CoachStage? get() = coachStage(coaching, meters, element == PracticeElement.HIGHWAY_IC)
}

fun nextPractice(tbtList: List<TBTInfo>): NextPractice? =
    firstAhead(tbtList) { it.toPracticeElement() != null }?.let {
        NextPractice(
            it.index, it.meters, it.type, it.type.toPracticeElement()!!, it.type.coaching(),
            signText(tbtList[it.index].description),
        )
    }

// 바로 다음 회전이 가까이 붙어 있을 때 미리 알려 줄 내용.
data class FollowUp(val distance: String, val type: TurnType, val turn: String, val advice: String)

data class LanePrep(
    val index: Int,
    val distance: String,
    val turn: String,
    val advice: String,
    val type: TurnType? = null,
    val sign: String? = null,
    val followUp: FollowUp? = null,
)

// shortcut: 회전이 '연달아' 온다고 보는 거리. 근거 없이 정한 값이라 실제 주행으로 조정한다.
private const val FOLLOW_UP_M = 200.0

// 회전 직후 다음 회전까지 짧으면, 돌고 나서 어느 차선으로 가야 하는지 미리 알려 준다.
private fun followUp(tbtList: List<TBTInfo>, index: Int): FollowUp? {
    val first = tbtList[index].type
    val next = tbtList.getOrNull(index + 1) ?: return null
    if (next.distance > FOLLOW_UP_M) return null
    val (turn, _) = next.type.lanePrep() ?: return null
    val to = side(next.type) ?: return null
    val advice = if (side(first) == to) "$to 차선을 그대로 유지해요" else "$to 차선으로 옮길 준비를 해요"
    return FollowUp(formatDistance(next.distance), next.type, turn, advice)
}

private const val LEFT_LANE = "왼쪽 차선으로 미리 가 두면 편해요"
private const val RIGHT_LANE = "오른쪽 끝 차선으로 미리 가 두면 편해요"

// 차선 번호 데이터가 SDK에 없어서 방향만 참고로 알려준다. 차선 번호 안내는 확장 과제로 남긴다.
private fun TurnType.lanePrep(): Pair<String, String>? = when (this) {
    TurnType.TURN_LEFT -> "좌회전" to LEFT_LANE
    TurnType.TURN_SHARP_L -> "급좌회전" to LEFT_LANE
    TurnType.TURN_UTURN -> "유턴" to LEFT_LANE
    TurnType.TURN_RIGHT -> "우회전" to RIGHT_LANE
    TurnType.TURN_SHARP_R -> "급우회전" to RIGHT_LANE
    TurnType.TURN_ENTER_IC_L, TurnType.TURN_EXIT_IC_L -> "왼쪽 진출입로" to LEFT_LANE
    TurnType.TURN_ENTER_IC_R, TurnType.TURN_EXIT_IC_R -> "오른쪽 진출입로" to RIGHT_LANE
    TurnType.TURN_KEEP_L -> "왼쪽 길" to "왼쪽 차선을 따라가면 편해요"
    TurnType.TURN_KEEP_R -> "오른쪽 길" to "오른쪽 차선을 따라가면 편해요"
    else -> null
}

fun nextLanePrep(tbtList: List<TBTInfo>): LanePrep? =
    firstAhead(tbtList) { it.lanePrep() != null }?.let {
        val (turn, advice) = it.type.lanePrep()!!
        LanePrep(
            it.index, formatDistance(it.meters), turn, advice, it.type,
            signText(tbtList[it.index].description), followUp(tbtList, it.index),
        )
    }
