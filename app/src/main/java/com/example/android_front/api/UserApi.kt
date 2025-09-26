package com.example.android_front.api

import com.example.android_front.model.ApiResponse
import com.example.android_front.model.UserDetailResponse
import retrofit2.Response
import retrofit2.http.GET

interface UserApi {
    @GET("/api/users/me")
    suspend fun getUserDetail(
    ): Response<ApiResponse<UserDetailResponse>>
}