package com.example.notificationreminder.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackedNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

object TrackedNotificationRepository {
    private val _trackedItems = MutableStateFlow<List<TrackedNotificationItem>>(emptyList())
    val trackedItems: StateFlow<List<TrackedNotificationItem>> = _trackedItems.asStateFlow()

    fun updateItems(items: List<TrackedNotificationItem>) {
        _trackedItems.value = items
    }

    fun clearAll() {
        _trackedItems.value = emptyList()
    }
}
