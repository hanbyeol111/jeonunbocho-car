package com.Jeonunbocho_car

import android.car.drivingstate.CarUxRestrictions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRestrictionTest {
    @Test
    fun setupIsBlockedOnlyWhenPlatformSaysNoSetup() {
        val noSetup = CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP
        assertTrue(blocksSetup(true, noSetup or CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO))
        assertFalse(blocksSetup(true, CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO)) // 영상만 막는 지역·상황
        assertFalse(blocksSetup(false, noSetup)) // 정차 중
    }
}
