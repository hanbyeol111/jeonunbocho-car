package com.Jeonunbocho_car

import ai.pleos.playground.navi.helper.NaviHelper
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var naviHelper: NaviHelper
    private var naviStatus = "내비 연결 중…"
    private lateinit var progress: Progress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progress = loadProgress()

        naviHelper = NaviHelper(applicationContext)
        naviHelper.initialize(
            onConnected = { runOnUiThread { updateNaviStatus("내비 연결됨") } },
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
        findViewById<TextView>(R.id.levelText).text = progress.required
            ?.let { "Lv.${progress.level} · ${progress.points} / ${it}p" }
            ?: "Lv.$MAX_LEVEL · MAX"
        findViewById<Button>(R.id.btnLoop).setOnClickListener { showLoop() }
        findViewById<Button>(R.id.btnElement).setOnClickListener { showElement() }
        findViewById<Button>(R.id.btnLevel).setOnClickListener { showLevel() }
        findViewById<Button>(R.id.btnDestination).setOnClickListener { showDestination() }
    }

    private fun showLoop() {
        setContentView(R.layout.screen_loop)
        val distanceText = findViewById<TextView>(R.id.distanceText)
        findViewById<Slider>(R.id.distanceSlider).addOnChangeListener { _, value, _ ->
            distanceText.text = "%.1f km".format(value)
        }
        setupBackAndNext()
    }

    private fun showElement() {
        setContentView(R.layout.screen_element)
        val list = findViewById<LinearLayout>(R.id.elementList)
        PracticeElement.entries.forEach { element ->
        val button = MaterialButton(this).apply {
            text = element.label
            setOnClickListener { goNext() }
        }
        val density = resources.displayMetrics.density
        list.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (96 * density).toInt()
        ).apply { bottomMargin = (12 * density).toInt() })
    }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
    }

    private fun showLevel() {
        setContentView(R.layout.screen_level)
        setupBackAndNext()
    }

    private fun showDestination() {
        setContentView(R.layout.screen_destination)
        setupBackAndNext()
    }

    private fun setupBackAndNext() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
        findViewById<Button>(R.id.btnNext).setOnClickListener { goNext() }
    }

    private fun goNext() {
        showConfirm()
    }

    private fun showConfirm() {
        setContentView(R.layout.screen_confirm)
        findViewById<Button>(R.id.btnStart).setOnClickListener { showDrive() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { showHome() }
    }

    private fun showDrive() {
        setContentView(R.layout.screen_drive)
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
        findViewById<TextView>(R.id.resultDetail).text = buildList {
            record.elements.filterNot { it.rerouted }.forEach {
                val signal = if (it.turnSignalOn) "방향지시등 켬" else "방향지시등 안 켬"
                add("${it.element.label} · $signal  ${it.element.points}p")
            }
            if (earned.turnSignalBonus > 0) add("방향지시등 보너스  ${earned.turnSignalBonus}p")
            add("거리 %.1fkm  ${earned.distance}p".format(record.distanceKm))
            add("예상 시간 ${record.estimatedMinutes}분  ${earned.time}p")
            if (earned.safetyBonus > 0) add("안전 운전 보너스  ${earned.safetyBonus}p")
            if (earned.penalty > 0) add("위험 운전 감점  -${earned.penalty}p")
        }.joinToString("\n")
        findViewById<Button>(R.id.btnHome).setOnClickListener { showHome() }
    }

    override fun onDestroy() {
        naviHelper.release()
        super.onDestroy()
    }
}