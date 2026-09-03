package com.example.notificationreminder.service

internal object ReminderSchedulePolicy {
    const val EXACT_ALARM_PERMISSION_API = 31

    fun canUseExactAlarm(sdkInt: Int, hasExactAlarmAccess: Boolean): Boolean =
        sdkInt < EXACT_ALARM_PERMISSION_API || hasExactAlarmAccess
}
