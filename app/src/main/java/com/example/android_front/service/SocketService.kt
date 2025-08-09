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

    private val speedChannel = Channel<Float>(Channel.BUFFERED)

    private val _serverReady = MutableStateFlow(false)
    val serverReady: StateFlow<Boolean> = _serverReady

    @Volatile
    private var isRunning = AtomicBoolean(true)

    // 약한 참조로 메모리 릭 방지
    private var speedCallbackRef: WeakReference<((Float) -> Unit)?> = WeakReference(null)

    inner class SocketBinder : Binder() {
        fun getService(): SocketService = this@SocketService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startSocketServer()
        startSpeedProcessing()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SocketService 시작됨")
        return START_STICKY
    }

    fun setSpeedCallback(callback: (Float) -> Unit) {
        speedCallbackRef = WeakReference(callback)
    }

    fun removeSpeedCallback() {
        speedCallbackRef.clear()
    }

    private fun startSpeedProcessing() {
        dataProcessingJob?.cancel()
        dataProcessingJob = scope.launch(Dispatchers.Default) {
            try {
                speedChannel.consumeAsFlow()
                    .buffer(Channel.BUFFERED)
                    .distinctUntilChanged()
                    .collect { speed ->
                        withContext(Dispatchers.Main) {
                            speedCallbackRef.get()?.invoke(speed)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "속도 데이터 처리 중 오류", e)
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
                val read = reader.read(buffer)
                if (read <= 0) {
                    Log.d(TAG, "클라이언트 연결 종료")
                    break
                }

                val data = String(buffer, 0, read).trim()
                data.split("\n").forEach { line ->
                    parseIncomingData(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "클라이언트 처리 오류", e)
        } finally {
            reader?.close()
            client.close()
            Log.d(TAG, "클라이언트 연결 종료")
        }
    }

    private fun parseIncomingData(line: String) {
        try {
            // JSON 데이터 형식 처리
            if (line.startsWith("{") && line.endsWith("}")) {
                val json = JSONObject(line)
                json.optDouble("speed", -1.0).takeIf { it >= 0 }?.let {
                    Log.d(TAG, "Received speed: $it")   // <-- 나중에 삭제
                    speedChannel.trySend(it.toFloat())
                }
                // 여기서 다른 데이터도 처리 가능 (졸음, 과속 등)
            } else {
                // 단순 숫자일 경우 속도로 처리
                line.toFloatOrNull()?.let { speed ->
                    if (speed >= 0) {
                        Log.d(TAG, "Received speed: $speed")   // <-- 나중에 삭제
                        speedChannel.trySend(speed)}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "데이터 파싱 오류: $line", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        speedChannel.close()
        dataProcessingJob?.cancel()
        serverJob?.cancel()
        scope.cancel()
        closeServer()
    }

    companion object {
        private const val TAG = "SocketService"
    }
}
