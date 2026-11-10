package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider

private const val SEARCH_TIMEOUT_MS = 15_000L

// shortcut: 상황별 연습은 5km 순환 코스 안에서 찾는다. 찾는 비율을 보고 조정한다.
private const val PRACTICE_SEARCH_KM = 5.0

class MainActivity : AppCompatActivity() {

    private lateinit var naviHelper: NaviHelper
    private var naviStatus = "내비 연결 중…"
    private lateinit var progress: Progress
    private var loopKm = 5f
    private var loopClockwise = true
    @Volatile private var naviConnected = false

    // 길 찾기 상태. 모든 값은 UI 스레드에서만 바꾼다.
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSearch: ((LatLng) -> CourseSearch)? = null
    private var search: CourseSearch? = null
    private var awaitingRoute = false
    private var routeStart: RouteStartInfo? = null
    private val searchTimeout = Runnable { searchStepFailed() }

    // 전운보초는 소리를 내지 않고, 주행 화면 문구만 바꾼다.
    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            // shortcut: TBT 원본 확인용 임시 로그. 유턴 표시 문제와 목록 범위를 확인하면 지운다.
            Log.d("JbTBT", "목록 ${tbtInfoList.size}개")
            tbtInfoList.forEach { Log.d("JbTBT", "${it.type} ${it.distance}m ${it.description}") }
            runOnUiThread {
                if (awaitingRoute && routeStart != null) checkCandidate(tbtInfoList)
                else showDriveGuidance(nextPractice(tbtInfoList), nextLanePrep(tbtInfoList))
            }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val here = LatLng(currentLocationInfo.latitude, currentLocationInfo.longitude)
            runOnUiThread {
                val start = pendingSearch ?: return@runOnUiThread
                pendingSearch = null
                search = start(here)
                requestCandidate()
            }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d("JbRoute", "경로 시작 $routeStartInfo")
            runOnUiThread {
                if (!awaitingRoute) return@runOnUiThread
                routeStart = routeStartInfo
                naviHelper.getTBTInfo()
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.d("JbRoute", "내비 오류 $errorCode")
            runOnUiThread { if (awaitingRoute) searchStepFailed() }
        }
    }

    // 경유지를 바꿔 가며 경로 후보를 요청하고, 회전 목록에 필요한 상황이 있으면 채택한다.
    // SDK에 경로 미리보기가 없어서 후보마다 기본 내비 경로가 바뀐다.
    private fun startSearch(
        title: String,
        distanceKm: Double,
        clockwise: Boolean,
        required: List<PracticeElement>,
        option: RouteOption,
    ) {
        setContentView(R.layout.screen_searching)
        findViewById<Button>(R.id.btnBack).setOnClickListener { cancelSearch() }
        findViewById<TextView>(R.id.searchTitle).text = title
        if (!naviConnected) {
            findViewById<TextView>(R.id.searchProgress).text = "내비에 연결된 뒤 다시 시도해 주세요"
            return
        }
        findViewById<TextView>(R.id.searchProgress).text = "현재 위치 확인 중"
        pendingSearch = { here -> CourseSearch(title, here, distanceKm, clockwise, required, option) }
        handler.postDelayed(searchTimeout, SEARCH_TIMEOUT_MS)
        naviHelper.getCurrentLocationInfo()
    }

    private fun requestCandidate() {
        val s = search ?: return
        awaitingRoute = true
        routeStart = null
        findViewById<TextView?>(R.id.searchProgress)?.text =
            "길 찾는 중 · ${s.attempt + 1} / $MAX_SEARCH_ATTEMPTS"
        handler.removeCallbacks(searchTimeout)
        handler.postDelayed(searchTimeout, SEARCH_TIMEOUT_MS)
        val (first, second) = s.waypoints
        // shortcut: 좌표로만 요청해서 POI 정보는 빈 값으로 둔다. 에뮬레이터에서 거부되면 다른 방법을 찾는다.
        val request = RouteWithWaypointsRequest(
            destination = RouteInfo(
                latitude = s.start.lat, longitude = s.start.lng, poiName = "출발지",
                poiId = "", address = "", poiSubId = "", routeOption = s.routeOption,
            ),
            waypoints = listOf(
                RequestWaypointInfo(first.lat, first.lng, "연습 경유지 1", WaypointIndex.FIRST, "", "", ""),
                RequestWaypointInfo(second.lat, second.lng, "연습 경유지 2", WaypointIndex.SECOND, "", "", ""),
            ),
        )
        try {
            naviHelper.requestRouteWithWaypoints(request)
        } catch (e: Exception) {
            Log.d("JbRoute", "경로 요청 실패", e)
            searchStepFailed()
        }
    }

    private fun checkCandidate(tbtList: List<TBTInfo>) {
        val s = search ?: return
        val start = routeStart ?: return
        handler.removeCallbacks(searchTimeout)
        awaitingRoute = false
        if (!s.matches(tbtList)) {
            searchStepFailed()
            return
        }
        search = null
        val route = "실제 %.1fkm · 약 %d분".format(start.distance / 1000.0, (start.duration + 59) / 60)
        showConfirm("${s.title}\n$route", routeActive = true)
    }

    // 응답이 없거나 오류가 나거나 필요한 상황이 없으면 다음 후보로 넘어간다.
    private fun searchStepFailed() {
        handler.removeCallbacks(searchTimeout)
        awaitingRoute = false
        val s = search
        if (s == null) {
            pendingSearch = null
            findViewById<TextView?>(R.id.searchProgress)?.text = "현재 위치를 받지 못했어요. 다시 시도해 주세요"
            return
        }
        if (s.nextAttempt()) {
            requestCandidate()
            return
        }
        search = null
        naviHelper.cancelRoute()
        findViewById<TextView?>(R.id.searchProgress)?.text = "이 근처에서 맞는 길을 찾지 못했어요"
    }

    private fun cancelSearch() {
        handler.removeCallbacks(searchTimeout)
        val wasSearching = awaitingRoute || search != null
        pendingSearch = null
        search = null
        awaitingRoute = false
        if (wasSearching) naviHelper.cancelRoute()
        showHome()
    }

    // 연습 상황은 멀면 한 줄, 가까우면 할 일 코칭으로 보여준다.
    private fun showDriveGuidance(next: NextPractice?, prep: LanePrep?) {
        val far = findViewById<TextView?>(R.id.practiceFar) ?: return
        val card = findViewById<View>(R.id.practiceCard)
        val near = next?.isNear == true
        far.visibility = if (near) View.GONE else View.VISIBLE
        card.visibility = if (near) View.VISIBLE else View.GONE
        far.text = next?.let { "다음 연습 · ${it.title} · ${it.distance}" } ?: NO_PRACTICE_AHEAD
        if (near) {
            findViewById<TextView>(R.id.nextPoint).text = next!!.title
            findViewById<TextView>(R.id.nextDistance).text = next.distance
            findViewById<TextView>(R.id.coachingText).text = next.coaching.joinToString("\n")
        }

        // 가까운 연습 상황과 같은 지점이면 코칭 카드에 이미 나오므로 차선 준비는 숨긴다.
        val showPrep = prep != null && !(near && prep.index == next!!.index)
        findViewById<View>(R.id.lanePrepCard).visibility = if (showPrep) View.VISIBLE else View.GONE
        if (showPrep) {
            findViewById<TextView>(R.id.lanePrepTurn).text = "${prep!!.distance} 앞 ${prep.turn}"
            findViewById<TextView>(R.id.lanePrepAdvice).text = prep.advice
            findViewById<TextView>(R.id.lanePrepSignal).text = prep.signal
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progress = loadProgress()

        naviHelper = NaviHelper(applicationContext)
        naviHelper.initialize(
            onConnected = {
                naviHelper.removeListener(naviListener)
                naviHelper.addListener(naviListener)
                naviConnected = true
                runOnUiThread { updateNaviStatus("내비 연결됨") }
            },
            onFailed = { e -> runOnUiThread { updateNaviStatus("내비 연결 실패: ${e.message}") } }
        )
        showHome()
    }

    // 다른 화면을 보는 중이면 화면을 바꾸지 않고 값만 저장한다.
    private fun updateNaviStatus(text: String) {
        naviStatus = text
        findViewById<TextView?>(R.id.navStatus)?.text = text
    }

    private fun loadProgress(): Progress {
        val prefs = getSharedPreferences("progress", MODE_PRIVATE)
        return Progress(prefs.getInt("level", 1), prefs.getInt("points", 0))
    }

    private fun saveProgress() {
        getSharedPreferences("progress", MODE_PRIVATE).edit()
            .putInt("level", progress.level)
            .putInt("points", progress.points)
            .apply()
    }

    private fun showHome() {
        setContentView(R.layout.screen_home)
        findViewById<TextView>(R.id.navStatus).text = naviStatus
        val required = progress.required
        findViewById<TextView>(R.id.levelValue).text =
            if (required == null) "Lv.$MAX_LEVEL · MAX" else "Lv.${progress.level}"
        findViewById<TextView>(R.id.levelPoints).text = required?.let { "${progress.points} / ${it}p" } ?: ""
        findViewById<LinearProgressIndicator>(R.id.levelProgress).progress =
            required?.let { progress.points * 100 / it } ?: 100
        findViewById<TextView>(R.id.levelHint).text =
            required?.let { "다음 레벨까지 ${it - progress.points}p" } ?: "최고 레벨이에요"
        findViewById<TextView>(R.id.levelCourseHint).text =
            "Lv.${progress.level} 추천 · ${courseForLevel(progress.level).summary}"
        findViewById<View>(R.id.btnLoop).setOnClickListener { showLoop() }
        findViewById<View>(R.id.btnElement).setOnClickListener { showElement() }
        findViewById<View>(R.id.btnLevel).setOnClickListener { showLevel() }
        findViewById<View>(R.id.btnDestination).setOnClickListener { showDestination() }
    }

    private fun showLoop() {
        setContentView(R.layout.screen_loop)
        val distanceText = findViewById<TextView>(R.id.distanceText)
        val slider = findViewById<Slider>(R.id.distanceSlider)
        val clockwise = findViewById<MaterialButton>(R.id.btnClockwise)
        val counterClockwise = findViewById<MaterialButton>(R.id.btnCounterClockwise)

        // 뒤로 갔다가 다시 와도 고른 값을 유지한다.
        slider.value = loopKm
        distanceText.text = "%.1f km".format(loopKm)
        fun selectDirection(isClockwise: Boolean) {
            loopClockwise = isClockwise
            clockwise.isChecked = isClockwise
            counterClockwise.isChecked = !isClockwise
        }
        selectDirection(loopClockwise)

        slider.addOnChangeListener { _, value, _ ->
            loopKm = value
            distanceText.text = "%.1f km".format(value)
        }
        // 이미 고른 버튼을 다시 눌러도 선택이 풀리지 않게 직접 정한다.
        clockwise.setOnClickListener { selectDirection(true) }
        counterClockwise.setOnClickListener { selectDirection(false) }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            val direction = if (loopClockwise) clockwise.text else counterClockwise.text
            startSearch(
                "순환 코스 %.1fkm · %s".format(loopKm, direction),
                loopKm.toDouble(), loopClockwise, emptyList(), RouteOption.RECOMMENDED,
            )
        }
    }

    private fun showElement() {
        setContentView(R.layout.screen_element)
        val list = findViewById<LinearLayout>(R.id.screenContent)
        PracticeElement.entries.forEach { element ->
            val item = layoutInflater.inflate(R.layout.item_element, list, false)
            item.findViewById<TextView>(R.id.elementTitle).text = element.label
            item.findViewById<TextView>(R.id.elementDesc).text =
                "통과하면 ${element.points}p · 방향지시등 켜면 +${TURN_SIGNAL_BONUS}p"
            item.setOnClickListener {
                // shortcut: 고속도로 IC는 멀리 있어서 더 길게 찾는다. 거리 값은 주행으로 확인해 조정한다.
                val highway = element == PracticeElement.HIGHWAY_IC
                startSearch(
                    "${element.label} 연습",
                    if (highway) 20.0 else PRACTICE_SEARCH_KM, loopClockwise, listOf(element),
                    if (highway) RouteOption.HIGHWAY else RouteOption.RECOMMENDED,
                )
            }
            list.addView(item)
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
    }

    // selected: 코스를 만들 레벨. 지난 레벨을 골라 다시 연습할 수 있다.
    private fun showLevel(selected: Int = progress.level, showAll: Boolean = false) {
        setContentView(R.layout.screen_level)
        val current = progress.level
        val course = courseForLevel(selected)
        val best = course.bestCase

        val required = progress.required
        findViewById<TextView>(R.id.levelValue).text =
            if (required == null) "Lv.$MAX_LEVEL · MAX" else "Lv.$current"
        findViewById<TextView>(R.id.levelPoints).text = required?.let { "${progress.points} / ${it}p" } ?: ""
        findViewById<LinearProgressIndicator>(R.id.levelProgress).progress =
            required?.let { progress.points * 100 / it } ?: 100
        findViewById<TextView>(R.id.levelHint).text = required?.let {
            val remaining = it - progress.points
            "레벨업까지 ${remaining}p · 이 코스로 약 ${course.runsFor(remaining)}번"
        } ?: "최고 레벨이에요"

        findViewById<TextView>(R.id.courseLabel).text =
            if (selected == current) "이번 코스 · Lv.$selected" else "다시 연습 · Lv.$selected"
        findViewById<TextView>(R.id.courseDistance).text = "${course.distanceKm}km"
        findViewById<TextView>(R.id.courseElements).text =
            if (course.elements.isEmpty()) "기본 주행" else "${course.elementsText} 포함"
        findViewById<TextView>(R.id.courseOption).text = course.routeOption.label
        findViewById<TextView>(R.id.courseDirection).text = if (loopClockwise) "시계 방향" else "반시계 방향"
        val pointRows = findViewById<LinearLayout>(R.id.pointRows)
        buildList {
            add("거리 ${course.distanceKm}km" to "+${best.distance}p")
            course.elements.forEach { add("${it.label} 통과" to "+${it.points}p") }
            if (best.turnSignalBonus > 0) add("방향지시등 사용" to "+${best.turnSignalBonus}p")
            add("안전 운전 보너스" to "최대 +${best.safetyBonus}p")
            add("예상 시간" to "경로를 만든 뒤")
        }.forEach { (label, value) ->
            val row = layoutInflater.inflate(R.layout.item_result_row, pointRows, false)
            row.findViewById<TextView>(R.id.rowLabel).text = label
            row.findViewById<TextView>(R.id.rowValue).text = value
            pointRows.addView(row)
        }

        // 처음에는 지금 레벨 주변만 보여주고, 더 보기로 전체를 펼친다.
        val visible = if (showAll) 1..MAX_LEVEL else maxOf(1, current - 1)..minOf(MAX_LEVEL, current + 2)
        val levelRows = findViewById<LinearLayout>(R.id.levelRows)
        visible.forEach { level ->
            val c = courseForLevel(level)
            val row = layoutInflater.inflate(R.layout.item_level_row, levelRows, false)
            val badge = row.findViewById<TextView>(R.id.levelBadge)
            badge.text = "$level"
            val state = when {
                level < current -> "완료"
                level == current -> "지금 레벨"
                else -> "잠김"
            }
            badge.setBackgroundResource(
                when {
                    level < current -> R.drawable.bg_badge_done
                    level == current -> R.drawable.bg_badge_current
                    else -> R.drawable.bg_badge_locked
                }
            )
            badge.setTextColor(getColor(if (level == current) R.color.jb_background else R.color.jb_text_secondary))
            row.findViewById<TextView>(R.id.levelRowTitle).text = "Lv.$level · ${c.distanceKm}km"
            row.findViewById<TextView>(R.id.levelRowDesc).text = "${c.elementsText} · $state"
            if (level == selected) row.setBackgroundResource(R.drawable.bg_level_card)
            if (level < current && level != selected) {
                row.findViewById<Button>(R.id.levelRowAction).apply {
                    visibility = View.VISIBLE
                    contentDescription = "Lv.$level 다시 연습"
                    setOnClickListener { showLevel(level, showAll) }
                }
            }
            levelRows.addView(row)
        }
        findViewById<Button>(R.id.btnMoreLevels).apply {
            text = if (showAll) "접기" else "모든 레벨 보기"
            setOnClickListener { showLevel(selected, !showAll) }
        }

        findViewById<Button>(R.id.btnNext).text = "Lv.$selected 코스 만들기"
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (selected != current) showLevel(current, showAll) else showHome()
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            startSearch(
                "Lv.${course.level} · ${course.summary}",
                course.distanceKm.toDouble(), loopClockwise, course.elements, course.routeOption,
            )
        }
    }

    private fun showDestination() {
        setContentView(R.layout.screen_destination)
        setupBackAndNext { "목적지 안내" }
    }

    private fun setupBackAndNext(courseInfo: () -> String) {
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
        findViewById<Button>(R.id.btnNext).setOnClickListener { showConfirm(courseInfo()) }
    }

    // routeActive: 길 찾기로 기본 내비에 경로가 이미 걸려 있는 경우. 뒤로 가면 취소한다.
    private fun showConfirm(courseInfo: String, routeActive: Boolean = false) {
        setContentView(R.layout.screen_confirm)
        findViewById<TextView>(R.id.courseInfo).text = courseInfo
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            showDrive(courseInfo.substringBefore('\n'))
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (routeActive) naviHelper.cancelRoute()
            showHome()
        }
    }

    private fun showDrive(courseInfo: String) {
        setContentView(R.layout.screen_drive)
        findViewById<TextView>(R.id.driveCourse).text = "연습 중 · $courseInfo"
        if (naviConnected) naviHelper.getTBTInfo()
        // 도착 감지(onDestinationArrived)를 연결하면 임시 버튼과 함께 지운다.
        findViewById<Button>(R.id.btnArrive).setOnClickListener { showResult() }
    }

    private fun showResult() {
        setContentView(R.layout.screen_result)
        // shortcut: 실제 주행 기록이 없어 예시 값을 쓴다. 경로·차량 데이터를 연결하면 교체한다.
        val record = DriveRecord(
            distanceKm = 10.0,
            estimatedMinutes = 30,
            elements = listOf(PassedElement(PracticeElement.U_TURN, turnSignalOn = true)),
        )
        val earned = calculatePoints(record)
        progress = progress.add(earned.total)
        saveProgress()

        findViewById<TextView>(R.id.earnedPoints).text = "%+dp".format(earned.total)
        findViewById<TextView>(R.id.resultLevel).text = progress.required
            ?.let { "지금 Lv.${progress.level} · ${progress.points} / ${it}p" }
            ?: "지금 Lv.$MAX_LEVEL · MAX"
        val rows = findViewById<LinearLayout>(R.id.resultRows)
        buildList {
            record.elements.filterNot { it.rerouted }.forEach {
                val signal = if (it.turnSignalOn) "방향지시등 켬" else "방향지시등 안 켬"
                add("${it.element.label} · $signal" to "+${it.element.points}p")
            }
            if (earned.turnSignalBonus > 0) add("방향지시등 보너스" to "+${earned.turnSignalBonus}p")
            add("거리 %.1fkm".format(record.distanceKm) to "+${earned.distance}p")
            add("예상 시간 ${record.estimatedMinutes}분" to "+${earned.time}p")
            if (earned.safetyBonus > 0) add("안전 운전 보너스" to "+${earned.safetyBonus}p")
            if (earned.penalty > 0) add("위험 운전 감점" to "-${earned.penalty}p")
        }.forEach { (label, value) ->
            val row = layoutInflater.inflate(R.layout.item_result_row, rows, false)
            row.findViewById<TextView>(R.id.rowLabel).text = label
            row.findViewById<TextView>(R.id.rowValue).text = value
            rows.addView(row)
        }
        findViewById<Button>(R.id.btnHome).setOnClickListener { showHome() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(searchTimeout)
        naviHelper.removeListener(naviListener)
        naviHelper.release()
        super.onDestroy()
    }
}