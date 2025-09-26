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

        setContentView(R.layout.activity_main)

        btnMyPage = findViewById(R.id.btn_myPage)
        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicator_layout)

        btnMyPage.setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
        }

        fetchDispatchList()
    }

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
                        if (body != null && body.success && body.data != null) {
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
}
