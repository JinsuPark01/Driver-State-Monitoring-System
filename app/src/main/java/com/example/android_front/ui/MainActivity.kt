//메인
package com.example.android_front.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.android_front.R
import com.example.android_front.adapter.DispatchPagerAdapter
import com.example.android_front.adapter.NotificationAdapter
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.DispatchStatus
import com.example.android_front.model.NotificationResponse
import com.example.android_front.model.UserDetailResponse
import com.example.android_front.websocket.NotificationState
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Observer
import com.example.android_front.model.DispatchDetailResponse

class MainActivity : AppCompatActivity() {

    private lateinit var tvPageTitle: TextView
    private lateinit var tvViewMore: TextView
    private lateinit var btnMyPage: LinearLayout
    private lateinit var btnSetting: LinearLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var tvDriverScoreAvg: TextView
    private lateinit var tvBeforeDrive: LinearLayout
    private lateinit var tvAfterDrive: LinearLayout
    private lateinit var tvBestWarning: TextView
    private lateinit var tvBest_warningCount: TextView
    private lateinit var ivAlarm: ImageView
    private lateinit var vRedDot: View
    private lateinit var tvNoDispatchMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPageTitle = findViewById(R.id.tv_page_title)
        tvViewMore = findViewById(R.id.tvViewMore)
        btnMyPage = findViewById(R.id.btn_myPage)
        btnSetting = findViewById(R.id.btn_setting)
        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicator_layout)
        ivAlarm = findViewById(R.id.iv_alarm)
        vRedDot = findViewById(R.id.iv_newAlarm)
        tvNoDispatchMessage = findViewById(R.id.tvNoDispatch)
        tvDriverScoreAvg = findViewById(R.id.tv_driver_score_avg)
        tvBeforeDrive = findViewById(R.id.tv_before_drive)
        tvAfterDrive = findViewById(R.id.tv_after_drive)
        tvBestWarning = findViewById(R.id.tv_best_warning)
        tvBest_warningCount = findViewById(R.id.tv_best_warning_count)



        // LiveData 관찰하여 빨간 점 상태 반영
        NotificationState.hasNewNotification.observe(this, Observer { hasNew ->
            vRedDot.visibility = if (hasNew) View.VISIBLE else View.INVISIBLE

            if (hasNew) { fetchDispatchList() } // 새로운 알림이 올 때마다 배차도 최신화
        })

        // 알람 버튼 클릭
        ivAlarm.setOnClickListener {
            showNotificationPopup()
        }

        btnMyPage.setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
        }

        btnSetting.setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }

        tvViewMore.setOnClickListener {
            startActivity(Intent(this, AllScoreActivity::class.java))
        }

        fetchUserDetail()
        fetchDispatchList()
    }

    /** 유저 정보 및 평균 점수 조회 */
    private fun fetchUserDetail() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.userApi.getUserDetail()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val data: UserDetailResponse? = response.body()?.data
                        if (data != null) {

                            // 1️⃣ 운행 점수 표시
                            val drivingScore = data.payload.avgDrivingScore?.toInt() ?: 0
                            tvDriverScoreAvg.text = "${drivingScore}점"

                            // 2️⃣ 각 카운트 가져오기 (null-safe)
                            val drowsiness = data.payload.avgDrowsinessCount ?: 0.0
                            val acceleration = data.payload.avgAccelerationCount ?: 0.0
                            val braking = data.payload.avgBrakingCount ?: 0.0
                            val abnormal = data.payload.avgAbnormalCount ?: 0.0

                            // 3️⃣ 운행 시작 여부 판단
                            if (drivingScore == 100 &&
                                drowsiness == 0.0 &&
                                acceleration == 0.0 &&
                                braking == 0.0 &&
                                abnormal == 0.0) {
                                tvBeforeDrive.visibility = View.VISIBLE
                                tvAfterDrive.visibility = View.GONE
                                tvDriverScoreAvg.text = "- 점"
                            } else {
                                tvBeforeDrive.visibility = View.GONE
                                tvAfterDrive.visibility = View.VISIBLE

                                // 4️⃣ 가장 높은 카운트 확인
                                val maxCount = maxOf(drowsiness, acceleration, braking, abnormal)
                                val bestWarningText = when (maxCount) {
                                    drowsiness -> "졸음운전"
                                    acceleration -> "급가속"
                                    braking -> "급제동"
                                    abnormal -> "이상행동"
                                    else -> ""
                                }

                                // 5️⃣ UI 업데이트
                                tvBestWarning.text = bestWarningText
                                tvBest_warningCount.text = String.format("%.1f", maxCount)
                            }

                            // 6️⃣ 페이지 제목
                            tvPageTitle.text = "${data.username}님"

                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "유저 정보를 불러오지 못했습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "유저 정보 요청 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    /** 배차 리스트 조회 */
    private fun fetchDispatchList() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchList(
                    startDate = java.time.LocalDate.now().toString(),
                    endDate = java.time.LocalDate.now().toString()
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.success == true && body.data != null) {
                            tvNoDispatchMessage.visibility = View.GONE
                            setupViewPager(body.data)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                body?.message ?: "배차 정보를 불러오지 못했습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "배차 정보를 불러오지 못했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvNoDispatchMessage.visibility = View.VISIBLE
                    Toast.makeText(
                        this@MainActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /** 알림 BottomSheetDialog 띄우기 */
    private fun showNotificationPopup() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.popup_notifications, null)
        dialog.setContentView(view)

        val rvNotifications = view.findViewById<RecyclerView>(R.id.rvNotifications)
        rvNotifications.layoutManager = LinearLayoutManager(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.notificationApi.getMyNotifications()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val notifications: List<NotificationResponse> =
                            response.body()?.data ?: emptyList()
                        val tvNoNotifications = view.findViewById<LinearLayout>(R.id.tvNoNotifications)

                        if (notifications.isEmpty()) {
                            rvNotifications.visibility = View.GONE
                            tvNoNotifications.visibility = View.VISIBLE
                        } else {
                            rvNotifications.visibility = View.VISIBLE
                            tvNoNotifications.visibility = View.GONE
                            val adapter = NotificationAdapter(notifications)
                            rvNotifications.adapter = adapter
                        }

                        // X 버튼 클릭 처리
                        val ivClose = view.findViewById<ImageView>(R.id.ivClosePopup)
                        ivClose.setOnClickListener {
                            dialog.dismiss()
                        }

                        // 모든 닫힘 이벤트에 동일한 동작 적용
                        dialog.setOnDismissListener {
                            notifications.filter { !it.isRead }.forEach { notif ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        RetrofitInstance.notificationApi.markNotificationRead(notif.notificationId)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            NotificationState.hideRedDot()
                        }

                        // 다이얼로그 닫힘 설정
                        dialog.setCancelable(true)
                        dialog.setCanceledOnTouchOutside(true)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            response.body()?.message ?: "알림 조회 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        dialog.show()
    }

    /** ViewPager2 세팅 */
    private fun setupViewPager(dispatchList: List<DispatchDetailResponse>) {
        viewPager.adapter = DispatchPagerAdapter(dispatchList) { dispatch ->
            when (dispatch.status) {
                DispatchStatus.SCHEDULED -> {
                    AlertDialog.Builder(this)
                        .setTitle("운행 시작")
                        .setMessage("운행을 시작하시겠습니까?")
                        .setPositiveButton("확인") { _, _ ->
                            startDispatch(dispatch.dispatchId, dispatch.driverName, dispatch.dispatchDate)
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
                //테스트용
//                DispatchStatus.SCHEDULED -> {
//                    AlertDialog.Builder(this)
//                        .setTitle("운행 시작")
//                        .setMessage("운행을 시작하시겠습니까?")
//                        .setPositiveButton("확인") { _, _ ->
//                            val intent = Intent(this, RunActivity::class.java).apply {
//                                putExtra("dispatchId", dispatch.dispatchId)
//                                putExtra("driverName", dispatch.driverName)
//                                putExtra("dispatchDate", dispatch.dispatchDate)
//                            }
//                            startActivity(intent)
//                        }
//                        .setNegativeButton("취소", null)
//                        .show()
//                }
                DispatchStatus.COMPLETED -> {
                    val intent = Intent(this, RecordActivity::class.java)
                    intent.putExtra("dispatchId", dispatch.dispatchId)
                    startActivity(intent)
                }
                DispatchStatus.CANCELED -> {
                    Toast.makeText(this, "취소된 배차입니다.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(
                        this,
                        "현재 상태: ${dispatch.status.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        /** ← 여기부터 Peek 관련 설정 추가 */
        viewPager.clipToPadding = false
        viewPager.clipChildren = false
        (viewPager.getChildAt(0) as RecyclerView).clipToPadding = false
        (viewPager.getChildAt(0) as RecyclerView).clipChildren = false
        viewPager.offscreenPageLimit = 3
        /** ← 여기까지 */
        setupIndicators(dispatchList.size)
        setCurrentIndicator(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentIndicator(position)
            }
        })
    }

    /** 인디케이터 세팅 */
    private fun setupIndicators(count: Int) {
        indicatorLayout.removeAllViews()

        // 🔹 count가 0이면 최소 1개는 생성 (레이아웃 유지용)
        val safeCount = if (count == 0) 1 else count

        val indicators = Array(safeCount) { View(this) }
        val params = LinearLayout.LayoutParams(16, 16)
        params.setMargins(8, 0, 8, 0)

        for (i in indicators.indices) {
            indicators[i] = View(this).apply {
                setBackgroundResource(R.drawable.indicator_unselected)
                layoutParams = params
            }
            indicatorLayout.addView(indicators[i])
        }
    }

    /** 현재 인디케이터 설정 */
    private fun setCurrentIndicator(index: Int) {
        val count = indicatorLayout.childCount
        if (count == 0) return  // 인디케이터가 없을 때 예외 방지

        // 🔹 index가 범위를 벗어나지 않도록 조정
        val safeIndex = index.coerceIn(0, count - 1)

        for (i in 0 until count) {
            val view = indicatorLayout.getChildAt(i)
            view.setBackgroundResource(
                if (i == safeIndex) R.drawable.indicator_selected
                else R.drawable.indicator_unselected
            )
        }
    }

    private fun startDispatch(dispatchId: Long, driverName: String?, dispatchDate: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.updateDispatchStart(dispatchId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success && body.data != null) {
                            // ✅ 시작 성공 → RunActivity로 이동
                            val intent = Intent(this@MainActivity, RunActivity::class.java).apply {
                                putExtra("dispatchId", dispatchId)
                                putExtra("driverName", driverName)
                                putExtra("dispatchDate", dispatchDate)
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                body?.message ?: "운행 시작 실패",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "운행 시작 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        fetchUserDetail()
        fetchDispatchList()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
