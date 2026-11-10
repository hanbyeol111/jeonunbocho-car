package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.RouteOption

data class LevelCourse(
    val level: Int,
    val distanceKm: Int,
    val elements: List<PracticeElement>,
    val routeOption: RouteOption,
) {
    val elementsText: String
        get() = elements.joinToString(" + ") { it.label }.ifEmpty { "기본 주행" }

    val summary: String
        get() = "${distanceKm}km · $elementsText"
}

private val LEVEL_COURSES = listOf(
    LevelCourse(1, 3, emptyList(), RouteOption.COMFORTABLE),
    LevelCourse(2, 5, listOf(PracticeElement.TUNNEL), RouteOption.COMFORTABLE),
    LevelCourse(3, 7, listOf(PracticeElement.OVERPASS_UNDERPASS), RouteOption.COMFORTABLE),
    LevelCourse(4, 10, listOf(PracticeElement.SHARP_TURN), RouteOption.RECOMMENDED),
    LevelCourse(5, 13, listOf(PracticeElement.KEEP_LANE), RouteOption.RECOMMENDED),
    LevelCourse(6, 16, listOf(PracticeElement.ROUNDABOUT), RouteOption.RECOMMENDED),
    LevelCourse(7, 20, listOf(PracticeElement.U_TURN), RouteOption.SHORT_TIME),
    LevelCourse(8, 25, listOf(PracticeElement.MERGE_SPLIT), RouteOption.SHORT_TIME),
    LevelCourse(9, 30, listOf(PracticeElement.HIGHWAY_IC), RouteOption.HIGHWAY),
    LevelCourse(
        10, 40,
        listOf(PracticeElement.MERGE_SPLIT, PracticeElement.HIGHWAY_IC),
        RouteOption.HIGHWAY,
    ),
)

fun courseForLevel(level: Int): LevelCourse = LEVEL_COURSES.first { it.level == level }

// 완주했을 때 받을 수 있는 최대 포인트. 상황마다 방향지시등을 켜고 과속·급감속이 없다고 본다.
// 예상 시간 포인트는 경로를 받아야 알 수 있어서 빼고, 주의 지점 보너스도 경로마다 달라 뺀다.
val LevelCourse.bestCase: EarnedPoints
    get() = calculatePoints(
        DriveRecord(distanceKm.toDouble(), 0, elements.map { PassedElement(it, turnSignalOn = true) })
    )

// 남은 포인트를 이 코스의 최대 포인트로 채우려면 몇 번 달려야 하는지(올림).
fun LevelCourse.runsFor(remainingPoints: Int): Int =
    (remainingPoints + bestCase.total - 1) / bestCase.total

// SDK 경로 옵션 이름을 우리말로 옮긴 것. 기본 내비 메뉴 이름과 같은지는 확인하지 못했다.
val RouteOption.label: String
    get() = when (this) {
        RouteOption.COMFORTABLE -> "편안한 길"
        RouteOption.RECOMMENDED -> "추천 경로"
        RouteOption.SHORT_TIME -> "최소 시간"
        RouteOption.SHORT_DISTANCE -> "최단 거리"
        RouteOption.HIGHWAY -> "고속도로 우선"
        RouteOption.FREE -> "무료 도로"
        RouteOption.SCHOOL_ZONE -> "어린이보호구역 회피"
    }
