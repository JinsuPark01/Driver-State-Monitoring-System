package com.example.android_front.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.MyApplication
import com.example.android_front.api.RetrofitInstance
import com.example.android_front.api.TokenManager
import com.example.android_front.model.LoginRequest
import com.example.android_front.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvUsernameError: TextView
    private lateinit var tvPasswordError: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnSignup: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 자동 로그인 체크
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (!token.isNullOrEmpty()) {
            TokenManager.token = token // 인터셉터용

            // 자동 로그인 시 WebSocket 연결
            val app = application as MyApplication
            app.connectWebSocket(token)

            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_sign_in)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        tvUsernameError = findViewById(R.id.tvUsernameError)
        tvPasswordError = findViewById(R.id.tvPasswordError)
        btnLogin = findViewById(R.id.btnLogin)
        btnSignup = findViewById(R.id.btnSignup)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty()) {
                tvUsernameError.visibility = View.VISIBLE
                tvUsernameError.text = "아이디를 입력해주세요."
                return@setOnClickListener
            } else tvUsernameError.visibility = View.GONE

            if (password.isEmpty()) {
                tvPasswordError.visibility = View.VISIBLE
                tvPasswordError.text = "비밀번호를 입력해주세요."
                return@setOnClickListener
            } else tvPasswordError.visibility = View.GONE

            doLogin(username, password)
        }

        btnSignup.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun doLogin(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitInstance.authApi.login(LoginRequest(username, password))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success && body.data != null) {
                            val token = body.data.accessToken
                            TokenManager.token = token // 인터셉터용
                            getSharedPreferences("auth", MODE_PRIVATE)
                                .edit()
                                .putString("token", token)
                                .apply()

                            // 로그인 성공 시 WebSocket 연결
                            val app = application as MyApplication
                            app.connectWebSocket(token)

                            Toast.makeText(this@LoginActivity, "로그인 성공!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            tvPasswordError.visibility = View.VISIBLE
                            tvPasswordError.text = body?.message ?: "로그인 실패"
                        }
                    } else {
                        tvPasswordError.visibility = View.VISIBLE
                        tvPasswordError.text = "아이디 또는 비밀번호가 올바르지 않습니다."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
