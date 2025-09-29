package com.example.android_front.model

data class UserDetailResponse(
    val userId: Int,
    val email: String,
    val username: String,
    val phoneNumber: String,
    val imagePath: String?,
    val role: String,
    val operatorId: Int?,
    val operatorName: String?,
    val licenseNumber: String?,
    val careerYears: Int?,
    val grade: String?,
    val avgDrowsinessCount: Double?,
    val avgAccelerationCount: Double?,
    val avgBrakingCount: Double?,
    val avgAbnormalCount: Double?,
    val avgDrivingScore: Double?
)
