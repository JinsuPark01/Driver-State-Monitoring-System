package com.example.android_front.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
//import com.bumptech.glide.Glide
import com.example.android_front.R
import com.example.android_front.adapter.DispatchPagerAdapter
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.DispatchResponse
import com.example.android_front.model.DispatchStatus
import com.example.android_front.model.UserDetailResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MyPageActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnPrevDate: LinearLayout
    private lateinit var btnSelectDate: ImageView
    private lateinit var btnNextDate: LinearLayout
    private lateinit var tvDriverName: TextView
    private lateinit var tvDate: TextView
    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout

    private var currentDate: LocalDate = LocalDate.now().minusDays(1) // 기본: 어제
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my)

        // View 연결
        btnBack = findViewById(R.id.btnBack)
        btnPrevDate = findViewById(R.id.btn_prevDate)
        btnSelectDate = findViewById(R.id.btn_selectDate)
        btnNextDate = findViewById(R.id.btn_nextDate)
        tvDriverName = findViewById(R.id.tv_driver_name)
        tvDate = findViewById(R.id.tv_date)
        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicator_layout)

        // 뒤로가기
        btnBack.setOnClickListener { finish() }

        // 날짜 버튼
        btnPrevDate.setOnClickListener {
            currentDate = currentDate.minusDays(1)
            fetchDispatchList(currentDate)
        }

        btnNextDate.setOnClickListener {
            if (currentDate.isBefore(LocalDate.now())) {
                currentDate = currentDate.plusDays(1)
                fetchDispatchList(currentDate)
            } else {
                Toast.makeText(this, "내일 이후 날짜는 조회할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 날짜 선택
        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        // 초기 데이터 로딩
        fetchUserDetail()
        fetchDispatchList(currentDate)
    }

    // 사용자 정보 호출
    private fun fetchUserDetail() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.userApi.getUserDetail()
                if (response.isSuccessful) {
                    val userDetail = response.body()?.data
                    withContext(Dispatchers.Main) {
                        userDetail?.let { updateUserUI(it) }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MyPageActivity, "사용자 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MyPageActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // UI 업데이트
    private fun updateUserUI(user: UserDetailResponse) {
        tvDriverName.text = "${user.username} 드라이버"
        findViewById<TextView>(R.id.tv_licenseNumber).text = ":  ${user.licenseNumber ?: "-"}"
        findViewById<TextView>(R.id.tv_grade).text = ":  ${user.grade ?: "-"}"
        findViewById<TextView>(R.id.tv_careerYear).text = ":  ${user.careerYears ?: 0}년"
        findViewById<TextView>(R.id.tv_operator).text = ":  ${user.operatorName ?: "-"}"
        findViewById<TextView>(R.id.tv_phoneNumber).text = ":  ${user.phoneNumber ?: "-"}"

        // 이미지 로딩 (Glide)
//        user.imagePath?.let { url ->
//            val ivProfile = findViewById<ImageView>(R.id.iv_profile)
//            Glide.with(this).load(url).into(ivProfile)
//        }
    }

    private fun showDatePicker() {
        val today = LocalDate.now()
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                if (selected.isAfter(today)) {
                    Toast.makeText(this, "내일 이후 날짜는 선택할 수 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    currentDate = selected
                    fetchDispatchList(currentDate)
                }
            },
            currentDate.year,
            currentDate.monthValue - 1,
            currentDate.dayOfMonth
        )
        datePicker.show()
    }

    private fun fetchDispatchList(date: LocalDate) {
        tvDate.text = date.format(dateFormatter)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.getDispatchList(
                    startDate = date.format(dateFormatter),
                    endDate = date.format(dateFormatter)
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiBody = response.body()
                        val dispatchList: List<DispatchResponse> = apiBody?.data ?: emptyList()
                        setupViewPager(dispatchList)
                    } else {
                        Toast.makeText(
                            this@MyPageActivity,
                            "배차 정보를 불러오지 못했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MyPageActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupViewPager(dispatchList: List<DispatchResponse>) {
        viewPager.adapter = DispatchPagerAdapter(dispatchList) { dispatch ->
            when (dispatch.status) {
                DispatchStatus.SCHEDULED -> {
                    startActivity(
                        Intent(this, StartActivity::class.java).apply {
                            putExtra("dispatchId", dispatch.dispatchId)
                        }
                    )
                }
                DispatchStatus.COMPLETED -> {
                    startActivity(
                        Intent(this, RecordActivity::class.java).apply {
                            putExtra("dispatchId", dispatch.dispatchId)
                        }
                    )
                }
                DispatchStatus.CANCELLED -> {
                    Toast.makeText(this, "취소된 배차입니다.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "현재 상태: ${dispatch.status.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        setupIndicators(dispatchList.size)
        setCurrentIndicator(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentIndicator(position)
            }
        })
    }

    private fun setupIndicators(count: Int) {
        indicatorLayout.removeAllViews()
        val indicators = Array(count) { View(this) }
        val params = LinearLayout.LayoutParams(16, 16)
        params.setMargins(8, 0, 8, 0)
        for (i in indicators.indices) {
            indicators[i] = View(this).apply {
                setBackgroundResource(R.drawable.indicator_unselected)
                layoutParams = params
            }
            indicatorLayout.addView(indicators[i])
        }
    }

    private fun setCurrentIndicator(index: Int) {
        for (i in 0 until indicatorLayout.childCount) {
            val view = indicatorLayout.getChildAt(i)
            if (i == index) {
                view.setBackgroundResource(R.drawable.indicator_selected)
            } else {
                view.setBackgroundResource(R.drawable.indicator_unselected)
            }
        }
    }
}
