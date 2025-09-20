package com.example.android_front.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.R
import com.example.android_front.model.RegisterRequest
import com.example.android_front.api.RetrofitInstance
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPasswordConfirm: EditText
    private lateinit var etName: EditText
    private lateinit var etOperate: EditText

    private lateinit var tvEmailError: TextView
    private lateinit var tvPasswordError: TextView
    private lateinit var tvPasswordConfirmError: TextView
    private lateinit var tvNameError: TextView
    private lateinit var tvOperateError: TextView

    private lateinit var btnSignup: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // 위젯 초기화
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        etName = findViewById(R.id.etName)
        etOperate = findViewById(R.id.etOperate)

        tvEmailError = findViewById(R.id.tvEmailError)
        tvPasswordError = findViewById(R.id.tvPasswordError)
        tvPasswordConfirmError = findViewById(R.id.tvPasswordConfirmError)
        tvNameError = findViewById(R.id.tvNameError)
        tvOperateError = findViewById(R.id.tvOperateError)

        btnSignup = findViewById(R.id.btnSignup)

        // 뒤로가기 버튼 처리
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // 회원가입 버튼 처리
        btnSignup.setOnClickListener {
            if (validateInputs()) {
                val request = RegisterRequest(
                    email= etEmail.text.toString().trim(),
                    password = etPassword.text.toString().trim(),
                    username = etName.text.toString().trim(),
                    operatorCode = etOperate.text.toString().trim(),
                    phoneNumber = "",
                    licenseNumber = "",
                    careerYears = 0,
                    imagePath = "",
                    role = "driver" // 자동으로 driver 지정
                )
                registerUser(request)
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // 에러 초기화
        tvEmailError.visibility = View.GONE
        tvPasswordError.visibility = View.GONE
        tvPasswordConfirmError.visibility = View.GONE
        tvNameError.visibility = View.GONE
        tvOperateError.visibility = View.GONE

        val driverId = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val passwordConfirm = etPasswordConfirm.text.toString().trim()
        val name = etName.text.toString().trim()
        val operatorId = etOperate.text.toString().trim()

        if (driverId.isEmpty()) {
            tvEmailError.text = "아이디를 입력해주세요"
            tvEmailError.visibility = View.VISIBLE
            isValid = false
        }

        if (password.length < 6) {
            tvPasswordError.text = "비밀번호는 6자리 이상이어야 합니다"
            tvPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        if (password != passwordConfirm) {
            tvPasswordConfirmError.text = "비밀번호가 일치하지 않습니다"
            tvPasswordConfirmError.visibility = View.VISIBLE
            isValid = false
        }

        if (name.isEmpty()) {
            tvNameError.text = "이름을 입력해주세요"
            tvNameError.visibility = View.VISIBLE
            isValid = false
        }

        if (operatorId.isEmpty()) {
            tvOperateError.text = "회사 코드를 입력해주세요"
            tvOperateError.visibility = View.VISIBLE
            isValid = false
        }

        return isValid
    }

    private fun registerUser(request: RegisterRequest) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.authApi.register(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SignUpActivity, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignUpActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@SignUpActivity, "회원가입 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignUpActivity, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
