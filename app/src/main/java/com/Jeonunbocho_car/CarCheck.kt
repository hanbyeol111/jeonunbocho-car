package com.Jeonunbocho_car

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import kotlin.math.abs

// 출발 전 확인 항목. 순서가 화면 순서다.
enum class CheckItem(val title: String, val text: String) {
    SEAT("시트와 핸들", "페달을 끝까지 밟아도 무릎이 살짝 굽혀지고, 핸들에 손이 편하게 닿게 맞춰요."),
    ROOM_MIRROR("룸미러", "앉은 자세에서 뒤 유리 전체가 보이게 맞춰요."),
    SIDE_MIRROR("사이드 미러", "양쪽 모두 내 차 몸체가 미러 안쪽 끝에 조금만 보이게 맞춰요."),
    BELT("안전벨트", "운전자와 함께 탄 모든 사람이 매요."),
    GEAR("기어와 주차 브레이크", "주차 브레이크를 풀고, 기어를 P에서 D로 바꾸기 전에 한 번 더 확인해요."),
    DASHBOARD("계기판", "경고등이 켜져 있지 않은지, 연료가 충분한지 확인해요."),
}

// 출발 전 확인에 보여 줄 차량 상태. 차종이 지원하지 않거나 아직 값이 안 왔으면 null이고, 그 줄은 숨긴다.
data class CarState(
    val gear: VehicleGear? = null,
    val brakePressed: Boolean? = null,
    val steeringDeg: Float? = null,
    val beltBuckled: Boolean? = null,
)

// ok: 정상이면 초록, 아니면 노란색으로 눈에 띄게 보여 준다.
data class CheckStatus(val text: String, val ok: Boolean)

// shortcut: '똑바로'의 기준 각도는 근거 없이 정한 값. 실제 차에서 시험해 보고 조정한다.
const val STRAIGHT_STEERING_DEG = 10f

fun beltStatus(buckled: Boolean?): CheckStatus? = when (buckled) {
    true -> CheckStatus("운전석 안전벨트를 맸어요", ok = true)
    false -> CheckStatus("아직 운전석 안전벨트를 안 맸어요", ok = false)
    null -> null
}

fun steeringStatus(deg: Float?): CheckStatus? = when {
    deg == null -> null
    abs(deg) <= STRAIGHT_STEERING_DEG -> CheckStatus("핸들이 똑바로 있어요", ok = true)
    else -> CheckStatus("핸들이 돌아가 있어요. 바퀴를 똑바로 하고 출발해요", ok = false)
}

// P에서 기어를 바꿀 때는 브레이크 페달을 밟고 있어야 하므로, P일 때만 페달 상태를 함께 보여 준다.
fun gearStatus(gear: VehicleGear?, brakePressed: Boolean?): CheckStatus? {
    val name = when (gear) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        null, VehicleGear.GEAR_UNKNOWN -> return null
        else -> "D"
    }
    val now = "지금 기어 $name"
    return when {
        name != "P" || brakePressed == null -> CheckStatus(now, ok = true)
        brakePressed -> CheckStatus("$now · 브레이크 페달을 밟고 있어요", ok = true)
        else -> CheckStatus("$now · 브레이크 페달을 밟고 기어를 바꿔요", ok = false)
    }
}

// 차량에서 읽는 항목만 상태가 있다. 나머지는 사용자가 직접 체크한다.
fun CarState.statusOf(item: CheckItem): CheckStatus? = when (item) {
    CheckItem.SEAT -> steeringStatus(steeringDeg)
    CheckItem.BELT -> beltStatus(beltBuckled)
    CheckItem.GEAR -> gearStatus(gear, brakePressed)
    else -> null
}
