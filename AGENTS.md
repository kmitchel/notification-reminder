# Repository guide

Notification Reminder is a Kotlin Android application using Jetpack Compose, Material 3, DataStore, `NotificationListenerService`, and `AlarmManager`.

## Verification

Use JDK 17 and an Android SDK containing API 36 and Build Tools 37.

```bash
./gradlew test lint assembleDebug assembleRelease
```

Release APKs are unsigned unless a local signing configuration is supplied.

## Change constraints

- Preserve Material 3 color tokens and system light/dark theme behavior.
- Keep notification synchronization serialized. Independent snapshots must not update state out of order.
- Query active notifications immediately before delivering an alert.
- Keep exact-alarm access optional and preserve the inexact fallback.
- Do not depend on the process-local listener instance without a rebind or recovery path.
- Treat the persisted paused state as authoritative. A selected app's next posted notification resumes reminders.
- Add focused tests for pure scheduling and quiet-hours policy changes.
- Do not commit signing keys, local SDK paths, APKs, or generated build output.
