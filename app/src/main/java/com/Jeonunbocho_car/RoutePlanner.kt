package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import kotlin.math.cos
import kotlin.math.sin

data class LatLng(val lat: Double, val lng: Double)

private const val EARTH_RADIUS_M = 6_371_000.0

// shortcut: 도로는 직선보다 길어서 대략 1.3배로 잡는다. 실제 경로 거리와 비교해 조정한다.
private const val ROAD_FACTOR = 1.3

// 다시 찾을 때마다 경유지 방향을 72도씩 돌려 다른 길을 시도한다(5번이면 한 바퀴).
private const val ATTEMPT_ROTATION_DEG = 72.0

const val MAX_SEARCH_ATTEMPTS = 5

// 짧은 거리라 평면 근사로 충분하다.
fun LatLng.offset(bearingDeg: Double, meters: Double): LatLng {
    val bearing = Math.toRadians(bearingDeg)
    val dLat = meters * cos(bearing) / EARTH_RADIUS_M
    val dLng = meters * sin(bearing) / (EARTH_RADIUS_M * cos(Math.toRadians(lat)))
    return LatLng(lat + Math.toDegrees(dLat), lng + Math.toDegrees(dLng))
}

// 출발지 → 경유지1 → 경유지2 → 출발지로 도는 정삼각형 코스.
// 경유지2가 경유지1보다 시계 방향에 있으면 시계 방향으로 돌고, 우측 통행이라 우회전이 많아진다.
fun loopWaypoints(start: LatLng, distanceKm: Double, clockwise: Boolean, attempt: Int): List<LatLng> {
    val side = distanceKm * 1000 / 3 / ROAD_FACTOR
    val first = attempt * ATTEMPT_ROTATION_DEG
    val second = if (clockwise) first + 60 else first - 60
    return listOf(start.offset(first, side), start.offset(second, side))
}

// 한 번의 길 찾기. 경로 후보를 요청하고, 회전 목록에 필요한 연습 상황이 모두 있으면 채택한다.
class CourseSearch(
    val title: String,
    val start: LatLng,
    val distanceKm: Double,
    val clockwise: Boolean,
    val required: List<PracticeElement>,
    val routeOption: RouteOption,
) {
    var attempt = 0
        private set

    val waypoints: List<LatLng> get() = loopWaypoints(start, distanceKm, clockwise, attempt)

    fun matches(tbtList: List<TBTInfo>): Boolean =
        required.all { element -> tbtList.any { it.type.toPracticeElement() == element } }

    // 다음 시도가 남아 있으면 true.
    fun nextAttempt(): Boolean {
        attempt++
        return attempt < MAX_SEARCH_ATTEMPTS
    }
}

// 출발 전 코스 확인 화면에 보여줄 요약. 모두 내비가 준 경로 데이터에서 센다.
data class RouteSummary(
    val distanceM: Int,
    val durationS: Int,
    val rightTurns: Int,
    val leftTurns: Int,
    val uTurns: Int,
    // 유턴을 뺀 연습 상황과 개수. 경로에 나오는 순서대로.
    val situations: List<Pair<String, Int>>,
    val firstPrep: LanePrep?,
    // 회전이 연달아 오는 곳(출발 전 '미리 알아 둘 구간').
    val tightTurns: List<TightTurn>,
    // 모든 연습 상황에서 방향지시등을 켜고 과속·급감속이 없을 때의 최대치.
    val bestPoints: EarnedPoints,
    val practiceElements: List<PracticeElement>,
) {
    val minutes: Int get() = (durationS + 59) / 60
}

fun summarizeRoute(distanceM: Int, durationS: Int, tbtList: List<TBTInfo>): RouteSummary {
    val types = tbtList.map { it.type }
    val practice = types.filter { it.toPracticeElement() != null }
    val elements = practice.map { it.toPracticeElement()!! }
    return RouteSummary(
        distanceM = distanceM,
        durationS = durationS,
        rightTurns = types.count { it == TurnType.TURN_RIGHT || it == TurnType.TURN_SHARP_R },
        leftTurns = types.count { it == TurnType.TURN_LEFT || it == TurnType.TURN_SHARP_L },
        uTurns = types.count { it == TurnType.TURN_UTURN },
        situations = practice.filter { it != TurnType.TURN_UTURN }
            .map { practiceTitle(it) }.groupingBy { it }.eachCount().toList(),
        firstPrep = nextLanePrep(tbtList),
        tightTurns = tightTurns(tbtList),
        bestPoints = calculatePoints(
            DriveRecord(distanceM / 1000.0, (durationS + 59) / 60, elements.map { PassedElement(it, turnSignalOn = true) })
        ),
        practiceElements = elements,
    )
}
