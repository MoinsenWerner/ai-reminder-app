#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
GRADLE_VERSION="8.7"
GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

run_as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

find_jdk17_home() {
  for candidate in \
    "${JAVA_HOME:-}" \
    /usr/lib/jvm/java-17-openjdk-* \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/temurin-17-jdk-* \
    /usr/lib/jvm/zulu-17-*; do
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      "$candidate/bin/java" -version 2>&1 | grep -q 'version "17\.' && {
        printf '%s\n' "$candidate"
        return 0
      }
    fi
  done
  return 1
}

ensure_java17() {
  if ! JDK17_HOME="$(find_jdk17_home)"; then
    run_as_root apt-get update
    run_as_root apt-get install -y openjdk-17-jdk
    JDK17_HOME="$(find_jdk17_home)"
  fi
  export JAVA_HOME="$JDK17_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
  printf 'Using Java: '
  java -version 2>&1 | head -n 1
}

ensure_base_tools() {
  missing=()
  for tool in curl unzip python3; do
    command -v "$tool" >/dev/null || missing+=("$tool")
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    run_as_root apt-get update
    run_as_root apt-get install -y "${missing[@]}"
  fi
}

ensure_gradle_wrapper_jar() {
  if [ -f "$WRAPPER_JAR" ]; then return 0; fi
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN
  curl -fsSL "$GRADLE_ZIP_URL" -o "$tmp_dir/gradle.zip"
  unzip -q "$tmp_dir/gradle.zip" -d "$tmp_dir"
  "$tmp_dir/gradle-${GRADLE_VERSION}/bin/gradle" --no-daemon wrapper --gradle-version "$GRADLE_VERSION"
}

ensure_java17
ensure_base_tools
if [ ! -d "${ANDROID_HOME:-$HOME/android-sdk}" ]; then
  SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
  mkdir -p "$SDK_DIR/cmdline-tools"
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/android-cmdline-tools.zip
  unzip -q /tmp/android-cmdline-tools.zip -d /tmp/android-cmdline-tools
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  mv /tmp/android-cmdline-tools/cmdline-tools/* "$SDK_DIR/cmdline-tools/latest/"
  rm -rf /tmp/android-cmdline-tools /tmp/android-cmdline-tools.zip
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null || true
yes | sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null || true
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
ensure_gradle_wrapper_jar
./scripts/train_model.py
./gradlew --no-daemon test lint assembleDebug
printf '\nAPK: %s\n' "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
