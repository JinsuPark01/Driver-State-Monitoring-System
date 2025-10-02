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
    val payload: UserDetailPayload
)
