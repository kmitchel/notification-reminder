# Notification Reminder

Notification Reminder is an Android application that repeats sound or vibration alerts while selected apps have active notifications. It is intended for Android devices that do not provide this behavior as a system feature.

## Features

- Monitors notifications from user-selected apps.
- Repeats alerts at 1, 2, 5, 10, or 15-minute intervals.
- Supports sound, vibration, and overnight quiet hours.
- Shows the notifications currently eligible for reminders.
- Pauses existing reminders until a selected app posts another notification.
- Uses exact alarms when Android grants access and falls back to inexact alarms otherwise.

Android can defer alarms while conserving battery. Granting the optional **Alarms & reminders** access improves timing but does not override platform rate limits.

## Requirements

- Android 8.0 (API 26) or newer
- JDK 17
- Android SDK with API 36 and Build Tools 37

The app requires Notification Access to inspect active notifications. On Android 12 and newer, it also offers a shortcut to the optional exact-alarm settings page. Notification content remains on the device.

## Build

```bash
./gradlew test lint assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Release builds are intentionally unsigned. Supply a private signing configuration outside source control before distribution:

```bash
./gradlew assembleRelease
```

## Install for development

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd notification allow_listener \
  com.example.notificationreminder/.service.AppNotificationListenerService
```

Notification Access can also be granted from Android Settings.

## Design

- `AppNotificationListenerService` serializes notification snapshots and maintains the foreground status notification.
- `ReminderAlarmScheduler` owns alarm creation, cancellation, and exact-to-inexact fallback behavior.
- `ReminderAlarmReceiver` validates current notifications immediately before delivering an alert.
- `PreferencesRepository` exposes one typed DataStore preferences stream.
- `TrackedNotificationRepository` publishes the current in-process notification view to the UI.

## License

[MIT](LICENSE)
