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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SharedPreferences에서 토큰 확인 (자동 로그인)
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val driverId = prefs.getInt("driverId", 1) // 예시: 로그인 시 driverId 저장해두었다고 가정

        if (token.isNullOrEmpty()) {
            // 토큰 없으면 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicator_layout)

        // 배차일지 API 호출
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
        viewPager.adapter = DispatchPagerAdapter(dispatchList) { dispatchId ->
            // 클릭 시 StartActivity로 이동
            val intent = Intent(this, StartActivity::class.java)
            intent.putExtra("dispatchId", dispatchId)
            startActivity(intent)
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
