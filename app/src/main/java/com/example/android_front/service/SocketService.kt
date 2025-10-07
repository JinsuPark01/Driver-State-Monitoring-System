package com.example.android_front.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.android_front.model.ObdResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class SocketService : Service() {

    private val binder = SocketBinder()
    private val port = 9999
    private val BUFFER_SIZE = 8192

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ✅ 최신 OBD 데이터만 유지
    private val _latestObdData = MutableStateFlow<ObdResponse?>(null)
    val latestObdData: StateFlow<ObdResponse?> = _latestObdData.asStateFlow()

    // ✅ 서버 상태 플래그
    private val _serverReady = MutableStateFlow(false)
    val serverReady: StateFlow<Boolean> = _serverReady

    @Volatile
    private var isRunning = AtomicBoolean(true)

    inner class SocketBinder : Binder() {
        fun getService(): SocketService = this@SocketService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startSocketServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SocketService 시작됨")
        return START_STICKY
    }

    /**
     * ✅ 서버 소켓 시작 (자동 재시작 포함)
     */
    private fun startSocketServer() {
        serverJob?.cancel()
        serverJob = scope.launch(Dispatchers.IO) {
            while (isRunning.get()) {
                try {
                    Log.d(TAG, "서버 소켓 열기 시도 중 (포트: $port)")
                    serverSocket = ServerSocket(port).apply {
                        receiveBufferSize = BUFFER_SIZE
                    }
                    _serverReady.value = true
                    Log.d(TAG, "서버 시작됨 - 포트: $port")

                    // 클라이언트 연결 수락 루프
                    while (isRunning.get()) {
                        try {
                            val client = serverSocket!!.accept()
                            client.apply {
                                tcpNoDelay = true
                                receiveBufferSize = BUFFER_SIZE
                                soTimeout = 30000
                            }
                            Log.d(TAG, "클라이언트 연결됨: ${client.inetAddress.hostAddress}")
                            launch { handleClientConnection(client) }
                        } catch (e: Exception) {
                            Log.e(TAG, "클라이언트 연결 오류 (계속 대기)", e)
                            delay(1000)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "서버 소켓 오류 발생 → 3초 후 재시작", e)
                    _serverReady.value = false
                    closeServer()
                    delay(3000)
                }
            }
        }
    }

    /**
     * ✅ 서버 닫기
     */
    private fun closeServer() {
        try {
            _serverReady.value = false
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "서버 종료됨")
        } catch (e: Exception) {
            Log.e(TAG, "서버 종료 중 오류", e)
        }
    }

    /**
     * ✅ 클라이언트 연결 처리
     */
    private suspend fun handleClientConnection(client: Socket) {
        var reader: BufferedReader? = null
        var incomingBuffer = ""  // TCP로 들어오는 데이터를 이어붙일 버퍼
        try {
            reader = BufferedReader(InputStreamReader(client.getInputStream()), BUFFER_SIZE)
            val buffer = CharArray(1024)

            while (isRunning.get() && !client.isClosed) {
                try {
                    val read = reader.read(buffer)
                    if (read <= 0) {
                        Log.d(TAG, "클라이언트 연결 종료 감지")
                        break
                    }

                    // 읽은 데이터 이어붙이기
                    incomingBuffer += String(buffer, 0, read)

                    // \n 단위로 완전한 JSON 분리
                    var newlineIndex: Int
                    while (true) {
                        newlineIndex = incomingBuffer.indexOf("\n")
                        if (newlineIndex == -1) break

                        val line = incomingBuffer.substring(0, newlineIndex).trim()
                        if (line.isNotBlank()) parseIncomingData(line)

                        incomingBuffer = incomingBuffer.substring(newlineIndex + 1)
                    }

                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "읽기 타임아웃 발생, 계속 대기 중")
                    continue
                } catch (e: Exception) {
                    Log.e(TAG, "클라이언트 데이터 처리 오류", e)
                    break
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "클라이언트 처리 중 예외 발생", e)
        } finally {
            try {
                reader?.close()
                client.close()
            } catch (e: Exception) {
                Log.e(TAG, "클라이언트 종료 중 오류", e)
            }
            Log.d(TAG, "클라이언트 연결 종료 완료")
        }
    }


    /**
     * ✅ 수신 데이터 파싱 (OBD JSON 형식)
     */
    private fun parseIncomingData(line: String) {
        try {
            if (line.startsWith("{") && line.endsWith("}")) {
                val json = JSONObject(line)
                val obdData = ObdResponse(
                    speed = json.optDouble("speed", 0.0).toFloat(),
                    batterySOC = json.optDouble("batterySOC", 100.0).toFloat(),
                    gear = json.optString("gear", ""),
                    steering = json.optDouble("steering", 0.0).toFloat(),
                    brake = json.optDouble("brake", 0.0).toFloat(),
                    throttle = json.optDouble("throttle", 0.0).toFloat(),
                    engineRpm = json.optDouble("engineRpm", 0.0).toFloat(),
                    engineStalled = json.optBoolean("engineStalled", false),
                    engineTorque = json.optDouble("engineTorque", 0.0).toFloat(),
                    clutch = json.optDouble("clutch", 0.0).toFloat(),
                )

                _latestObdData.value = obdData
                Log.d(TAG, "OBD 데이터 갱신: $obdData")

            } else {
                // 단순 속도값 처리
                line.toFloatOrNull()?.let { speed ->
                    if (speed >= 0) {
                        _latestObdData.value =
                            ObdResponse(speed, 100f, "", 0f, 0f, 0f, 0f, 0f, false, 0f)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "데이터 파싱 오류: $line", e)
        }
    }

    // SocketService.kt 내부에 추가
    fun disconnectAll() {
        try {
            // 1️⃣ 서버 동작 중단
            isRunning.set(false)
            serverJob?.cancel()
            scope.cancel()
            // 2️⃣ 로컬 TCP 서버 소켓만 닫기
            closeServer()
            // 3️⃣ 서비스 종료
            stopSelf()
            Log.d(TAG, "✅ SocketService 종료 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ SocketService 종료 중 오류", e)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        serverJob?.cancel()
        scope.cancel()
        closeServer()
        Log.d(TAG, "SocketService 완전 종료됨")
    }

    companion object {
        private const val TAG = "SocketService"
    }
}
