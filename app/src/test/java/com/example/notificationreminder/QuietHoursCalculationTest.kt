package com.example.notificationreminder

import com.example.notificationreminder.service.ReminderAlarmReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursCalculationTest {

    @Test
    fun `test overnight quiet hours window - 22 to 7`() {
        val start = 22
        val end = 7

        // Inside quiet hours (overnight)
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(22, start, end))
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(23, start, end))
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(0, start, end))
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(3, start, end))
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(6, start, end))

        // Outside quiet hours
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(7, start, end))
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(8, start, end))
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(12, start, end))
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(21, start, end))
    }

    @Test
    fun `test daytime quiet hours window - 13 to 16`() {
        val start = 13
        val end = 16

        // Inside
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(13, start, end))
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(14, start, end))
        assertTrue(ReminderAlarmReceiver.isHourInQuietRange(15, start, end))

        // Outside
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(12, start, end))
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(16, start, end))
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(22, start, end))
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(0, start, end))
    }

    @Test
    fun `test equal start and end hours returns false`() {
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(10, 10, 10))
        assertFalse(ReminderAlarmReceiver.isHourInQuietRange(15, 10, 10))
    }
}
