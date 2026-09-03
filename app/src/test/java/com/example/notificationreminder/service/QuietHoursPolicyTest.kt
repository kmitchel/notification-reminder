package com.example.notificationreminder.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursPolicyTest {

    @Test
    fun `overnight range includes start and excludes end`() {
        assertTrue(QuietHoursPolicy.contains(currentHour = 22, startHour = 22, endHour = 7))
        assertTrue(QuietHoursPolicy.contains(currentHour = 0, startHour = 22, endHour = 7))
        assertTrue(QuietHoursPolicy.contains(currentHour = 6, startHour = 22, endHour = 7))
        assertFalse(QuietHoursPolicy.contains(currentHour = 7, startHour = 22, endHour = 7))
        assertFalse(QuietHoursPolicy.contains(currentHour = 21, startHour = 22, endHour = 7))
    }

    @Test
    fun `daytime range includes start and excludes end`() {
        assertFalse(QuietHoursPolicy.contains(currentHour = 12, startHour = 13, endHour = 16))
        assertTrue(QuietHoursPolicy.contains(currentHour = 13, startHour = 13, endHour = 16))
        assertTrue(QuietHoursPolicy.contains(currentHour = 15, startHour = 13, endHour = 16))
        assertFalse(QuietHoursPolicy.contains(currentHour = 16, startHour = 13, endHour = 16))
    }

    @Test
    fun `equal bounds disable quiet hours`() {
        assertFalse(QuietHoursPolicy.contains(currentHour = 10, startHour = 10, endHour = 10))
    }

    @Test
    fun `invalid hours are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            QuietHoursPolicy.contains(currentHour = 24, startHour = 22, endHour = 7)
        }
        assertThrows(IllegalArgumentException::class.java) {
            QuietHoursPolicy.contains(currentHour = 12, startHour = -1, endHour = 7)
        }
    }
}
