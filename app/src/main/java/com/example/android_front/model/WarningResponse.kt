package com.example.android_front.model

import java.time.LocalTime

data class WarningResponse(
    val warningType: WarningType,
    val warningTime: LocalTime
)
