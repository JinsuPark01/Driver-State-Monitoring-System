package com.example.android_front.ui


import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.android_front.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// 요청 DTO
data class LoginRequest(
    val username: String,
    val password: String
)

// 응답 DTO
data class LoginResponse(
    val token: String,
    val username: String
)

// Retrofit API 정의
interface AuthApi {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvUsernameError: TextView
    private lateinit var tvPasswordError: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnSignup: Button

    private lateinit var authApi: AuthApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in) // XML 레이아웃

        // 뷰 초기화
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        tvUsernameError = findViewById(R.id.tvUsernameError)
        tvPasswordError = findViewById(R.id.tvPasswordError)
        btnLogin = findViewById(R.id.btnLogin)
        btnSignup = findViewById(R.id.btnSignup)

        // Retrofit 초기화
        val retrofit = Retrofit.Builder()
            .baseUrl("https://your-api-domain.com") // 서버 URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        authApi = retrofit.create(AuthApi::class.java)

        // 로그인 버튼 클릭
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            var valid = true
            if (username.isEmpty()) {
                tvUsernameError.visibility = View.VISIBLE
                tvUsernameError.text = "아이디를 입력해주세요."
                valid = false
            } else {
                tvUsernameError.visibility = View.GONE
            }

            if (password.isEmpty()) {
                tvPasswordError.visibility = View.VISIBLE
                tvPasswordError.text = "비밀번호를 입력해주세요."
                valid = false
            } else {
                tvPasswordError.visibility = View.GONE
            }

            if (valid) {
                doLogin(username, password)
            }
        }

        // 회원가입 버튼 클릭
        btnSignup.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }

    private fun doLogin(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = authApi.login(LoginRequest(username, password))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val loginResponse = response.body()
                        if (loginResponse != null) {
                            // JWT 토큰 저장
                            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
                            prefs.edit().putString("token", loginResponse.token).apply()

                            Toast.makeText(
                                this@LoginActivity,
                                "${loginResponse.username}님, 로그인 성공!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // MainActivity로 이동
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish() // 로그인 액티비티 종료
                        }
                    } else {
                        tvPasswordError.visibility = View.VISIBLE
                        tvPasswordError.text = "아이디 또는 비밀번호가 올바르지 않습니다."
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LoginActivity,
                        "서버 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LoginActivity,
                        "네트워크 오류: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
