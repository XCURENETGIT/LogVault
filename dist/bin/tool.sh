#!/usr/bin/env bash
# Usage: ./tool.sh encrypt xcurenet1
# Notes:
#  - JDK, WAR, MAIN_CLASS 값은 필요 시 수정하세요.

set -o pipefail

JAR="/users/jdk-21/bin/jar"
JAVA="/users/jdk-21/bin/java"
WAR="LogVault-0.0.1.war"
MAIN_CLASS="com.xcurenet.logvault.tool.encrypt.EncryptTool"

error() { echo "$*" >&2; exit 1; }
info()  { echo "$*"; }
ok()    { echo "$*"; }

# 1) 인자 체크
if [ $# -lt 1 ]; then
  cat <<EOF
Usage: $(basename "$0") <args...>
  ex) $(basename "$0") encrypt xcurenet1
EOF
  exit 2
fi

# 2) 기본 환경 체크
[ -x "$JAVA" ] || error "JAVA 실행 파일 없음: $JAVA"
[ -f "$WAR" ]  || error "WAR 파일 없음: $WAR"

# 3) WAR 구조 판별 (BOOT-INF vs WEB-INF)
if "$JAVA" -jar "$JAVA_HOME_DOES_NOT_EXIST" >/dev/null 2>&1; then :; fi  # no-op to keep shellcheck happy

if "$JAVA" -jar "$JAVA" >/dev/null 2>&1; then :; fi  # dummy to avoid false positives; harmless

if "$JAR" tf "$WAR" | grep -q '^BOOT-INF/classes/'; then
  LOADER_PATH="BOOT-INF/classes,BOOT-INF/lib"
  STRUCT="BOOT-INF"
elif "$JAR" tf "$WAR" | grep -q '^WEB-INF/classes/'; then
  LOADER_PATH="WEB-INF/classes,WEB-INF/lib"
  STRUCT="WEB-INF"
else
  error "WAR 내부에서 BOOT-INF/classes 또는 WEB-INF/classes 를 찾을 수 없습니다."
fi

# 4) 실행 클래스 존재 확인
CLASS_PATH_IN_WAR="${MAIN_CLASS//.//}.class"
if ! "$JAR" tf "$WAR" | grep -q "$CLASS_PATH_IN_WAR"; then
  info "경고: WAR 내부에서 ${CLASS_PATH_IN_WAR} 파일을 찾지 못했습니다."
  info "  - 패키지/클래스명이 맞는지 확인하세요: $MAIN_CLASS"
  info "  - 멀티모듈/의존성 scope(provided)로 빠지지 않았는지 확인하세요."
fi

# 5) 실행
CMD=(
  "$JAVA"
  -cp "$WAR"
  "-Dloader.path=${LOADER_PATH}"
  "-Dloader.main=${MAIN_CLASS}"
  org.springframework.boot.loader.launch.PropertiesLauncher
  "$@"
)

"${CMD[@]}"
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
  error "프로그램이 비정상 종료되었습니다. (exit=$EXIT_CODE)"
fi
