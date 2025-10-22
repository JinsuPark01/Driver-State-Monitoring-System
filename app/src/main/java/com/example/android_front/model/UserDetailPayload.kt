package com.example.android_front.model

data class UserDetailPayload(
    val licenseNumber: String,
    val careerYears: Int?,
    val grade: String?,
    val avgDrowsinessCount: Double?,
    val avgAccelerationCount: Double?,
    val avgBrakingCount: Double?,
    val avgSmokingCount: Double?,
    val avgSeatbeltUnfastenedCount: Double?,
    val avgPhoneUsageCount: Double?,
    val avgDrivingScore: Double?
)