package com.example.android_front.model

data class UserDetailPayload(
    val licenseNumber: String,
    val careerYears: Int?,
    val grade: String?,
    val avgDrowsinessCount: Double?,
    val avgAccelerationCount: Double?,
    val avgBrakingCount: Double?,
    val avgAbnormalCount: Double?,
    val avgDrivingScore: Double?
)