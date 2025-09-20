package com.example.android_front.model

data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    val phoneNumber: String,
    val licenseNumber: String,
    val careerYears: Int,
    val operatorCode: String,
    val imagePath: String,
    val role: String = "driver"   // 무조건 driver 고정
)