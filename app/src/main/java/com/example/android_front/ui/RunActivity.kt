package com.example.android_front.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.R
import com.example.android_front.service.SocketService

class RunActivity : AppCompatActivity() {

    private lateinit var tvCurrentSpeed: TextView  // 속도 표시
    private lateinit var tvAcceleration: TextView  // 급가속 횟수 표시
    private lateinit var tvBraking: TextView       // 급제동 횟수 표시

    private var socketService: SocketService? = null
    private var isBound = false

    // 속도 기록용 버퍼
    private val speedBuffer = mutableListOf<Pair<Long, Double>>() // (timestamp, speed)
    private var lastCheckedTime: Long = 0

    // 카운터
    private var accelerationCount = 0
    private var brakingCount = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SocketService.SocketBinder
            socketService = binder.getService()
            isBound = true

            // 속도 데이터 콜백 등록
            socketService?.setSpeedCallback { speed ->
                runOnUiThread {
                    val speedText = "${speed.toInt()} km/h"
                    tvCurrentSpeed.text = speedText
                }

                // 급가속/급제동 판별
                handleSpeedUpdate(speed.toDouble())
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

        // UI 초기화
        tvCurrentSpeed = findViewById(R.id.tv_current_speed)
        tvAcceleration = findViewById(R.id.tv_over_speed)
        tvBraking = findViewById(R.id.tv_under_speed)

        setupEndButton()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SocketService::class.java)

        // 서비스 시작 및 바인딩
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            socketService?.removeSpeedCallback()
            unbindService(connection)
            isBound = false
        }
    }

    private fun setupEndButton() {
        val btnEnd = findViewById<TextView>(R.id.btnEnd)
        btnEnd.setOnClickListener {
            // TODO: DB 저장 로직 추가 예정
            socketService?.removeSpeedCallback()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    /**
     * 속도 업데이트 처리 (급가속/급제동 판별)
     */
    private fun handleSpeedUpdate(currentSpeed: Double) {
        val now = System.currentTimeMillis()

        // 현재 값 기록
        speedBuffer.add(now to currentSpeed)

        // 1초가 지난 데이터는 제거
        speedBuffer.removeAll { (time, _) -> now - time > 1000 }

        // 최소 1초 단위로만 체크
        if (now - lastCheckedTime >= 1000) {
            lastCheckedTime = now

            if (speedBuffer.size >= 2) {
                val oldest = speedBuffer.first().second
                val newest = speedBuffer.last().second

                val deltaV = newest - oldest
                val deltaT = (speedBuffer.last().first - speedBuffer.first().first) / 1000.0

                if (deltaT > 0) {
                    val rate = deltaV / deltaT // km/h per second

                    when {
                        rate >= 10.0 -> { // 급가속
                            accelerationCount++
                            runOnUiThread {
                                tvAcceleration.text = "${accelerationCount}회"
                            }
                        }
                        rate <= -10.0 -> { // 급제동
                            brakingCount++
                            runOnUiThread {
                                tvBraking.text = "${brakingCount}회"
                            }
                        }
                    }
                }
            }
        }
    }
}
