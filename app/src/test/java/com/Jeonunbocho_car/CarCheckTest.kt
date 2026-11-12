package com.Jeonunbocho_car

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarCheckTest {
    @Test
    fun unsupportedSignalsHideTheirLine() {
        assertNull(beltStatus(null))
        assertNull(steeringStatus(null))
        assertNull(gearStatus(null, true))
        assertNull(gearStatus(VehicleGear.GEAR_UNKNOWN, true))
    }

    @Test
    fun beltShowsBuckledOrNot() {
        assertEquals(CheckStatus("운전석 안전벨트를 맸어요", true), beltStatus(true))
        assertEquals(CheckStatus("아직 운전석 안전벨트를 안 맸어요", false), beltStatus(false))
    }

    @Test
    fun steeringIsStraightWithinLimitEitherSide() {
        assertEquals(true, steeringStatus(-STRAIGHT_STEERING_DEG)?.ok)
        assertEquals(true, steeringStatus(STRAIGHT_STEERING_DEG)?.ok)
        assertEquals(false, steeringStatus(-45f)?.ok)
    }

    @Test
    fun brakeIsShownOnlyInPark() {
        assertEquals(CheckStatus("지금 기어 P · 브레이크 페달을 밟고 있어요", true), gearStatus(VehicleGear.GEAR_PARK, true))
        assertEquals(CheckStatus("지금 기어 P · 브레이크 페달을 밟고 기어를 바꿔요", false), gearStatus(VehicleGear.GEAR_PARK, false))
        assertEquals(CheckStatus("지금 기어 P", true), gearStatus(VehicleGear.GEAR_PARK, null))
        assertEquals(CheckStatus("지금 기어 D", true), gearStatus(VehicleGear.GEAR_DRIVE, false))
        assertEquals("지금 기어 D", gearStatus(VehicleGear.GEAR_3, true)?.text)
        assertEquals("지금 기어 R", gearStatus(VehicleGear.GEAR_REVERSE, true)?.text)
    }

    @Test
    fun onlyCarReadItemsHaveStatus() {
        val car = CarState(VehicleGear.GEAR_PARK, true, 0f, true)
        assertEquals(true, car.statusOf(CheckItem.SEAT)?.ok)
        assertEquals(true, car.statusOf(CheckItem.BELT)?.ok)
        assertEquals(true, car.statusOf(CheckItem.GEAR)?.ok)
        assertNull(car.statusOf(CheckItem.ROOM_MIRROR))
        assertNull(car.statusOf(CheckItem.SIDE_MIRROR))
        assertNull(car.statusOf(CheckItem.DASHBOARD))
    }
}
