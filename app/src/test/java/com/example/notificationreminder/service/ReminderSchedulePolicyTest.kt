package com.example.notificationreminder.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulePolicyTest {

    @Test
    fun `pre Android 12 devices do not require special access`() {
        assertTrue(
            ReminderSchedulePolicy.canUseExactAlarm(
                sdkInt = ReminderSchedulePolicy.EXACT_ALARM_PERMISSION_API - 1,
                hasExactAlarmAccess = false
            )
        )
    }

    @Test
    fun `Android 12 and newer require exact alarm access`() {
        assertFalse(
            ReminderSchedulePolicy.canUseExactAlarm(
                sdkInt = ReminderSchedulePolicy.EXACT_ALARM_PERMISSION_API,
                hasExactAlarmAccess = false
            )
        )
        assertTrue(
            ReminderSchedulePolicy.canUseExactAlarm(
                sdkInt = ReminderSchedulePolicy.EXACT_ALARM_PERMISSION_API,
                hasExactAlarmAccess = true
            )
        )
    }
}
