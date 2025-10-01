package com.example.android_front.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    //private const val BASE_URL = "http://10.0.2.2:8080/"
    private const val BASE_URL = "http://10.50.2.93:8080/"

    // 토큰 자동 추가용 인터셉터
    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        TokenManager.token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        chain.proceed(requestBuilder.build())
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    val userApi: UserApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // 토큰 자동 추가
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserApi::class.java)
    }

    val dispatchApi: DispatchApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // 토큰 자동 추가
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DispatchApi::class.java)
    }

    val warningApi: WarningApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // 토큰 자동 추가
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WarningApi::class.java)
    }

    val notificationApi: NotificationApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // 토큰 자동 추가
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationApi::class.java)
    }
}
