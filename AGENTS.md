# AGENTS.md - Developer & AI Agent Context Guide

This document provides architectural context, project guidelines, and build instructions for AI agents and developer contributors working on the **Notification Reminder** repository.

---

## 📌 Project Overview

**Notification Reminder** is a native Android application written in **Kotlin** using **Jetpack Compose (Material 3)**. It duplicates Samsung One UI's "Notification Reminders" feature on Google Pixel devices (including Pixel 10 XL) and stock Android phones.

### Core Value Proposition
Stock Android does not natively support periodic repeating sound/vibration chimes for unread notifications. This app intercepts system notifications via `NotificationListenerService`, maintains an active unread queue, and triggers periodic audio/haptic alerts until the notification is dismissed.

---

## 🏗️ Architecture & Component Hierarchy

```
app/src/main/java/com/example/notificationreminder/
├── data/
│   ├── PreferencesRepository.kt      # Jetpack DataStore preferences (intervals, quiet hours, app toggles)
│   └── TrackedNotificationRepository.kt # Reactive StateFlow holding currently tracked unread notifications
├── service/
│   ├── AppNotificationListenerService.kt # Foreground service observing system notification events
│   └── ReminderAlarmReceiver.kt         # BroadcastReceiver triggered by AlarmManager for chimes & haptics
└── ui/
    ├── MainActivity.kt               # Main Activity checking permissions & setting up Compose UI
    ├── screens/MainScreen.kt          # Primary Compose screen with live active reminder cards & app picker
    └── theme/Theme.kt                 # Material 3 theme supporting system Light & Dark Mode
```

### Key Technical Mechanisms

1. **`AppNotificationListenerService`**:
   - Inherits from Android's `NotificationListenerService`.
   - Runs as a **Foreground Service** (`android:foregroundServiceType="specialUse"`) to prevent Android Doze Mode from killing the background process.
   - Filters out ongoing system tasks (`FLAG_ONGOING_EVENT`, `FLAG_FOREGROUND_SERVICE`, `FLAG_GROUP_SUMMARY`) to prevent ghost alarms.
   - Reactively listens to `PreferencesRepository.enabledAppsFlow` to immediately sync sitting unread notifications when an app is toggled ON.

2. **`ReminderAlarmReceiver`**:
   - Invoked by `AlarmManager.setExactAndAllowWhileIdle()`.
   - Acquires a 5-second CPU `PowerManager.PARTIAL_WAKE_LOCK` to ensure audio chimes play even when the display is locked in deep sleep.
   - Re-queries active notifications before playing chimes to suppress phantom alerts.
   - Respects scheduled quiet hours (default: 10:00 PM – 7:00 AM).

3. **Jetpack Compose UI & Theme**:
   - Utilizes Material 3 `dynamicLightColorScheme` / `dynamicDarkColorScheme` (Android 12+) and custom fallback palettes.
   - Adapts seamlessly to system Dark Mode settings without hardcoding theme colors.

---

## 🛠️ Build & Verification Commands

### Build Debug APK
```bash
./gradlew assembleDebug
```
*Output: `app/build/outputs/apk/debug/app-debug.apk`*

### Build Release APK
```bash
./gradlew assembleRelease
```
*Output: `app/build/outputs/apk/release/app-release.apk`*

### ADB Deployment & Authorization Commands
```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant Notification Access Permission directly via ADB
adb shell cmd notification allow_listener com.example.notificationreminder/.service.AppNotificationListenerService

# Whitelist from Doze Mode Battery Optimization
adb shell dumpsys deviceidle whitelist +com.example.notificationreminder

# Grant Exact Alarm Permission (Android 12+)
adb shell appops set com.example.notificationreminder SCHEDULE_EXACT_ALARM allow

# Launch Main Activity
adb shell am start -n com.example.notificationreminder/.ui.MainActivity
```

---

## 📋 Coding Guidelines & Rules for AI Agents

1. **Material 3 Theme Consistency**:
   - Never hardcode colors like `Color.White` or `Color.Black` directly in UI components. Always use `MaterialTheme.colorScheme` tokens or `isSystemInDarkTheme()` checks to preserve Dark Mode compatibility.

2. **Background Execution Safety**:
   - Do not remove the `PowerManager.PARTIAL_WAKE_LOCK` from `ReminderAlarmReceiver` or change `setExactAndAllowWhileIdle` to standard alarms. Doing so will break interval chimes when the screen is locked.

3. **Notification Filtering**:
   - When updating `AppNotificationListenerService`, preserve the notification flag filtering (`FLAG_ONGOING_EVENT`, `FLAG_FOREGROUND_SERVICE`, `FLAG_GROUP_SUMMARY`) to prevent non-user system notifications from triggering reminders.

4. **Reactive State Pattern**:
   - Keep state synchronization flowing through `TrackedNotificationRepository` and `PreferencesRepository`. Avoid passing mutable state variables across component boundaries.
