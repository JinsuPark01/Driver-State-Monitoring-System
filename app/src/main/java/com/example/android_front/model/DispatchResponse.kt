package com.example.android_front.model

import java.time.LocalDateTime

data class DispatchResponse(
    val dispatchId: Long,
    val driverName: String,
    val routeNumber: String,
    val scheduledDepartureTime: String,
    val status: DispatchStatus
)