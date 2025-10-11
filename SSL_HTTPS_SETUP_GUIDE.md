# SSL/HTTPS 설정 및 테스트 가이드

## 개요

이 문서는 QR 출입 관리 시스템에 HTTPS 지원을 추가하여 iOS Safari에서 Web Share API를 사용할 수 있도록 하는 방법을 설명합니다.

---

## 구현 내용

### 변경된 파일

1. **generate_ssl_keystore.sh** (신규 생성)
   - SSL 인증서 자동 생성 스크립트
   - JKS 형식의 keystore 파일 생성

2. **app/src/main/res/raw/keystore.jks** (신규 생성)
   - 자체 서명 SSL 인증서
   - 유효기간: 10년
   - 알고리즘: RSA 2048-bit

3. **MonitoringServer.kt** (수정)
   - SSL 관련 import 추가
   - `makeSecureServerSocketFactory()` 함수 추가
   - `startServer()` 함수에 SSL 활성화 코드 추가

4. **MonitoringViewModel.kt** (수정)
   - 포트 변경: 8080 → 8443
   - URL 프로토콜 변경: http:// → https://

---

## SSL 인증서 정보

```yaml
파일 위치: app/src/main/res/raw/keystore.jks
Alias: qr_server
Password: qrserver123
키 알고리즘: RSA 2048-bit
서명: SHA256withRSA
유효기간: 3650일 (10년)
인증서 정보:
  CN: QR Server
  OU: Development
  O: QR App
  L: Seoul
  ST: Seoul
  C: KR
```

---

## 빌드 및 설치

### 1단계: 앱 빌드

```bash
cd /Users/soo/AndroidStudioProjects/QR

# Gradle 데몬 중지 (선택사항)
./gradlew --stop

# 앱 빌드
./gradlew assembleDebug
```

### 2단계: 기기에 설치

```bash
# 안드로이드 기기에 설치
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3단계: 웹 서버 시작

1. 안드로이드 폰에서 QR 앱 실행
2. **모니터링** 탭으로 이동
3. **웹 서버 시작** 버튼 클릭
4. 표시되는 HTTPS URL 확인 (예: `https://192.168.0.10:8443`)

---

## SSL 인증서 신뢰 설정

### iOS (iPhone, iPad)

자체 서명 인증서는 iOS에서 기본적으로 신뢰되지 않습니다. 수동으로 신뢰 설정이 필요합니다.

#### 방법 1: Safari에서 직접 승인

1. **Safari 브라우저** 실행
2. HTTPS URL 입력 (예: `https://192.168.0.10:8443`)
3. **"이 연결은 비공개 연결이 아닙니다"** 경고 표시
4. **상세 정보 표시** 클릭
5. **웹 사이트 방문** 클릭
6. 확인 다이얼로그에서 **방문** 클릭

#### 방법 2: 설정에서 인증서 신뢰

1. Safari에서 위 방법 1 실행 (인증서 다운로드)
2. **설정** 앱 실행
3. **일반** → **정보** → **인증서 신뢰 설정**
4. **"QR Server"** 인증서 찾기
5. 스위치를 **켜기**로 전환
6. 경고 메시지에서 **계속** 선택

**참고**: 설정에서 인증서를 신뢰하면 세션 간 지속되어 매번 승인할 필요가 없습니다.

---

### Android (Chrome, Edge, Samsung Internet)

Android는 iOS보다 관대하지만, 첫 접속 시 경고가 표시됩니다.

#### Chrome/Edge에서 승인

1. **Chrome** 또는 **Edge** 브라우저 실행
2. HTTPS URL 입력 (예: `https://192.168.0.10:8443`)
3. **"사용자의 연결이 비공개로 설정되어 있지 않습니다"** 경고 표시
4. **고급** 클릭
5. **192.168.x.x(안전하지 않음)로 이동** 클릭

**참고**: Android는 도메인별로 세션 동안 인증서를 기억하므로 같은 IP 주소는 재승인 불필요합니다.

#### Samsung Internet에서 승인

1. **Samsung Internet** 브라우저 실행
2. HTTPS URL 입력
3. **"보안 연결을 설정할 수 없습니다"** 경고 표시
4. **상세 정보** → **이 페이지로 이동** 클릭

---

### macOS / Windows (데스크톱)

**중요**: Web Share API는 데스크톱 브라우저에서 지원되지 않으므로, 이 기능은 모바일 전용입니다.

하지만 웹 인터페이스 자체는 데스크톱에서도 접근 가능합니다.

#### Chrome/Edge (macOS/Windows)

1. Chrome 또는 Edge 실행
2. HTTPS URL 입력
3. **"사용자의 연결이 비공개로 설정되지 않음"** 경고
4. **고급** → **192.168.x.x(안전하지 않음)로 이동** 클릭

#### Safari (macOS)

1. Safari 실행
2. HTTPS URL 입력
3. **"이 연결은 비공개 연결이 아닙니다"** 경고
4. **상세 정보 표시** → **웹 사이트 방문** 클릭

---

