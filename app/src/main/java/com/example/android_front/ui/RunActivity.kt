package com.example.android_front.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android_front.R
import com.example.android_front.adapter.ScoreAdapter
import com.example.android_front.data.ScoreItem
import com.example.android_front.service.SocketService

class RunActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var scoreAdapter: ScoreAdapter
    private lateinit var tvCurrentSpeed: TextView  // 속도 표시용 TextView

    private val scoreList = mutableListOf(
        ScoreItem("종합 점수", "0점"),
        ScoreItem("과속 감지", "0회"),
        ScoreItem("졸음 감지", "0회"),
        ScoreItem("급가속", "0회"),
        ScoreItem("급제동", "0회"),
        ScoreItem("이상 행동", "미감지")
    )

    private var socketService: SocketService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SocketService.SocketBinder
            socketService = binder.getService()
            isBound = true

            // 속도 데이터 콜백 등록
            socketService?.setSpeedCallback { speed ->
                runOnUiThread {
                    val speedText = "${speed.toInt()} km/h"

                    // 1. RecyclerView의 scoreList 첫 번째 아이템 업데이트
                    scoreList[0] = scoreList[0].copy(value = speedText)
                    scoreAdapter.notifyItemChanged(0)

                    // 2. tv_current_speed TextView 업데이트
                    tvCurrentSpeed.text = speedText
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            socketService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_run)

        // UI 컴포넌트 초기화
        tvCurrentSpeed = findViewById(R.id.tv_current_speed)

        setupEndButton()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SocketService::class.java)

        // 1. 먼저 서비스 시작 (onCreate 보장)
        startService(intent)

        // 2. 그 다음 바인딩
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            // 속도 수신 콜백 해제
            socketService?.removeSpeedCallback()
            unbindService(connection)
            isBound = false
        }
        // 서비스는 계속 실행되도록 stopService() 호출하지 않음
    }


    private fun setupEndButton() {
        val btnEnd = findViewById<TextView>(R.id.btnEnd)
        btnEnd.setOnClickListener {
            // TODO: DB 저장 로직 추가 예정

            // 속도 수신 중단(콜백 해제) - 액티비티 종료 전에 실행해도 되지만 onStop() 에서도 처리됨
            socketService?.removeSpeedCallback()

            // 메인 화면으로 이동하며 종료
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }
}