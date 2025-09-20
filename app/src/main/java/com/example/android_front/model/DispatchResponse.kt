package com.example.android_front.model

import java.time.LocalTime

data class DispatchResponse(
    val dispatchId: Long,
    val username: String,
    val routeNumber: String,
    val scheduledDeparture: LocalTime,
    val status: DispatchStatus
)