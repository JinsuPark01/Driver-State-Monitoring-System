package com.example.android_front.model

data class ObdResponse(
    val speed: Float,
    val batterySOC: Float,
    val gear: String,
    val steering: Float,
    val brake: Float,
    val throttle: Float,
    val clutch: Float,
    val engineRpm: Float,
    val engineStalled: Boolean,
    val engineTorque: Float
)