# Web Share API 기능 테스트 가이드

## 🎯 구현 내용

**"내 폰에서 발송" 기능**이 추가되었습니다!

이제 웹 브라우저(노트북, 아이폰, 안드로이드 폰)에서 **각 사용자가 자신의 기기에서 직접 QR 코드를 공유/발송**할 수 있습니다.

---

## 📋 변경 사항 요약

### 추가된 파일
- `WEB_SHARE_API_TEST_GUIDE.md` (이 파일)

### 수정된 파일
- `MonitoringServer.kt` (총 1개 파일만 수정)

### 구현 내용

#### 1️⃣ 새 API 엔드포인트
```kotlin
GET /api/barcode-image/{participantId}
→ QR 코드 이미지를 PNG 파일로 반환
```

#### 2️⃣ JavaScript 함수
```javascript
shareQrCodeFromMyPhone(participantId)  // 개별 공유
checkWebShareSupport()                  // 브라우저 지원 확인
```

#### 3️⃣ UI 버튼
- **"📱 내 폰에서 발송"** 버튼 추가 (보라색 그라데이션)
- Web Share API 미지원 브라우저에서는 자동 숨김

---

## 🚀 빌드 및 실행

### 1단계: 앱 빌드
```bash
cd /Users/soo/AndroidStudioProjects/QR

# Gradle 캐시 정리 (선택사항)
./gradlew clean

# 앱 빌드
./gradlew assembleDebug
```

### 2단계: 앱 설치
```bash
# 안드로이드 기기에 설치
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3단계: 앱 실행 및 웹 서버 시작
1. 안드로이드 폰에서 QR 앱 실행
2. "모니터링" 탭으로 이동
3. "웹 서버 시작" 버튼 클릭
4. 표시되는 IP 주소 확인 (예: `http://192.168.0.10:8080`)

---

## 🧪 테스트 방법

### ✅ 테스트 환경

#### 지원되는 브라우저
| 플랫폼 | 브라우저 | Web Share API | 테스트 결과 |
|--------|----------|---------------|-------------|
| **Android** | Chrome 61+ | ✅ | 테스트 필요 |
| **Android** | Edge 79+ | ✅ | 테스트 필요 |
| **Android** | Samsung Internet | ✅ | 테스트 필요 |
| **iOS** | Safari 13+ | ✅ | 테스트 필요 |
| **macOS** | Safari 14+ | ⚠️ | 미지원 (버튼 숨김) |
| **Windows** | Chrome/Edge | ⚠️ | 미지원 (버튼 숨김) |

---

### 🔬 테스트 시나리오

#### 시나리오 1: Android Chrome에서 테스트

**준비:**
1. Android 폰에서 Chrome 브라우저 실행
2. 웹 서버 주소 입력: `http://192.168.x.x:8080`
3. 참가자 목록 확인

**테스트 단계:**
```
1. "📱 내 폰에서 발송" 버튼 확인
   ✅ 보라색 그라데이션 버튼이 보여야 함

2. 버튼 클릭
   ✅ Web Share API 공유 화면이 열림

3. 메시지 앱 선택 (예: "메시지", "카카오톡")
   ✅ QR 코드 이미지 + 메시지가 함께 첨부됨

4. 수신자 입력 및 전송
   ✅ MMS/이미지 메시지로 정상 전송됨
```

**예상 결과:**
- ✅ QR 코드 이미지가 첨부됨
- ✅ 메시지 내용에 참가자 정보 포함됨
- ✅ 사용자가 수동으로 전화번호 입력 및 전송

---

#### 시나리오 2: iOS Safari에서 테스트

**준비:**
1. iPhone에서 Safari 브라우저 실행
2. 웹 서버 주소 입력: `http://192.168.x.x:8080`
3. 참가자 목록 확인

