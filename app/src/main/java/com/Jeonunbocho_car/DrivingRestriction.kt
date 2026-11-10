package com.Jeonunbocho_car

import android.car.drivingstate.CarUxRestrictions

// 코스 고르기·출발 같은 설정 화면은 주행 중 막는다(Pleos 지역별 주행 규제: 설정 UX_RESTRICTIONS_NO_SETUP).
// 어떤 제한을 걸지는 플랫폼이 지역에 맞게 내려주므로 받은 값만 따른다.
// 주행 화면(보기만 하는 안내)과 결과 화면은 막지 않는다.
fun blocksSetup(requiresDistractionOptimization: Boolean, activeRestrictions: Int): Boolean =
    requiresDistractionOptimization &&
        (activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) != 0
