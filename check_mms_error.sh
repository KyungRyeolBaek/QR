#!/bin/bash

# QR 코드 발송 실패 원인 진단 스크립트

echo "📱 QR 코드 발송 실패 원인 진단 중..."
echo ""

# 1. 앱 로그 필터링 (MMS 관련)
echo "🔍 1. MMS 검증 로그 확인:"
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "\[검증\]|MMS 서비스|verifyMms" | tail -20
echo ""

# 2. 권한 관련 로그
echo "🔐 2. 권한 관련 로그:"
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "권한|permission|SMS|SEND_SMS" | tail -10
echo ""

# 3. 네트워크 관련 로그
echo "📡 3. 네트워크 관련 로그:"
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "네트워크|Network|WiFi|모바일" | tail -10
echo ""

# 4. 기본 MMS 앱 관련 로그
echo "📧 4. 기본 MMS 앱 관련 로그:"
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "기본 앱|defaultMmsApp|Telephony" | tail -10
echo ""

# 5. 최근 발송 실패 로그
echo "❌ 5. 최근 QR 코드 발송 실패 로그:"
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -E "QR 코드 발송|MMS.*실패|발송.*실패" | tail -15
echo ""

echo "✅ 진단 완료!"
echo ""
echo "💡 해결 방법:"
echo "   1. '기본 앱이 설정되지 않음' 에러가 있다면 → 아래 설정 확인"
echo "   2. '권한 없음' 에러가 있다면 → 앱 설정 > 권한 > SMS 권한 허용"
echo "   3. '네트워크 연결 없음' 에러가 있다면 → WiFi 또는 모바일 데이터 활성화"
echo "   4. 'SIM 카드' 에러가 있다면 → SIM 카드 상태 확인"
