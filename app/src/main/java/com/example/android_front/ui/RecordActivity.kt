package com.example.android_front.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.adapter.DispatchEventAdapter
import com.example.android_front.api.RetrofitInstance
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordActivity : AppCompatActivity() {

    private lateinit var tvDrivingScore: TextView
    private lateinit var tvVehicleNumber: TextView
    private lateinit var tvRouteNumber: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDepartureTime: TextView
    private lateinit var tvArrivalTime: TextView
    private lateinit var tvDriveStatus: TextView

    private lateinit var tvSleepAvg: TextView
    private lateinit var tvOverSpeed: TextView
    private lateinit var tvUnderSpeed: TextView
    private lateinit var tvAbnormal: TextView

    private lateinit var rvEvents: RecyclerView
    private lateinit var eventsAdapter: DispatchEventAdapter
    private lateinit var circularGauge: CircularProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_record)

        // View 연결
        val btnBack = findViewById<View>(R.id.btnBack)
        tvDrivingScore = findViewById(R.id.tv_circular_gauge_percentage)
        tvVehicleNumber = findViewById(R.id.tvVehicleNumber)
        tvRouteNumber = findViewById(R.id.tvRouteNumber)
        tvDate = findViewById(R.id.tvDate)
        tvDepartureTime = findViewById(R.id.tvDepartureTime)
        tvArrivalTime = findViewById(R.id.tvArrivalTime)
        tvDriveStatus = findViewById(R.id.tvDriveStatus)

        tvSleepAvg = findViewById(R.id.tv_sleep_avg)
        tvOverSpeed = findViewById(R.id.tv_over_speed)
        tvUnderSpeed = findViewById(R.id.tv_under_speed)
        tvAbnormal = findViewById(R.id.tv_abnormal)

        rvEvents = findViewById(R.id.rvEvents)
        eventsAdapter = DispatchEventAdapter(emptyList())
        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.adapter = eventsAdapter

        circularGauge = findViewById(R.id.item_circular_gauge_bar)

        // 뒤로가기
        btnBack.setOnClickListener { finish() }

        // 전달받은 dispatchId
        val dispatchId = intent.getLongExtra("dispatchId", -1)
        if (dispatchId != -1L) {
            fetchDispatchDetail(dispatchId)
            fetchDispatchRecord(dispatchId)
            fetchDispatchEvents(dispatchId) // 이벤트 리스트 불러오기
        } else {
            Toast.makeText(this, "잘못된 배차 정보입니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchDispatchDetail(dispatchId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchDetail(dispatchId)
                withContext(Dispatchers.Main) {
                    val apiResponse = response.body()
                    if (response.isSuccessful && apiResponse != null && apiResponse.success && apiResponse.data != null) {
                        val detail = apiResponse.data
                        // UI 업데이트
                        tvVehicleNumber.text = ": ${detail.vehicleNumber}"
                        tvRouteNumber.text = ": ${detail.routeNumber}"
                        tvDate.text = ": ${detail.dispatchDate}"
                        tvDepartureTime.text =
                            ": ${detail.actualDepartureTime?.substringAfter("T") ?: "미기록"}"
                        tvArrivalTime.text =
                            ": ${detail.actualArrivalTime?.substringAfter("T") ?: "미기록"}"
                        tvDriveStatus.text = ": ${detail.status.displayName}"
                    } else {
                        Toast.makeText(
                            this@RecordActivity,
                            apiResponse?.message ?: "상세 조회 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RecordActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun fetchDispatchRecord(dispatchId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchRecord(dispatchId)
                withContext(Dispatchers.Main) {
                    val apiResponse = response.body()
                    if (response.isSuccessful && apiResponse != null && apiResponse.success && apiResponse.data != null) {
                        val detail = apiResponse.data
                        // UI 업데이트
                        tvDrivingScore.text = "${detail.drivingScore}점"
                        circularGauge.progress = detail.drivingScore
                        tvSleepAvg.text = "${detail.drowsinessCount}회"
                        tvOverSpeed.text = "${detail.accelerationCount}회"
                        tvUnderSpeed.text = "${detail.brakingCount}회"
                        tvAbnormal.text = "${detail.abnormalCount}회"
                    } else {
                        Toast.makeText(
                            this@RecordActivity,
                            apiResponse?.message ?: "기록 조회 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RecordActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun fetchDispatchEvents(dispatchId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchEvents(dispatchId)
                withContext(Dispatchers.Main) {
                    val apiResponse = response.body()
                    if (response.isSuccessful && apiResponse?.success == true && apiResponse.data != null) {
                        // 처음에 한 번만 세팅
                        val eventsAdapter = DispatchEventAdapter(apiResponse.data)
                        rvEvents.adapter = eventsAdapter
                    } else {
                        Toast.makeText(
                            this@RecordActivity,
                            apiResponse?.message ?: "이벤트 조회 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RecordActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
