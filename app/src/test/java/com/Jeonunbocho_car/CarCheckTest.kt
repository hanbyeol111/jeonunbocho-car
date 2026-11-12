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
        assertEquals("✓ 운전석 안전벨트를 맸어요", beltStatus(true))
        assertEquals("아직 운전석 안전벨트를 안 맸어요", beltStatus(false))
    }

    @Test
    fun steeringIsStraightWithinLimitEitherSide() {
        assertEquals("✓ 핸들이 똑바로 있어요", steeringStatus(-STRAIGHT_STEERING_DEG))
        assertEquals("✓ 핸들이 똑바로 있어요", steeringStatus(STRAIGHT_STEERING_DEG))
        assertEquals("핸들이 돌아가 있어요. 바퀴를 똑바로 하고 출발해요", steeringStatus(-45f))
    }

    @Test
    fun brakeIsShownOnlyInPark() {
        assertEquals("지금 기어 P · ✓ 브레이크 페달을 밟고 있어요", gearStatus(VehicleGear.GEAR_PARK, true))
        assertEquals("지금 기어 P · 브레이크 페달을 밟고 기어를 바꿔요", gearStatus(VehicleGear.GEAR_PARK, false))
        assertEquals("지금 기어 P", gearStatus(VehicleGear.GEAR_PARK, null))
        assertEquals("지금 기어 D", gearStatus(VehicleGear.GEAR_DRIVE, false))
        assertEquals("지금 기어 D", gearStatus(VehicleGear.GEAR_3, true))
        assertEquals("지금 기어 R", gearStatus(VehicleGear.GEAR_REVERSE, true))
    }
}