**테스트 단계:**
```
1. "📱 내 폰에서 발송" 버튼 확인
   ✅ 보라색 그라데이션 버튼이 보여야 함

2. 버튼 클릭
   ✅ iOS 공유 시트가 열림

3. "메시지" 앱 선택
   ✅ 메시지 앱이 열리고 QR 코드 이미지 첨부됨

4. 수신자 입력 및 전송
   ✅ iMessage/SMS로 정상 전송됨
```

**예상 결과:**
- ✅ iOS 네이티브 공유 시트 사용
- ✅ QR 코드 이미지가 첨부됨
- ✅ 메시지 내용에 참가자 정보 포함됨

---

#### 시나리오 3: 노트북 Chrome에서 테스트

**준비:**
1. 노트북(Mac/Windows)에서 Chrome 브라우저 실행
2. 웹 서버 주소 입력: `http://192.168.x.x:8080`
3. 참가자 목록 확인

**테스트 단계:**
```
1. "📱 내 폰에서 발송" 버튼 확인
   ⚠️ 버튼이 보이지 않아야 함 (Web Share API 미지원)

2. 브라우저 콘솔 확인 (F12)
   ✅ "ℹ️ Web Share API not supported in this browser" 메시지 확인
```

**예상 결과:**
- ✅ 버튼이 자동으로 숨겨짐
- ✅ 기존 "QR 코드발송" 버튼은 정상 작동
- ✅ 콘솔에 안내 메시지 출력

---

## 🔍 디버깅 방법

### 브라우저 콘솔 로그

```javascript
// Web Share API 지원 확인
✅ Web Share API supported

// QR 코드 공유 프로세스
📱 [WEB_SHARE] QR 코드 공유 시작: 참가자 ID=39
📋 [WEB_SHARE] 참가자 정보 조회 중...
✅ [WEB_SHARE] 참가자 정보 조회 완료: 백경렬
🖼️ [WEB_SHARE] QR 코드 이미지 다운로드 중...
✅ [WEB_SHARE] QR 코드 이미지 다운로드 완료: 25.3KB
📤 [WEB_SHARE] Web Share API 호출 중...
✅ [WEB_SHARE] 공유 완료!
```

### 안드로이드 로그 (adb logcat)

```bash
# 실시간 로그 모니터링
~/Library/Android/sdk/platform-tools/adb logcat | grep "BARCODE_IMAGE\|WEB_SHARE"
```

**예상 로그:**
```
📥 [BARCODE_IMAGE] QR 코드 이미지 요청: 참가자 ID=39
📋 [BARCODE_IMAGE] 참가자 정보: 백경렬 (QR001)
✅ [BARCODE_IMAGE] QR 코드 이미지 생성 완료: barcode_xxx.png (25KB)
📤 [BARCODE_IMAGE] QR 코드 이미지 전송 완료: 백경렬
```

---

## ⚠️ 알려진 제약사항

### 1. Web Share API 지원
- **데스크톱 브라우저**: 대부분 미지원 (버튼 자동 숨김)
- **모바일 브라우저**: Android Chrome, iOS Safari만 지원

### 2. 사용자 액션 필요
- **완전 자동화 불가**: 사용자가 수동으로 수신자 선택 및 전송 버튼 클릭 필요
- **전화번호 미리 입력 불가**: Web Share API 제약

### 3. 파일 크기
- **QR 코드 이미지**: 약 25-50KB
- **MMS 제한**: 통신사별 300KB-1MB (현재 크기는 문제없음)

---

## 🎯 기존 방식과의 비교

### 기존: 중앙 집중식 (서버 폰에서 자동 발송)
```
[QR 코드발송] 버튼
↓
✅ 서버 폰에서 자동 발송
❌ 서버 폰의 모바일 데이터 필요
❌ 서버 폰의 MMS 설정 필요
```

### 신규: 분산식 (각 기기에서 수동 발송)
```
[📱 내 폰에서 발송] 버튼
↓
✅ iOS, Android 모두 지원
✅ QR 코드 이미지 첨부
✅ 각 기기의 SMS 할당량 사용
⚠️ 사용자가 수동으로 전송
```

