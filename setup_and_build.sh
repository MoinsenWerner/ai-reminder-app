#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
GRADLE_VERSION="8.7"
GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
COMMAND_LINE_TOOLS_BUILD="13114758"

ensure_gradle_wrapper_jar() {
  if [ -f "$WRAPPER_JAR" ]; then return 0; fi
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN
  curl -fsSL "$GRADLE_ZIP_URL" -o "$tmp_dir/gradle.zip"
  unzip -q "$tmp_dir/gradle.zip" -d "$tmp_dir"
  "$tmp_dir/gradle-${GRADLE_VERSION}/bin/gradle" --no-daemon wrapper --gradle-version "$GRADLE_VERSION"
}


if ! command -v java >/dev/null || ! java -version 2>&1 | grep -q '17\|21'; then
  sudo apt-get update
  sudo apt-get install -y openjdk-17-jdk unzip curl python3
fi
SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_MARKER="$SDK_DIR/cmdline-tools/latest/.jarvis-build"
if [ ! -f "$TOOLS_MARKER" ] || [ "$(cat "$TOOLS_MARKER")" != "$COMMAND_LINE_TOOLS_BUILD" ]; then
  mkdir -p "$SDK_DIR/cmdline-tools"
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${COMMAND_LINE_TOOLS_BUILD}_latest.zip" -o /tmp/android-cmdline-tools.zip
  unzip -q /tmp/android-cmdline-tools.zip -d /tmp/android-cmdline-tools
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  mv /tmp/android-cmdline-tools/cmdline-tools/* "$SDK_DIR/cmdline-tools/latest/"
  printf '%s\n' "$COMMAND_LINE_TOOLS_BUILD" > "$TOOLS_MARKER"
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
