package com.example.android_front.model

import com.google.gson.annotations.SerializedName

data class NotificationResponse(
    val notificationId: Long,
    val message: String,
    @SerializedName("isRead") val isRead: Boolean,
    val notificationType: String, // Enum을 문자열로 받을 경우
    val relatedUrl: String,
    val createdAt: String, // 서버가 ISO-8601 문자열로 보내면 String, 필요시 LocalDateTime으로 변환
    val payload: NotificationPayload?
)