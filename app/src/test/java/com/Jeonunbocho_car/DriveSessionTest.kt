package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveSessionTest {
    private fun tbt(type: TurnType, distance: Double) = TBTInfo(type, distance, "")

    // 유턴 → 회전교차로 → 도착, 총 2.0km
    private val full = listOf(
        tbt(TurnType.TURN_UTURN, 500.0),
        tbt(TurnType.TURN_ENTER_ROUNDABOUT_06, 700.0),
        tbt(TurnType.TURN_ARRIVE, 800.0),
    )

    @Test
    fun countsPassedSituationWithSignalUsedNearby() {
        val s = DriveSession(plannedDistanceM = 2000.0, estimatedMinutes = 10)
        s.onTbt(full)
        s.onTbt(listOf(tbt(TurnType.TURN_UTURN, 200.0)) + full.drop(1)) // 가까워짐
        s.onTurnSignal(true)
        s.onTurnSignal(false)
        s.onTbt(listOf(tbt(TurnType.TURN_ENTER_ROUNDABOUT_06, 600.0), full[2])) // 유턴 통과
        assertEquals(listOf(PassedElement(PracticeElement.U_TURN, turnSignalOn = true)), s.passedElements)
        assertEquals(0.6, s.drivenKm, 0.001)
    }

    @Test
    fun signalUsedFarAwayDoesNotCount() {
        val s = DriveSession(2000.0, 10)
        s.onTbt(full) // 유턴까지 500m: 아직 멀다
        s.onTurnSignal(true)
        s.onTurnSignal(false)
        s.onTbt(full.drop(1))
        assertEquals(listOf(PassedElement(PracticeElement.U_TURN, turnSignalOn = false)), s.passedElements)
    }

    @Test
    fun signalAlreadyOnWhenComingNearCounts() {
        val s = DriveSession(2000.0, 10)
        s.onTbt(full)
        s.onTurnSignal(true)
        s.onTbt(listOf(tbt(TurnType.TURN_UTURN, 100.0)) + full.drop(1))
        s.onTbt(full.drop(1))
        assertEquals(true, s.passedElements.single().turnSignalOn)
    }

    @Test
    fun rerouteNearSituationRecordsItOnceWithoutPoints() {
        val s = DriveSession(2000.0, 10)
        s.onTbt(listOf(tbt(TurnType.TURN_UTURN, 100.0)) + full.drop(1))
        s.onReRoute()
        // 새 경로도 유턴을 지나간다
        s.onTbt(listOf(tbt(TurnType.TURN_UTURN, 300.0)) + full.drop(1))
        s.onTbt(full.drop(1))
        assertEquals(listOf(PassedElement(PracticeElement.U_TURN, false, rerouted = true)), s.passedElements)
        assertEquals(0, calculatePoints(s.record(arrived = true)).elements)
    }

    @Test
    fun progressShowsLeftSituationsAndDistance() {
        val s = DriveSession(2000.0, 10)
        assertEquals(null, s.progressPercent)
        s.onTbt(full.drop(1)) // 유턴 지나 1.5km 남음
        assertEquals(1, s.practicesLeft)
        assertEquals(1.5, s.remainingKm!!, 0.001)
        assertEquals(25, s.progressPercent)
    }

    @Test
    fun arrivedUsesWholeRouteAndEstimatedTime() {
        val s = DriveSession(null, 18)
        s.onTbt(full)
        val record = s.record(arrived = true)
        assertEquals(2.0, record.distanceKm, 0.001)
        assertEquals(18, record.estimatedMinutes)
        assertEquals(0, s.record(arrived = false).estimatedMinutes)
    }
}
