package com.example.android_front.ui

import android.content.Intent
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

class StartActivity : AppCompatActivity() {

    private lateinit var tvDriverName: TextView
    private lateinit var tvVehicleNumber: TextView
    private lateinit var tvRouteNumber: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDepartureTime: TextView
    private lateinit var tvArrivalTime: TextView
    private lateinit var tvDriveStatus: TextView
    private lateinit var btnStart: TextView

    private var dispatchDetail: DispatchDetailResponse? = null
    private var dispatchId: Long = -1L

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

        btnBack.setOnClickListener { finish() }

        // 전달받은 dispatchId
        dispatchId = intent.getLongExtra("dispatchId", -1)
        if (dispatchId != -1L) {
            fetchDispatchDetail(dispatchId)

            btnStart.setOnClickListener {
                startDispatch()
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
                        val body = response.body()
                        if (body != null && body.success && body.data != null) {
                            dispatchDetail = body.data
                            updateUI(dispatchDetail!!)
                        } else {
                            Toast.makeText(
                                this@StartActivity,
                                body?.message ?: "상세 조회 실패",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(this@StartActivity, "상세 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@StartActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateUI(detail: DispatchDetailResponse) {
        tvDriverName.text = ":  ${detail.driverName}"
        tvVehicleNumber.text = ":  ${detail.vehicleNumber}"
        tvRouteNumber.text = ":  ${detail.routeNumber}"
        tvDate.text = ":  ${detail.dispatchDate}"
        tvDepartureTime.text = ":  ${detail.scheduledDepartureTime.substringAfter("T")}"
        tvArrivalTime.text = ":  ${detail.scheduledArrivalTime.substringAfter("T")}"
        tvDriveStatus.text = ":  ${detail.status.displayName}"
    }

    private fun startDispatch() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.updateDispatchStart(dispatchId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success && body.data != null) {
                            // 시작 성공 → RunActivity로 이동
                            val intent = Intent(this@StartActivity, RunActivity::class.java)
                            intent.putExtra("dispatchId", dispatchId)
                            intent.putExtra("driverName", dispatchDetail?.driverName)
                            intent.putExtra("dispatchDate", dispatchDetail?.dispatchDate)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(
                                this@StartActivity,
                                body?.message ?: "운행 시작 실패",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(this@StartActivity, "운행 시작 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@StartActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

}
