package com.example.android_front.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.R

class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_start)

        // 👇 btnBack 처리 추가
        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val dispatchId = intent.getLongExtra("dispatchId", -1)

        if (dispatchId != -1L) {
            // 여기서 API 호출해서 상세 데이터 불러오기
            // fetchDispatchDetail(dispatchId)
            // 운행 시작 버튼 클릭 처리
            val btnStart = findViewById<TextView>(R.id.btnStart)
            btnStart.setOnClickListener {
                val intent = Intent(this, RunActivity::class.java).apply {
                    putExtra("dispatchId", dispatchId) // 나중에 데이터 전달
                }
                startActivity(intent)
            }
        } else {
            Toast.makeText(this, "잘못된 배차 정보입니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}