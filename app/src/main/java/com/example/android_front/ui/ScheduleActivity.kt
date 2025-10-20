package com.example.android_front.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.example.android_front.R
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.DispatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleActivity : AppCompatActivity() {

    private lateinit var weeklyView: WeeklyDispatchView
    private lateinit var tvDate: TextView

    private var currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY)
    private var currentWeekEnd = currentWeekStart.plusDays(6)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_calendar)

        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
        weeklyView = findViewById(R.id.weeklyDispatchView)
        tvDate = findViewById(R.id.tv_date)

        findViewById<View>(R.id.btn_prevDate).setOnClickListener {
            changeWeek(-1)
        }
        findViewById<View>(R.id.btn_nextDate).setOnClickListener {
            changeWeek(1)
        }

        updateDateText()
        fetchDispatchList(currentWeekStart, currentWeekEnd)
    }

    private fun changeWeek(offset: Long) {
        currentWeekStart = currentWeekStart.plusWeeks(offset)
        currentWeekEnd = currentWeekStart.plusDays(6)

        weeklyView.currentWeekStart = currentWeekStart // ← 추가
        weeklyView.invalidate() // 뷰 새로 그리기

        updateDateText()
        fetchDispatchList(currentWeekStart, currentWeekEnd)
    }

    private fun updateDateText() {
        val startText = "${currentWeekStart.monthValue}/${currentWeekStart.dayOfMonth}"
        val endText = "${currentWeekEnd.monthValue}/${currentWeekEnd.dayOfMonth}"
        tvDate.text = "$startText ~ $endText"
    }

    private fun fetchDispatchList(startDate: LocalDate, endDate: LocalDate) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchList(
                    startDate = startDate.toString(),
                    endDate = endDate.toString()
                )

                withContext(Dispatchers.Main) {
                    val events = if (response.isSuccessful && response.body()?.success == true) {
                        response.body()?.data?.mapNotNull { dispatch ->
                            try {
                                // 상태 필터링
                                val statusEnum = dispatch.status
                                if (statusEnum != DispatchStatus.SCHEDULED && statusEnum != DispatchStatus.COMPLETED) {
                                    return@mapNotNull null
                                }

                                val localDate = LocalDate.parse(dispatch.dispatchDate)
                                val dayOfWeek = (localDate.dayOfWeek.value + 6) % 7

                                val startTime = dispatch.scheduledDepartureTime.substringAfter("T")
                                    .substring(0, 5)
                                val endTime = dispatch.scheduledArrivalTime.substringAfter("T")
                                    .substring(0, 5)

                                val startParts = startTime.split(":")
                                val endParts = endTime.split(":")

                                DispatchEvent(
                                    dispatchId = dispatch.dispatchId,
                                    routeNumber = dispatch.routeNumber,
                                    dayOfWeek = dayOfWeek,
                                    startHour = startParts[0].toInt(),
                                    startMinute = startParts[1].toInt(),
                                    endHour = endParts[0].toInt(),
                                    endMinute = endParts[1].toInt(),
                                    status = statusEnum.displayName
                                )
                            } catch (e: Exception) {
                                Log.e("ScheduleActivity", "Error parsing dispatch: $dispatch", e)
                                null
                            }
                        } ?: emptyList()
                    } else {
                        Log.e("ScheduleActivity", "API failed: ${response.code()}, ${response.body()?.message}")
                        emptyList()
                    }

                    Log.d("ScheduleActivity", "Loaded ${events.size} dispatch events")
                    weeklyView.dispatchEvents = events
                    weeklyView.invalidate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ScheduleActivity", "Network error", e)
                    weeklyView.dispatchEvents = emptyList()
                    weeklyView.invalidate()
                }
            }
        }
    }

}

// ================== 데이터 클래스 ==================
data class DispatchEvent(
    val dispatchId: Long,
    val routeNumber: String,
    val dayOfWeek: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val status: String
)

// ================== 커스텀 뷰 ==================
class WeeklyDispatchView(context: android.content.Context, attrs: AttributeSet?) : View(context, attrs) {

