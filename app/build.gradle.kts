plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.android_front"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.android_front"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    //implementation("com.google.android.material:material:1.9.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // 코루틴
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    //카메라
    val cameraxVersion = "1.2.3"
    implementation("androidx.camera:camera-core:$cameraxVersion") //CameraX 기본 기능
    implementation("androidx.camera:camera-camera2:$cameraxVersion") //Camera2 API 연동
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion") //Lifecycle 기반 바인딩
    implementation("androidx.camera:camera-view:$cameraxVersion") //PreviewView 지원, 화면 출력

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    // 필요하면 GPU/NNAPI/Support 라이브러리도 추가 가능
    // GPU delegate
    // implementation 'org.tensorflow:tensorflow-lite-gpu:2.13.0'
    // Support library (모델 입출력 편하게 처리)
    implementation ("org.tensorflow:tensorflow-lite-support:0.4.3")

    // STOMP (WebSocket)
    implementation("com.github.NaikSoftware:StompProtocolAndroid:1.6.6")
    // OkHttp 필요 (STOMP 내부에서 사용)
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    // RxJava2 / RxAndroid2 (StompProtocolAndroid uses RxJava2 APIs)
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    //Gson (JSON 직렬화)
    implementation("com.google.code.gson:gson:2.10.1")

    //GPS
    implementation("com.google.android.gms:play-services-location:21.0.1")

    //카카오맵
    implementation("com.kakao.maps.open:android:2.12.18")
    implementation("com.kakao.sdk:v2-common:2.13.0") // 해시키 추출용

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
}