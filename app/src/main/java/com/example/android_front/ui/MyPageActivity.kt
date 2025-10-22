package com.example.android_front.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.android_front.R
import com.example.android_front.adapter.DispatchPagerAdapter
import com.example.android_front.adapter.NotificationAdapter
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.DispatchDetailResponse
import com.example.android_front.model.DispatchStatus
import com.example.android_front.model.NotificationResponse
import com.example.android_front.model.UserDetailResponse
import com.example.android_front.websocket.NotificationState
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private lateinit var ivAlarm: ImageView
    private lateinit var vRedDot: View
    private lateinit var tvNoDispatchMessage: TextView

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
        ivAlarm = findViewById(R.id.iv_alarm)
        vRedDot = findViewById(R.id.iv_newAlarm)
        tvNoDispatchMessage = findViewById(R.id.tvNoDispatch)

        // 뒤로가기
        btnBack.setOnClickListener { finish() }

        // LiveData 관찰하여 빨간 점 상태 반영
        NotificationState.hasNewNotification.observe(this, Observer { hasNew ->
            vRedDot.visibility = if (hasNew) View.VISIBLE else View.INVISIBLE

            if (hasNew) { fetchDispatchList(currentDate) } // 새로운 알림이 올 때마다 배차도 최신화
        })

        // 알람 버튼 클릭
        ivAlarm.setOnClickListener {
            showNotificationPopup()
        }

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
        findViewById<TextView>(R.id.tv_licenseNumber).text = "${user.payload.licenseNumber ?: "-"}"
        findViewById<TextView>(R.id.tv_grade).text = "${user.payload.grade ?: "-"}"
        findViewById<TextView>(R.id.tv_careerYear).text = "${user.payload.careerYears ?: "-"}년"
        findViewById<TextView>(R.id.tv_operator).text = "${user.operatorName ?: "-"}"

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
                        val dispatchList: List<DispatchDetailResponse> = apiBody?.data ?: emptyList()
                        viewPager.visibility = View.VISIBLE
                        tvNoDispatchMessage.visibility = View.GONE
                        setupViewPager(dispatchList)
                    } else {
                        viewPager.visibility = View.GONE
                        tvNoDispatchMessage.visibility = View.VISIBLE
                        Toast.makeText(
                            this@MyPageActivity,
                            "배차 정보를 불러오지 못했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    viewPager.visibility = View.GONE
                    tvNoDispatchMessage.visibility = View.VISIBLE
                    Toast.makeText(
                        this@MyPageActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    /** 알림 BottomSheetDialog 띄우기 */
    private fun showNotificationPopup() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.popup_notifications, null)
        dialog.setContentView(view)

        val rvNotifications = view.findViewById<RecyclerView>(R.id.rvNotifications)
        rvNotifications.layoutManager = LinearLayoutManager(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.notificationApi.getMyNotifications()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val notifications: List<NotificationResponse> =
                            response.body()?.data ?: emptyList()
                        val tvNoNotifications = view.findViewById<TextView>(R.id.tvNoNotifications)

                        if (notifications.isEmpty()) {
                            rvNotifications.visibility = View.GONE
                            tvNoNotifications.visibility = View.VISIBLE
                        } else {
                            rvNotifications.visibility = View.VISIBLE
                            tvNoNotifications.visibility = View.GONE
                            val adapter = NotificationAdapter(notifications)
                            rvNotifications.adapter = adapter
                        }

                        // X 버튼 클릭 처리
                        val ivClose = view.findViewById<ImageView>(R.id.ivClosePopup)
                        ivClose.setOnClickListener {
                            dialog.dismiss()
                        }

                        // 모든 닫힘 이벤트에 동일한 동작 적용
                        dialog.setOnDismissListener {
                            notifications.filter { !it.isRead }.forEach { notif ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        RetrofitInstance.notificationApi.markNotificationRead(notif.notificationId)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            NotificationState.hideRedDot()
                        }

                        // 다이얼로그 닫힘 설정
                        dialog.setCancelable(true)
                        dialog.setCanceledOnTouchOutside(true)
                    } else {
                        Toast.makeText(
                            this@MyPageActivity,
                            response.body()?.message ?: "알림 조회 실패",
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

        dialog.show()
    }
    private fun setupViewPager(dispatchList: List<DispatchDetailResponse>) {
        viewPager.adapter = DispatchPagerAdapter(dispatchList) { dispatch ->
            when (dispatch.status) {
                DispatchStatus.SCHEDULED -> {
                    AlertDialog.Builder(this)
                        .setTitle("운행 시작")
                        .setMessage("운행을 시작하시겠습니까?")
                        .setPositiveButton("확인") { _, _ ->
                            startDispatch(dispatch.dispatchId, dispatch.driverName, dispatch.dispatchDate)
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
                //테스트용
//                DispatchStatus.SCHEDULED -> {
//                    AlertDialog.Builder(this)
//                        .setTitle("운행 시작")
//                        .setMessage("운행을 시작하시겠습니까?")
//                        .setPositiveButton("확인") { _, _ ->
//                            val intent = Intent(this, RunActivity::class.java).apply {
//                                putExtra("dispatchId", dispatch.dispatchId)
//                                putExtra("driverName", dispatch.driverName)
//                                putExtra("dispatchDate", dispatch.dispatchDate)
//                            }
//                            startActivity(intent)
//                        }
//                        .setNegativeButton("취소", null)
//                        .show()
//                }
                DispatchStatus.COMPLETED -> {
                    startActivity(
                        Intent(this, RecordActivity::class.java).apply {
                            putExtra("dispatchId", dispatch.dispatchId)
                        }
                    )
                }
                DispatchStatus.CANCELED -> {
                    Toast.makeText(this, "취소된 배차입니다.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "현재 상태: ${dispatch.status.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        /** ← 여기부터 Peek 관련 설정 추가 */
        viewPager.clipToPadding = false
        viewPager.clipChildren = false
        (viewPager.getChildAt(0) as RecyclerView).clipToPadding = false
        (viewPager.getChildAt(0) as RecyclerView).clipChildren = false
        viewPager.offscreenPageLimit = 3
        /** ← 여기까지 */
        setupIndicators(dispatchList.size)
        setCurrentIndicator(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setCurrentIndicator(position)
            }
        })
    }

    /** 인디케이터 세팅 */
    private fun setupIndicators(count: Int) {
        indicatorLayout.removeAllViews()

        // 🔹 count가 0이면 최소 1개는 생성 (레이아웃 유지용)
        val safeCount = if (count == 0) 1 else count

        val indicators = Array(safeCount) { View(this) }
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

    /** 현재 인디케이터 설정 */
    private fun setCurrentIndicator(index: Int) {
        val count = indicatorLayout.childCount
        if (count == 0) return  // 인디케이터가 없을 때 예외 방지

        // 🔹 index가 범위를 벗어나지 않도록 조정
        val safeIndex = index.coerceIn(0, count - 1)

        for (i in 0 until count) {
            val view = indicatorLayout.getChildAt(i)
            view.setBackgroundResource(
                if (i == safeIndex) R.drawable.indicator_selected
                else R.drawable.indicator_unselected
            )
        }
    }

    private fun startDispatch(dispatchId: Long, driverName: String?, dispatchDate: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.dispatchApi.updateDispatchStart(dispatchId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success && body.data != null) {
                            // ✅ 시작 성공 → RunActivity로 이동
                            val intent = Intent(this@MyPageActivity, RunActivity::class.java).apply {
                                putExtra("dispatchId", dispatchId)
                                putExtra("driverName", driverName)
                                putExtra("dispatchDate", dispatchDate)
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(
                                this@MyPageActivity,
                                body?.message ?: "운행 시작 실패",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(this@MyPageActivity, "운행 시작 실패", Toast.LENGTH_SHORT).show()
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
}
