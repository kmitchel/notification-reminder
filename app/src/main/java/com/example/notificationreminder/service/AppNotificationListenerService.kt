package com.example.notificationreminder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.data.TrackedNotificationItem
import com.example.notificationreminder.data.TrackedNotificationRepository
import com.example.notificationreminder.ui.MainActivity
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private lateinit var preferencesRepository: PreferencesRepository
    private var preferencesJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesRepository = PreferencesRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onDestroy() {
        instance = null
        preferencesJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateForegroundNotification(emptyList())
        observeSchedulingPreferences()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(componentName)
    }

    fun requestSync() {
        scope.launch { synchronizeNotifications() }
    }

    suspend fun queryTrackedNotifications(): List<TrackedNotificationItem>? = syncMutex.withLock {
        val preferences = preferencesRepository.preferencesFlow.first()
        if (preferences.remindersPaused) return@withLock emptyList()
        queryActiveNotifications()?.let { buildTrackedList(it, preferences.enabledApps) }
    }

    fun cancelReminder() {
        ReminderAlarmScheduler.cancel(applicationContext)
        TrackedNotificationRepository.clearAll()
        updateForegroundNotification(emptyList())
    }

    private fun observeSchedulingPreferences() {
        preferencesJob?.cancel()
        preferencesJob = scope.launch {
            preferencesRepository.preferencesFlow
                .map { preferences ->
                    SchedulingPreferences(
                        enabledApps = preferences.enabledApps,
                        repeatIntervalMinutes = preferences.repeatIntervalMinutes,
                        remindersPaused = preferences.remindersPaused
                    )
                }
                .distinctUntilChanged()
                .collect { synchronizeNotifications() }
        }
    }

    private suspend fun synchronizeNotifications() = syncMutex.withLock {
        val preferences = preferencesRepository.preferencesFlow.first()
        val trackedItems = when {
            preferences.remindersPaused -> emptyList()
            else -> queryActiveNotifications()?.let {
                buildTrackedList(it, preferences.enabledApps)
            } ?: return@withLock
        }

        TrackedNotificationRepository.updateItems(trackedItems)
        updateForegroundNotification(trackedItems)

        if (trackedItems.isEmpty()) {
            ReminderAlarmScheduler.cancel(applicationContext)
        } else {
            ReminderAlarmScheduler.schedule(
                applicationContext,
                Duration.ofMinutes(preferences.repeatIntervalMinutes.toLong())
            )
        }
    }

    private fun queryActiveNotifications(): Array<StatusBarNotification>? = try {
        activeNotifications
    } catch (error: RuntimeException) {
        Log.e(TAG, "Unable to query active notifications", error)
        null
    }

    private fun buildTrackedList(
        activeNotifications: Array<StatusBarNotification>,
        enabledApps: Set<String>
    ): List<TrackedNotificationItem> = activeNotifications.mapNotNull { notification ->
        val packageName = notification.packageName ?: return@mapNotNull null
        if (packageName == this.packageName || packageName !in enabledApps) {
            return@mapNotNull null
        }

        val flags = notification.notification?.flags ?: 0
        val excludedFlags = Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_GROUP_SUMMARY
        if (flags and excludedFlags != 0) return@mapNotNull null

        val extras = notification.notification?.extras
        TrackedNotificationItem(
            key = notification.key,
            packageName = packageName,
            appName = applicationLabel(packageName),
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Notification",
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postTime = notification.postTime
        )
    }

    private fun applicationLabel(packageName: String): String = try {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (error: PackageManager.NameNotFoundException) {
        packageName
    }

    override fun onNotificationPosted(notification: StatusBarNotification?) {
        val packageName = notification?.packageName ?: return
        if (packageName == this.packageName) return

        scope.launch {
            val preferences = preferencesRepository.preferencesFlow.first()
            if (packageName in preferences.enabledApps) {
                if (preferences.remindersPaused) {
                    preferencesRepository.setRemindersPaused(false)
                } else {
                    synchronizeNotifications()
                }
            }
        }
    }

    override fun onNotificationRemoved(notification: StatusBarNotification?) {
        if (notification != null) requestSync()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Notification Reminder Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the status of notification reminders"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateForegroundNotification(trackedItems: List<TrackedNotificationItem>) {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val clearIntent = PendingIntent.getBroadcast(
            this,
            CLEAR_REQUEST_CODE,
            Intent(this, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_CLEAR_ALL_REMINDERS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                if (trackedItems.isEmpty()) "Notification Reminders Active"
                else "Reminding (${trackedItems.size} unread)"
            )
            .setContentText(
                if (trackedItems.isEmpty()) "No active unread alerts"
                else "Active for: ${trackedItems.map { it.appName }.distinct().joinToString()}"
            )
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (trackedItems.isNotEmpty()) {
                    addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop Reminders",
                        clearIntent
                    )
                }
            }
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private val componentName
        get() = android.content.ComponentName(this, AppNotificationListenerService::class.java)

    private data class SchedulingPreferences(
        val enabledApps: Set<String>,
        val repeatIntervalMinutes: Int,
        val remindersPaused: Boolean
    )

    companion object {
        private const val TAG = "AppNotifListener"
        private const val CHANNEL_ID = "reminder_service_channel"
        private const val CLEAR_REQUEST_CODE = 2002
        private const val FOREGROUND_NOTIFICATION_ID = 9001

        @Volatile
        var instance: AppNotificationListenerService? = null
            private set
    }
}
