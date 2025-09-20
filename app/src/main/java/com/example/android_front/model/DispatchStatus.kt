package com.example.android_front.model

enum class DispatchStatus(val displayName: String) {
    SCHEDULED("예정"),
    RUNNING("운행중"),
    COMPLETED("완료"),
    DELAYED("지연"),
    CANCELLED("취소")
}