package com.example.android_front.model

enum class WarningType (val warningName: String){
    ACCELERATION("급가속"),   // 급가속
    BRAKING("급제동"),        // 급제동
    DROWSINESS("졸음"),     // 졸음
    SMOKING("담배"),
    SEATBELT_UNFASTENED("벨트"),
    PHONE_USAGE("전화")       // 이상행동
}