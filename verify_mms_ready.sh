#!/bin/bash

echo "📱 MMS 발송 준비 상태 검증"
echo "================================"
echo ""

# 1. 모바일 데이터 상태
echo "📡 1. 모바일 데이터 상태:"
DATA_STATE=$(~/Library/Android/sdk/platform-tools/adb shell dumpsys telephony.registry | grep "mDataConnectionState" | head -1)
SERVICE_STATE=$(~/Library/Android/sdk/platform-tools/adb shell dumpsys telephony.registry | grep "mVoiceRegState" | head -1)

echo "$DATA_STATE"
echo "$SERVICE_STATE"

if echo "$DATA_STATE" | grep -q "\-1\|OUT_OF_SERVICE"; then
    echo "❌ 모바일 데이터 연결 안 됨"
    echo "   → 설정 > 연결 > 모바일 데이터 ON"
else
    echo "✅ 모바일 데이터 정상"
fi
echo ""

# 2. 기본 SMS 앱 확인
echo "📧 2. 기본 메시지 앱:"
DEFAULT_SMS=$(~/Library/Android/sdk/platform-tools/adb shell settings get secure sms_default_application)
echo "   현재: $DEFAULT_SMS"

if [ -z "$DEFAULT_SMS" ] || [ "$DEFAULT_SMS" = "null" ]; then
    echo "❌ 기본 메시지 앱 미설정"
    echo "   → 설정 > 앱 > 기본 앱 > 메시지 앱 선택"
else
    echo "✅ 기본 메시지 앱 설정됨"
fi
echo ""

# 3. WiFi 상태
echo "📶 3. WiFi 상태:"
WIFI_STATE=$(~/Library/Android/sdk/platform-tools/adb shell dumpsys wifi | grep "Wi-Fi is" | head -1)
echo "   $WIFI_STATE"
echo ""

# 4. 비행기 모드
echo "✈️ 4. 비행기 모드:"
AIRPLANE_MODE=$(~/Library/Android/sdk/platform-tools/adb shell settings get global airplane_mode_on)
if [ "$AIRPLANE_MODE" = "1" ]; then
    echo "❌ 비행기 모드 켜짐"
    echo "   → 비행기 모드를 꺼주세요"
else
    echo "✅ 비행기 모드 꺼짐"
fi
echo ""

echo "================================"
echo "📋 요약:"
echo ""

READY=true

if echo "$DATA_STATE" | grep -q "\-1\|OUT_OF_SERVICE"; then
    READY=false
    echo "❌ 모바일 데이터를 활성화해주세요"
fi

if [ -z "$DEFAULT_SMS" ] || [ "$DEFAULT_SMS" = "null" ]; then
    READY=false
    echo "❌ 기본 메시지 앱을 설정해주세요"
fi

if [ "$AIRPLANE_MODE" = "1" ]; then
    READY=false
    echo "❌ 비행기 모드를 꺼주세요"
fi

echo ""
if [ "$READY" = true ]; then
    echo "✅ MMS 발송 준비 완료!"
    echo "   → 이제 웹에서 QR 코드를 발송할 수 있습니다"
else
    echo "⚠️ 위의 문제를 해결한 후 다시 확인하세요"
    echo "   → 이 스크립트를 다시 실행: ./verify_mms_ready.sh"
fi
