package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val next = nextPractice(list)!!
        assertEquals(PracticeElement.U_TURN, next.element)
        assertEquals("400m", next.distance)
        assertEquals(2, next.index)
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
        val next = nextPractice(list)!!
        assertEquals(PracticeElement.HIGHWAY_IC, next.element)
        assertEquals("고속도로 진입", next.title)
        assertEquals("2.9km", next.distance)
    }

    @Test
    fun highwayExitIsNamedSeparately() {
        assertEquals("고속도로 진출", nextPractice(listOf(tbt(TurnType.TURN_EXIT_IC_L, 10.0)))!!.title)
        assertEquals(PracticeElement.U_TURN.label, nextPractice(listOf(tbt(TurnType.TURN_UTURN, 10.0)))!!.title)
    }

    @Test
    fun nearThresholdDependsOnSituation() {
        assertTrue(nextPractice(listOf(tbt(TurnType.TURN_UTURN, 300.0)))!!.isNear)
        assertFalse(nextPractice(listOf(tbt(TurnType.TURN_UTURN, 301.0)))!!.isNear)
        assertTrue(nextPractice(listOf(tbt(TurnType.TURN_EXIT_IC_R, 1000.0)))!!.isNear)
        assertFalse(nextPractice(listOf(tbt(TurnType.TURN_EXIT_IC_R, 1001.0)))!!.isNear)
    }

    @Test
    fun coachingIncludesSignalTiming() {
        assertEquals("30m 전부터 왼쪽 깜빡이", TurnType.TURN_UTURN.coaching().last())
        assertEquals("100m 전부터 오른쪽 깜빡이", TurnType.TURN_EXIT_IC_R.coaching().last())
        assertEquals("30m 전부터 오른쪽 깜빡이", TurnType.TURN_ENTER_IC_R.coaching().last())
        assertEquals("차로를 바꿀 때 깜빡이", TurnType.TURN_EXIT_IC_C.coaching().last())
        assertEquals("차로를 바꿀 때 왼쪽 깜빡이", TurnType.TURN_KEEP_L.coaching().last())
        assertEquals(listOf("전조등 켜기", "터널 안에서는 앞지르기 금지"), TurnType.TURN_TUNNEL.coaching())
    }

    @Test
    fun everyPracticeTypeHasShortCoaching() {
        TurnType.entries.filter { it.toPracticeElement() != null }.forEach {
            val lines = it.coaching()
            assertTrue(it.name, lines.size in 1..3)
        }
    }

    @Test
    fun lanePrepUsesFirstTurnWithDirection() {
        val list = listOf(
            tbt(TurnType.TURN_STRAIGHT, 100.0),
            tbt(TurnType.TURN_TOLL, 200.0),
            tbt(TurnType.TURN_LEFT, 100.0),
            tbt(TurnType.TURN_RIGHT, 50.0),
        )
        assertEquals(
            LanePrep(2, "400m", "좌회전", "왼쪽 차선으로 미리 가 두면 편해요", "30m 전부터 왼쪽 깜빡이"),
            nextLanePrep(list),
        )
    }

    @Test
    fun lanePrepDirections() {
        assertEquals("오른쪽 끝 차선으로 미리 가 두면 편해요", nextLanePrep(listOf(tbt(TurnType.TURN_RIGHT, 10.0)))?.advice)
        assertEquals("30m 전부터 오른쪽 깜빡이", nextLanePrep(listOf(tbt(TurnType.TURN_RIGHT, 10.0)))?.signal)
        assertEquals("100m 전부터 오른쪽 깜빡이", nextLanePrep(listOf(tbt(TurnType.TURN_EXIT_IC_R, 10.0)))?.signal)
        assertEquals("오른쪽 길", nextLanePrep(listOf(tbt(TurnType.TURN_KEEP_R, 10.0)))?.turn)
        assertNull(nextLanePrep(listOf(tbt(TurnType.TURN_ENTER_ROUNDABOUT_06, 10.0), tbt(TurnType.TURN_ARRIVE, 5.0))))
    }

    @Test
    fun formatsKilometers() {
        assertEquals("1.2km", nextPractice(listOf(tbt(TurnType.TURN_TUNNEL, 1234.0)))!!.distance)
        assertEquals("999m", formatDistance(999.4))
    }

    @Test
    fun noPracticePointAhead() {
        assertNull(nextPractice(emptyList()))
        assertNull(nextPractice(listOf(tbt(TurnType.TURN_RIGHT, 100.0))))
    }

    @Test
    fun splitsNumberFromUnit() {
        assertEquals("80" to "m", splitUnit("80m"))
        assertEquals("1.2" to "km", splitUnit("1.2km"))
    }

    @Test
    fun picksSubjectParticle() {
        assertEquals("이", subjectParticle("유턴"))
        assertEquals("가", subjectParticle("회전교차로"))
        assertEquals("이", subjectParticle("고속도로 진입·진출"))
    }
}
