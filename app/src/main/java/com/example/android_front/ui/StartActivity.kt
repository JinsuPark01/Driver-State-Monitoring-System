package com.example.android_front.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.R
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.api.DispatchApi
import com.example.android_front.model.DispatchResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StartActivity : AppCompatActivity() {

    private lateinit var tvDriverName: TextView
    private lateinit var tvVehicleNumber: TextView
    private lateinit var tvRouteNumber: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDepartureTime: TextView
    private lateinit var tvArrivalTime: TextView
    private lateinit var tvDriveStatus: TextView
    private lateinit var btnStart: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_start)

        // View 연결
        val btnBack = findViewById<View>(R.id.btnBack)
        tvDriverName = findViewById(R.id.tvDriverName)
        tvVehicleNumber = findViewById(R.id.tvVehicleNumber)
        tvRouteNumber = findViewById(R.id.tvRouteNumber)
        tvDate = findViewById(R.id.tvDate)
        tvDepartureTime = findViewById(R.id.tvDepartureTime)
        tvArrivalTime = findViewById(R.id.tvArrivalTime)
        tvDriveStatus = findViewById(R.id.tvDriveStatus)
        btnStart = findViewById(R.id.btnStart)

        // 뒤로가기
        btnBack.setOnClickListener { finish() }

        // 전달받은 dispatchId
        val dispatchId = intent.getLongExtra("dispatchId", -1)

        if (dispatchId != -1L) {
            fetchDispatchDetail(dispatchId)

            btnStart.setOnClickListener {
                val intent = Intent(this, RunActivity::class.java).apply {
                    putExtra("dispatchId", dispatchId)
                }
                startActivity(intent)
            }
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
                        val detail = response.body()
                        if (detail != null) {
                            findViewById<TextView>(R.id.tvDriverName).text = "운행자 : ${detail.username}"
                            findViewById<TextView>(R.id.tvVehicleNumber).text = "차량 번호 : ${detail.busNumber}"
                            findViewById<TextView>(R.id.tvRouteNumber).text = "노선 : ${detail.routeNumber}"
                            findViewById<TextView>(R.id.tvDate).text = "날짜 : ${detail.dispatchDate}"
                            findViewById<TextView>(R.id.tvDepartureTime).text = "출발 시간 : ${detail.scheduledDeparture}"
                            findViewById<TextView>(R.id.tvArrivalTime).text = "도착 시간 : ${detail.scheduledArrival}"
                            findViewById<TextView>(R.id.tvDriveStatus).text = detail.status.displayName
                        }
                    } else {
                        Toast.makeText(this@StartActivity, "상세 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StartActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
