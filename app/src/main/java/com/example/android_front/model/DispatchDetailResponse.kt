package com.example.android_front.model

import java.time.LocalTime

data class DispatchDetailResponse(
    val dispatchId: Long,
    val username: String,
    val busNumber: String,
    val routeNumber: String,
    val status: DispatchStatus,
    val dispatchDate: String,
    val scheduledDeparture: LocalTime,
    val scheduledArrival: LocalTime,
    val actualDeparture: LocalTime,
    val actualArrival: LocalTime,
    val drowsinessCount: Int,
    val accelerationCount: Int,
    val brakingCount: Int,
    val abnormalCount: Int,
    val drivingScore: Int
)