## Web Share API 테스트

### iOS Safari 테스트 (권장)

HTTPS가 정상 작동하면 Web Share API를 테스트할 수 있습니다.

1. **인증서 신뢰 설정 완료 확인**
2. Safari에서 HTTPS URL 재접속
3. **참가자 목록** 화면으로 이동
4. **"📱 내 폰에서 발송"** 버튼 확인 (보라색 그라데이션)
5. 버튼 클릭
6. **iOS 공유 시트** 열림 확인
7. **메시지** 앱 선택
8. QR 코드 이미지 첨부 확인
9. 수신자 입력 및 전송

**예상 결과**:
- ✅ iOS 네이티브 공유 시트 사용
- ✅ QR 코드 이미지 첨부됨
- ✅ 메시지 내용에 참가자 정보 포함됨

---

### Android Chrome 테스트

1. **인증서 승인 완료 확인**
2. Chrome에서 HTTPS URL 재접속
3. **참가자 목록** 화면으로 이동
4. **"📱 내 폰에서 발송"** 버튼 확인
5. 버튼 클릭
6. **공유 대상 선택** 화면 열림 확인
7. **메시지**, **카카오톡** 등 선택
8. QR 코드 이미지 첨부 확인
9. 수신자 입력 및 전송

**예상 결과**:
- ✅ Android 공유 대상 선택 UI
- ✅ QR 코드 이미지 첨부됨
- ✅ 메시지 내용에 참가자 정보 포함됨

---

## 브라우저 콘솔 로그

테스트 중 브라우저 콘솔에서 다음 로그를 확인할 수 있습니다:

### 정상 동작 로그

```javascript
✅ Web Share API supported

📱 [WEB_SHARE] QR 코드 공유 시작: 참가자 ID=39
📋 [WEB_SHARE] 참가자 정보 조회 중...
✅ [WEB_SHARE] 참가자 정보 조회 완료: 백경렬
🖼️ [WEB_SHARE] QR 코드 이미지 다운로드 중...
✅ [WEB_SHARE] QR 코드 이미지 다운로드 완료: 25.3KB
📤 [WEB_SHARE] Web Share API 호출 중...
✅ [WEB_SHARE] 공유 완료!
```

### 오류 로그

```javascript
❌ [WEB_SHARE] 참가자 정보를 찾을 수 없습니다
❌ [WEB_SHARE] QR 코드 이미지 다운로드 실패: 404
❌ [WEB_SHARE] 공유 실패: AbortError
```

---

## Android 로그 (adb logcat)

서버 측 동작을 확인하려면 adb logcat을 사용하세요:

```bash
# SSL 관련 로그
~/Library/Android/sdk/platform-tools/adb logcat | grep "SSL\|HTTPS"

# QR 코드 이미지 API 로그
~/Library/Android/sdk/platform-tools/adb logcat | grep "BARCODE_IMAGE"
```

### 예상 로그

```
🔐 SSL 인증서 로드 중...
✅ SSL 인증서 로드 완료
✅ SSL 설정 완료
🚀 HTTPS 웹 서버 시작 시도 - 포트: 8443
✅ HTTPS 웹 서버 성공적으로 시작됨 - 포트: 8443

📥 [BARCODE_IMAGE] QR 코드 이미지 요청: 참가자 ID=39
📋 [BARCODE_IMAGE] 참가자 정보: 백경렬 (QR001)
✅ [BARCODE_IMAGE] QR 코드 이미지 생성 완료: barcode_xxx.png (25KB)
📤 [BARCODE_IMAGE] QR 코드 이미지 전송 완료: 백경렬
```

---

## 문제 해결

### 문제 1: iOS Safari에서 "이 연결은 비공개 연결이 아닙니다" 계속 표시

**원인**: 인증서를 신뢰하지 않음

**해결 방법**:
1. Safari에서 **상세 정보 표시** → **웹 사이트 방문** 클릭
2. 설정 앱에서 인증서 신뢰 설정 (위 "iOS 방법 2" 참조)
3. Safari 재시작 후 다시 접속

---

### 문제 2: Android에서 "ERR_CERT_AUTHORITY_INVALID" 오류

**원인**: Chrome이 자체 서명 인증서를 신뢰하지 않음

**해결 방법**:
1. 주소창에 **고급** 클릭
2. **안전하지 않음(계속)** 또는 **192.168.x.x(안전하지 않음)로 이동** 클릭
3. 페이지 재로딩

---

### 문제 3: Web Share API가 여전히 지원되지 않음

**증상**: HTTPS로 접속했는데도 "📱 내 폰에서 발송" 버튼이 보이지 않음

**확인 사항**:
1. **URL이 https://로 시작하는지 확인**
2. **브라우저 콘솔 확인**: `navigator.share` 존재 여부
3. **브라우저 버전 확인**: iOS Safari 13+, Android Chrome 61+

**디버깅**:
```javascript
// 브라우저 콘솔에서 실행
console.log('navigator.share:', navigator.share);
console.log('location.protocol:', location.protocol);
```

