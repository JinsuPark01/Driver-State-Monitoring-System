package com.example.android_front.api

import com.example.android_front.model.WarningRequest
import com.example.android_front.model.WarningResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WarningApi {
    // 경고 조회
    @GET("/api/warnings")
    suspend fun getWarnings(
        @Query("dispatchId") dispatchId: Long
    ): List<WarningResponse>

    // 경고 생성
    @POST("/api/warnings")
    suspend fun createWarning(
        @Body request: WarningRequest
    ): Response<Void>
}