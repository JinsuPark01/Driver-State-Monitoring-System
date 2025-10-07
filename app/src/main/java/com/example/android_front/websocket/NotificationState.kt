package com.example.android_front.websocket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object NotificationState {
    // 알림이 도착하면 true, 닫으면 false
    private val _hasNewNotification = MutableLiveData(false)
    val hasNewNotification: LiveData<Boolean> get() = _hasNewNotification

    fun showRedDot() {
        _hasNewNotification.postValue(true)
    }

    fun hideRedDot() {
        _hasNewNotification.postValue(false)
    }
}
