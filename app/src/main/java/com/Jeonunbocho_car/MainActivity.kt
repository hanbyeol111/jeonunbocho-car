package com.Jeonunbocho_car

import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var naviHelper: NaviHelper
    private var naviStatus = "내비 연결 중…"
    private lateinit var progress: Progress
    private var loopKm = 5f
    private var loopClockwise = true
    @Volatile private var naviConnected = false

    // 전운보초는 소리를 내지 않고, 주행 화면 문구만 바꾼다.
    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            // shortcut: TBT 원본 확인용 임시 로그. 유턴 표시 문제와 목록 범위를 확인하면 지운다.
            Log.d("JbTBT", "목록 ${tbtInfoList.size}개")
            tbtInfoList.forEach { Log.d("JbTBT", "${it.type} ${it.distance}m ${it.description}") }
            runOnUiThread { showNextPractice(nextPractice(tbtInfoList)) }
        }
    }

    private fun showNextPractice(next: NextPractice?) {
        val distance = findViewById<TextView?>(R.id.nextDistance) ?: return
        distance.text = next?.distance
        distance.visibility = if (next == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.nextPoint).text = next?.element?.label ?: NO_PRACTICE_AHEAD
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
        setupBackAndNext {
            val direction = if (loopClockwise) clockwise.text else counterClockwise.text
            "%.1fkm · %s".format(loopKm, direction)
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
            item.setOnClickListener { showConfirm("${element.label} 연습") }
            list.addView(item)
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
    }

    private fun showLevel() {
        setContentView(R.layout.screen_level)
        val course = courseForLevel(progress.level)
        findViewById<TextView>(R.id.levelRecTitle).text = "Lv.${course.level} 추천 코스"
        findViewById<TextView>(R.id.levelRecDistance).text = "${course.distanceKm}km"
        findViewById<TextView>(R.id.levelRecElements).text = course.elementsText
        setupBackAndNext { "Lv.${course.level} · ${course.summary}" }
    }

    private fun showDestination() {
        setContentView(R.layout.screen_destination)
        setupBackAndNext { "목적지 안내" }
    }

    private fun setupBackAndNext(courseInfo: () -> String) {
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
        findViewById<Button>(R.id.btnNext).setOnClickListener { showConfirm(courseInfo()) }
    }

    private fun showConfirm(courseInfo: String) {
        setContentView(R.layout.screen_confirm)
        findViewById<TextView>(R.id.courseInfo).text = courseInfo
        findViewById<Button>(R.id.btnStart).setOnClickListener { showDrive(courseInfo) }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
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
        naviHelper.removeListener(naviListener)
        naviHelper.release()
        super.onDestroy()
    }
}