package com.example.android_front.model

data class DispatchResponse(
    val dispatchId: Long,
    val driverName: String,
    val routeNumber: String,
    val scheduledDepartureTime: String,
    val status: DispatchStatus
)