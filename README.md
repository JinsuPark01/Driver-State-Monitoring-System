# <프로젝트명> - Driver App (Android) 🚍📱
AI 기반 차량 관제 시스템의 **운전자용 Android 앱**입니다.  
운전자는 배차 일정 확인, 운행 시작/종료, 실시간 주행 데이터 확인, AI 기반 운전자 이상상태 감지(졸음/휴대폰/흡연/안전벨트 등), 경고 및 리포트 기능을 사용할 수 있습니다.

---

## 1) 데모 / 스크린샷
> 여기에 캡처 넣으면 완성도 급상승

- 로그인 / 회원가입
- 메인(배차 요약)
- 운행 화면(실시간 데이터 + 경고 오버레이)
- 리포트/점수 화면

예시:
![main](images/main.png)

---

## 2) 핵심 기능

### ✅ 인증/계정
- JWT 기반 로그인/회원가입
- 토큰 저장 및 자동 로그인(만료 시 재로그인/갱신 정책)

### ✅ 배차/운행
- 배차 일정 조회(일/주 단위)
- 배차 상세(노선/차량/시간) 확인
- 운행 상태 전환: `SCHEDULED → RUNNING → COMPLETED` (지연/취소 등 포함)

### ✅ 실시간 주행 데이터 수신
- 시뮬레이터/서버로부터 주행 텔레메트리 수신(속도/가속 등)
- 급가속/급제동 등 이벤트 감지 및 표시

### ✅ AI 운전자 모니터링
- CameraX 기반 실시간 분석
- TFLite/YOLO 기반 감지: <졸음/휴대폰/흡연/안전벨트 ...>
- 이상 감지 시 오버레이 경고 + 서버 전송(경고 로그)

### ✅ 리포트/점수
- 위험행동 카운트/점수화(예: 급가속/급제동/이상상태)
- 운행 기록 조회(일/주/전체)

---

## 3) 시스템 구성(Architecture)
- Driver App (Android/Kotlin)
- Backend (Spring Boot): REST API + WebSocket(STOMP)
- DB (MySQL)
- Admin Web (React)
- Simulator (Unity): 주행 데이터 스트리밍

> 앱은 **REST(조회/저장)** + **WebSocket(실시간)**를 병행합니다.

(선택) 아키텍처 그림 넣기:
![architecture](images/architecture.png)

---

## 4) 기술 스택
- **Android**: Kotlin, Android Studio, (Jetpack: ViewModel/LiveData 등)
- **Network**: Retrofit2, OkHttp, WebSocket(STOMP)
- **AI**: TensorFlow Lite, CameraX
- **Chart/UI**: <MPAndroidChart 등 사용하면 추가>
- **Build**: Gradle

---

## 5) 프로젝트 구조(예시)
```text
app/
 ├─ data/             # DTO, Repository
 ├─ network/          # Retrofit, Interceptor(JWT)
 ├─ websocket/        # STOMP client, subscribe
 ├─ socket/           # Simulator SocketService (port <9999>)
 ├─ ai/               # ModelHandler, preprocess, inference
 ├─ ui/               # Activity/Fragment/Adapter
 └─ utils/            # TokenManager, constants
