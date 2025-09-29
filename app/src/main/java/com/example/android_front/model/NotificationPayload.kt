package com.example.android_front.model

data class NotificationPayload(
    val scheduledDepartureTime: String?, // LocalDateTime 대신 String으로 받음
    val vehicleNumber: String?,
    val driverName: String?,
    val dispatchId: Long?
)
