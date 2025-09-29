package com.example.android_front.websocket

import android.util.Log
import com.google.gson.Gson
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import okhttp3.OkHttpClient
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WebSocketManager {

    private const val TAG = "WebSocketManager"
    private lateinit var stompClient: StompClient
    private val disposables = CompositeDisposable()
    private val gson = Gson()
    private var isConnected: Boolean = false

    /** STOMP 서버 연결 */
    fun connect(
        token: String,  // JWT 토큰
        onConnected: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        // ✅ Handshake 단계에서 Authorization 헤더 추가
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            }
            .build()

        stompClient = Stomp.over(
            Stomp.ConnectionProvider.OKHTTP,
            "ws://10.0.2.2:8080/ws-native",
            null,
            okHttpClient
        )

        // ✅ STOMP CONNECT 프레임에서도 토큰 전달
        val headers = listOf(
            StompHeader("Authorization", "Bearer $token")
        )

        // 연결 상태 구독
        disposables.add(
            stompClient.lifecycle()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { event ->
                    when (event.type) {
                        LifecycleEvent.Type.OPENED -> {
                            Log.d(TAG, "STOMP Connected")
                            isConnected = true
                            onConnected?.invoke()
                        }
                        LifecycleEvent.Type.ERROR -> {
                            Log.e(TAG, "STOMP Error: ${event.exception?.message}")
                            isConnected = false
                            onError?.invoke(event.exception ?: Throwable("Unknown STOMP error"))
                        }
                        LifecycleEvent.Type.CLOSED -> {
                            Log.d(TAG, "STOMP Closed")
                            isConnected = false
                        }
                        LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> {
                            Log.e(TAG, "STOMP Failed server heartbeat")
                            isConnected = false
                        }
                    }
                }
        )

        // ✅ handshake + STOMP CONNECT 둘 다 토큰 적용
        stompClient.connect(headers)
    }

    /** 운행 이벤트 전송 (위치 포함) */
    fun sendDriveEvent(
        dispatchId: Long,
        eventType: String,
        eventTimestamp: String = "",
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (!::stompClient.isInitialized) {
            Log.e(TAG, "STOMP client not initialized. Call connect() first.")
            return
        }
        if (!isConnected) {
            Log.e(TAG, "STOMP not connected. Event is dropped: type=$eventType")
            return
        }

        // 빈 문자열이면 현재 시간 ISO_LOCAL_DATE_TIME 형식으로
        val timestamp = if (eventTimestamp.isEmpty()) {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        } else eventTimestamp

        // 기본 payload
        val payload = mutableMapOf<String, Any>(
            "dispatchId" to dispatchId,
            "eventType" to eventType,
            "eventTimestamp" to timestamp
        )

        // 위치 있으면 추가
        latitude?.let { payload["latitude"] = it }
        longitude?.let { payload["longitude"] = it }

        val json = gson.toJson(payload)
        disposables.add(
            stompClient.send("/app/drive-events", json)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.d(TAG, "Event sent: $json")
                }, { error ->
                    Log.e(TAG, "Send error: ${error.message}")
                })
        )
    }

    /** 서버 메시지 구독 */
    fun subscribeTopic(topic: String, onMessageReceived: (String) -> Unit) {
        if (!::stompClient.isInitialized) {
            Log.e(TAG, "STOMP client not initialized. Call connect() first.")
            return
        }
        disposables.add(
            stompClient.topic(topic)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ stompMessage ->
                    onMessageReceived(stompMessage.payload)
                }, { error ->
                    Log.e(TAG, "Topic subscribe error: ${error.message}")
                })
        )
    }

    /** 연결 종료 */
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
        Log.d(TAG, "STOMP Disconnected")
    }
}
