package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceTest {
    private fun tbt(type: TurnType, distance: Double) = TBTInfo(type, distance, "")

    @Test
    fun turnTypesMapToPracticeElements() {
        assertEquals(PracticeElement.U_TURN, TurnType.TURN_UTURN.toPracticeElement())
        assertEquals(PracticeElement.ROUNDABOUT, TurnType.TURN_ENTER_ROUNDABOUT_06.toPracticeElement())
        assertEquals(PracticeElement.HIGHWAY_IC, TurnType.TURN_ENTER_IC_R.toPracticeElement())
        assertEquals(PracticeElement.HIGHWAY_IC, TurnType.TURN_EXIT_IC_C.toPracticeElement())
        assertEquals(PracticeElement.MERGE_SPLIT, TurnType.TURN_DEVIDE_MERGE.toPracticeElement())
        assertEquals(PracticeElement.TUNNEL, TurnType.TURN_TUNNEL.toPracticeElement())
        assertEquals(PracticeElement.OVERPASS_UNDERPASS, TurnType.TURN_ENTER_UNDER_L.toPracticeElement())
        assertEquals(PracticeElement.OVERPASS_UNDERPASS, TurnType.TURN_ENTER_OVER_C.toPracticeElement())
        assertEquals(PracticeElement.SHARP_TURN, TurnType.TURN_SHARP_L.toPracticeElement())
        assertEquals(PracticeElement.KEEP_LANE, TurnType.TURN_KEEP_R.toPracticeElement())
    }

    @Test
    fun ordinaryTurnsAreIgnored() {
        listOf(
            TurnType.TURN_LEFT, TurnType.TURN_RIGHT, TurnType.TURN_STRAIGHT,
            TurnType.TURN_EXIT_ROUNDABOUT, TurnType.TURN_AVOID_UNDER_R, TurnType.TURN_ARRIVE,
        ).forEach { assertNull(it.name, it.toPracticeElement()) }
    }

    @Test
    fun addsSegmentDistancesUpToFirstPracticePoint() {
        val list = listOf(
            tbt(TurnType.TURN_LEFT, 50.0),
            tbt(TurnType.TURN_RIGHT, 250.0),
            tbt(TurnType.TURN_UTURN, 100.0),
        )
        assertEquals(NextPractice("400m", PracticeElement.U_TURN), nextPractice(list))
    }

    // 에뮬레이터에서 나온 실제 사례: 구간 거리가 가장 작은 뒤쪽 유턴이 아니라 순서상 첫 IC를 골라야 한다.
    @Test
    fun laterPointWithShortSegmentIsNotPicked() {
        val list = listOf(
            tbt(TurnType.TURN_LEFT, 80.0),
            tbt(TurnType.TURN_RIGHT, 380.0),
            tbt(TurnType.TURN_AVOID_UNDER_R, 730.0),
            tbt(TurnType.TURN_ENTER_IC_R, 1700.0),
            tbt(TurnType.TURN_EXIT_IC_R, 10000.0),
            tbt(TurnType.TURN_UTURN, 80.0),
        )
        assertEquals(NextPractice("2.9km", PracticeElement.HIGHWAY_IC), nextPractice(list))
    }

    @Test
    fun formatsKilometers() {
        assertEquals(NextPractice("1.2km", PracticeElement.TUNNEL), nextPractice(listOf(tbt(TurnType.TURN_TUNNEL, 1234.0))))
        assertEquals("999m", formatDistance(999.4))
    }

    @Test
    fun noPracticePointAhead() {
        assertNull(nextPractice(emptyList()))
        assertNull(nextPractice(listOf(tbt(TurnType.TURN_RIGHT, 100.0))))
    }
}
