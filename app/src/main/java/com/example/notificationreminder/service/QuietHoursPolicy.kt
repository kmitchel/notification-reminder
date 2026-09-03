package com.example.notificationreminder.service

internal object QuietHoursPolicy {
    fun contains(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        require(currentHour in 0..23 && startHour in 0..23 && endHour in 0..23) {
            "Hours must be between 0 and 23"
        }
        return when {
            startHour < endHour -> currentHour in startHour until endHour
            startHour > endHour -> currentHour >= startHour || currentHour < endHour
            else -> false
        }
    }
}
