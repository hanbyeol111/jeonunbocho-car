package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RoutePlannerTest {
    private val start = LatLng(37.40, 127.10)

    private fun meters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * 6_371_000.0 * asin(sqrt(h))
    }

    private fun bearing(a: LatLng, b: LatLng): Double {
        val y = sin(Math.toRadians(b.lng - a.lng)) * cos(Math.toRadians(b.lat))
        val x = cos(Math.toRadians(a.lat)) * sin(Math.toRadians(b.lat)) -
            sin(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * cos(Math.toRadians(b.lng - a.lng))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    @Test
    fun offsetMovesByDistanceAndBearing() {
        val east = start.offset(90.0, 1000.0)
        assertEquals(1000.0, meters(start, east), 2.0)
        assertEquals(90.0, bearing(start, east), 0.5)
    }

    @Test
    fun waypointsFormEquilateralTriangle() {
        val (w1, w2) = loopWaypoints(start, 6.5, clockwise = true, attempt = 0)
        val side = 6500.0 / 3 / 1.3
        assertEquals(side, meters(start, w1), 3.0)
        assertEquals(side, meters(start, w2), 3.0)
        assertEquals(side, meters(w1, w2), 3.0)
    }

    @Test
    fun directionAndAttemptRotateWaypoints() {
        val (cw1, cw2) = loopWaypoints(start, 5.0, clockwise = true, attempt = 0)
        val (_, ccw2) = loopWaypoints(start, 5.0, clockwise = false, attempt = 0)
        assertEquals(0.0, bearing(start, cw1), 0.5)
        assertEquals(60.0, bearing(start, cw2), 0.5)
        assertEquals(300.0, bearing(start, ccw2), 0.5)
        val (next1, _) = loopWaypoints(start, 5.0, clockwise = true, attempt = 1)
        assertEquals(72.0, bearing(start, next1), 0.5)
    }

    private fun search(required: List<PracticeElement>) =
        CourseSearch("테스트", start, 5.0, true, required, RouteOption.RECOMMENDED)

    @Test
    fun searchAcceptsRouteWithAllRequiredSituations() {
        val route = listOf(
            TBTInfo(TurnType.TURN_LEFT, 100.0, ""),
            TBTInfo(TurnType.TURN_UTURN, 200.0, ""),
            TBTInfo(TurnType.TURN_ENTER_IC_R, 300.0, ""),
        )
        assertTrue(search(emptyList()).matches(route))
        assertTrue(search(listOf(PracticeElement.U_TURN)).matches(route))
        assertTrue(search(listOf(PracticeElement.U_TURN, PracticeElement.HIGHWAY_IC)).matches(route))
        assertFalse(search(listOf(PracticeElement.TUNNEL)).matches(route))
    }

    @Test
    fun summaryCountsTurnsSituationsAndPoints() {
        val route = listOf(
            TBTInfo(TurnType.TURN_LEFT, 80.0, ""),
            TBTInfo(TurnType.TURN_RIGHT, 200.0, ""),
            TBTInfo(TurnType.TURN_ENTER_ROUNDABOUT_06, 300.0, ""),
            TBTInfo(TurnType.TURN_RIGHT, 100.0, ""),
            TBTInfo(TurnType.TURN_UTURN, 400.0, ""),
            TBTInfo(TurnType.TURN_TUNNEL, 500.0, ""),
            TBTInfo(TurnType.TURN_ENTER_ROUNDABOUT_03, 300.0, ""),
            TBTInfo(TurnType.TURN_ARRIVE, 50.0, ""),
        )
        val s = summarizeRoute(distanceM = 6200, durationS = 1080, tbtList = route)
        assertEquals(2, s.rightTurns)
        assertEquals(1, s.leftTurns)
        assertEquals(1, s.uTurns)
        assertEquals(listOf("회전교차로" to 2, "터널" to 1), s.situations)
        assertEquals("좌회전", s.firstPrep?.turn)
        assertEquals(18, s.minutes)
        // 거리 6 + 시간 18분/5 = 3 + 회전교차로 14×2 + 유턴 15 + 터널 10 + 방향지시등 3×4 (안전 보너스는 속도를 재지 않아 0)
        assertEquals(6, s.bestPoints.distance)
        assertEquals(3, s.bestPoints.time)
        assertEquals(53, s.bestPoints.elements)
        assertEquals(12, s.bestPoints.turnSignalBonus)
        assertEquals(0, s.bestPoints.safetyBonus)
    }

    @Test
    fun searchStopsAfterMaxAttempts() {
        val s = search(listOf(PracticeElement.TUNNEL))
        repeat(MAX_SEARCH_ATTEMPTS - 1) { assertTrue(s.nextAttempt()) }
        assertFalse(s.nextAttempt())
    }
}
