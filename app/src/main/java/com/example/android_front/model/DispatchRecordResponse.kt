package com.example.android_front.model

data class DispatchRecordResponse(
    val dispatchId: Long,
    val drowsinessCount: Int,
    val accelerationCount: Int,
    val brakingCount: Int,
    val smokingCount: Int,
    val seatbeltUnfastenedCount: Int,
    val phoneUsageCount: Int,
    val drivingScore: Int
)
