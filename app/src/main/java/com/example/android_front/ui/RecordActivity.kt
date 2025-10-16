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
import com.example.android_front.model.DispatchEventsResponse
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordActivity : AppCompatActivity() {

    // 기본 UI
    private lateinit var tvDrivingScore: TextView
    private lateinit var tvVehicleNumber: TextView
    private lateinit var tvRouteNumber: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDepartureTime: TextView
    private lateinit var tvArrivalTime: TextView

    private lateinit var tvSleep: TextView
    private lateinit var tvOverSpeed: TextView
    private lateinit var tvUnderSpeed: TextView
    private lateinit var tvAbnormal: TextView

    private lateinit var circularGauge: CircularProgressIndicator

    // 이벤트 RecyclerView
    private lateinit var rvEvents: RecyclerView
    private lateinit var eventsAdapter: DispatchEventAdapter

    // 카카오 벡터맵 v2
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private var labelLayer: LabelLayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_record)

        // UI 연결
        val btnBack = findViewById<View>(R.id.btnBack)
        tvDrivingScore = findViewById(R.id.tv_circular_gauge_percentage)
        tvVehicleNumber = findViewById(R.id.tvVehicleNumber)
        tvRouteNumber = findViewById(R.id.tvRouteNumber)
        tvDate = findViewById(R.id.tvDate)
        tvDepartureTime = findViewById(R.id.tvDepartureTime)
        tvArrivalTime = findViewById(R.id.tvArrivalTime)

        tvSleep = findViewById(R.id.tv_sleep)
        tvOverSpeed = findViewById(R.id.tv_over_speed)
        tvUnderSpeed = findViewById(R.id.tv_under_speed)
        tvAbnormal = findViewById(R.id.tv_abnormal)

        circularGauge = findViewById(R.id.item_circular_gauge_bar)

        rvEvents = findViewById(R.id.rvEvents)
        rvEvents.layoutManager = LinearLayoutManager(this)

        mapView = findViewById(R.id.map_view)

        // 뒤로가기 버튼
        btnBack.setOnClickListener { finish() }

        // MapView 시작 (콜백 객체 미리 선언해서 전달)
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception?) {
                Toast.makeText(this@RecordActivity, "지도 로딩 오류: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                labelLayer = map.labelManager!!.layer!!
            }
        })

        // 전달받은 dispatchId
        val dispatchId = intent.getLongExtra("dispatchId", -1)
        if (dispatchId != -1L) {
            fetchDispatchDetail(dispatchId)
            fetchDispatchRecord(dispatchId)
            fetchDispatchEvents(dispatchId)
        } else {
            Toast.makeText(this, "잘못된 배차 정보입니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** 배차 상세 조회 */
    private fun fetchDispatchDetail(dispatchId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchDetail(dispatchId)
                withContext(Dispatchers.Main) {
                    val apiResponse = response.body()
                    if (response.isSuccessful && apiResponse != null && apiResponse.success && apiResponse.data != null) {
                        val detail = apiResponse.data
                        tvVehicleNumber.text = "${detail.vehicleNumber}"
                        tvRouteNumber.text = "${detail.routeNumber}"
                        tvDate.text = "${detail.dispatchDate}"
                        tvDepartureTime.text = "${detail.actualDepartureTime?.substringAfter("T")?.substringBefore(".")  ?: "미기록"}"
                        tvArrivalTime.text = "${detail.actualArrivalTime?.substringAfter("T")?.substringBefore(".")  ?: "미기록"}"
                    } else {
                        Toast.makeText(this@RecordActivity, apiResponse?.message ?: "상세 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 배차 기록 조회 */
    private fun fetchDispatchRecord(dispatchId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchRecord(dispatchId)
                withContext(Dispatchers.Main) {
                    val apiResponse = response.body()
                    if (response.isSuccessful && apiResponse != null && apiResponse.success && apiResponse.data != null) {
                        val record = apiResponse.data
                        tvDrivingScore.text = "${record.drivingScore}점"
                        circularGauge.progress = record.drivingScore
                        tvSleep.text = "${record.drowsinessCount}회"
                        tvOverSpeed.text = "${record.accelerationCount}회"
                        tvUnderSpeed.text = "${record.brakingCount}회"
                        tvAbnormal.text = "${record.abnormalCount}회"
                    } else {
                        Toast.makeText(this@RecordActivity, apiResponse?.message ?: "기록 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 이벤트 조회 및 지도 연동 */
    private fun fetchDispatchEvents(dispatchId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchEvents(dispatchId)
                withContext(Dispatchers.Main) {
                    val apiResponse = response.body()
                    val tvEvents = findViewById<TextView>(R.id.tvEvents) // 이벤트 없을 때 보여줄 TextView
                    val rvEvents = findViewById<RecyclerView>(R.id.rvEvents)

                    if (response.isSuccessful && apiResponse != null) {
                        if (apiResponse.success) {
                            val events = apiResponse.data ?: emptyList()
                            if (events.isEmpty()) {
                                // 이벤트 없음
                                rvEvents.visibility = View.GONE
                                tvEvents.visibility = View.VISIBLE
                                tvEvents.text = "경고 없음"
                            } else {
                                // 이벤트 있음
                                rvEvents.visibility = View.VISIBLE
                                tvEvents.visibility = View.GONE
                                eventsAdapter = DispatchEventAdapter(events) { event ->
                                    if (event.latitude != null && event.longitude != null) {
                                        showEventOnMap(event)
                                    } else {
                                        Toast.makeText(
                                            this@RecordActivity,
                                            "위치 정보가 없습니다.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                rvEvents.adapter = eventsAdapter
                            }
                        } else {
                            // 서버에서 실패 응답
                            rvEvents.visibility = View.GONE
                            tvEvents.visibility = View.VISIBLE
                            tvEvents.text = apiResponse.message ?: "조회 실패"
                        }
                    } else {
                        // 네트워크/응답 실패
                        rvEvents.visibility = View.GONE
                        tvEvents.visibility = View.VISIBLE
                        tvEvents.text = "조회 실패\n${response.message()}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val tvEvents = findViewById<TextView>(R.id.tvEvents)
                    val rvEvents = findViewById<RecyclerView>(R.id.rvEvents)
                    rvEvents.visibility = View.GONE
                    tvEvents.visibility = View.VISIBLE
                    tvEvents.text = "네트워크 오류\n${e.message}"
                }
            }
        }
    }



    /** 클릭한 이벤트 지도에 라벨 표시 */
    private fun showEventOnMap(event: DispatchEventsResponse) {
        val map = kakaoMap ?: return
        val layer = labelLayer ?: return

        val lat = event.latitude
        val lon = event.longitude
        if (lat == null || lon == null) {
            Toast.makeText(this, "위치 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val target = LatLng.from(lat, lon)

        // 카메라 이동
        val cameraUpdate = CameraUpdateFactory.newCenterPosition(target)
        map.moveCamera(cameraUpdate)

        // 기존 라벨 제거
        layer.removeAll()

        // drawable 아이콘을 LabelStyle로 변환
        val iconStyle = LabelStyle.from(R.drawable.ic_pin)

        // 라벨 옵션 생성 (텍스트 없이 아이콘만)
        val labelOptions = LabelOptions.from(target)
            .setStyles(iconStyle)

        // 지도에 라벨 추가
        layer.addLabel(labelOptions)
    }
    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }
}
