package com.example.android_front.api

import com.example.android_front.model.ApiResponse
import com.example.android_front.model.NotificationResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface NotificationApi {

    // 1. 현재 로그인한 사용자의 알림 목록 조회 (최신순)
    @GET("/api/notifications/me")
    suspend fun getMyNotifications(
    ): Response<ApiResponse<List<NotificationResponse>>>

    // 2. 특정 알림 읽음 처리
    @PATCH("/api/notifications/{notificationId}/read")
    suspend fun markNotificationRead(
        @Path("notificationId") notificationId: Long
    ): Response<ApiResponse<Unit>>
}