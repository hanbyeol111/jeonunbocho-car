package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.RouteOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTest {
    @Test
    fun everyLevelMatchesTheTable() {
        val expected = listOf(
            Triple(3, emptyList(), RouteOption.COMFORTABLE),
            Triple(5, listOf(PracticeElement.TUNNEL), RouteOption.COMFORTABLE),
            Triple(7, listOf(PracticeElement.OVERPASS_UNDERPASS), RouteOption.COMFORTABLE),
            Triple(10, listOf(PracticeElement.SHARP_TURN), RouteOption.RECOMMENDED),
            Triple(13, listOf(PracticeElement.KEEP_LANE), RouteOption.RECOMMENDED),
            Triple(16, listOf(PracticeElement.ROUNDABOUT), RouteOption.RECOMMENDED),
            Triple(20, listOf(PracticeElement.U_TURN), RouteOption.SHORT_TIME),
            Triple(25, listOf(PracticeElement.MERGE_SPLIT), RouteOption.SHORT_TIME),
            Triple(30, listOf(PracticeElement.HIGHWAY_IC), RouteOption.HIGHWAY),
            Triple(
                40,
                listOf(PracticeElement.MERGE_SPLIT, PracticeElement.HIGHWAY_IC),
                RouteOption.HIGHWAY,
            ),
        )

        expected.forEachIndexed { index, (km, elements, option) ->
            val course = courseForLevel(index + 1)
            assertEquals(index + 1, course.level)
            assertEquals(km, course.distanceKm)
            assertEquals(elements, course.elements)
            assertEquals(option, course.routeOption)
        }
    }

    @Test
    fun distanceGrowsWithLevel() {
        val distances = (1..MAX_LEVEL).map { courseForLevel(it).distanceKm }
        assertTrue(distances.zipWithNext().all { (low, high) -> low < high })
    }

    @Test
    fun summaryShowsDistanceAndElements() {
        assertEquals("3km · 기본 주행", courseForLevel(1).summary)
        assertEquals("10km · ${PracticeElement.SHARP_TURN.label}", courseForLevel(4).summary)
        assertEquals(
            "40km · ${PracticeElement.MERGE_SPLIT.label} + ${PracticeElement.HIGHWAY_IC.label}",
            courseForLevel(10).summary,
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun unknownLevelIsRejected() {
        courseForLevel(11)
    }

    @Test
    fun bestCaseAddsDistanceSituationAndSignal() {
        // Lv.4: 거리 10 + 급회전 12 + 방향지시등 3 (속도를 재지 않아 안전 보너스 없음)
        val best = courseForLevel(4).bestCase
        assertEquals(10, best.distance)
        assertEquals(12, best.elements)
        assertEquals(3, best.turnSignalBonus)
        assertEquals(0, best.safetyBonus)
        assertEquals(25, best.total)
        // Lv.1은 상황이 없어 거리 3
        assertEquals(3, courseForLevel(1).bestCase.total)
    }

    @Test
    fun runsRoundUp() {
        assertEquals(4, courseForLevel(4).runsFor(100))
        assertEquals(1, courseForLevel(4).runsFor(25))
        assertEquals(2, courseForLevel(4).runsFor(26))
    }

    @Test
    fun routeOptionsHaveKoreanNames() {
        assertEquals("추천 경로", RouteOption.RECOMMENDED.label)
        assertEquals("고속도로 우선", RouteOption.HIGHWAY.label)
    }
}
