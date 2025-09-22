package com.example.android_front.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.R
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.DispatchDetailResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordActivity : AppCompatActivity() {

    private lateinit var tvDriverName: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_record)

        // View 연결
        val btnBack = findViewById<View>(R.id.btnBack)
        tvDriverName = findViewById(R.id.tvDriverName)
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

        // 뒤로가기
        btnBack.setOnClickListener { finish() }

        // 전달받은 dispatchId
        val dispatchId = intent.getLongExtra("dispatchId", -1)
        if (dispatchId != -1L) {
            fetchDispatchDetail(dispatchId)
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
                    if (response.isSuccessful) {
                        val detail: DispatchDetailResponse? = response.body()
                        if (detail != null) {
                            // UI 업데이트
                            tvDriverName.text = "운행자 : ${detail.username}"
                            tvVehicleNumber.text = "차량 번호 : ${detail.busNumber}"
                            tvRouteNumber.text = "노선 : ${detail.routeNumber}"
                            tvDate.text = "날짜 : ${detail.dispatchDate}"
                            tvDepartureTime.text = "출발 시간 : ${detail.scheduledDeparture}"
                            tvArrivalTime.text = "도착 시간 : ${detail.actualArrival}"
                            tvDriveStatus.text = detail.status.displayName

                            tvSleepAvg.text = "${detail.drowsinessCount}회"
                            tvOverSpeed.text = "${detail.accelerationCount}회"
                            tvUnderSpeed.text = "${detail.brakingCount}회"
                            tvAbnormal.text = "${detail.abnormalCount}회"
                        }
                    } else {
                        Toast.makeText(this@RecordActivity, "상세 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