---

## 📊 테스트 체크리스트

### 기능 테스트
- [ ] Android Chrome에서 버튼 표시 확인
- [ ] iOS Safari에서 버튼 표시 확인
- [ ] 노트북 Chrome에서 버튼 숨김 확인
- [ ] QR 코드 이미지 다운로드 확인
- [ ] Web Share API 공유 화면 열림 확인
- [ ] 메시지 앱에 QR 코드 첨부 확인
- [ ] 메시지 내용 확인 (참가자 정보 포함)

### UI/UX 테스트
- [ ] 버튼 스타일 확인 (보라색 그라데이션)
- [ ] 버튼 hover 효과 확인
- [ ] 버튼 위치 확인 (QR 코드발송과 재전송 사이)
- [ ] 반응형 디자인 확인 (모바일 화면)

### 에러 처리 테스트
- [ ] 참가자 ID 없을 때 에러 메시지
- [ ] QR 코드 생성 실패 시 에러 메시지
- [ ] 네트워크 오류 시 에러 메시지
- [ ] 공유 취소 시 정상 동작

---

## 🔧 문제 해결

### 문제 1: 버튼이 보이지 않음 (모바일)

**원인:** JavaScript 오류 또는 브라우저 버전

**해결:**
```bash
# 1. 브라우저 콘솔 확인 (F12 또는 Safari 개발자 도구)
# 2. JavaScript 오류 확인
# 3. 브라우저 업데이트
```

---

### 문제 2: QR 코드 이미지가 다운로드되지 않음

**원인:** API 엔드포인트 오류

**확인:**
```bash
# adb logcat으로 로그 확인
~/Library/Android/sdk/platform-tools/adb logcat | grep "BARCODE_IMAGE"

# 예상 로그:
# ❌ [BARCODE_IMAGE] QR 코드 이미지 생성 실패
# ❌ [BARCODE_IMAGE] 참가자를 찾을 수 없음
```

**해결:**
1. 참가자 ID가 유효한지 확인
2. 파일 저장 권한 확인
3. 앱 재시작

---

### 문제 3: Web Share API 호출 실패

**원인:** HTTPS 필요 또는 브라우저 제약

**참고:**
- Web Share API는 **HTTPS 또는 localhost**에서만 작동
- 현재는 **로컬 네트워크 (192.168.x.x)**이므로 정상 작동
- 외부 배포 시 **HTTPS 인증서** 필요

---

## 📱 다음 단계

### 선택적 개선 사항

#### 1. SMS URI 폴백
Web Share API 미지원 브라우저용 대체 방법:
```javascript
// SMS 앱 직접 열기 (이미지 첨부 불가)
window.location.href = `sms:${phoneNumber}?body=${message}`;
```

#### 2. QR 코드 다운로드 버튼
사용자가 QR 코드를 직접 다운로드:
```html
<button onclick="downloadQrCode(participantId)">
    QR 코드 다운로드
</button>
```

#### 3. 대량 공유 기능
여러 참가자에게 순차적으로 공유:
```javascript
shareBulkQrCodes()  // 구현 예정
```

---

## ✅ 테스트 완료 후

모든 테스트가 완료되면:

1. ✅ 테스트 결과를 이 문서에 기록
2. ✅ 발견된 버그를 이슈로 등록
3. ✅ 사용자 매뉴얼 업데이트
4. ✅ 프로덕션 배포 준비

---

## 🎉 성공!

모든 것이 정상 작동하면:

**기존 방식 (중앙 발송) + 새 방식 (분산 발송)** 두 가지 옵션을 모두 사용할 수 있습니다!

사용자가 상황에 따라 적절한 방법을 선택할 수 있어 더욱 유연한 시스템이 되었습니다. 🚀

---

**작성일:** 2025-10-11
**버전:** 1.0.0
**문서 작성자:** Claude Code
