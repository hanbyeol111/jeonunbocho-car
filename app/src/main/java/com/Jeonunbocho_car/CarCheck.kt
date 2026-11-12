package com.Jeonunbocho_car

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import kotlin.math.abs

// 출발 전 확인에 보여 줄 차량 상태. 차종이 지원하지 않거나 아직 값이 안 왔으면 null이고, 그 줄은 숨긴다.
data class CarState(
    val gear: VehicleGear? = null,
    val brakePressed: Boolean? = null,
    val steeringDeg: Float? = null,
    val beltBuckled: Boolean? = null,
)

// shortcut: '똑바로'의 기준 각도는 근거 없이 정한 값. 실제 차에서 시험해 보고 조정한다.
const val STRAIGHT_STEERING_DEG = 10f

fun beltStatus(buckled: Boolean?): String? = when (buckled) {
    true -> "✓ 운전석 안전벨트를 맸어요"
    false -> "아직 운전석 안전벨트를 안 맸어요"
    null -> null
}

fun steeringStatus(deg: Float?): String? = when {
    deg == null -> null
    abs(deg) <= STRAIGHT_STEERING_DEG -> "✓ 핸들이 똑바로 있어요"
    else -> "핸들이 돌아가 있어요. 바퀴를 똑바로 하고 출발해요"
}

// P에서 기어를 바꿀 때는 브레이크 페달을 밟고 있어야 하므로, P일 때만 페달 상태를 함께 보여 준다.
fun gearStatus(gear: VehicleGear?, brakePressed: Boolean?): String? {
    val name = when (gear) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        null, VehicleGear.GEAR_UNKNOWN -> return null
        else -> "D"
    }
    val now = "지금 기어 $name"
    return when {
        name != "P" || brakePressed == null -> now
        brakePressed -> "$now · ✓ 브레이크 페달을 밟고 있어요"
        else -> "$now · 브레이크 페달을 밟고 기어를 바꿔요"
    }
}
