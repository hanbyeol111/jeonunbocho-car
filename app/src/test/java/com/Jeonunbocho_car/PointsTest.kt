package com.Jeonunbocho_car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsTest {

    @Test
    fun documentExampleGives44Points() {
        val record = DriveRecord(
            distanceKm = 10.0,
            estimatedMinutes = 30,
            elements = listOf(PassedElement(PracticeElement.U_TURN, turnSignalOn = true)),
        )

        val earned = calculatePoints(record)

        assertEquals(10, earned.distance)
        assertEquals(6, earned.time)
        assertEquals(15, earned.elements)
        assertEquals(3, earned.turnSignalBonus)
        assertEquals(10, earned.safetyBonus)
        assertEquals(44, earned.total)
    }

    @Test
    fun distanceAndTimeRoundDown() {
        val earned = calculatePoints(DriveRecord(12.8, 34, emptyList()))

        assertEquals(12, earned.distance)
        assertEquals(6, earned.time)
    }

    @Test
    fun reroutedElementGivesNoPointsOrBonus() {
        val record = DriveRecord(
            distanceKm = 0.0,
            estimatedMinutes = 0,
            elements = listOf(
                PassedElement(PracticeElement.U_TURN, turnSignalOn = true, rerouted = true),
                PassedElement(PracticeElement.ROUNDABOUT, turnSignalOn = false),
            ),
        )

        val earned = calculatePoints(record)

        assertEquals(14, earned.elements)
        assertEquals(0, earned.turnSignalBonus)
    }

    @Test
    fun elementPointsRiseOnePointFromEasyToHard() {
        val points = PracticeElement.entries.map { it.points }

        assertEquals((10..17).toList(), points)
    }

    @Test
    fun pointsAccumulateBelowRequirement() {
        assertEquals(Progress(1, 49), Progress(1, 40).add(9))
    }

    @Test
    fun levelUpResetsPointsToZeroWithoutCarryOver() {
        assertEquals(Progress(2, 0), Progress(1, 45).add(34))
    }

    @Test
    fun requiredPointsFollowAgreedTable() {
        val required = (1 until MAX_LEVEL).map { Progress(it).required }

        assertEquals(listOf(50, 75, 125, 200, 300, 425, 575, 750, 950), required)
    }

    @Test
    fun levelTenIsMaxAndStopsAccumulating() {
        val max = Progress(9, 949).add(1)

        assertEquals(Progress(10, 0), max)
        assertTrue(max.isMax)
        assertNull(max.required)
        assertEquals(max, max.add(1000))
    }


    @Test
    fun safetyBonusNeedsNoSpeedingAndNoHardBrake() {
        val earned = calculatePoints(
            DriveRecord(0.0, 0, emptyList(), speedingCount = 1, cautionPassedCount = 2)
        )

        assertEquals(9, earned.safetyBonus)
    }

    @Test
    fun penaltiesAreSubtractedFromTotal() {
        val earned = calculatePoints(
            DriveRecord(
                distanceKm = 0.0,
                estimatedMinutes = 0,
                elements = emptyList(),
                speedingCount = 1,
                cautionSpeedingCount = 1,
                hardBrakeCount = 1,
                hardStopCount = 1,
                hardTurnCount = 1,
                hardUTurnCount = 1,
            )
        )

        assertEquals(16, earned.penalty)
        assertEquals(-16, earned.total)
    }

    @Test
    fun negativeTotalLowersLevelWhenPointsRunOut() {
        assertEquals(Progress(3, 5), Progress(3, 10).add(-5))
        assertEquals(Progress(2, 70), Progress(3, 10).add(-15))
    }

    @Test
    fun levelOneStopsAtZero() {
        assertEquals(Progress(1, 0), Progress(1, 5).add(-10))
    }

    @Test
    fun maxLevelDropsOnNegativeTotal() {
        assertEquals(Progress(9, 947), Progress(10, 0).add(-3))
    }

    @Test
    fun missingTurnSignalIsPenalized() {
        val earned = calculatePoints(
            DriveRecord(
                distanceKm = 0.0,
                estimatedMinutes = 0,
                elements = listOf(
                    PassedElement(PracticeElement.U_TURN, turnSignalOn = false),
                    PassedElement(PracticeElement.ROUNDABOUT, turnSignalOn = false, rerouted = true),
                ),
                laneChangeNoSignalCount = 1,
            )
        )

        assertEquals(4, earned.penalty)
    }
}
