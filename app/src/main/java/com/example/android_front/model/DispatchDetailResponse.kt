package com.example.android_front.model

data class DispatchDetailResponse(
    val dispatchId: Long,
    val driverName: String,
    val vehicleNumber: String,
    val routeNumber: String,
    val status: DispatchStatus,
    val dispatchDate: String,
    val scheduledDepartureTime: String,
    val scheduledArrivalTime: String,
    val actualDepartureTime: String?,
    val actualArrivalTime: String?,
    val drowsinessCount: Int?,
    val accelerationCount: Int?,
    val brakingCount: Int?,
    val abnormalCount: Int?,
    val drivingScore: Int?
)