package com.Jeonunbocho_car

enum class PracticeElement(val label: String, val points: Int) {
    TUNNEL("터널", 10),
    OVERPASS_UNDERPASS("고가도로·지하차도", 11),
    SHARP_TURN("급회전", 12),
    KEEP_LANE("갈림길 차로 유지", 13),
    ROUNDABOUT("회전교차로", 14),
    U_TURN("유턴", 15),
    MERGE_SPLIT("합류·분기", 16),
    HIGHWAY_IC("고속도로 진입·진출", 17),
}

// rerouted: 재탐색이 일어나 지점을 제대로 통과하지 못한 경우. 포인트를 주지 않는다.
data class PassedElement(
    val element: PracticeElement,
    val turnSignalOn: Boolean,
    val rerouted: Boolean = false,
)

// 급감속·급정지·급회전·급U턴 횟수는 위험운전행동 판별 기준(2022)의 택시 기준으로 센다.
// 과속은 그 기준보다 엄격하게, 제한 속도를 넘으면 1회로 센다.
// 차선 변경은 공식 기준이 없어 핸들과 진행 방향의 움직임으로 추정한 횟수를 쓴다.
data class DriveRecord(
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val elements: List<PassedElement>,
    val speedingCount: Int = 0,
    val cautionPassedCount: Int = 0,
    val cautionSpeedingCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val hardStopCount: Int = 0,
    val hardTurnCount: Int = 0,
    val hardUTurnCount: Int = 0,
    val laneChangeNoSignalCount: Int = 0,
    // 속도를 재지 않은 주행은 과속·급감속 0회가 '안 쟀다'는 뜻이라 안전 보너스를 주지 않는다.
    val safetyMeasured: Boolean = false,
)

data class EarnedPoints(
    val distance: Int,
    val time: Int,
    val elements: Int,
    val turnSignalBonus: Int,
    val safetyBonus: Int,
    val penalty: Int,
) {
    val total: Int get() = distance + time + elements + turnSignalBonus + safetyBonus - penalty
}

private const val MINUTES_PER_POINT = 5
const val TURN_SIGNAL_BONUS = 3
private const val NO_SPEEDING_BONUS = 5
private const val NO_HARD_BRAKE_BONUS = 5
private const val CAUTION_PASS_BONUS = 2
private const val SPEEDING_PENALTY = 3
private const val CAUTION_SPEEDING_PENALTY = 3
private const val HARD_BRAKE_PENALTY = 2
private const val HARD_TURN_PENALTY = 3
private const val TURN_SIGNAL_PENALTY = 2

// 거리와 시간 포인트는 소수점을 버린다.
// 시간 포인트는 출발 전 예상 시간으로 계산한다.
// 실제 주행 시간은 정체나 신호 대기처럼 운전 실력과 무관한 요인으로 달라지기 때문이다.
fun calculatePoints(record: DriveRecord): EarnedPoints {
    val passed = record.elements.filterNot { it.rerouted }
    return EarnedPoints(
        distance = record.distanceKm.toInt(),
        time = record.estimatedMinutes / MINUTES_PER_POINT,
        elements = passed.sumOf { it.element.points },
        turnSignalBonus = passed.count { it.turnSignalOn } * TURN_SIGNAL_BONUS,
        safetyBonus = if (!record.safetyMeasured) 0 else
            (if (record.speedingCount == 0) NO_SPEEDING_BONUS else 0) +
                (if (record.hardBrakeCount == 0) NO_HARD_BRAKE_BONUS else 0) +
                record.cautionPassedCount * CAUTION_PASS_BONUS,
        penalty = record.speedingCount * SPEEDING_PENALTY +
                record.cautionSpeedingCount * CAUTION_SPEEDING_PENALTY +
                (record.hardBrakeCount + record.hardStopCount) * HARD_BRAKE_PENALTY +
                (record.hardTurnCount + record.hardUTurnCount) * HARD_TURN_PENALTY +
                passed.count { !it.turnSignalOn } * TURN_SIGNAL_PENALTY +
                record.laneChangeNoSignalCount * TURN_SIGNAL_PENALTY,
    )
}

const val MAX_LEVEL = 10

// 인덱스 0이 Lv.1 → Lv.2에 필요한 포인트다.
private val REQUIRED_POINTS = listOf(50, 75, 125, 200, 300, 425, 575, 750, 950)

data class Progress(val level: Int = 1, val points: Int = 0) {
    val isMax: Boolean get() = level >= MAX_LEVEL
    val required: Int? get() = REQUIRED_POINTS.getOrNull(level - 1)

    // 레벨이 오르면 포인트는 0부터 다시 시작하고, 넘친 포인트는 이월하지 않는다.
    // 합계가 음수면 보유 포인트에서 빼고, 모자라면 이전 레벨로 내려간다.
    fun add(earned: Int): Progress {
        if (earned < 0) return subtract(-earned)
        val needed = required ?: return this
        val sum = points + earned
        return if (sum >= needed) Progress(level + 1, 0) else copy(points = sum)
    }

    private fun subtract(lost: Int): Progress {
        var newLevel = level
        var newPoints = points - lost
        while (newPoints < 0 && newLevel > 1) {
            newLevel--
            newPoints += REQUIRED_POINTS[newLevel - 1]
        }
        return Progress(newLevel, maxOf(newPoints, 0))
    }
}