package com.example.android_front.model

data class ObdRequest(
    val dispatchId: Long,
    val speed: Float,
    val batterySOC: Float,
    val brake: Float,
    val throttle: Float,
    val clutch: Float,
    val engineRpm: Float,
    val engineStalled: Boolean,
    val engineTorque: Float
)
