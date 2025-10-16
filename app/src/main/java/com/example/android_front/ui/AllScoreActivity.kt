package com.example.android_front.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.android_front.R
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.DispatchRecordResponse
import com.example.android_front.model.DispatchStatus
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AllScoreActivity : AppCompatActivity() {

    private lateinit var circularGauge: CircularProgressIndicator
    private lateinit var tvPercentage: TextView
    private lateinit var tvSleep: TextView
    private lateinit var tvOverSpeed: TextView
    private lateinit var tvUnderSpeed: TextView
    private lateinit var tvAbnormal: TextView
    private lateinit var lineChart: LineChart

    // 버튼 및 선택 표시 뷰
    private lateinit var btnAll: LinearLayout
    private lateinit var btnSleep: LinearLayout
    private lateinit var btnAbnormal: LinearLayout
    private lateinit var btnOver: LinearLayout
    private lateinit var btnUnder: LinearLayout

    private lateinit var selectedAll: View
    private lateinit var selectedSleep: View
    private lateinit var selectedAbnormal: View
    private lateinit var selectedOver: View
    private lateinit var selectedUnder: View

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val chartFormatter = DateTimeFormatter.ofPattern("MM/dd")

    // 데이터셋 멤버 변수
    private lateinit var setDrowsiness: LineDataSet
    private lateinit var setAcceleration: LineDataSet
    private lateinit var setBraking: LineDataSet
    private lateinit var setAbnormal: LineDataSet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_score)

        // 뒤로가기 버튼
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        circularGauge = findViewById(R.id.item_circular_gauge_bar)
        tvPercentage = findViewById(R.id.tv_circular_gauge_percentage)
        tvSleep = findViewById(R.id.tv_sleep)
        tvOverSpeed = findViewById(R.id.tv_over_speed)
        tvUnderSpeed = findViewById(R.id.tv_under_speed)
        tvAbnormal = findViewById(R.id.tv_abnormal)
        lineChart = findViewById(R.id.line_chart)

        btnAll = findViewById(R.id.btn_all)
        btnSleep = findViewById(R.id.btn_sleep)
        btnAbnormal = findViewById(R.id.btn_abnormal)
        btnOver = findViewById(R.id.btn_over)
        btnUnder = findViewById(R.id.btn_under)

        selectedAll = findViewById(R.id.selected_all)
        selectedSleep = findViewById(R.id.selected_sleep)
        selectedAbnormal = findViewById(R.id.selected_abnormal)
        selectedOver = findViewById(R.id.selected_over)
        selectedUnder = findViewById(R.id.selected_under)

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(6)

                val dispatchListResponse = RetrofitInstance.dispatchApi.getDispatchList(
                    startDate.format(dateFormatter),
                    endDate.format(dateFormatter)
                )

                if (!dispatchListResponse.isSuccessful) {
                    Toast.makeText(this@AllScoreActivity, "배차 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val dispatchList = dispatchListResponse.body()?.data ?: emptyList()
                val completedDispatches = dispatchList.filter { it.status == DispatchStatus.COMPLETED }

                if (completedDispatches.isEmpty()) {
                    Toast.makeText(this@AllScoreActivity, "완료된 배차가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val groupedByDate = completedDispatches.groupBy { it.dispatchDate }
                val dailyRecords = mutableMapOf<String, List<DispatchRecordResponse>>()

                groupedByDate.forEach { (date, dispatches) ->
                    val recordDeferred = dispatches.map { dispatch ->
                        async {
                            val resp = RetrofitInstance.dispatchApi.getDispatchRecord(dispatch.dispatchId)
                            if (resp.isSuccessful) resp.body()?.data else null
                        }
                    }
                    val records = recordDeferred.awaitAll().filterNotNull()
                    dailyRecords[date] = records
                }

                val allRecords = dailyRecords.values.flatten()
                if (allRecords.isEmpty()) {
                    Toast.makeText(this@AllScoreActivity, "배차 기록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                updateTopStats(allRecords)
                updateChart(dailyRecords, startDate, endDate)
                setupButtonClick()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@AllScoreActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTopStats(records: List<DispatchRecordResponse>) {
        var totalScore = 0
        var totalSleep = 0
        var totalOver = 0
        var totalUnder = 0
        var totalAbnormal = 0

        records.forEach { record ->
            totalScore += record.drivingScore
            totalSleep += record.drowsinessCount
            totalOver += record.accelerationCount
            totalUnder += record.brakingCount
            totalAbnormal += record.abnormalCount
        }

        val count = records.size.toDouble()
        val avgScore = totalScore / count

        tvPercentage.text = "${String.format("%.0f", avgScore)}점"
        circularGauge.progress = avgScore.toInt()

        tvSleep.text = "${String.format("%.1f", totalSleep / count)}회"
        tvOverSpeed.text = "${String.format("%.1f", totalOver / count)}회"
        tvUnderSpeed.text = "${String.format("%.1f", totalUnder / count)}회"
        tvAbnormal.text = "${String.format("%.1f", totalAbnormal / count)}회"
    }

    private fun updateChart(
        dailyRecords: Map<String, List<DispatchRecordResponse>>,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        val dateList = mutableListOf<String>()
        val entriesDrowsiness = mutableListOf<Entry>()
        val entriesAcceleration = mutableListOf<Entry>()
        val entriesBraking = mutableListOf<Entry>()
        val entriesAbnormal = mutableListOf<Entry>()

        var current = startDate
        var index = 0f
        while (!current.isAfter(endDate)) {
            val key = current.format(dateFormatter)
            val chartLabel = current.format(chartFormatter)
            dateList.add(chartLabel)

            val records = dailyRecords[key] ?: emptyList()
            val count = records.size.toDouble().coerceAtLeast(1.0)

            val avgDrowsiness = records.sumOf { it.drowsinessCount } / count
            val avgAcceleration = records.sumOf { it.accelerationCount } / count
            val avgBraking = records.sumOf { it.brakingCount } / count
            val avgAbnormal = records.sumOf { it.abnormalCount } / count

            entriesDrowsiness.add(Entry(index, avgDrowsiness.toFloat()))
            entriesAcceleration.add(Entry(index, avgAcceleration.toFloat()))
            entriesBraking.add(Entry(index, avgBraking.toFloat()))
            entriesAbnormal.add(Entry(index, avgAbnormal.toFloat()))

            index++
            current = current.plusDays(1)
        }

        val valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getPointLabel(entry: Entry?): String {
                return String.format("%.1f", entry?.y ?: 0f)
            }
        }

        // 데이터셋 생성 후 멤버 변수에 할당
        setDrowsiness = LineDataSet(entriesDrowsiness, "졸음").apply {
            color = 0xFF1E88E5.toInt()           // 라인 색
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFF64B5F6.toInt())   // 원 색
            valueTextColor = 0xFF64B5F6.toInt()  // 값 텍스트 색
            this.valueFormatter = valueFormatter
        }

        setAcceleration = LineDataSet(entriesAcceleration, "급가속").apply {
            color = 0xFFD32F2F.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFFE57373.toInt())   // 원 색
            valueTextColor = 0xFFE57373.toInt()  // 값 텍스트 색
            this.valueFormatter = valueFormatter
        }

        setBraking = LineDataSet(entriesBraking, "급제동").apply {
            color = 0xFFFFA000.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFFFFC107.toInt())   // 원 색
            valueTextColor = 0xFFFFC107.toInt()  // 값 텍스트 색
            this.valueFormatter = valueFormatter
        }

        setAbnormal = LineDataSet(entriesAbnormal, "이상행동").apply {
            color = 0xFF388E3C.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFF81C784.toInt())   // 원 색
            valueTextColor = 0xFF81C784.toInt()  // 값 텍스트 색
            this.valueFormatter = valueFormatter
        }

        // 초기 전체 그래프 표시
        lineChart.apply {
            data = LineData(setDrowsiness, setAcceleration, setBraking, setAbnormal)
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                this.valueFormatter = XAxisValueFormatter(dateList)
                granularity = 1f
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(true)
            }

            axisLeft.apply {
                axisMinimum = -1f
                axisMaximum = 10f
                granularity = 2f
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
            }
            legend.apply {
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                xEntrySpace = 8f
//                setDrawInside(false)
//                yOffset = -10f
//                formToTextSpace = 8f
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = true
            invalidate()
        }
    }

    private fun setupButtonClick() {
        btnAll.setOnClickListener { showChart("all") }
        btnSleep.setOnClickListener { showChart("sleep") }
        btnAbnormal.setOnClickListener { showChart("abnormal") }
        btnOver.setOnClickListener { showChart("over") }
        btnUnder.setOnClickListener { showChart("under") }
    }

    private fun showChart(type: String) {
        // 전체 버튼일 때만 valueTextSize 작게
        if (type == "all") {
            setDrowsiness.valueTextSize = 8f
            setAcceleration.valueTextSize = 8f
            setBraking.valueTextSize = 8f
            setAbnormal.valueTextSize = 8f
        } else {
            // 다른 버튼 클릭 시 원래 크기로
            setDrowsiness.valueTextSize = 12f
            setAcceleration.valueTextSize = 12f
            setBraking.valueTextSize = 12f
            setAbnormal.valueTextSize = 12f
        }

        val dataSets = when (type) {
            "all" -> listOf(setDrowsiness, setAcceleration, setBraking, setAbnormal)
            "sleep" -> listOf(setDrowsiness)
            "abnormal" -> listOf(setAbnormal)
            "over" -> listOf(setAcceleration)
            "under" -> listOf(setBraking)
            else -> emptyList()
        }

        lineChart.data = LineData(dataSets)
        lineChart.invalidate()

        // 선택 표시 색상 업데이트
        selectedAll.setBackgroundColor(if (type == "all") Color.parseColor("#2196F3") else Color.WHITE)
        selectedSleep.setBackgroundColor(if (type == "sleep") Color.parseColor("#2196F3") else Color.WHITE)
        selectedAbnormal.setBackgroundColor(if (type == "abnormal") Color.parseColor("#2196F3") else Color.WHITE)
        selectedOver.setBackgroundColor(if (type == "over") Color.parseColor("#2196F3") else Color.WHITE)
        selectedUnder.setBackgroundColor(if (type == "under") Color.parseColor("#2196F3") else Color.WHITE)
    }

    class XAxisValueFormatter(private val labels: List<String>) :
        com.github.mikephil.charting.formatter.ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index in labels.indices) labels[index] else ""
        }
    }
}
