package com.example.android_front.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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

class MainActivity : AppCompatActivity() {

    private lateinit var btnMyPage: LinearLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)

        if (token.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        btnMyPage = findViewById(R.id.btn_myPage)
        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicator_layout)

        btnMyPage.setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
        }

        //fetchDispatchList()만 호출
        fetchDispatchList()
    }

    private fun fetchDispatchList() {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return

        // 오늘 날짜 (yyyy-MM-dd 형식)
        val today = java.time.LocalDate.now().toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchList(
                    "Bearer $token",
                    today
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val dispatchList = response.body() ?: emptyList()
                        setupViewPager(dispatchList)
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


    private fun setupViewPager(dispatchList: List<DispatchResponse>) {
        viewPager.adapter = DispatchPagerAdapter(dispatchList) { dispatch ->
            when (dispatch.status) {
                DispatchStatus.SCHEDULED -> {
                    // 예정 → StartActivity 이동
                    val intent = Intent(this, StartActivity::class.java)
                    intent.putExtra("dispatchId", dispatch.dispatchId)
                    startActivity(intent)
                }
                DispatchStatus .COMPLETED -> {
                    // 완료 → RecordActivity 이동
                    val intent = Intent(this, RecordActivity::class.java)
                    intent.putExtra("dispatchId", dispatch.dispatchId)
                    startActivity(intent)
                }
                DispatchStatus.CANCELLED -> {
                    // 취소 → 아무 액션 없음 (필요하면 Toast)
                    Toast.makeText(this, "취소된 배차입니다.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // 나머지 상태 (예: RUNNING, DELAYED)
                    Toast.makeText(this, "현재 상태: ${dispatch.status.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // 인디케이터 설정
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
