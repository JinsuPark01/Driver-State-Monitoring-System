package com.example.android_front.api

import com.example.android_front.model.DispatchDetailResponse
import com.example.android_front.model.DispatchResponse
import com.example.android_front.model.DispatchFinishRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface DispatchApi {

    @GET("/api/dispatch/{driverId}")
    suspend fun getDispatchList(
        @Path("id") driverId: Int
    ): Response<List<DispatchResponse>>

    @GET("/api/dispatch/{dispatchId}")
    suspend fun getDispatchDetail(
        @Path("dispatchId") dispatchId: Long
    ): Response<DispatchDetailResponse>

    @PATCH("/api/dispatch/{dispatchId}")
    suspend fun updateDispatchFinish(
        @Path("dispatchId") dispatchId: Long,
        @Body record: DispatchFinishRequest
    ): Response<Unit>
}