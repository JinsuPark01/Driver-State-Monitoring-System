package com.example.android_front.api

import com.example.android_front.model.DispatchDetailResponse
import com.example.android_front.model.DispatchResponse
import com.example.android_front.model.DispatchFinishRequest
import com.example.android_front.model.DispatchStartRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface DispatchApi {

    @GET("/api/dispatches/me")
    suspend fun getDispatchList(
        @Header("Authorization") token: String,
        @Query("dispatchDate") dispatchDate: String
    ): Response<List<DispatchResponse>>

    @GET("/api/dispatch/{dispatchId}")
    suspend fun getDispatchDetail(
        @Path("dispatchId") dispatchId: Long
    ): Response<DispatchDetailResponse>

    @PATCH("/api/dispatch/{dispatchId}/start")
    suspend fun updateDispatchStart(
        @Path("dispatchId") dispatchId: Long,
        @Body record: DispatchStartRequest
    ): Response<Unit>
    @PATCH("/api/dispatch/{dispatchId}/finish")
    suspend fun updateDispatchFinish(
        @Path("dispatchId") dispatchId: Long,
        @Body record: DispatchFinishRequest
    ): Response<Unit>
}