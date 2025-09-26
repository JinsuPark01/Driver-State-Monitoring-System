package com.example.android_front.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.R
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.model.RegisterRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPasswordConfirm: EditText
    private lateinit var etName: EditText
    private lateinit var etPhoneNumber: EditText
    private lateinit var etLicenseNumber: EditText
    private lateinit var etCareerYears: EditText
    private lateinit var etOperate: EditText

    private lateinit var btnSignup: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // 위젯 초기화
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        etName = findViewById(R.id.etName)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etLicenseNumber = findViewById(R.id.etLicenseNumber)
        etCareerYears = findViewById(R.id.etCareerYears)
        etOperate = findViewById(R.id.etOperate)

        btnSignup = findViewById(R.id.btnSignup)

        // 뒤로가기 버튼
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // 회원가입 버튼
        btnSignup.setOnClickListener {
            if (validateInputs()) {
                val request = RegisterRequest(
                    email = etEmail.text.toString().trim(),
                    password = etPassword.text.toString().trim(),
                    username = etName.text.toString().trim(),
                    operatorCode = etOperate.text.toString().trim(),
                    phoneNumber = etPhoneNumber.text.toString().trim(),
                    licenseNumber = etLicenseNumber.text.toString().trim(),
                    careerYears = etCareerYears.text.toString().trim().toIntOrNull() ?: 0,
                    imagePath = "1", // 기본값
                    role = "DRIVER"
                )
                registerUser(request)
            }
        }
    }

    private fun validateInputs(): Boolean {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val passwordConfirm = etPasswordConfirm.text.toString().trim()
        val name = etName.text.toString().trim()
        val operatorId = etOperate.text.toString().trim()
        val phoneNumber = etPhoneNumber.text.toString().trim()
        val licenseNumber = etLicenseNumber.text.toString().trim()
        val career = etCareerYears.text.toString().trim()

        // 모든 필드 기본 tint 초기화
        val fields = listOf(etEmail, etPassword, etPasswordConfirm, etName, etOperate, etPhoneNumber, etLicenseNumber, etCareerYears)
        fields.forEach { it.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BDBDBD")) }

        when {
            email.isEmpty() -> {
                etEmail.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("아이디를 입력해주세요")
                return false
            }
            password.length < 6 -> {
                etPassword.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("비밀번호는 6자리 이상이어야 합니다")
                return false
            }
            password != passwordConfirm -> {
                etPasswordConfirm.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("비밀번호가 일치하지 않습니다")
                return false
            }
            name.isEmpty() -> {
                etName.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("이름을 입력해주세요")
                return false
            }
            operatorId.isEmpty() -> {
                etOperate.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("회사 코드를 입력해주세요")
                return false
            }
            phoneNumber.isEmpty() -> {
                etPhoneNumber.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("전화번호를 입력해주세요")
                return false
            }
            licenseNumber.isEmpty() -> {
                etLicenseNumber.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("면허번호를 입력해주세요")
                return false
            }
            career.isEmpty() -> {
                etCareerYears.backgroundTintList = ColorStateList.valueOf(Color.RED)
                showSnackbar("경력을 입력해주세요")
                return false
            }
            else -> return true
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.btnSignup), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun registerUser(request: RegisterRequest) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.authApi.register(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        showSnackbar("회원가입 성공!")
                        startActivity(Intent(this@SignUpActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        showSnackbar("회원가입 실패: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showSnackbar("오류: ${e.message}")
                }
            }
        }
    }
}
