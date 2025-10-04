package com.example.android_front.model

data class DispatchEventsResponse(
    val drivingEventId: Long,
    val eventType: WarningType,
    val eventTimestamp: String,
    val latitude: Double?,
    val longitude: Double?,
)