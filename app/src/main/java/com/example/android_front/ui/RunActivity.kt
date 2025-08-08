package com.example.android_front.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.adapter.ScoreAdapter
import com.example.android_front.data.ScoreItem
import com.example.android_front.decoration.SpaceItemDecoration

class RunActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_run)

        val recyclerView = findViewById<RecyclerView>(R.id.rv_score_cards)

        // 예시 데이터
        val scoreList = listOf(
            ScoreItem("종합 점수", "85점"),
            ScoreItem("과속 감지", "72점"),
            ScoreItem("졸음 감지", "100점"),
            ScoreItem("운행 거리", "40KM"),
            ScoreItem("역사", "85"),
            ScoreItem("체육", "100"),
            ScoreItem("음악", "100"),
            ScoreItem("미술", "100"),
            ScoreItem("정보", "100")
        )

        // 3열로 Grid 배치
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        val spacing = resources.getDimensionPixelSize(R.dimen.score_item_spacing)
        recyclerView.addItemDecoration(SpaceItemDecoration(spacing))
        recyclerView.adapter = ScoreAdapter(scoreList)
    }
}