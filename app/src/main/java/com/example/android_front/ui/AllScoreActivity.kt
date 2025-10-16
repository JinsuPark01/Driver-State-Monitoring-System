package com.example.android_front.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AllScoreActivity : AppCompatActivity() {
    private lateinit var btnBack: ImageView
    private lateinit var circularGauge: CircularProgressIndicator
    private lateinit var tvPercentage: TextView
    private lateinit var tvSleep: TextView
    private lateinit var tvOverSpeed: TextView
    private lateinit var tvUnderSpeed: TextView
    private lateinit var tvAbnormal: TextView
    private lateinit var lineChartDriveScore: LineChart
    private lateinit var lineChart: LineChart


    // 버튼 및 선택 표시 뷰
    private lateinit var btnSleep: LinearLayout
    private lateinit var btnAbnormal: LinearLayout
    private lateinit var btnOver: LinearLayout
    private lateinit var btnUnder: LinearLayout

    private lateinit var selectedSleep: View
    private lateinit var selectedAbnormal: View
    private lateinit var selectedOver: View
    private lateinit var selectedUnder: View

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val chartFormatter = DateTimeFormatter.ofPattern("MM/dd")

    // 데이터셋 멤버 변수
    private lateinit var setDriveScore: LineDataSet
    private lateinit var setDrowsiness: LineDataSet
    private lateinit var setAcceleration: LineDataSet
    private lateinit var setBraking: LineDataSet
    private lateinit var setAbnormal: LineDataSet
    private lateinit var setDispatchCount: LineDataSet // 배차횟수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_score)

        // 뷰 초기화
        btnBack = findViewById(R.id.btnBack)
        circularGauge = findViewById(R.id.item_circular_gauge_bar)
        tvPercentage = findViewById(R.id.tv_circular_gauge_percentage)
        tvSleep = findViewById(R.id.tv_sleep)
        tvOverSpeed = findViewById(R.id.tv_over_speed)
        tvUnderSpeed = findViewById(R.id.tv_under_speed)
        tvAbnormal = findViewById(R.id.tv_abnormal)
        lineChartDriveScore = findViewById(R.id.line_chart_driveScore)
        lineChart = findViewById(R.id.line_chart)

        btnSleep = findViewById(R.id.btn_sleep)
        btnAbnormal = findViewById(R.id.btn_abnormal)
        btnOver = findViewById(R.id.btn_over)
        btnUnder = findViewById(R.id.btn_under)

        selectedSleep = findViewById(R.id.selected_sleep)
        selectedAbnormal = findViewById(R.id.selected_abnormal)
        selectedOver = findViewById(R.id.selected_over)
        selectedUnder = findViewById(R.id.selected_under)

        // 뒤로가기
        btnBack.setOnClickListener { finish() }

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
                updateDriveScoreChart(dailyRecords, startDate, endDate)
                showChart("sleep") // 초기 화면은 졸음만
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
            // 세 가지 이상행동 합산
            val abnormalCount = (record.smokingCount ?: 0) +
                    (record.seatbeltUnfastenedCount ?: 0) +
                    (record.phoneUsageCount ?: 0)
            totalAbnormal += abnormalCount
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
    private fun updateDriveScoreChart(
        dailyRecords: Map<String, List<DispatchRecordResponse>>,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        val dateList = mutableListOf<String>()
        val entriesDriveScore = mutableListOf<Entry>()
        val entriesDispatchCountDriveScore = mutableListOf<Entry>() // 운행점수용 배차 횟수
        var current = startDate
        var index = 0f
        val pretendard = ResourcesCompat.getFont(this, R.font.pretendard_regular)
        val pretendardBold = ResourcesCompat.getFont(this, R.font.pretendard_bold)

        while (!current.isAfter(endDate)) {
            val key = current.format(dateFormatter)
            val chartLabel = current.format(chartFormatter)
            dateList.add(chartLabel)

            val records = dailyRecords[key] ?: emptyList()

            // 운행점수 평균
            val avgScore = if (records.isNotEmpty()) records.sumOf { it.drivingScore }.toFloat() / records.size else 0f
            entriesDriveScore.add(Entry(index, avgScore))

            // 배차횟수 (운행점수용)
            entriesDispatchCountDriveScore.add(Entry(index, records.size.toFloat()))

            index++
            current = current.plusDays(1)
        }

        // 운행점수 데이터셋
        val setDriveScore = LineDataSet(entriesDriveScore, "운행 점수(평균)").apply {
            color = 0x904C7DCC.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFF4C7DCC.toInt())
            valueTextColor = 0xFF4C7DCC.toInt()
            valueTextSize = 12f
            valueTypeface = pretendardBold
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
        }

        // 배차횟수 데이터셋 (운행점수용)
        val setDispatchCountDrive = LineDataSet(entriesDispatchCountDriveScore, "배차횟수(회)").apply {
            color = Color.LTGRAY
            lineWidth = 3f
            circleRadius = 1f
            setDrawCircleHole(false)
            setCircleColor(Color.LTGRAY)
            valueTextColor = Color.LTGRAY
            valueTextSize = 12f
            valueTypeface = pretendard
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
            setDrawValues(false)
        }

        lineChartDriveScore.apply {
            // X축, Y축, 값 텍스트에 폰트 적용
            xAxis.typeface = pretendard
            legend.typeface = pretendard
            data = LineData(setDispatchCountDrive, setDriveScore)
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                granularity = 1f
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(true)
                valueFormatter = XAxisValueFormatter(dateList)
            }
            axisLeft.apply {
                axisMinimum = -10f
                axisMaximum = 120f
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
            }
            axisRight.apply {
                axisMinimum = -1f
                axisMaximum = 12f
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
            }
            legend.apply {
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                xEntrySpace = 8f
                setDrawInside(false)
            }

            description.isEnabled = false
            legend.isEnabled = true
            invalidate()
        }
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
        val entriesDispatchCount = mutableListOf<Entry>()
        val pretendard = ResourcesCompat.getFont(this, R.font.pretendard_regular)
        val pretendardBold = ResourcesCompat.getFont(this, R.font.pretendard_bold)

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
            val avgAbnormal = records.sumOf {
                (it.smokingCount ?: 0) +
                        (it.seatbeltUnfastenedCount ?: 0) +
                        (it.phoneUsageCount ?: 0)
            } / count

            entriesDrowsiness.add(Entry(index, avgDrowsiness.toFloat()))
            entriesAcceleration.add(Entry(index, avgAcceleration.toFloat()))
            entriesBraking.add(Entry(index, avgBraking.toFloat()))
            entriesAbnormal.add(Entry(index, avgAbnormal.toFloat()))
            entriesDispatchCount.add(Entry(index, records.size.toFloat())) // 배차 횟수

            index++
            current = current.plusDays(1)
        }

        val valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getPointLabel(entry: Entry?): String {
                return String.format("%.1f", entry?.y ?: 0f)
            }
        }

        setDrowsiness = LineDataSet(entriesDrowsiness, "졸음(평균)").apply {
            color = 0x90449fd5.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFF449fd5.toInt())
            valueTextColor = 0xFF449fd5.toInt()
            valueTextSize = 12f
            valueTypeface = pretendardBold
            this.valueFormatter = valueFormatter
        }

        setAcceleration = LineDataSet(entriesAcceleration, "급가속(평균)").apply {
            color = 0x90498bd0.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFF498bd0.toInt())
            valueTextColor = 0xFF498bd0.toInt()
            valueTextSize = 12f
            valueTypeface = pretendardBold
            this.valueFormatter = valueFormatter
        }

        setBraking = LineDataSet(entriesBraking, "급제동(평균)").apply {
            color = 0x904f71c8.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFF4f71c8.toInt())
            valueTextColor = 0xFF4f71c8.toInt()
            valueTextSize = 12f
            valueTypeface = pretendardBold
            this.valueFormatter = valueFormatter
        }

        setAbnormal = LineDataSet(entriesAbnormal, "이상행동(평균)").apply {
            color = 0x90555cc1.toInt()
            lineWidth = 3f
            circleRadius = 5f
            setCircleColor(0xFF555cc1.toInt())
            valueTextColor = 0xFF555cc1.toInt()
            valueTextSize = 12f
            valueTypeface = pretendardBold
            this.valueFormatter = valueFormatter
        }

        // 배차 횟수는 회색
        setDispatchCount = LineDataSet(entriesDispatchCount, "배차횟수(회)").apply {
            color = Color.LTGRAY
            lineWidth = 3f
            circleRadius = 1f
            setDrawCircleHole(false)
            setCircleColor(Color.LTGRAY)
            valueTextColor = Color.LTGRAY
            this.valueFormatter = valueFormatter
            setDrawValues(false)
        }

        // 초기 그래프는 졸음 + 배차횟수
        lineChart.apply {
            // X축, Y축, 값 텍스트에 폰트 적용
            xAxis.typeface = pretendard
            legend.typeface = pretendard
            data = LineData(setDrowsiness, setDispatchCount)
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
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                xEntrySpace = 8f
            }

            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = true
            invalidate()
        }
    }

    private fun setupButtonClick() {
        btnSleep.setOnClickListener { showChart("sleep") }
        btnAbnormal.setOnClickListener { showChart("abnormal") }
        btnOver.setOnClickListener { showChart("over") }
        btnUnder.setOnClickListener { showChart("under") }
    }

    private fun showChart(type: String) {
        val dataSets = when (type) {
            "sleep" -> listOf(setDrowsiness)
            "abnormal" -> listOf(setAbnormal)
            "over" -> listOf(setAcceleration)
            "under" -> listOf(setBraking)
            else -> emptyList()
        }

        // 항상 배차횟수 포함
        val allDataSets = mutableListOf<ILineDataSet>()
        allDataSets.add(setDispatchCount)  // 배차 횟수
        allDataSets.addAll(dataSets)       // 선택한 경고 그래프

        lineChart.data = LineData(allDataSets)
        lineChart.invalidate()
        // 선택 표시 색상 업데이트
        selectedSleep.setBackgroundColor(if (type == "sleep") ContextCompat.getColor(this, R.color.drowsiness) else ContextCompat.getColor(this, R.color.white))
        selectedAbnormal.setBackgroundColor(if (type == "abnormal") ContextCompat.getColor(this, R.color.abnormal) else ContextCompat.getColor(this, R.color.white))
        selectedOver.setBackgroundColor(if (type == "over") ContextCompat.getColor(this, R.color.over) else ContextCompat.getColor(this, R.color.white))
        selectedUnder.setBackgroundColor(if (type == "under") ContextCompat.getColor(this, R.color.under) else ContextCompat.getColor(this, R.color.white))
    }

    class XAxisValueFormatter(private val labels: List<String>) :
        com.github.mikephil.charting.formatter.ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index in labels.indices) labels[index] else ""
        }
    }
}