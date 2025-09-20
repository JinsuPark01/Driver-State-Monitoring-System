package com.example.android_front.model

enum class WarningType (val warningName: String){
    ACCELERATION("급가속"),   // 급가속
    BRAKING("급제동"),        // 급제동
    DROWSINESS("졸음"),     // 졸음
    ABNORMAL("이상행동")        // 이상행동
}