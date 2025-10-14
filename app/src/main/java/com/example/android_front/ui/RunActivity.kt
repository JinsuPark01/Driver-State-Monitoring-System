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
import android.view.View
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.android_front.R
import com.example.android_front.ai.ModelHandler
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.LocationRequest
import com.example.android_front.model.ObdRequest
import com.example.android_front.model.ObdResponse
import com.example.android_front.model.WarningType
import com.example.android_front.service.SocketService
import com.example.android_front.websocket.WebSocketManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RunActivity : AppCompatActivity() {

    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvGeer: TextView
    private lateinit var tvHandle: TextView

    private lateinit var tvAcceleration: TextView
    private lateinit var tvBraking: TextView
    private lateinit var tvDrowsiness: TextView
    private lateinit var tvAbnormal: TextView

    private lateinit var tvStatusDrowsiness: TextView
    private lateinit var tvStatusCigarette: TextView
    private lateinit var tvStatusPhone: TextView
    private lateinit var tvStatusSeatbelt: TextView
    private lateinit var tvOverlay: TextView

    private var socketService: SocketService? = null
    private var isBound = false
    private var lastObdSentTime = 0L

    private val lastWarningSentTime = mutableMapOf<WarningType, Long>()
    private val speedBuffer = mutableListOf<Pair<Long, Double>>()
    private var lastCheckedTime: Long = 0

    private var accelerationCount = 0
    private var brakingCount = 0
    private var drowsinessCount = 0
    private var abnormalCount = 0

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    private var dispatchId: Long = -1
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_run)

        dispatchId = intent.getLongExtra("dispatchId", -1)
        val driverName = intent.getStringExtra("driverName")
        val dispatchDate = intent.getStringExtra("dispatchDate")

        if (dispatchId == -1L) {
            Toast.makeText(this, "잘못된 배차 정보입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvCurrentSpeed = findViewById(R.id.tv_current_speed)
        tvGeer = findViewById(R.id.tv_geer)
        tvHandle = findViewById(R.id.tv_steer)

        tvAcceleration = findViewById(R.id.tv_over_speed)
        tvBraking = findViewById(R.id.tv_under_speed)
        tvDrowsiness = findViewById(R.id.tv_sleep)
        tvAbnormal = findViewById(R.id.tv_abnormal)

        tvStatusDrowsiness = findViewById(R.id.tvStatusDrowsiness)
        tvStatusCigarette = findViewById(R.id.tvStatusCigarette)
        tvStatusPhone = findViewById(R.id.tvStatusPhone)
        tvStatusSeatbelt = findViewById(R.id.tvStatusSeatbelt)
        tvOverlay = findViewById(R.id.tvOverlay)

        previewView = findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()
        ModelHandler.init(this)

        val tvDate = findViewById<TextView>(R.id.tv_date)
        val tvDriverName = findViewById<TextView>(R.id.tv_driver_name)

        tvDate.text = dispatchDate ?: "정보 없음"
        tvDriverName.text = driverName ?: "정보 없음"

        setupEndButton()

        // 위치 클라이언트 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 테스트 버튼 이벤트 -> 추후 삭제
        tvAbnormal.setOnClickListener {
            onDrowsinessOrAbnormalBehaviorDetected(listOf("cigarette", "phone"))
            Toast.makeText(this, "테스트: 이상행동 이벤트 발생", Toast.LENGTH_SHORT).show()
        }
        tvDrowsiness.setOnClickListener {
            onDrowsinessOrAbnormalBehaviorDetected(listOf("DROWSINESS"))
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

        // 앱 시작 시 카메라 권한 체크
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            checkLocationPermissionAndStartCamera()
        }
    }
    // ServiceConnection
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SocketService.SocketBinder
            socketService = binder.getService()
            isBound = true

            // ✅ Flow 구독으로 변경 (콜백 제거)
            // ✅ 최신 Lifecycle 안전 방식
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    socketService?.latestObdData
                        ?.filterNotNull()
                        ?.collectLatest { obdData ->
                            // UI 업데이트
                            tvCurrentSpeed.text = "${obdData.speed.toInt()}"
                            tvGeer.text = "${obdData.gear}"
                            tvHandle.text = "${obdData.steering.toInt()}°"

                            Log.d("RunActivity", "OBD Data -> " +
                                    "Speed: ${obdData.speed}, " +
                                    "SOC: ${obdData.batterySOC}, " +
                                    "Gear: ${obdData.gear}, " +
                                    "Steering: ${obdData.steering}, " +
                                    "Brake: ${obdData.brake}, " +
                                    "Throttle: ${obdData.throttle}")

                            // 속도 변화 처리
                            handleSpeedUpdate(obdData.speed.toDouble())

                            // 서버 전송은 별도의 Flow로 1초마다
                            sendPeriodicSocketData(obdData)
                        }
                }
            }

        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            socketService = null
        }
    }

    private fun sendPeriodicSocketData(obdData: ObdResponse) {
        val now = System.currentTimeMillis()
        if (now - lastObdSentTime >= 1000) { // 1초 단위 전송
            lastObdSentTime = now

            // Request 생성
            val obdRequest = ObdRequest(
                dispatchId = dispatchId,
                speed = obdData.speed,
                batterySOC = obdData.batterySOC,
                brake = obdData.brake,
                throttle = obdData.throttle,
                clutch = obdData.clutch,
                engineRpm = obdData.engineRpm,
                engineStalled = obdData.engineStalled,
                engineTorque = obdData.engineTorque
            )

            getCurrentLocation { lat, lon ->
                val locationRequest = LocationRequest(
                    dispatchId = dispatchId,
                    latitude = lat,
                    longitude = lon
                )
                WebSocketManager.sendLocationData(locationRequest)

                // 전송
                WebSocketManager.sendObdData(obdRequest)
            }
        }
    }
    // 카메라 권한 요청
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkLocationPermissionAndStartCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    // 위치 권한 요청
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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
            unbindService(connection)
            isBound = false
        }
    }

    /** 카메라 시작 전 위치 권한 체크 */
    private fun checkLocationPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        ModelHandler.analyzeImage(imageProxy) { results ->
                            runOnUiThread {

                                runOnUiThread {
                                    // abnormalLabels가 비어있으면 정상, 값이 있으면 이상
                                    onDrowsinessOrAbnormalBehaviorDetected(results)
                                }
//                                어짜피 항상 함수 실행
//                                results.forEach { result ->
//                                    when (result) {
//                                        "cigarette" -> onAbnormalBehaviorDetected()
//                                        "phone" -> onAbnormalBehaviorDetected()
//                                        "noseatbelt" -> onAbnormalBehaviorDetected()
//                                        "DROWSINESS" -> onDrowsinessDetected()
//                                    }
//                                }
                            }
                        }
                    }
                }

            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }


    private fun setupEndButton() {
        val btnEnd = findViewById<TextView>(R.id.btnEnd)
        btnEnd.setOnClickListener {
            sendDrivingFinish()
        }
    }

    /** 현재 위치 가져오기 */
    private fun getCurrentLocation(onLocationReady: (Double?, Double?) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    onLocationReady(location.latitude, location.longitude)
                } else {
                    Log.w("RunActivity", "위치를 가져오지 못했습니다 (null)")
                    onLocationReady(null, null)
                }
            }
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun sendWarning(type: WarningType) {
        val now = System.currentTimeMillis()
        val lastSent = lastWarningSentTime[type] ?: 0L

        // 🔹 마지막 전송 후 10초 미만이면 무시
        if (now - lastSent < 10_000) return

        // 🔹 전송 시간 갱신
        lastWarningSentTime[type] = now

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val warningTime = LocalDateTime.now().format(formatter)

        getCurrentLocation { lat, lon ->
            WebSocketManager.sendDriveEvent(dispatchId, type.name, warningTime, lat, lon)
            runOnUiThread {
                Toast.makeText(this, "${type.name} 이벤트 전송됨", Toast.LENGTH_SHORT).show()

                // ✅ 타입별 UI 업데이트 틀
                when (type) {
                    WarningType.ACCELERATION -> {
                        accelerationCount++
                        tvAcceleration.text = "${accelerationCount}회"
                    }
                    WarningType.BRAKING -> {
                        brakingCount++
                        tvBraking.text = "${brakingCount}회"
                    }
                    WarningType.DROWSINESS -> {
                        drowsinessCount++
                        tvDrowsiness.text = "${drowsinessCount}회"
                    }
                    WarningType.ABNORMAL -> {
                        abnormalCount++
                        tvAbnormal.text = "${abnormalCount}회"
                    }
                }
            }
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
                            sendWarning(WarningType.ACCELERATION)
                        }
                        rate <= -15.0 -> {
                            sendWarning(WarningType.BRAKING)
                        }
                    }
                }
            }
        }
    }

    private fun onDrowsinessOrAbnormalBehaviorDetected(abnormalLabels: List<String>) {
        // 색상 정의
        val green = ContextCompat.getColor(this, android.R.color.holo_green_dark)
        val red = ContextCompat.getColor(this, android.R.color.holo_red_dark)

        // 졸음 상태
        if (abnormalLabels.contains("DROWSINESS")) {
            tvStatusDrowsiness.text = "비정상"
            tvStatusDrowsiness.setTextColor(red)
        } else {
            tvStatusDrowsiness.text = "정상"
            tvStatusDrowsiness.setTextColor(green)
        }

        // 담배 상태
        if (abnormalLabels.contains("cigarette")) {
            tvStatusCigarette.text = "비정상"
            tvStatusCigarette.setTextColor(red)
        } else {
            tvStatusCigarette.text = "정상"
            tvStatusCigarette.setTextColor(green)
        }

        // 휴대폰 상태
        if (abnormalLabels.contains("phone")) {
            tvStatusPhone.text = "비정상"
            tvStatusPhone.setTextColor(red)
        } else {
            tvStatusPhone.text = "정상"
            tvStatusPhone.setTextColor(green)
        }

        // 안전벨트 상태
        if (abnormalLabels.contains("noseatbelt")) {
            tvStatusSeatbelt.text = "비정상"
            tvStatusSeatbelt.setTextColor(red)
        } else {
            tvStatusSeatbelt.text = "정상"
            tvStatusSeatbelt.setTextColor(green)
        }

        // tvOverlay 업데이트
        if (abnormalLabels.isNotEmpty()) {
            if (tvOverlay.visibility != View.VISIBLE) {
                tvOverlay.visibility = View.VISIBLE
                tvOverlay.alpha = 0f
                tvOverlay.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setListener(null)
            }
        } else {
            if (tvOverlay.visibility == View.VISIBLE) {
                tvOverlay.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction {
                        tvOverlay.visibility = View.GONE
                    }
            }
        }

        // 경고 전송
        if (abnormalLabels.contains("DROWSINESS")) {
            sendWarning(WarningType.DROWSINESS)
        }
        if (abnormalLabels.any { it == "cigarette" || it == "phone" || it == "noseatbelt" }) {
            sendWarning(WarningType.ABNORMAL)
        }
    }


    private fun sendDrivingFinish() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.dispatchApi.updateDispatchFinish(dispatchId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@RunActivity, "운행 종료 처리 완료", Toast.LENGTH_SHORT).show()

                        // ✅ OBD 소켓 종료 추가
                        socketService?.disconnectAll()
                        unbindService(connection)
                        isBound = false

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
    }
}
