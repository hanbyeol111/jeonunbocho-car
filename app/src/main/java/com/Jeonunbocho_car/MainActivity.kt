package com.Jeonunbocho_car

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider

private const val SEARCH_TIMEOUT_MS = 15_000L
private const val DRIVING_NOTICE_MS = 5_000L
private const val MAX_TIGHT_TURNS = 3

// shortcut: 바뀐 카드를 강조하는 시간. 근거 없이 정한 값이라 실제 주행으로 조정한다.
private const val CHANGED_HIGHLIGHT_MS = 3_000L

// 순환 방향 버튼 글자: 방향은 크게, 설명은 작게 두 줄로. 레이아웃 XML의 \n은 그대로 보일 수 있어 코드로 만든다.
fun directionLabel(clockwise: Boolean): CharSequence {
    val title = if (clockwise) "시계 방향" else "반시계 방향"
    val sub = if (clockwise) "오른쪽으로 크게 돌아요" else "왼쪽으로 크게 돌아요"
    return SpannableStringBuilder(title).append("\n")
        .append(sub, RelativeSizeSpan(0.8f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

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

    // 주행 기록. 주행 화면에 있는 동안만 있다. UI 스레드에서만 바꾼다.
    private var session: DriveSession? = null
    // 주행 화면 큰 카드에 지금 보이는 내용(회전·단계). 바뀌었는지 보는 데 쓴다.
    private var shownKey: String? = null
    private var vehicle: Vehicle? = null
    // 차종마다 방향지시등 지원이 다르다. 지원을 확인했거나 신호가 실제로 들어왔을 때만 true.
    private var turnSignalSupported = false

    // 주행 규제(CarUxRestrictions). 주행 중이면 설정 화면을 막는다.
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var setupBlocked = false
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        Log.d("JbUx", "주행 규제 DO=${r.isRequiresDistractionOptimization} active=${r.activeRestrictions}")
        runOnUiThread {
            setupBlocked = blocksSetup(r.isRequiresDistractionOptimization, r.activeRestrictions)
            updateDrivingBlock()
        }
    }
    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            runOnUiThread {
                if (state != null) markTurnSignalSupported()
                session?.onTurnSignal(state == TurnSignalValue.LEFT || state == TurnSignalValue.RIGHT)
            }
        }

        override fun onFailed(e: Exception) {
            Log.d("JbDrive", "방향지시등 신호 실패", e)
            runOnUiThread { turnSignalSupported = false }
        }
    }

    // 출발 전 확인에 보여 줄 기어·브레이크 페달·조향각·운전석 안전벨트. 리스너는 다른 스레드에서 올 수 있다.
    private var carState = CarState()
    private fun updateCar(change: (CarState) -> CarState) = runOnUiThread {
        carState = change(carState)
        showCarStatus()
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = updateCar { it.copy(gear = gear) }
        override fun onFailed(e: Exception) { Log.d("JbCar", "기어 실패", e) }
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = updateCar { it.copy(brakePressed = status) }
        override fun onFailed(e: Exception) { Log.d("JbCar", "브레이크 실패", e) }
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = updateCar { it.copy(steeringDeg = angle) }
        override fun onFailed(e: Exception) { Log.d("JbCar", "조향각 실패", e) }
    }
    private val beltListener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(state: Boolean?) = updateCar { it.copy(beltBuckled = state) }
        override fun onFailed(e: Exception) { Log.d("JbCar", "안전벨트 실패", e) }
    }
    // 운전석. 국내 차량 기준 왼쪽 앞자리.
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)

    // 출발 전 확인 화면이 떠 있을 때만 줄을 바꾼다. 값이 없으면(미지원) 그 줄은 숨긴다.
    private fun showCarStatus() {
        listOf(
            R.id.statusSteering to steeringStatus(carState.steeringDeg),
            R.id.statusBelt to beltStatus(carState.beltBuckled),
            R.id.statusGear to gearStatus(carState.gear, carState.brakePressed),
        ).forEach { (id, text) ->
            findViewById<TextView?>(id)?.apply {
                this.text = text
                visibility = if (text == null) View.GONE else View.VISIBLE
            }
        }
    }

    // 주행 도중에 지원이 확인돼도 그 주행 기록에 반영한다.
    private fun markTurnSignalSupported() {
        turnSignalSupported = true
        session?.signalMeasured = true
    }

    // 전운보초는 소리를 내지 않고, 주행 화면 문구만 바꾼다.
    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            // shortcut: TBT 원본 확인용 임시 로그. 유턴 표시 문제와 목록 범위를 확인하면 지운다.
            Log.d("JbTBT", "목록 ${tbtInfoList.size}개")
            tbtInfoList.forEach { Log.d("JbTBT", "${it.type} ${it.distance}m ${it.description}") }
            runOnUiThread {
                if (awaitingRoute && routeStart != null) {
                    checkCandidate(tbtInfoList)
                } else {
                    session?.onTbt(tbtInfoList)
                    showDriveGuidance(nextPractice(tbtInfoList), nextLanePrep(tbtInfoList), tbtInfoList.size)
                }
            }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.d("JbDrive", "도착 $destinationArrivedInfo")
            runOnUiThread { finishDrive(arrived = true) }
        }

        override fun onReRouteEnded() {
            Log.d("JbDrive", "재탐색")
            runOnUiThread { session?.onReRoute() }
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
        showSetupScreen(R.layout.screen_searching)
        findViewById<Button>(R.id.btnBack).setOnClickListener { cancelSearch() }
        findViewById<TextView>(R.id.searchTitle).text = title
        if (!naviConnected) {
            findViewById<TextView>(R.id.searchProgress).text = "내비에 연결된 뒤 다시 시도해 주세요"
            return
        }
        findViewById<TextView>(R.id.searchProgress).text = "현재 위치 확인 중"
        findViewById<TextView>(R.id.searchTotal).text = " / $MAX_SEARCH_ATTEMPTS"
        val steps = findViewById<LinearLayout>(R.id.searchSteps)
        repeat(MAX_SEARCH_ATTEMPTS) { i ->
            steps.addView(View(this).apply {
                setBackgroundResource(R.drawable.bg_pill)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .apply { if (i > 0) marginStart = dp(8) }
            })
        }
        pendingSearch = { here -> CourseSearch(title, here, distanceKm, clockwise, required, option) }
        handler.postDelayed(searchTimeout, SEARCH_TIMEOUT_MS)
        naviHelper.getCurrentLocationInfo()
    }

    private fun requestCandidate() {
        val s = search ?: return
        awaitingRoute = true
        routeStart = null
        showAttempt(s)
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

    // 지난 후보는 진하게, 지금 후보는 흐리게, 남은 후보는 흰색.
    private fun showAttempt(s: CourseSearch) {
        val progressText = findViewById<TextView?>(R.id.searchProgress) ?: return
        findViewById<View>(R.id.searchAttemptLine).visibility = View.VISIBLE
        findViewById<TextView>(R.id.searchAttempt).text = "${s.attempt + 1}"
        val names = s.required.joinToString(", ") { it.label }
        progressText.text = if (names.isEmpty()) "순환 코스를 찾는 중" else "${names}${subjectParticle(names)} 있는 길을 찾는 중"
        val steps = findViewById<LinearLayout>(R.id.searchSteps)
        for (i in 0 until steps.childCount) {
            val step = steps.getChildAt(i)
            step.backgroundTintList = colorOf(if (i <= s.attempt) R.color.jb_on_block else R.color.white)
            step.alpha = if (i == s.attempt) 0.4f else 1f
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
        showConfirm(s.title, summarizeRoute(start.distance, start.duration, tbtList))
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

    // 멀 때: 지금 할 일(다음 회전과 차선 준비) → 다음 연습.
    // 가까울 때: 두 카드 대신 연습 상황 코칭 카드. 코스 진행은 두 상태 모두 아래에 있다.
    // listSize: 회전 목록 길이. 지난 지점이 앞에서 빠지므로 (길이 - 위치)가 같은 지점을 가리킨다.
    private fun showDriveGuidance(next: NextPractice?, prep: LanePrep?, listSize: Int) {
        val far = findViewById<View?>(R.id.practiceFar) ?: return
        val near = next?.isNear == true
        // 큰 카드의 내용이 바뀐 순간(새 회전, 새 단계)에만 잠깐 강조한다. 거리 숫자가 줄어드는 건 바뀜이 아니다.
        val key = when {
            near -> "P${listSize - next!!.index}:${next.stage?.index}"
            prep != null -> "N${listSize - prep.index}"
            else -> null
        }
        if (key != null && key != shownKey) flashChanged(if (near) R.id.practiceCard else R.id.nowCard)
        shownKey = key
        far.visibility = if (near) View.GONE else View.VISIBLE
        findViewById<View>(R.id.practiceCard).visibility = if (near) View.VISIBLE else View.GONE
        findViewById<View>(R.id.nowCard).visibility = if (!near && prep != null) View.VISIBLE else View.GONE
        showCourseProgress()

        if (!near && prep != null) {
            val (number, unit) = splitUnit(prep.distance)
            showIcon(R.id.nowIcon, prep.type?.iconRes())
            findViewById<TextView>(R.id.nowDistance).text = number
            findViewById<TextView>(R.id.nowUnit).text = unit
            findViewById<TextView>(R.id.nowTurn).text = prep.turn
            showSign(R.id.nowSign, prep.sign)
            // 회전이 연달아 오면 차선 준비 대신 '바로 이어서'를 보여 준다(같은 칸을 두 번 쓰지 않는다).
            val follow = prep.followUp
            findViewById<TextView>(R.id.nowAdvice).apply {
                text = prep.advice
                visibility = if (follow == null) View.VISIBLE else View.GONE
            }
            findViewById<View>(R.id.followCard).visibility = if (follow == null) View.GONE else View.VISIBLE
            if (follow != null) {
                showIcon(R.id.followIcon, follow.type.iconRes())
                findViewById<TextView>(R.id.followTurn).text = "${follow.distance} 뒤 ${follow.turn}"
                findViewById<TextView>(R.id.followAdvice).text = follow.advice
            }
        }

        showIcon(R.id.farIcon, next?.type?.iconRes())
        findViewById<TextView>(R.id.farTitle).apply {
            text = next?.title ?: NO_PRACTICE_AHEAD
            // 참고 정보라 작게. 거리(32sp)와 한 줄에 들어가게, 긴 이름(고가도로·지하차도 등)은 더 줄인다.
            textSize = when {
                next == null -> 24f
                next.title.length > 5 -> 28f
                else -> 32f
            }
        }
        findViewById<TextView>(R.id.farDistance).text = next?.distance ?: ""

        if (near) {
            val (number, unit) = splitUnit(next!!.distance)
            showIcon(R.id.nextIcon, next.type.iconRes())
            findViewById<TextView>(R.id.nextDistance).text = number
            findViewById<TextView>(R.id.nextUnit).text = unit
            findViewById<TextView>(R.id.nextPoint).apply {
                text = next.title
                textSize = if (next.title.length > 6) 44f else 56f
            }
            showSign(R.id.nextSign, next.sign)
            showStage(next.stage)
        }
    }

    // 테두리를 씌웠다가 일정 시간 뒤 걷는다. 다른 카드가 바뀌면 이전 강조는 바로 걷는다.
    private fun flashChanged(cardId: Int) {
        handler.removeCallbacks(clearChanged)
        clearChanged.run()
        findViewById<View>(cardId).foreground = getDrawable(R.drawable.fg_changed)
        handler.postDelayed(clearChanged, CHANGED_HIGHLIGHT_MS)
    }

    private val clearChanged = Runnable {
        findViewById<View?>(R.id.nowCard)?.foreground = null
        findViewById<View?>(R.id.practiceCard)?.foreground = null
    }

    // 출발 전에 차가 서 있을 때 익히도록, 회전이 연달아 오는 곳을 가까운 순서로 최대 3곳 보여 준다.
    private fun showTightTurns(tight: List<TightTurn>) {
        findViewById<View>(R.id.tightGroup).visibility = if (tight.isEmpty()) View.GONE else View.VISIBLE
        val rows = findViewById<LinearLayout>(R.id.tightRows)
        tight.take(MAX_TIGHT_TURNS).forEach { t ->
            val row = layoutInflater.inflate(R.layout.item_tight_turn, rows, false)
            row.findViewById<TextView>(R.id.tightAt).text = "${t.at} 지점"
            row.findViewById<TextView>(R.id.tightTurns).text = "${t.turn} → ${t.followUp.distance} 뒤 ${t.followUp.turn}"
            row.findViewById<TextView>(R.id.tightAdvice).text = t.followUp.advice
            rows.addView(row)
        }
        findViewById<TextView>(R.id.tightMore).apply {
            val more = tight.size - MAX_TIGHT_TURNS
            text = "외 ${more}곳"
            visibility = if (more > 0) View.VISIBLE else View.GONE
        }
    }

    private fun showIcon(id: Int, res: Int?) {
        findViewById<ImageView>(id).apply {
            if (res == null) visibility = View.GONE
            else { setImageResource(res); visibility = View.VISIBLE }
        }
    }

    private fun showSign(id: Int, sign: String?) {
        findViewById<TextView>(id).apply {
            text = sign?.let { "표지판 · $it" }
            visibility = if (sign == null) View.GONE else View.VISIBLE
        }
    }

    // 지금 단계 한 줄과, 지난 단계는 진하게 남은 단계는 흰색인 막대.
    private fun showStage(stage: CoachStage?) {
        val steps = findViewById<LinearLayout>(R.id.stageSteps)
        val label = findViewById<TextView>(R.id.stageLabel)
        val text = findViewById<TextView>(R.id.stageText)
        val visible = if (stage == null) View.GONE else View.VISIBLE
        steps.visibility = visible; label.visibility = visible; text.visibility = visible
        if (stage == null) return
        if (steps.childCount != stage.total) {
            steps.removeAllViews()
            repeat(stage.total) { i ->
                steps.addView(View(this).apply {
                    setBackgroundResource(R.drawable.bg_pill)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        .apply { if (i > 0) marginStart = dp(8) }
                })
            }
        }
        for (i in 0 until steps.childCount) {
            steps.getChildAt(i).backgroundTintList = colorOf(if (i <= stage.index) R.color.jb_on_block else R.color.white)
        }
        label.text = "${stage.label} · ${stage.index + 1}/${stage.total}"
        text.text = stage.text
    }

    private fun showCourseProgress() {
        val s = session ?: return
        val left = s.practicesLeft ?: return
        findViewById<TextView>(R.id.progressText).text =
            "남은 연습 ${left}곳 · %.1fkm 남음".format(s.remainingKm ?: 0.0)
        findViewById<LinearProgressIndicator>(R.id.courseProgress).progress = s.progressPercent ?: 0
    }

    // 시안(600×800) 비율 그대로 보이게, 창이 시안보다 크면 화면 전체를 같은 비율로 키운다.
    // 창 크기를 바꾸면 액티비티가 다시 만들어지며 새로 계산된다.
    // dp·sp를 픽셀로 바꿀 때 쓰는 밀도 값을 직접 키운다. setContentView 전에 불러야 한다.
    @Suppress("DEPRECATION") // scaledDensity: sp 변환에 아직 쓰인다.
    private fun applyDesignScale() {
        val config = resources.configuration
        val scale = designScale(config.screenWidthDp, config.screenHeightDp)
        Log.d("JbScreen", "창 ${config.screenWidthDp}×${config.screenHeightDp}dp · 배율 $scale")
        if (scale <= 1f) return
        val density = config.densityDpi / 160f * scale
        resources.displayMetrics.apply {
            this.density = density
            scaledDensity = density * config.fontScale
            densityDpi = (config.densityDpi * scale).toInt()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDesignScale()
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
        // 방향지시등 기록용. 차량 서비스가 없는 환경에서도 앱은 계속 동작하게 한다.
        try {
            vehicle = Vehicle(applicationContext).also {
                it.initialize()
                it.getTurnSignal().checkTurnSignalStateCapabilities(
                    onComplete = { c ->
                        val readable = c.accessType == AccessType.READ || c.accessType == AccessType.READ_WRITE
                        Log.d("JbDrive", "방향지시등 지원=${c.accessType}")
                        runOnUiThread { if (readable) markTurnSignalSupported() }
                    },
                    onFailed = { e -> Log.d("JbDrive", "방향지시등 지원 확인 실패", e) },
                )
                it.getTurnSignal().registerTurnSignalState(turnSignalListener)
            }
        } catch (e: Exception) {
            Log.d("JbDrive", "차량 연결 실패", e)
            vehicle = null
        }
        // 출발 전 확인용 신호. 차종마다 지원이 달라 하나가 실패해도 나머지는 계속 받는다.
        vehicle?.let { v ->
            listOf<Pair<String, () -> Unit>>(
                "기어" to { v.getGear().registerGearSelection(gearListener) },
                "브레이크" to { v.getBrake().registerBrakePedalStatus(brakeListener) },
                "조향각" to { v.getSteeringWheel().registerSteeringWheelAngle(steeringListener) },
                "안전벨트" to { v.getSeat().registerSeatBeltBuckledState(driverSeat, beltListener) },
            ).forEach { (name, register) ->
                try { register() } catch (e: Exception) { Log.d("JbCar", "$name 등록 실패", e) }
            }
        }
        // 주행 규제. 차량용이 아닌 기기에는 android.car가 없을 수 있어 실패해도 앱은 계속 동작한다.
        try {
            car = Car.createCar(this)
            uxManager = (car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also {
                it.registerListener(uxListener)
                uxListener.onUxRestrictionsChanged(it.currentCarUxRestrictions)
            }
        } catch (e: Throwable) {
            Log.d("JbUx", "주행 규제 연결 실패", e)
        }
        showHome()
    }

    // 설정 화면(코스 고르기·출발)은 위에 '주행 중' 판을 함께 깔고, 규제 상태에 따라 보이거나 숨긴다.
    private fun showSetupScreen(layout: Int) {
        handler.removeCallbacks(hideDrivingNotice)
        setContentView(layout)
        val block = layoutInflater.inflate(R.layout.view_driving_block, null)
        // 투명한 판이 터치를 모두 받아 아래 버튼이 눌리지 않게 하고, 누를 때마다 안내를 띄운다.
        block.setOnClickListener { showDrivingNotice() }
        addContentView(
            block,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        updateDrivingBlock()
    }

    private fun updateDrivingBlock() {
        findViewById<View?>(R.id.drivingBlock)?.visibility = if (setupBlocked) View.VISIBLE else View.GONE
        if (!setupBlocked) {
            handler.removeCallbacks(hideDrivingNotice)
            findViewById<View?>(R.id.drivingNotice)?.apply { animate().cancel(); alpha = 0f; visibility = View.GONE }
        }
    }

    // 안내는 5초 보이고 서서히 사라진다. 다시 누르면 5초를 새로 센다.
    private fun showDrivingNotice() {
        val notice = findViewById<View?>(R.id.drivingNotice) ?: return
        handler.removeCallbacks(hideDrivingNotice)
        notice.animate().cancel()
        notice.visibility = View.VISIBLE
        notice.animate().alpha(1f).setDuration(200).start()
        notice.announceForAccessibility("주행 중에는 사용할 수 없어요. 차를 세우면 다시 쓸 수 있어요")
        handler.postDelayed(hideDrivingNotice, DRIVING_NOTICE_MS)
    }

    private val hideDrivingNotice = Runnable {
        findViewById<View?>(R.id.drivingNotice)?.let { notice ->
            notice.animate().alpha(0f).setDuration(400).withEndAction { notice.visibility = View.GONE }.start()
        }
    }

    // 다른 화면을 보는 중이면 화면을 바꾸지 않고 값만 저장한다.
    private fun updateNaviStatus(text: String) {
        naviStatus = text
        findViewById<TextView?>(R.id.navStatus)?.text = text
        findViewById<View?>(R.id.navDot)?.visibility = if (naviConnected) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun colorOf(id: Int) = ColorStateList.valueOf(getColor(id))

    // onBlock: 색 블록 위면 다크에서도 어두운 글자. 흰 카드 위면 줄 사이에 구분선을 넣는다.
    private fun addRows(container: LinearLayout, rows: List<Pair<String, String>>, onBlock: Boolean) {
        rows.forEachIndexed { i, (label, value) ->
            if (!onBlock && i > 0) {
                container.addView(View(this).apply {
                    setBackgroundColor(getColor(R.color.jb_background))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2))
                })
            }
            val row = layoutInflater.inflate(R.layout.item_result_row, container, false)
            row.findViewById<TextView>(R.id.rowLabel).apply {
                text = label
                if (onBlock) setTextColor(getColor(R.color.jb_on_block_secondary))
            }
            row.findViewById<TextView>(R.id.rowValue).apply {
                text = value
                if (onBlock) setTextColor(getColor(R.color.jb_on_block))
            }
            container.addView(row)
        }
    }

    // 홈과 난이도별 코스 화면의 레벨 카드
    private fun showLevelCard(hint: (Int) -> String) {
        val required = progress.required
        findViewById<TextView>(R.id.levelValue).text = "Lv.${progress.level}"
        findViewById<TextView>(R.id.levelPoints).text = required?.let { "${progress.points} / ${it}p" } ?: "MAX"
        findViewById<LinearProgressIndicator>(R.id.levelProgress).progress =
            required?.let { progress.points * 100 / it } ?: 100
        findViewById<TextView>(R.id.levelHint).text =
            required?.let { hint(it - progress.points) } ?: "최고 레벨이에요"
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
        showSetupScreen(R.layout.screen_home)
        updateNaviStatus(naviStatus)
        showLevelCard { "다음 레벨까지 ${it}p" }
        findViewById<TextView>(R.id.levelCourseBadge).text = "Lv.${progress.level} 추천"
        findViewById<TextView>(R.id.levelCourseHint).text = courseForLevel(progress.level).summary
        findViewById<View>(R.id.btnLoop).setOnClickListener { showLoop() }
        findViewById<View>(R.id.btnElement).setOnClickListener { showElement() }
        findViewById<View>(R.id.btnLevel).setOnClickListener { showLevel() }
        findViewById<View>(R.id.btnDestination).setOnClickListener { showDestination() }
    }

    private fun showLoop() {
        showSetupScreen(R.layout.screen_loop)
        val distanceText = findViewById<TextView>(R.id.distanceText)
        val slider = findViewById<Slider>(R.id.distanceSlider)
        val clockwise = findViewById<MaterialButton>(R.id.btnClockwise)
        val counterClockwise = findViewById<MaterialButton>(R.id.btnCounterClockwise)
        clockwise.text = directionLabel(clockwise = true)
        counterClockwise.text = directionLabel(clockwise = false)

        // 뒤로 갔다가 다시 와도 고른 값을 유지한다.
        slider.value = loopKm
        distanceText.text = "%.1f".format(loopKm)
        fun selectDirection(isClockwise: Boolean) {
            loopClockwise = isClockwise
            clockwise.isChecked = isClockwise
            counterClockwise.isChecked = !isClockwise
        }
        selectDirection(loopClockwise)

        slider.addOnChangeListener { _, value, _ ->
            loopKm = value
            distanceText.text = "%.1f".format(value)
        }
        // 이미 고른 버튼을 다시 눌러도 선택이 풀리지 않게 직접 정한다.
        clockwise.setOnClickListener { selectDirection(true) }
        counterClockwise.setOnClickListener { selectDirection(false) }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            val direction = if (loopClockwise) "시계 방향" else "반시계 방향"
            startSearch(
                "순환 코스 %.1fkm · %s".format(loopKm, direction),
                loopKm.toDouble(), loopClockwise, emptyList(), RouteOption.RECOMMENDED,
            )
        }
    }

    private fun showElement() {
        showSetupScreen(R.layout.screen_element)
        findViewById<TextView>(R.id.elementHint).text = "골라서 통과하면 포인트를 받아요"
        val list = findViewById<LinearLayout>(R.id.screenContent)
        // 포인트가 낮은 것부터 두 개씩 하늘·민트·라임·버터
        val tagColors = listOf(R.color.jb_sky, R.color.jb_mint, R.color.jb_lime, R.color.jb_butter)
        PracticeElement.entries.forEachIndexed { i, element ->
            val item = layoutInflater.inflate(R.layout.item_element, list, false)
            item.findViewById<TextView>(R.id.elementTitle).text = element.label
            item.findViewById<TextView>(R.id.elementPoints).apply {
                text = "+${element.points}p"
                backgroundTintList = colorOf(tagColors[minOf(i / 2, tagColors.lastIndex)])
            }
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
        showSetupScreen(R.layout.screen_level)
        val current = progress.level
        val course = courseForLevel(selected)
        val best = course.bestCase

        showLevelCard { remaining -> "레벨업까지 ${remaining}p · 이 코스로 약 ${course.runsFor(remaining)}번" }

        findViewById<TextView>(R.id.courseLabel).text =
            if (selected == current) "이번 코스 · Lv.$selected" else "다시 연습 · Lv.$selected"
        findViewById<TextView>(R.id.courseDistance).text = "${course.distanceKm}km"
        findViewById<TextView>(R.id.courseElements).text =
            if (course.elements.isEmpty()) "기본 주행" else "${course.elementsText} 포함"
        findViewById<TextView>(R.id.courseOption).text = course.routeOption.label
        findViewById<TextView>(R.id.courseDirection).text = if (loopClockwise) "시계 방향" else "반시계 방향"
        addRows(findViewById(R.id.pointRows), buildList {
            add("거리 ${course.distanceKm}km" to "+${best.distance}p")
            course.elements.forEach { add("${it.label} 통과" to "+${it.points}p") }
            add("예상 시간" to "경로를 만든 뒤")
        }, onBlock = true)

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
            badge.setTextColor(getColor(if (level <= current) R.color.jb_on_block else R.color.jb_text_secondary))
            row.findViewById<TextView>(R.id.levelRowTitle).text = "Lv.$level · ${c.distanceKm}km"
            row.findViewById<TextView>(R.id.levelRowDesc).text = "${c.elementsText} · $state"
            if (level == selected) {
                row.setBackgroundResource(R.drawable.bg_block)
                row.backgroundTintList = colorOf(R.color.jb_background)
            }
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
        showSetupScreen(R.layout.screen_destination)
        setupBackAndNext { "목적지 안내" }
    }

    private fun setupBackAndNext(courseInfo: () -> String) {
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
        findViewById<Button>(R.id.btnNext).setOnClickListener { showConfirm(courseInfo()) }
    }

    // route: 길 찾기로 기본 내비에 걸어 둔 순환 경로의 요약. 있으면 뒤로 갈 때 경로를 취소한다.
    private fun showConfirm(title: String, route: RouteSummary? = null) {
        showSetupScreen(R.layout.screen_confirm)
        findViewById<TextView>(R.id.courseInfo).text = title
        findViewById<Button>(R.id.btnStart).setOnClickListener { showChecklist(title, route) }
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (route != null) naviHelper.cancelRoute()
            showHome()
        }
        showSafetyNote(route)
        if (route == null) return

        findViewById<View>(R.id.routeLine).visibility = View.VISIBLE
        findViewById<TextView>(R.id.routeDistance).text = "%.1fkm".format(route.distanceM / 1000.0)
        findViewById<TextView>(R.id.routeTime).text = "약 ${route.minutes}분"
        findViewById<View>(R.id.routeNote).visibility = View.VISIBLE

        findViewById<View>(R.id.summaryCard).visibility = View.VISIBLE
        findViewById<TextView>(R.id.statRight).text = "${route.rightTurns}"
        findViewById<TextView>(R.id.statLeft).text = "${route.leftTurns}"
        findViewById<TextView>(R.id.statUturn).text = "${route.uTurns}"
        findViewById<TextView>(R.id.routeSituations).text =
            route.situations.joinToString(" · ") { (name, count) -> "$name ${count}곳" }.ifEmpty { "없음" }
        val first = route.firstPrep
        if (first == null) {
            findViewById<View>(R.id.routeFirstGroup).visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.routeFirst).text = "${first.distance} 앞 ${first.turn}"
            findViewById<TextView>(R.id.routeFirstAdvice).text = first.advice
        }
        showTightTurns(route.tightTurns)

        findViewById<View>(R.id.pointsCard).visibility = View.VISIBLE
        val best = route.bestPoints
        // 방향지시등 보너스는 출발 전에 안내하지 않고 결과 화면에서만 보여준다.
        findViewById<TextView>(R.id.bestTotal).text = "+${best.total - best.turnSignalBonus}p"
        addRows(findViewById(R.id.pointRows), buildList {
            add("거리 ${route.distanceM / 1000}km" to "+${best.distance}p")
            add("예상 시간 ${route.minutes}분" to "+${best.time}p")
            if (best.elements > 0) {
                val names = route.practiceElements.distinct().joinToString(", ") { it.label }
                add(names to "+${best.elements}p")
            }
        }, onBlock = true)
    }

    // 출발 전 확인. 뒤로 가면 경로를 그대로 둔 채 코스 확인으로 돌아간다.
    private fun showChecklist(title: String, route: RouteSummary?) {
        showSetupScreen(R.layout.screen_checklist)
        findViewById<Button>(R.id.btnBack).setOnClickListener { showConfirm(title, route) }
        findViewById<Button>(R.id.btnGo).setOnClickListener { showDrive(title, route) }
        showCarStatus()
    }

    // 밤과 고속도로는 막지 않고 권장만 한다. 밤은 차량의 주간·야간 모드(앱의 어두운 화면과 같은 기준)로 판단한다.
    private fun showSafetyNote(route: RouteSummary?) {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val highway = route?.practiceElements?.contains(PracticeElement.HIGHWAY_IC) == true
        val notes = buildList {
            if (night) add("밤에는 길이 잘 안 보여요. 짧은 코스부터 달려 보길 권해요")
            if (highway) add("처음 고속도로에 갈 때는 운전에 익숙한 사람과 함께 타기를 권해요")
        }
        if (notes.isEmpty()) return
        findViewById<TextView>(R.id.safetyNote).apply {
            text = notes.joinToString("\n")
            visibility = View.VISIBLE
        }
    }

    // route: 길 찾기로 만든 경로. 목적지 안내처럼 없으면 거리는 첫 회전 목록에서, 예상 시간은 0으로 둔다.
    private fun showDrive(courseInfo: String, route: RouteSummary?) {
        setContentView(R.layout.screen_drive)
        session = DriveSession(route?.distanceM?.toDouble(), route?.minutes ?: 0, turnSignalSupported)
        shownKey = null
        findViewById<TextView>(R.id.driveCourse).text = "연습 중 · $courseInfo"
        if (naviConnected) naviHelper.getTBTInfo()
        // 도착 신호(onDestinationArrived)가 오지 않을 때를 위해 직접 끝내는 버튼을 둔다.
        findViewById<Button>(R.id.btnArrive).setOnClickListener {
            naviHelper.cancelRoute()
            finishDrive(arrived = false)
        }
    }

    private fun finishDrive(arrived: Boolean) {
        val s = session ?: return
        session = null
        Log.d("JbDrive", "주행 끝 arrived=$arrived 통과=${s.passedElements} 달린 거리=${s.drivenKm}km")
        showResult(s.record(arrived), arrived)
    }

    // 도중에 끝내면 포인트 없이 기록만 보여준다.
    private fun showResult(record: DriveRecord, arrived: Boolean) {
        setContentView(R.layout.screen_result)
        val earned = calculatePoints(record)
        if (arrived) {
            progress = progress.add(earned.total)
            saveProgress()
        } else {
            findViewById<TextView>(R.id.resultTitle).text = "주행을 끝냈어요"
            findViewById<TextView>(R.id.earnedLabel).text = "끝까지 달리면 포인트를 받아요"
        }

        if (record.elements.any { it.rerouted }) findViewById<View>(R.id.resultNote).visibility = View.VISIBLE
        findViewById<TextView>(R.id.earnedPoints).text = if (arrived) "%+dp".format(earned.total) else "0p"
        val required = progress.required
        findViewById<TextView>(R.id.resultLevel).text = "지금 Lv.${progress.level}"
        findViewById<TextView>(R.id.resultPoints).text = required?.let { "${progress.points} / ${it}p" } ?: "MAX"
        findViewById<LinearProgressIndicator>(R.id.resultProgress).progress =
            required?.let { progress.points * 100 / it } ?: 100
        addRows(findViewById(R.id.resultRows), buildList {
            record.elements.forEach {
                val label = when {
                    it.rerouted -> "${it.element.label} · 놓침(감점 없음)"
                    !record.turnSignalMeasured -> it.element.label
                    it.turnSignalOn -> "${it.element.label} · 방향지시등 켬"
                    else -> "${it.element.label} · 방향지시등 안 켬"
                }
                val value = when {
                    !arrived -> "통과"
                    it.rerouted -> "0p"
                    else -> "+${it.element.points}p"
                }
                add(label to value)
            }
            if (record.elements.isEmpty()) add("통과한 연습 상황" to "없음")
            if (!record.turnSignalMeasured) add("방향지시등" to "이 차량에서는 확인할 수 없어요")
            if (!arrived) {
                add("달린 거리" to "%.1fkm".format(record.distanceKm))
                return@buildList
            }
            if (earned.turnSignalBonus > 0) add("방향지시등 보너스" to "+${earned.turnSignalBonus}p")
            add("거리 %.1fkm".format(record.distanceKm) to "+${earned.distance}p")
            add("예상 시간 ${record.estimatedMinutes}분" to "+${earned.time}p")
            if (earned.safetyBonus > 0) add("안전 운전 보너스" to "+${earned.safetyBonus}p")
            if (earned.penalty > 0) add("방향지시등 안 켬 감점" to "-${earned.penalty}p")
        }, onBlock = false)
        findViewById<Button>(R.id.btnHome).setOnClickListener { showHome() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(searchTimeout)
        handler.removeCallbacks(hideDrivingNotice)
        handler.removeCallbacks(clearChanged)
        naviHelper.removeListener(naviListener)
        naviHelper.release()
        vehicle?.let {
            it.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            runCatching { it.getGear().unregisterGearSelection(gearListener) }
            runCatching { it.getBrake().unregisterBrakePedalStatus(brakeListener) }
            runCatching { it.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
            runCatching { it.getSeat().unregisterSeatBeltBuckledState(driverSeat, beltListener) }
            it.release()
        }
        uxManager?.unregisterListener()
        car?.disconnect()
        super.onDestroy()
    }
}