    var dispatchEvents: List<DispatchEvent> = emptyList()
    var currentWeekStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = Color.BLACK
    }

    private val headerHeight = 50f
    private val timeLabelWidth = 50f
    private val columnCount = 7

    // 🔧 클래스 레벨 상수로 이동
    private val startHour = 0
    private val endHour = 24
    private val hourHeight = 180f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 🔧 뷰 크기 체크
        if (width <= 0 || height <= 0) {
            Log.w("WeeklyDispatchView", "Invalid view size: ${width}x${height}")
            return
        }
        Log.d("WeeklyDispatchView", "Drawing view: ${width}x${height}, events: ${dispatchEvents.size}")
        drawTodayBackground(canvas)
        drawGrid(canvas)
        drawDayHeaders(canvas)
        drawTimeLabels(canvas)
        drawDispatchBlocks(canvas)
    }

    // ----------------- 격자선 -----------------
    private fun drawGrid(canvas: Canvas) {
        val columnWidth = (width - timeLabelWidth) / columnCount.toFloat()

        paint.color = Color.LTGRAY
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE

        // ─────────── 가로선 (시간별, 라벨 포함)
        // 마지막 줄(24시)은 제외하고 그리기
        for (h in startHour until endHour) { // ← until 로 변경
            val y = headerHeight + (h - startHour) * hourHeight
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
        }

        // ─────────── 헤더 하단선 (요일 라벨 아래 구분선)
        canvas.drawLine(0f, headerHeight, width.toFloat(), headerHeight, paint)

        // ─────────── 세로선 (라벨 경계 + 요일 컬럼)
        // 시간 라벨 구역 오른쪽 경계
        canvas.drawLine(timeLabelWidth, 0f, timeLabelWidth, headerHeight + (endHour - startHour) * hourHeight, paint)

        // 요일 구분선 (마지막 컬럼 제외)
        for (i in 0 until columnCount) { // ← until 로 변경
            val x = timeLabelWidth + i * columnWidth
            canvas.drawLine(x, 0f, x, headerHeight + (endHour - startHour) * hourHeight, paint)
        }
    }


    // ----------------- 요일 헤더 -----------------
    private fun drawDayHeaders(canvas: Canvas) {
        val columnWidth = (width - timeLabelWidth) / columnCount.toFloat()
        val dayNames = arrayOf("월", "화", "수", "목", "금", "토", "일")

        paint.textSize = 24f
        paint.color = Color.GRAY
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER

        val typefaceRegular = ResourcesCompat.getFont(context, R.font.pretendard_regular)
        paint.typeface = typefaceRegular

        for (i in dayNames.indices) {
            val x = timeLabelWidth + i * columnWidth + columnWidth / 2
            val y = headerHeight / 2 + paint.textSize / 2
            canvas.drawText(dayNames[i], x, y, paint)
        }
    }

    // ----------------- 시간 라벨 -----------------
    private fun drawTimeLabels(canvas: Canvas) {
        val typefaceRegular = ResourcesCompat.getFont(context, R.font.pretendard_regular)

        paint.color = Color.GRAY
        paint.textSize = 24f
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = typefaceRegular

        val fontMetrics = paint.fontMetrics

        for (h in startHour until endHour) {
            val yTop = headerHeight + (h - startHour) * hourHeight
            // 오른쪽 상단 기준으로 배치 (약간의 패딩 추가)
            val x = timeLabelWidth - 12f
            val y = yTop - fontMetrics.ascent + 8f // ascent 보정으로 상단 정렬
            canvas.drawText("${h}", x, y, paint)
        }
    }


    // ----------------- 배차 블록 -----------------
    private fun drawDispatchBlocks(canvas: Canvas) {
        val columnWidth = (width - timeLabelWidth) / columnCount.toFloat()
        val typefaceSemibold = ResourcesCompat.getFont(context, R.font.pretendard_semibold)

        dispatchEvents.forEach { event ->
            val startY = headerHeight + ((event.startHour + event.startMinute / 60f) - startHour) * hourHeight
            val endYRaw = event.endHour + event.endMinute / 60f

            if (endYRaw > endHour) {
                // 오늘
                val todayEndY = headerHeight + (endHour - startHour) * hourHeight
                drawBlock(canvas, event.dayOfWeek, startY, todayEndY, event.routeNumber, event.status)

                // 다음날
                val nextDayEvent = event.copy(
                    dayOfWeek = (event.dayOfWeek + 1) % 7,
                    startHour = 0,
                    startMinute = 0
                )
                val nextEndY = headerHeight + ((event.endHour + event.endMinute / 60f) - 24 - startHour) * hourHeight
                drawBlock(canvas, nextDayEvent.dayOfWeek, headerHeight, nextEndY, nextDayEvent.routeNumber, nextDayEvent.status)
            } else {
                val endY = headerHeight + ((event.endHour + event.endMinute / 60f) - startHour) * hourHeight
                drawBlock(canvas, event.dayOfWeek, startY, endY, event.routeNumber, event.status)
            }
        }
    }

    private fun drawBlock(canvas: Canvas, dayOfWeek: Int, top: Float, bottom: Float, route: String, status: String) {
        val columnWidth = (width - timeLabelWidth) / columnCount.toFloat()
        val left = timeLabelWidth + dayOfWeek * columnWidth
        val right = left + columnWidth - 1f

        // 박스 색상 결정
        paint.color = when(status) {
            DispatchStatus.SCHEDULED.displayName -> ContextCompat.getColor(context, R.color.main)
            DispatchStatus.COMPLETED.displayName -> Color.parseColor("#4CAF50") // 녹색
            else -> ContextCompat.getColor(context, R.color.main)
        }
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, top, right, bottom, paint)

        val blockHeight = bottom - top
        val minTextHeight = 30f * hourHeight / 60f // 30분에 해당하는 높이
        if (blockHeight < minTextHeight) return

        // 텍스트 왼쪽 상단 정렬
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = ResourcesCompat.getFont(context, R.font.pretendard_semibold)

        val padding = 8f // 블록 안쪽 여백
        val x = left + padding

        // 노선번호
        paint.textSize = 32f
        val routeY = top - paint.ascent() + padding
        canvas.drawText(route, x, routeY, paint)

        // 배차 상태
        paint.textSize = 24f
        val statusY = routeY + paint.descent() - paint.ascent() + 4f
        canvas.drawText(status, x, statusY, paint)
    }
    private fun drawTodayBackground(canvas: Canvas) {
        val today = LocalDate.now()
        val startOfWeek = currentWeekStart
        val endOfWeek = startOfWeek.plusDays(6)

        if (today.isBefore(startOfWeek) || today.isAfter(endOfWeek)) return

        val columnWidth = (width - timeLabelWidth) / columnCount.toFloat()
        val dayIndex = (today.dayOfWeek.value + 6) % 7 // 월=0, ..., 일=6

        val left = timeLabelWidth + dayIndex * columnWidth
        val right = left + columnWidth

        paint.color = Color.parseColor("#F5F5F5")
        paint.style = Paint.Style.FILL
        canvas.drawRect(left, headerHeight, right, headerHeight + (endHour - startHour) * hourHeight, paint)
    }

    // ----------------- 높이 측정 (ScrollView용) -----------------
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (headerHeight + (endHour - startHour) * hourHeight).toInt()
        val heightSpec = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
