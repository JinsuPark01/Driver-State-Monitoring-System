package com.example.android_front.model

import java.time.LocalTime

data class WarningRequest(
    val dispatchId: Long,
    val warningType: WarningType,
    val warningTime: LocalTime
)