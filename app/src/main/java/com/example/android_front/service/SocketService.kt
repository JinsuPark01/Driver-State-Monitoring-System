package com.example.android_front.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class SocketService : Service() {

    private val binder = SocketBinder()
    private val port = 9999
    private val BUFFER_SIZE = 8192

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var dataProcessingJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // OBD 데이터 수신용 채널
    private val obdChannel = Channel<ObdData>(Channel.BUFFERED)

    private val _serverReady = MutableStateFlow(false)
    val serverReady: StateFlow<Boolean> = _serverReady

    @Volatile
    private var isRunning = AtomicBoolean(true)

    // 약한 참조로 메모리 릭 방지
    private var obdCallbackRef: WeakReference<((ObdData) -> Unit)?> = WeakReference(null)

    inner class SocketBinder : Binder() {
        fun getService(): SocketService = this@SocketService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startSocketServer()
        startObdProcessing()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SocketService 시작됨")
        return START_STICKY
    }

    fun setObdCallback(callback: (ObdData) -> Unit) {
        obdCallbackRef = WeakReference(callback)
    }

    fun removeObdCallback() {
        obdCallbackRef.clear()
    }

    private fun startObdProcessing() {
        dataProcessingJob?.cancel()
        dataProcessingJob = scope.launch(Dispatchers.Default) {
            try {
                obdChannel.consumeAsFlow()
                    .buffer(Channel.BUFFERED)
                    .collect { obdData ->
                        withContext(Dispatchers.Main) {
                            obdCallbackRef.get()?.invoke(obdData)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "OBD 데이터 처리 중 오류", e)
            }
        }
    }

    private fun startSocketServer() {
        serverJob?.cancel()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    receiveBufferSize = BUFFER_SIZE
                }
                _serverReady.value = true
                Log.d(TAG, "서버 시작 - 포트: $port")

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
                        Log.e(TAG, "클라이언트 연결 오류", e)
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "서버 소켓 오류", e)
                _serverReady.value = false
            } finally {
                closeServer()
            }
        }
    }

    private fun closeServer() {
        try {
            _serverReady.value = false
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "서버 종료")
        } catch (e: Exception) {
            Log.e(TAG, "서버 종료 중 오류", e)
        }
    }

    private suspend fun handleClientConnection(client: Socket) {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(InputStreamReader(client.getInputStream()), BUFFER_SIZE)
            val buffer = CharArray(128)

            while (isRunning.get() && !client.isClosed) {
                try {
                    val read = reader.read(buffer)
                    if (read <= 0) {
                        Log.d(TAG, "클라이언트 연결 종료")
                        break
                    }

                    val data = String(buffer, 0, read).trim()
                    data.split("\n").forEach { line ->
                        parseIncomingData(line)
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "읽기 타임아웃 발생, 계속 대기")
                    continue // 타임아웃 시 루프 유지
                } catch (e: Exception) {
                    Log.e(TAG, "클라이언트 처리 오류", e)
                    break // 다른 예외는 종료
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "클라이언트 처리 초기화 오류", e)
        } finally {
            try {
                reader?.close()
                client.close()
            } catch (e: Exception) {
                Log.e(TAG, "클라이언트 종료 중 오류", e)
            }
            Log.d(TAG, "클라이언트 연결 종료")
        }
    }
    private fun parseIncomingData(line: String) {
        try {
            // JSON 데이터 처리
            if (line.startsWith("{") && line.endsWith("}")) {
                val json = JSONObject(line)
                val obdData = ObdData(
                    speed = json.optDouble("speed", -1.0).toFloat(),
                    batterySOC = json.optDouble("batterySOC", -1.0).toFloat(),
                    gear = json.optString("gear", ""),
                    steering = json.optDouble("steering", 0.0).toFloat(),
                    brake = json.optDouble("brake", 0.0).toFloat(),
                    throttle = json.optDouble("throttle", 0.0).toFloat()
                )
                obdChannel.trySend(obdData)
            } else {
                // 숫자만 들어오면 speed로 처리
                line.toFloatOrNull()?.let { speed ->
                    if (speed >= 0) {
                        obdChannel.trySend(
                            ObdData(speed, -1f, "", 0f, 0f, 0f)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "데이터 파싱 오류: $line", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        obdChannel.close()
        dataProcessingJob?.cancel()
        serverJob?.cancel()
        scope.cancel()
        closeServer()
    }

    data class ObdData(
        val speed: Float,
        val batterySOC: Float,
        val gear: String,
        val steering: Float,
        val brake: Float,
        val throttle: Float
    )

    companion object {
        private const val TAG = "SocketService"
    }
}
