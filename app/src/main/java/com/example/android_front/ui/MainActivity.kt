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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.android_front.R
import com.example.android_front.adapter.DispatchPagerAdapter
import com.example.android_front.adapter.NotificationAdapter
import com.example.android_front.adapter.ScoreAdapter
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.api.TokenManager
import com.example.android_front.model.DispatchResponse
import com.example.android_front.model.DispatchStatus
import com.example.android_front.model.NotificationResponse
import com.example.android_front.model.UserDetailResponse
import com.example.android_front.websocket.NotificationState
import com.example.android_front.websocket.WebSocketManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Observer

class MainActivity : AppCompatActivity() {

    private lateinit var tvPageTitle: TextView
    private lateinit var ivAlarm: ImageView
    private lateinit var tvViewMore: TextView
    private lateinit var btnMyPage: LinearLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var rvDrivingScores: RecyclerView
    private lateinit var scoreAdapter: ScoreAdapter
    private lateinit var vRedDot: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPageTitle = findViewById(R.id.tv_page_title)
        tvViewMore = findViewById(R.id.tvViewMore)
        btnMyPage = findViewById(R.id.btn_myPage)
        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicator_layout)
        rvDrivingScores = findViewById(R.id.rvDrivingScores)
        ivAlarm = findViewById(R.id.iv_alarm)
        vRedDot = findViewById(R.id.iv_newAlarm)

        // LiveData 관찰하여 빨간 점 상태 반영
        NotificationState.hasNewNotification.observe(this, Observer { hasNew ->
            vRedDot.visibility = if (hasNew) View.VISIBLE else View.INVISIBLE
        })

        // 알람 버튼 클릭
        ivAlarm.setOnClickListener {
            showNotificationPopup()
        }

        // Score RecyclerView 세팅 (수평)
        rvDrivingScores.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        btnMyPage.setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
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
                            tvPageTitle.text = "${data.username}님"
                            scoreAdapter = ScoreAdapter(data)
                            rvDrivingScores.adapter = scoreAdapter
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
                        val adapter = NotificationAdapter(notifications)
                        rvNotifications.adapter = adapter

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
    private fun setupViewPager(dispatchList: List<DispatchResponse>) {
        viewPager.adapter = DispatchPagerAdapter(dispatchList) { dispatch ->
            when (dispatch.status) {
                DispatchStatus.SCHEDULED -> {
                    val intent = Intent(this, StartActivity::class.java)
                    intent.putExtra("dispatchId", dispatch.dispatchId)
                    startActivity(intent)
                }
                DispatchStatus.COMPLETED -> {
                    val intent = Intent(this, RecordActivity::class.java)
                    intent.putExtra("dispatchId", dispatch.dispatchId)
                    startActivity(intent)
                }
                DispatchStatus.CANCELLED -> {
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
        val indicators = Array(count) { View(this) }
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

    private fun setCurrentIndicator(index: Int) {
        for (i in 0 until indicatorLayout.childCount) {
            val view = indicatorLayout.getChildAt(i)
            view.setBackgroundResource(
                if (i == index) R.drawable.indicator_selected
                else R.drawable.indicator_unselected
            )
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
