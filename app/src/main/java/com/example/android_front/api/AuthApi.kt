package com.example.android_front.api

import com.example.android_front.model.LoginRequest
import com.example.android_front.model.LoginResponse
import com.example.android_front.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/auth/signup")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<Unit>  // 서버에서 성공/실패 상태만 반환한다고 가정
}