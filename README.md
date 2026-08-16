# Jarvis Android Assistant

Native Kotlin Android app (`com.jarvis.assistant`) with dashboard, chat, settings, Tasker broadcast receivers, foreground voice service, Accessibility-based screen observer, local action logging, and a tiny generated local intent model.

## Build on Debian server

```bash
./setup_and_build.sh
```

The script installs Java/Python prerequisites when needed, creates the Gradle wrapper, trains `app/src/main/assets/jarvis_model.json`, runs tests/lint, and builds `app/build/outputs/apk/debug/app-debug.apk`.

## Tasker intents

Send broadcasts to package `com.jarvis.assistant` with actions/classes:
`receiveNewCommand`, `receiveNewReminder`, `receiveNewData`, `receiveNewVideo`, `receiveNewAudio`.
Use extras `command`/`instructions` and, for media, `address` containing `file://...` or `https://...`.

## Privacy and Android limits

Screen reading requires the user to enable the Jarvis Accessibility Service. Background microphone use runs as a visible foreground service. Other-app playback capture depends on Android consent and app capture policy; Jarvis logs requested media addresses and can process accessible files/URLs added by Tasker.
