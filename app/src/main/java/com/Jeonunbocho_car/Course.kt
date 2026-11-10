package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.RouteOption

data class LevelCourse(
    val level: Int,
    val distanceKm: Int,
    val elements: List<PracticeElement>,
    val routeOption: RouteOption,
) {
    val summary: String
        get() = "${distanceKm}km · ${elements.joinToString(" + ") { it.label }.ifEmpty { "기본 주행" }}"
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
