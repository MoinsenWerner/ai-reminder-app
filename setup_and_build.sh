#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
GRADLE_VERSION="8.7"
GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
COMMAND_LINE_TOOLS_BUILD="13114758"

java_major() {
  "$1" -version 2>&1 | awk -F'[".]' '/version/ { print ($2 == 1 ? $3 : $2); exit }'
}

find_java_17() {
  local candidate
  while IFS= read -r candidate; do
    if [ -x "$candidate" ] && [ "$(java_major "$candidate")" = "17" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done < <(find /usr/lib/jvm -type f -path '*/bin/java' -print 2>/dev/null | sort)
  return 1
}

install_packages() {
  if [ "$(id -u)" -eq 0 ]; then
    apt-get update
    apt-get install -y "$@"
  else
    sudo apt-get update
    sudo apt-get install -y "$@"
  fi
}

select_supported_java() {
  local java_bin=""
  java_bin="$(find_java_17 || true)"
  if [ -z "$java_bin" ]; then
    install_packages openjdk-17-jdk-headless
    java_bin="$(find_java_17 || true)"
  fi
  if [ -z "$java_bin" ]; then
    printf 'Fehler: Java 17 wurde installiert, konnte aber unter /usr/lib/jvm nicht gefunden werden.\n' >&2
    return 1
  fi
  export JAVA_HOME
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$java_bin")")")"
  export PATH="$JAVA_HOME/bin:$PATH"
  printf 'Using Java: %s\n' "$(java -version 2>&1 | head -n 1)"
}

ensure_gradle_wrapper_jar() {
  if [ -f "$WRAPPER_JAR" ]; then return 0; fi
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' RETURN
  curl -fsSL "$GRADLE_ZIP_URL" -o "$tmp_dir/gradle.zip"
  unzip -q "$tmp_dir/gradle.zip" -d "$tmp_dir"
  "$tmp_dir/gradle-${GRADLE_VERSION}/bin/gradle" --no-daemon wrapper --gradle-version "$GRADLE_VERSION"
}


for command in curl unzip python3; do
  if ! command -v "$command" >/dev/null; then
    install_packages curl unzip python3
    break
  fi
done
select_supported_java
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
