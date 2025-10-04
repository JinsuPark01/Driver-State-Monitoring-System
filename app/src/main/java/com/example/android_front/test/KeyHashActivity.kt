package com.example.android_front.test

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.kakao.sdk.common.util.Utility
import com.example.android_front.R

class KeyHashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_hash)

        // Key Hash 확인
        val keyHash = Utility.getKeyHash(this)
        Log.d("KeyHashActivity", "앱 Key Hash: $keyHash")
    }
}
