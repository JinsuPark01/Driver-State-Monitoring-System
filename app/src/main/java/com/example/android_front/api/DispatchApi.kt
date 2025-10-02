package com.example.android_front.api

import com.example.android_front.model.ApiResponse
import com.example.android_front.model.DispatchDetailResponse
import com.example.android_front.model.DispatchEventsResponse
import com.example.android_front.model.DispatchRecordResponse
import com.example.android_front.model.DispatchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface DispatchApi {

    @GET("/api/driver/me/dispatches")
    suspend fun getDispatchList(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): Response<ApiResponse<List<DispatchResponse>>>

    @GET("/api/driver/me/dispatches/{dispatchId}")
    suspend fun getDispatchDetail(
        @Path("dispatchId") dispatchId: Long
    ): Response<ApiResponse<DispatchDetailResponse>>

    @GET("/api/driver/me/dispatches/{dispatchId}/driving-record")
    suspend fun getDispatchRecord(
        @Path("dispatchId") dispatchId: Long
    ): Response<ApiResponse<DispatchRecordResponse>>

    @GET("/api/driver/me/dispatches/{dispatchId}/events")
    suspend fun getDispatchEvents(
        @Path("dispatchId") dispatchId: Long
    ): Response<ApiResponse<List<DispatchEventsResponse>>>


    @PATCH("/api/driver/me/dispatches/{dispatchId}/start")
    suspend fun updateDispatchStart(
        @Path("dispatchId") dispatchId: Long
    ): Response<ApiResponse<DispatchDetailResponse>>

    @PATCH("/api/driver/me/dispatches/{dispatchId}/end")
    suspend fun updateDispatchFinish(
        @Path("dispatchId") dispatchId: Long
    ): Response<ApiResponse<DispatchDetailResponse>>
}