예상 출력:
```
navigator.share: function share() { [native code] }
location.protocol: "https:"
```

---

### 문제 4: SSL 인증서 로드 실패

**증상**: 앱 실행 시 "SSL 설정 실패" 오류

**확인 사항**:
```bash
# 인증서 파일 존재 확인
ls -lh app/src/main/res/raw/keystore.jks

# 예상 출력:
# -rw-r--r--  1 user  staff   4.0K  2025-10-12  keystore.jks
```

**해결 방법**:
1. 인증서 파일이 없으면 재생성:
```bash
./generate_ssl_keystore.sh
```

2. 앱 재빌드:
```bash
./gradlew clean assembleDebug
```

3. 재설치:
```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### 문제 5: 포트 8443이 이미 사용 중

**증상**: "포트 충돌: 8443 포트가 이미 사용중입니다"

**해결 방법**:
1. 기존 서버 중지 (앱에서 "웹 서버 중지" 버튼 클릭)
2. 앱 재시작
3. 다른 앱이 8443 포트를 사용 중인지 확인:
```bash
# macOS/Linux
lsof -i :8443

# 프로세스 종료
kill -9 <PID>
```

---

## 보안 고려사항

### 자체 서명 인증서의 제약

1. **브라우저 경고**: 모든 브라우저에서 경고 메시지 표시
2. **수동 신뢰 필요**: 사용자가 인증서를 수동으로 신뢰해야 함
3. **공인 CA 아님**: 인증 기관의 서명이 없음
4. **로컬 네트워크 전용**: 외부 인터넷에서는 권장하지 않음

### 프로덕션 배포 시 권장사항

**로컬 네트워크 사용 시 (현재 시나리오)**:
- ✅ 자체 서명 인증서 사용 가능
- ✅ iOS/Android에서 수동 신뢰 설정
- ✅ 비용 없음, 간편한 설정

**외부 인터넷 배포 시**:
- 🔴 Let's Encrypt 등 공인 CA 인증서 필요
- 🔴 도메인 이름 필요 (IP 주소로는 공인 인증서 발급 불가)
- 🔴 ngrok 또는 Cloudflare Tunnel 같은 서비스 사용 권장

---

## 참고 자료

### Web Share API 문서

- [MDN: Web Share API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Share_API)
- [Can I Use: Web Share API](https://caniuse.com/web-share)

### SSL/TLS 문서

- [Android Developer: Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [NanoHTTPD: SSL Support](https://github.com/NanoHttpd/nanohttpd)

### iOS 인증서 신뢰 설정

- [Apple Support: Certificate Trust Settings](https://support.apple.com/en-us/HT204477)

---

## 테스트 체크리스트

### 빌드 및 설치
- [ ] SSL 인증서 생성 완료 (keystore.jks)
- [ ] 앱 빌드 성공 (gradlew assembleDebug)
- [ ] 안드로이드 기기에 설치 완료
- [ ] 앱 실행 및 웹 서버 시작 성공

### HTTPS 연결
- [ ] iOS Safari: 인증서 신뢰 설정 완료
- [ ] iOS Safari: HTTPS URL 접속 성공 (경고 없음)
- [ ] Android Chrome: 인증서 승인 완료
- [ ] Android Chrome: HTTPS URL 접속 성공

### Web Share API 기능
- [ ] iOS Safari: "📱 내 폰에서 발송" 버튼 표시됨
- [ ] iOS Safari: 버튼 클릭 시 공유 시트 열림
- [ ] iOS Safari: QR 코드 이미지 첨부됨
- [ ] iOS Safari: 메시지 앱으로 전송 성공
- [ ] Android Chrome: "📱 내 폰에서 발송" 버튼 표시됨
- [ ] Android Chrome: 버튼 클릭 시 공유 대상 선택 화면 열림
- [ ] Android Chrome: QR 코드 이미지 첨부됨
- [ ] Android Chrome: 메시지 앱으로 전송 성공

### 기존 기능 (회귀 테스트)
- [ ] 참가자 목록 조회 정상 작동
- [ ] QR 코드 스캔 정상 작동
- [ ] 통계 표시 정상 작동
- [ ] 기존 "QR 코드발송" 버튼 (서버 폰에서 발송) 정상 작동

---

## 완료!

모든 테스트가 통과하면 Web Share API 기능이 정상적으로 작동하는 것입니다.

이제 다음 두 가지 방식으로 QR 코드를 전송할 수 있습니다:

1. **서버 폰에서 자동 발송** (기존 방식)
   - "QR 코드발송" 버튼 사용
   - 서버 폰의 MMS 설정 필요
   - 완전 자동화

2. **각 기기에서 수동 발송** (신규 방식)
   - "📱 내 폰에서 발송" 버튼 사용
   - iOS, Android 모두 지원
   - 사용자가 수동으로 수신자 선택 및 전송

두 방식 모두 사용 가능하므로 상황에 맞게 선택하여 사용하세요!

---

**작성일**: 2025-10-12
**버전**: 1.0.0
**문서 작성자**: Claude Code
