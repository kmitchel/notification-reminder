# 🔔 Notification Reminders for Google Pixel & Android

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A native Android application built with Kotlin and Jetpack Compose that brings Samsung One UI's **Notification Reminders** functionality to Google Pixel devices (including Pixel 10 XL) and stock Android phones.

---

## 🌟 Key Features

- 🔁 **Periodic Alert Reminders**: Set recurring chimes and vibration alerts every **1, 2, 5, 10, or 15 minutes** for unread notifications.
- 📱 **Dynamic Installed App Discovery**: Lists only the apps actually installed on your phone. Toggle reminders per-app (Google Messages, WhatsApp, Phone/Missed Calls, Gmail, etc.).
- 🌙 **System Dark Mode**: Automatic Material 3 dynamic color scheme adapting seamlessly to your device's light or dark mode setting.
- 👁️ **Live Active Reminders Dashboard**: Displays a real-time card showing exactly which unread notifications are actively being reminded (App name, title/sender, text snippet, and timestamp).
- 🛑 **Instant Stop / Clear Action**: Dedicated **[Clear All]** button in the app and a **"Stop Reminders"** action on the persistent notification to instantly halt alarms without rebooting.
- 🔋 **Screen-Off & Doze Mode Protection**: Uses a persistent Foreground Service, CPU `WakeLock`s, and `SCHEDULE_EXACT_ALARM` to guarantee reliable alerts even when your screen is locked in deep sleep for hours.

---

## 🛠️ Architecture

* **`AppNotificationListenerService`**: Subscribes to Android's `NotificationListenerService` to observe system notifications, filter out ongoing/system tasks, and publish active unread items.
* **`ReminderAlarmReceiver`**: `BroadcastReceiver` invoked by `AlarmManager` to trigger audio/haptic feedback and enforce quiet hours (10:00 PM – 7:00 AM).
* **`TrackedNotificationRepository`**: Shared reactive `StateFlow` store exposing live active notification states to the Compose UI.
* **`PreferencesRepository`**: Persists user settings, interval preferences, and per-app toggles via Jetpack DataStore.

---

## 🚀 Building & Installation via ADB

### Prerequisites
* JDK 17 or higher
* Android SDK (API 35 target, API 26 minimum)

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
*(The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`)*

### 2. Install on Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Grant Notification Access via ADB
```bash
adb shell cmd notification allow_listener com.example.notificationreminder/.service.AppNotificationListenerService
```

### 4. Optional: Battery Optimization Whitelist
```bash
adb shell dumpsys deviceidle whitelist +com.example.notificationreminder
adb shell appops set com.example.notificationreminder SCHEDULE_EXACT_ALARM allow
```

---

## 📁 Project Structure

```
NotificationReminderApp/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/notificationreminder/
│           ├── data/
│           │   ├── PreferencesRepository.kt
│           │   └── TrackedNotificationRepository.kt
│           ├── service/
│           │   ├── AppNotificationListenerService.kt
│           │   └── ReminderAlarmReceiver.kt
│           └── ui/
│               ├── MainActivity.kt
│               ├── screens/MainScreen.kt
│               └── theme/Theme.kt
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── .gitignore
├── LICENSE
└── README.md
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
