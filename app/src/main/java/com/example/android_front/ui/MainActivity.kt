package com.example.android_front.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.android_front.R
import com.example.android_front.adapter.DispatchPagerAdapter
import com.example.android_front.adapter.ScoreAdapter
import com.example.android_front.data.DispatchItem
import com.example.android_front.data.ScoreItem

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val indicatorLayout = findViewById<LinearLayout>(R.id.indicator_layout)

        val items = listOf(
            DispatchItem("박진수", "86", "09:20", "운행 전"),
            DispatchItem("김철수", "24", "10:10", "운행 중"),
            DispatchItem("이영희", "12", "11:30", "운행 전"),
            DispatchItem("이영희", "14", "11:30", "운행 전")
        )

        viewPager.adapter = DispatchPagerAdapter(items)
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // 운행 점수 RecyclerView 초기화
        val scores = listOf(
            ScoreItem("종합 점수", "85점"),
            ScoreItem("과속 감지", "72점"),
            ScoreItem("졸음 감지", "100점"),
            ScoreItem("운행 거리", "40KM")
        )

        val scoreRecyclerView = findViewById<RecyclerView>(R.id.rvDrivingScores)
        scoreRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        scoreRecyclerView.adapter = ScoreAdapter(scores)

        // 인디케이터 생성
        setupIndicators(items.size, indicatorLayout)
        setCurrentIndicator(0, indicatorLayout)

        // 페이지 변경 시 인디케이터 업데이트
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentIndicator(position, indicatorLayout)
            }
        })

        // 운행 기록 자세히 보기 버튼
        val viewMoreText = findViewById<TextView>(R.id.tvViewMore)
        viewMoreText.setOnClickListener {
            val intent = Intent(this, AllScoreActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupIndicators(count: Int, layout: LinearLayout) {
        val indicators = Array(count) { View(this) }
        val params = LinearLayout.LayoutParams(16, 16)
        params.setMargins(8, 0, 8, 0)
        for (i in indicators.indices) {
            indicators[i] = View(this).apply {
                setBackgroundResource(R.drawable.indicator_unselected)
                layoutParams = params
            }
            layout.addView(indicators[i])
        }
    }

    private fun setCurrentIndicator(index: Int, layout: LinearLayout) {
        for (i in 0 until layout.childCount) {
            val view = layout.getChildAt(i)
            if (i == index) {
                view.setBackgroundResource(R.drawable.indicator_selected)
            } else {
                view.setBackgroundResource(R.drawable.indicator_unselected)
            }
        }
    }
}
