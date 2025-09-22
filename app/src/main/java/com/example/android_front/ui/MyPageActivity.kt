package com.example.android_front.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.android_front.R
import com.example.android_front.adapter.DispatchPagerAdapter
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.DispatchResponse
import com.example.android_front.model.DispatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyPageActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnPrevDate: LinearLayout
    private lateinit var btnSelectDate: LinearLayout
    private lateinit var btnNextDate: LinearLayout
    private lateinit var tvDriverName: TextView
    private lateinit var tvDate: TextView
    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 로그인 토큰 체크 (자동 로그인 유지)
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val driverId = prefs.getInt("driverId", 1)

        if (token.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_my)

        // View 연결
        btnBack = findViewById(R.id.btnBack)
        btnPrevDate = findViewById(R.id.btn_prevDate)
        btnSelectDate = findViewById(R.id.btn_selectDate)
        btnNextDate = findViewById(R.id.btn_nextDate)
        tvDriverName = findViewById(R.id.tv_driver_name)
        tvDate = findViewById(R.id.tv_date)
        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicator_layout)

        // 뒤로가기 버튼
        btnBack.setOnClickListener { finish() }

        // 날짜 클릭 (TODO: DatePicker 연결 가능)
        btnSelectDate.setOnClickListener {
            Toast.makeText(this, "날짜 선택하기", Toast.LENGTH_SHORT).show()
        }

        // API로 배차 데이터 불러오기
        fetchDispatchList(driverId)
    }

    private fun fetchDispatchList(driverId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchList(driverId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val dispatchList = response.body() ?: emptyList()
                        setupViewPager(dispatchList)
                    } else {
                        Toast.makeText(
                            this@MyPageActivity,
                            "배차 정보를 불러오지 못했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MyPageActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

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
                    Toast.makeText(this, "현재 상태: ${dispatch.status.displayName}", Toast.LENGTH_SHORT).show()
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
            if (i == index) {
                view.setBackgroundResource(R.drawable.indicator_selected)
            } else {
                view.setBackgroundResource(R.drawable.indicator_unselected)
            }
        }
    }
}
