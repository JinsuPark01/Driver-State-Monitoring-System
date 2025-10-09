package com.example.android_front

import android.app.Application
import android.util.Log
import com.example.android_front.websocket.NotificationState
import com.example.android_front.websocket.WebSocketManager
import ua.naiksoftware.stomp.dto.StompHeader
import java.io.FileInputStream
import java.util.Properties

class MyApplication : Application() {

    companion object {
        lateinit var context: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = this
//        val props = Properties().apply {
//            load(FileInputStream(filesDir.parent + "/local.properties"))
//        }
//        val kakaoKey = props.getProperty("KAKAO_MAP_KEY")
        com.kakao.vectormap.KakaoMapSdk.init(this, "d9b7be427350a8ff6f7b040fe4ec032f")
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
