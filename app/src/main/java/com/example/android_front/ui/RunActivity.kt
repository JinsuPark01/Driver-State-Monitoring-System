package com.example.android_front.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.android_front.R
import com.example.android_front.ai.ModelHandler
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.api.TokenManager
import com.example.android_front.model.WarningType
import com.example.android_front.service.SocketService
import com.example.android_front.websocket.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RunActivity : AppCompatActivity() {

    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvAcceleration: TextView
    private lateinit var tvBraking: TextView
    private lateinit var tvDrowsiness: TextView
    private lateinit var tvAbnormal: TextView

    private var socketService: SocketService? = null
    private var isBound = false

    private val speedBuffer = mutableListOf<Pair<Long, Double>>()
    private var lastCheckedTime: Long = 0

    private var accelerationCount = 0
    private var brakingCount = 0
    private var drowsinessCount = 0
    private var abnormalCount = 0

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var modelHandler: ModelHandler

    private var dispatchId: Long = -1

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SocketService.SocketBinder
            socketService = binder.getService()
            isBound = true

            socketService?.setSpeedCallback { speed ->
                runOnUiThread { tvCurrentSpeed.text = "${speed.toInt()} km/h" }
                handleSpeedUpdate(speed.toDouble())
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            socketService = null
        }
    }

    // 런타임 권한 요청 결과
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_run)

        dispatchId = intent.getLongExtra("dispatchId", -1)
        if (dispatchId == -1L) {
            Toast.makeText(this, "잘못된 배차 정보입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvCurrentSpeed = findViewById(R.id.tv_current_speed)
        tvAcceleration = findViewById(R.id.tv_over_speed)
        tvBraking = findViewById(R.id.tv_under_speed)
        tvDrowsiness = findViewById(R.id.tv_sleep)
        tvAbnormal = findViewById(R.id.tv_abnormal)

        previewView = findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()
        modelHandler = ModelHandler(this) // 모델 초기화

        //checkCameraPermission()
        setupEndButton()

        // 🚀 WebSocket 연결
        WebSocketManager.connect(
            token = TokenManager.token ?: "",
            onConnected = { Log.d("WebSocket", "Connected with JWT") },
            onError = { error -> Log.e("WebSocket", "STOMP connection failed: ${error.message}") }
        )

        //임시 이벤트 발생 버튼
        tvAbnormal.setOnClickListener {
            onAbnormalBehaviorDetected()
            Toast.makeText(this, "테스트: 이상행동 이벤트 발생", Toast.LENGTH_SHORT).show()
        }
        tvDrowsiness.setOnClickListener {
            onDrowsinessDetected()
            Toast.makeText(this, "테스트: 졸음감지 이벤트 발생", Toast.LENGTH_SHORT).show()
        }
        tvAcceleration.setOnClickListener {
            accelerationCount++
            tvAcceleration.text = "${accelerationCount}회"
            sendWarning(WarningType.ACCELERATION)
            Toast.makeText(this, "테스트: 급가속 이벤트 발생", Toast.LENGTH_SHORT).show()
        }
        tvBraking.setOnClickListener {
            brakingCount++
            tvBraking.text = "${brakingCount}회"
            sendWarning(WarningType.BRAKING)
            Toast.makeText(this, "테스트: 급제동 이벤트 발생", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SocketService::class.java)
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

//    private fun checkCameraPermission() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//            == PackageManager.PERMISSION_GRANTED
//        ) {
//            startCamera()
//        } else {
//            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
//        }
//    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        modelHandler.analyzeImage(imageProxy) { result ->
                            runOnUiThread {
                                when (result) {
                                    "DROWSINESS" -> onDrowsinessDetected()
                                    "ABNORMAL" -> onAbnormalBehaviorDetected()
                                }
                            }
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupEndButton() {
        val btnEnd = findViewById<TextView>(R.id.btnEnd)
        btnEnd.setOnClickListener {
            socketService?.removeSpeedCallback()
            sendDrivingFinish()
        }
    }

    /** 경고 이벤트 WebSocket 전송 */
    private fun sendWarning(type: WarningType) {
        val warningTime = LocalTime.now().toString()
        WebSocketManager.sendDriveEvent(dispatchId, type.name, warningTime)
        runOnUiThread {
            Toast.makeText(this, "${type.name} 이벤트 전송됨", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSpeedUpdate(currentSpeed: Double) {
        val now = System.currentTimeMillis()
        speedBuffer.add(now to currentSpeed)
        speedBuffer.removeAll { (time, _) -> now - time > 1000 }

        if (now - lastCheckedTime >= 1000) {
            lastCheckedTime = now
            if (speedBuffer.size >= 2) {
                val oldest = speedBuffer.first().second
                val newest = speedBuffer.last().second
                val deltaV = newest - oldest
                val deltaT = (speedBuffer.last().first - speedBuffer.first().first) / 1000.0

                if (deltaT > 0) {
                    val rate = deltaV / deltaT
                    when {
                        rate >= 15.0 -> {
                            accelerationCount++
                            tvAcceleration.text = "${accelerationCount}회"
                            sendWarning(WarningType.ACCELERATION)
                        }
                        rate <= -15.0 -> {
                            brakingCount++
                            tvBraking.text = "${brakingCount}회"
                            sendWarning(WarningType.BRAKING)
                        }
                    }
                }
            }
        }
    }

    private fun onDrowsinessDetected() {
        drowsinessCount++
        tvDrowsiness.text = "${drowsinessCount}회"
        sendWarning(WarningType.DROWSINESS)
    }

    private fun onAbnormalBehaviorDetected() {
        abnormalCount++
        tvAbnormal.text = "${abnormalCount}회"
        sendWarning(WarningType.ABNORMAL)
    }

    /** 운행 종료는 HTTP 그대로 */
    private fun sendDrivingFinish() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.dispatchApi.updateDispatchFinish(dispatchId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@RunActivity, "운행 종료 처리 완료", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@RunActivity, "운행 종료 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RunActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        WebSocketManager.disconnect()
    }
}
