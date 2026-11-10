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
}
