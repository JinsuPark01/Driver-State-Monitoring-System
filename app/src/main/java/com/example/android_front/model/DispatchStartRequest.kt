package com.example.android_front.model

import java.time.LocalTime

data class DispatchStartRequest(
    val actualDeparture: LocalTime
)