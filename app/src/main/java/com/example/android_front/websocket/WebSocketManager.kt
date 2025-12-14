package com.example.android_front.websocket

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.android_front.api.TokenManager
import com.example.android_front.model.LocationRequest
import com.example.android_front.model.ObdRequest
import com.example.android_front.model.ObdResponse
import com.google.gson.Gson
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import okhttp3.OkHttpClient
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompCommand
import ua.naiksoftware.stomp.dto.StompHeader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import ua.naiksoftware.stomp.dto.StompMessage


object WebSocketManager {

    private const val TAG = "WebSocketManager"
    private lateinit var stompClient: StompClient
    private val disposables = CompositeDisposable()
    private val gson = Gson()
    private var isConnected: Boolean = false

    // 재연결 관련
    private var reconnectAttempts = 0
    private const val MAX_RECONNECT = 5
    private const val RECONNECT_DELAY_MS = 3000L
    private val handler = Handler(Looper.getMainLooper())

    /**
     * STOMP 서버 연결
     */
    fun connect(
        token: String? = TokenManager.token,
        onConnected: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "connect() called but token is null. Skipping connection.")
            return
        }

        // ✅ OkHttpClient with Authorization header
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            }
            .build()

        // ✅ Create STOMP client
        stompClient = Stomp.over(
            Stomp.ConnectionProvider.OKHTTP,
            "ws://172.30.1.74:8080/ws-native",
            null,
            okHttpClient
        )

//        stompClient = Stomp.over(
//            Stomp.ConnectionProvider.OKHTTP,
//            "ws://ec2-43-201-195-11.ap-northeast-2.compute.amazonaws.com/ws-native",
//            null,
//            okHttpClient
//        )

        // ✅ STOMP CONNECT frame header
        val headers = listOf(StompHeader("Authorization", "Bearer $token"))

        // lifecycle 구독
        disposables.add(
            stompClient.lifecycle()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { event ->
                    when (event.type) {
                        LifecycleEvent.Type.OPENED -> {
                            Log.d(TAG, "STOMP Connected")
                            isConnected = true
                            reconnectAttempts = 0
                            onConnected?.invoke()
                        }
                        LifecycleEvent.Type.ERROR -> {
                            Log.e(TAG, "STOMP Error: ${event.exception?.message}")
                            isConnected = false
                            onError?.invoke(event.exception ?: Throwable("Unknown STOMP error"))
                            attemptReconnect()
                        }
                        LifecycleEvent.Type.CLOSED -> {
                            Log.d(TAG, "STOMP Closed")
                            isConnected = false
                            attemptReconnect()
                        }
                        LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> {
                            Log.e(TAG, "STOMP Failed server heartbeat")
                            isConnected = false
                            attemptReconnect()
                        }
                    }
                }
        )

        stompClient.connect(headers)
    }

    /**
     * 자동 재연결 시도
     */
    private fun attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT) {
            Log.w(TAG, "Max reconnect attempts reached. Stop trying.")
            return
        }
        reconnectAttempts++
        Log.d(TAG, "Reconnecting... attempt $reconnectAttempts")
        handler.postDelayed({
            connect(TokenManager.token)
        }, RECONNECT_DELAY_MS)
    }

    /**
     * 운행 이벤트 전송
     */
    fun sendDriveEvent(
        dispatchId: Long,
        eventType: String,
        eventTimestamp: String = "",
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (!::stompClient.isInitialized || !isConnected) {
            Log.e(TAG, "STOMP not connected. Event is dropped: type=$eventType")
            return
        }

        val timestamp = eventTimestamp.ifEmpty {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        }

        val payload = mutableMapOf<String, Any>(
            "dispatchId" to dispatchId,
            "eventType" to eventType,
            "eventTimestamp" to timestamp
        )
        latitude?.let { payload["latitude"] = it }
        longitude?.let { payload["longitude"] = it }

        val json = gson.toJson(payload)

        // ✅ SEND 프레임용 StompMessage 생성
        val headers = listOf(
            StompHeader("destination", "/app/drive-events"),
            StompHeader("Authorization", "Bearer ${TokenManager.token}")
        )

        val message = StompMessage(StompCommand.SEND, headers, json)

        disposables.add(
            stompClient.send(message)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.d(TAG, "Event sent with headers: $json")
                }, { error ->
                    Log.e(TAG, "Send error: ${error.message}")
                })
        )
    }

    fun sendObdData(obdData: ObdRequest) {
        if (!::stompClient.isInitialized || !isConnected) {
            Log.e(TAG, "STOMP not connected. OBD data dropped")
            return
        }

        // payload 구성
        val payload = mapOf(
            "dispatchId" to obdData.dispatchId,
            "stalled" to obdData.engineStalled,
            "soc" to obdData.batterySOC,
            "engineRpm" to obdData.engineRpm,
            "torque" to obdData.engineTorque,
            "brake" to obdData.brake,
            "throttle" to obdData.throttle,
            "clutch" to obdData.clutch
        )

        val json = gson.toJson(payload)

        val headers = listOf(
            StompHeader("destination", "/app/obd/update"),
            StompHeader("Authorization", "Bearer ${TokenManager.token}")
        )

        val message = StompMessage(StompCommand.SEND, headers, json)

        disposables.add(
            stompClient.send(message)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.d(TAG, "OBD data sent: $json")
                }, { error ->
                    Log.e(TAG, "OBD send error: ${error.message}")
                })
        )
    }

    fun sendLocationData(locationData: LocationRequest) {
        if (!::stompClient.isInitialized || !isConnected) {
            Log.e(TAG, "STOMP not connected. Location data dropped")
            return
        }

        // payload 구성
        val payload = mapOf(
            "dispatchId" to locationData.dispatchId,
            "latitude" to locationData.latitude,
            "longitude" to locationData.longitude
        )

        val json = gson.toJson(payload)

        val headers = listOf(
            StompHeader("destination", "/app/location/update"),
            StompHeader("Authorization", "Bearer ${TokenManager.token}")
        )

        val message = StompMessage(StompCommand.SEND, headers, json)

        disposables.add(
            stompClient.send(message)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.d(TAG, "Location data sent: $json")
                }, { error ->
                    Log.e(TAG, "Location send error: ${error.message}")
                })
        )
    }

    /**
     * 서버 메시지 구독
     */
    fun subscribeTopic(
        topic: String,
        headers: List<StompHeader> = emptyList(),
        onMessageReceived: (String) -> Unit
    ) {
        if (!::stompClient.isInitialized || !isConnected) {
            Log.e(TAG, "STOMP not connected. Subscription failed: $topic")
            return
        }

        disposables.add(
            stompClient.topic(topic, headers)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ stompMessage ->
                    onMessageReceived(stompMessage.payload)
                }, { error ->
                    Log.e(TAG, "Topic subscribe error: ${error.message}")
                })
        )
    }

    /**
     * 연결 종료
     */
    fun disconnect() {
        disposables.clear()
        if (::stompClient.isInitialized) {
            try {
                stompClient.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnect: ${e.message}")
            }
        }
        isConnected = false
        reconnectAttempts = 0
        Log.d(TAG, "STOMP Disconnected")
    }
}
