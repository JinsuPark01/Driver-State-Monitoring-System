package com.example.android_front

import android.app.Application
import android.util.Log
import com.example.android_front.api.TokenManager
import com.example.android_front.websocket.NotificationState
import com.example.android_front.websocket.WebSocketManager
import ua.naiksoftware.stomp.dto.StompHeader

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }

    fun connectWebSocket(token: String) {
        WebSocketManager.connect(
            token = token,
            onConnected = {
                Log.d("MyApp", "✅ WebSocket Connected")

                // 전역적으로 알림 구독
                WebSocketManager.subscribeTopic(
                    "/user/queue/notifications",
                    headers = listOf(StompHeader("Authorization", "Bearer $token"))
                ) { msg ->
                    Log.d("MyApp", "📩 Notification received: $msg")
                    NotificationState.showRedDot()
                }
            },
            onError = { error ->
                Log.e("MyApp", "❌ WebSocket Error: ${error.message}")
            }
        )
    }
}
