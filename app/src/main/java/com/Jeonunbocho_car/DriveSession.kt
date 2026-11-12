package com.Jeonunbocho_car

import ai.pleos.playground.navi.data.TBTInfo

// 한 번의 주행 기록. 내비 회전 목록·재탐색·방향지시등 신호를 받아 통과한 연습 상황을 센다.
// plannedDistanceM: 출발 전 경로 거리. 모르면(목적지 안내) 첫 회전 목록의 거리 합을 쓴다.
// signalMeasured: 이 차량에서 방향지시등을 읽을 수 있는지. 못 읽으면 방향지시등 평가를 뺀다.
class DriveSession(
    private var plannedDistanceM: Double?,
    private val estimatedMinutes: Int,
    var signalMeasured: Boolean = true,
) {
    private val passed = mutableListOf<PassedElement>()
    private var ahead: List<PracticeElement> = emptyList()
    private var near = false
    private var signalOn = false
    private var signalUsedNear = false
    private var remainingM: Double? = null
    private var reroutedElement: PracticeElement? = null

    val passedElements: List<PassedElement> get() = passed

    // shortcut: 남은 연습 상황 수가 줄면 앞에 있던 것을 통과한 것으로 본다.
    // 주행 중 회전 목록이 이렇게 줄어드는지는 에뮬레이터 주행으로 확인해야 한다.
    fun onTbt(tbtList: List<TBTInfo>) {
        val now = tbtList.mapNotNull { it.type.toPracticeElement() }
        val gone = ahead.size - now.size
        if (gone > 0) {
            ahead.take(gone).forEachIndexed { i, element ->
                // 재탐색으로 이미 기록한 상황을 새 경로에서 다시 지나면 두 번 세지 않는다.
                if (i == 0 && element == reroutedElement) return@forEachIndexed
                // 방향지시등은 바로 앞에 있던 상황에만 붙인다.
                passed += PassedElement(element, turnSignalOn = i == 0 && signalUsedNear)
            }
            signalUsedNear = false
            reroutedElement = null
        }
        ahead = now
        val total = tbtList.sumOf { it.distance }
        if (plannedDistanceM == null) plannedDistanceM = total
        remainingM = total
        near = nextPractice(tbtList)?.isNear == true
        if (near && signalOn) signalUsedNear = true
    }

    fun onTurnSignal(on: Boolean) {
        signalOn = on
        if (on && near) signalUsedNear = true
    }

    // 가까운 상황 앞에서 재탐색되면 그 상황은 포인트 없이 기록하고, 다음 목록부터 새로 센다.
    fun onReRoute() {
        reroutedElement = if (near) ahead.firstOrNull() else null
        reroutedElement?.let { passed += PassedElement(it, signalUsedNear, rerouted = true) }
        ahead = emptyList()
        near = false
        signalUsedNear = false
    }

    // 코스 진행 표시용. 첫 회전 목록을 받기 전에는 null.
    val practicesLeft: Int? get() = remainingM?.let { ahead.size }
    val remainingKm: Double? get() = remainingM?.let { it / 1000 }
    val progressPercent: Int?
        get() {
            val planned = plannedDistanceM ?: return null
            if (remainingM == null || planned <= 0) return null
            return (drivenKm * 1000 / planned * 100).toInt().coerceIn(0, 100)
        }

    val drivenKm: Double
        get() {
            val planned = plannedDistanceM ?: return 0.0
            return ((planned - (remainingM ?: planned)) / 1000).coerceAtLeast(0.0)
        }

    // 완주: 경로 전체 거리와 예상 시간. 도중에 끝내면 포인트를 주지 않으므로 기록만 쓴다.
    fun record(arrived: Boolean): DriveRecord = DriveRecord(
        distanceKm = if (arrived) (plannedDistanceM ?: 0.0) / 1000 else drivenKm,
        estimatedMinutes = if (arrived) estimatedMinutes else 0,
        elements = passed.toList(),
        turnSignalMeasured = signalMeasured,
    )
}
