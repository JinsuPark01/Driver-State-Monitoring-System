package com.example.android_front.api

object TokenManager {
    var token: String? = null
}

//Retrofit 인터셉터는 Context를 몰라서 SharedPreferences를 바로 참조하기 어려워서, TokenManager를 중간 매개체로 두는 게 일반적인 패턴이에요.
