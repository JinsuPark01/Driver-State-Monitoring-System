package com.example.android_front.model

import java.time.LocalTime

data class DispatchFinishRequest(
    val actualArrival: LocalTime,
    val drowsinessCount: Int,
    val accelerationCount: Int,
    val brakingCount: Int,
    val abnormalCount: Int,
    val drivingScore: Int
)