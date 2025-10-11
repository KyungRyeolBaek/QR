#!/bin/bash

# SSL 인증서 생성 스크립트
# 안드로이드 앱의 HTTPS 웹 서버용

echo "🔐 SSL 인증서 생성 시작..."
echo ""

# 설정
KEYSTORE_FILE="app/src/main/res/raw/keystore.p12"
ALIAS="qr_server"
KEYPASS="qrserver123"
STOREPASS="qrserver123"
VALIDITY=3650  # 10년
KEYSIZE=2048

# raw 디렉토리 생성
mkdir -p app/src/main/res/raw

# 기존 keystore 삭제 (있다면)
if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  기존 keystore 파일 발견. 삭제합니다..."
    rm "$KEYSTORE_FILE"
fi

# SSL 인증서 생성
echo "📝 인증서 정보 입력..."
echo ""
echo "다음 정보를 입력하세요 (또는 Enter로 기본값 사용):"
echo "- 이름과 성 (First and Last Name): QR Server"
echo "- 조직 단위 (Organizational Unit): Development"
echo "- 조직 이름 (Organization): Your Organization"
echo "- 도시/지역 (City or Locality): Seoul"
echo "- 시/도 (State or Province): Seoul"
echo "- 국가 코드 (Country Code): KR"
echo ""

keytool -genkey -v \
  -keystore "$KEYSTORE_FILE" \
  -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize $KEYSIZE \
  -validity $VALIDITY \
  -storepass "$STOREPASS" \
  -keypass "$KEYPASS" \
  -dname "CN=QR Server, OU=Development, O=QR App, L=Seoul, ST=Seoul, C=KR"

# 생성 결과 확인
if [ -f "$KEYSTORE_FILE" ]; then
    echo ""
    echo "✅ SSL 인증서 생성 완료!"
    echo ""
    echo "📁 파일 위치: $KEYSTORE_FILE"
    echo "📏 파일 크기: $(du -h $KEYSTORE_FILE | cut -f1)"
    echo ""
    echo "🔑 인증서 정보:"
    echo "   - Alias: $ALIAS"
    echo "   - Password: $KEYPASS"
    echo "   - 유효기간: $VALIDITY일 (약 10년)"
    echo ""
    echo "📋 인증서 상세 정보 확인:"
    keytool -list -v -keystore "$KEYSTORE_FILE" -storetype PKCS12 -storepass "$STOREPASS" -alias "$ALIAS"
    echo ""
    echo "🎯 다음 단계:"
    echo "   1. Android Studio에서 앱 빌드"
    echo "   2. 웹 서버 시작 (HTTPS 모드)"
    echo "   3. https://192.168.x.x:8443 접속"
    echo "   4. 브라우저에서 인증서 경고 수락"
    echo ""
    echo "⚠️  주의: 자체 서명 인증서는 브라우저에서 경고가 표시됩니다."
    echo "   iOS Safari: 설정 > 일반 > 정보 > 인증서 신뢰 설정"
    echo "   Android Chrome: '고급' > '안전하지 않음(계속)' 클릭"
    echo ""
else
    echo ""
    echo "❌ SSL 인증서 생성 실패!"
    echo ""
    echo "문제 해결:"
    echo "   1. Java keytool이 설치되어 있는지 확인"
    echo "   2. 디렉토리 권한 확인"
    echo "   3. 명령어를 수동으로 실행:"
    echo ""
    echo "keytool -genkey -keystore $KEYSTORE_FILE -alias $ALIAS \\"
    echo "  -keyalg RSA -keysize $KEYSIZE -validity $VALIDITY \\"
    echo "  -storepass $STOREPASS -keypass $KEYPASS"
    echo ""
    exit 1
fi
