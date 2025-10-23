package com.example.android_front.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.android_front.R
import com.google.android.material.button.MaterialButton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class WeatherActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val windyBaseUrl = "https://embed.windy.com/embed2.html?menu=true&message=true&type=map"
    private var currentOverlay = "temp"
    private var currentLat = 36.5
    private var currentLon = 127.7
    private var currentZoom = 7

    private lateinit var btnTemp: MaterialButton
    private lateinit var btnRain: MaterialButton
    private lateinit var btnWind: MaterialButton

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        webView = findViewById(R.id.webViewWeather)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btnTemp = findViewById(R.id.btnTemp)
        btnRain = findViewById(R.id.btnRain)
        btnWind = findViewById(R.id.btnWind)

        setupWebView()
        updateWebView() // 초기 로드
        updateOverlayButtonUI() // 초기 버튼 상태

        // 뒤로가기
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // 오버레이 버튼 클릭
        btnTemp.setOnClickListener {
            currentOverlay = "temp"
            updateWebView()
            updateOverlayButtonUI()
        }
        btnRain.setOnClickListener {
            currentOverlay = "rain"
            updateWebView()
            updateOverlayButtonUI()
        }
        btnWind.setOnClickListener {
            currentOverlay = "ptype"
            updateWebView()
            updateOverlayButtonUI()
        }

        // 위치 버튼
        findViewById<MaterialButton>(R.id.btnAll).setOnClickListener {
            currentLat = 36.5
            currentLon = 127.7
            currentZoom = 7
            updateWebView()
        }
        findViewById<MaterialButton>(R.id.btnNow).setOnClickListener {
            checkLocationPermissionAndLoad()
        }
    }
    // 선택된 오버레이 버튼 UI 업데이트
    private fun updateOverlayButtonUI() {
        val mainColor = ContextCompat.getColor(this, R.color.main)
        val whiteColor = ContextCompat.getColor(this, android.R.color.white)
        val defaultTextColor = mainColor
        val defaultBgColor = ContextCompat.getColor(this, android.R.color.transparent)

        listOf(btnTemp, btnRain, btnWind).forEach { button ->
            if ((button == btnTemp && currentOverlay == "temp") ||
                (button == btnRain && currentOverlay == "rain") ||
                (button == btnWind && currentOverlay == "ptype")
            ) {
                button.setBackgroundColor(mainColor)
                button.setTextColor(whiteColor)
            } else {
                button.setBackgroundColor(defaultBgColor)
                button.setTextColor(defaultTextColor)
            }
        }
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
    }

    private fun updateWebView() {
        val url = "$windyBaseUrl&lat=$currentLat&lon=$currentLon&zoom=$currentZoom&overlay=$currentOverlay"
        webView.loadUrl(url)
    }



    private fun checkLocationPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            loadCurrentLocation()
        }
    }

    private fun loadCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return // 권한 없으면 그냥 리턴

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLat = it.latitude
                currentLon = it.longitude
                currentZoom = 10
                updateWebView()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                loadCurrentLocation()
            }
        }
    }
}
