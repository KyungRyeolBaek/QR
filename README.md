# QR 출입 관리 시스템

Android 기반의 QR 코드를 활용한 출입 관리 시스템입니다. 사용자 등록, QR 코드 생성/스캔, 출입 기록 관리, 통계 분석 및 Excel 리포트 기능을 제공합니다.

## 주요 기능

### 사용자 관리
- **사용자 등록**: 이름, 전화번호 입력으로 간편 등록
- **QR 코드 생성**: 개인별 고유 QR 코드 자동 생성
- **SMS 전송**: 생성된 QR 코드를 SMS/MMS로 전송
- **사용자 목록**: 등록된 사용자 조회 및 관리

### QR 스캐너
- **실시간 카메라 스캔**: ZXing 라이브러리 기반 고성능 스캔
- **출입 기록**: 입장/퇴장 상태 자동 토글
- **실시간 통계**: 오늘 입장, 퇴장, 현재 체류자 수 표시
- **빠른 반응**: 스캔 즉시 결과 처리

### Excel 리포트
- **전체 출입 기록**: 기간별 모든 출입 기록 추출
- **개인별 상세 기록**: 특정 사용자의 상세 출입 내역
- **사용자 목록**: 등록된 모든 사용자 정보
- **일별 통계**: 날짜별 출입 통계 및 근무시간 분석

### 파일 전송
- **MMS 전송**: Excel 파일을 직접 MMS로 전송
- **메신저 공유**: 카카오톡, 텔레그램 등 메신저 앱 연동
- **일반 공유**: Android 공유 기능을 통한 다양한 앱 지원
- **파일 크기 체크**: MMS 2MB 제한 사전 확인

## 기술 스택

### Frontend
- **Kotlin**: 100% Kotlin 기반 개발
- **Jetpack Compose**: 모던 UI 프레임워크
- **Material Design 3**: 일관된 디자인 시스템
- **Navigation Compose**: 선언적 네비게이션

### Backend & Database
- **Room Database**: 로컬 SQLite 데이터베이스
- **MVVM Architecture**: Repository 패턴 적용
- **Coroutines & Flow**: 비동기 처리 및 반응형 프로그래밍
- **StateFlow**: 상태 관리

### 외부 라이브러리
- **ZXing**: QR 코드 생성 및 스캔
- **Apache POI**: Excel 파일 생성 (Android 최적화)
- **CameraX**: 카메라 기능
- **Retrofit**: HTTP 통신 (SMS API)

## 요구사항

- **Android 8.0 (API 26)** 이상
- **카메라 권한**: QR 코드 스캔
- **SMS 권한**: QR 코드 및 Excel 파일 전송
- **저장소 권한**: Excel 파일 생성 및 저장

## 설치 및 실행

### 1. 프로젝트 클론
```bash
git clone [repository-url]
cd QR
```

### 2. 개발 환경 설정
- **Android Studio**: Arctic Fox (2020.3.1) 이상
- **Java**: 11 이상
- **Gradle**: 8.0 이상

### 3. 빌드 및 실행
```bash
./gradlew clean build
./gradlew installDebug
```

## 프로젝트 구조

```
app/src/main/java/com/example/qr/
├── data/
│   ├── database/           # Room 데이터베이스 설정
│   ├── entities/           # 데이터 엔티티 (User, EntryLog, SMSLog)
│   └── dao/               # 데이터 접근 객체
├── repository/            # 데이터 저장소 계층
├── service/              # 비즈니스 로직 서비스
│   ├── ExcelExportService.kt    # Excel 생성 및 공유
│   ├── NativeSMSService.kt      # SMS/MMS 전송
│   └── QRCodeService.kt         # QR 코드 생성
├── viewmodel/            # MVVM 뷰모델
├── ui/
│   ├── screens/          # Compose 스크린
│   ├── components/       # 재사용 가능한 UI 컴포넌트
│   └── theme/            # Material Design 테마
└── utils/                # 유틸리티 함수
```

## 주요 해결 과제

### 1. Apache POI Android 호환성 문제
**문제**: `autoSizeColumn()` 사용 시 AWT 의존성으로 `NoClassDefFoundError` 발생
**해결**: 고정 컬럼 폭 사용으로 Android 환경 최적화

### 2. Room Database Flow 무한 대기
**문제**: `collect {}` 사용 시 무한 대기로 ANR 발생
**해결**: `first()` 메서드로 일회성 데이터 조회

### 3. N+1 쿼리 성능 최적화
**문제**: 반복문 내 개별 사용자 조회로 성능 저하
**해결**: `associateBy()` 맵 사용으로 O(1) 조회 최적화

### 4. ZXing 카메라 프리뷰 문제
**문제**: Compose와 ZXing CompoundBarcodeView 연동 실패
**해결**: AndroidView 래퍼와 라이프사이클 관리

## 데이터베이스 스키마

### Users 테이블
```sql
CREATE TABLE users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phoneNumber TEXT NOT NULL,
    qrCode TEXT,
    smsStatus TEXT DEFAULT 'PENDING',
    isActive INTEGER DEFAULT 1,
    createdAt INTEGER NOT NULL
)
```

### EntryLogs 테이블
```sql
CREATE TABLE entry_logs (
    id TEXT PRIMARY KEY,
    userId TEXT NOT NULL,
    userName TEXT NOT NULL,
    entryType TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    location TEXT,
    FOREIGN KEY(userId) REFERENCES users(id)
)
```

### SMSLogs 테이블
```sql
CREATE TABLE sms_logs (
    id TEXT PRIMARY KEY,
    userId TEXT NOT NULL,
    phoneNumber TEXT NOT NULL,
    message TEXT NOT NULL,
    status TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    errorMessage TEXT
)
```

## UI/UX 특징

- **Material Design 3**: 최신 디자인 가이드라인 준수
- **다크 모드 지원**: 시스템 테마 자동 적용
- **반응형 레이아웃**: 다양한 화면 크기 지원
- **직관적 네비게이션**: 바텀 네비게이션으로 쉬운 이동
- **실시간 피드백**: 로딩 상태 및 결과 즉시 표시

## 보안 고려사항

- **SMS 권한 최소화**: 필요시에만 권한 요청
- **로컬 데이터 암호화**: 민감 정보 보호
- **QR 코드 유효성**: 고유 ID 기반 검증
- **파일 공유 보안**: FileProvider 사용으로 안전한 파일 공유

## 향후 개선 계획

- [ ] **클라우드 백업**: Firebase 연동으로 데이터 백업
- [ ] **관리자 대시보드**: 웹 기반 관리 인터페이스
- [ ] **얼굴 인식**: QR + 얼굴 인식 이중 인증
- [ ] **출입 알림**: 실시간 푸시 알림
- [ ] **다국어 지원**: 영어, 중국어 등 다국어 UI

## 라이센스

이 프로젝트는 개인/상업적 목적으로 자유롭게 사용 가능합니다.

## 문의 및 지원

프로젝트 관련 문의사항이나 버그 리포트는 Issues를 통해 연락주세요.

---

**개발 환경**: Android Studio | **언어**: Kotlin | **UI**: Jetpack Compose | **DB**: Room SQLite