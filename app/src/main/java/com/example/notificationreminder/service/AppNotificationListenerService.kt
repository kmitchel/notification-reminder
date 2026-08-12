package com.example.notificationreminder.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.data.TrackedNotificationItem
import com.example.notificationreminder.data.TrackedNotificationRepository
import com.example.notificationreminder.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: PreferencesRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = PreferencesRepository(applicationContext)
        Log.d(TAG, "Notification Listener Service created.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        startForegroundNotification(emptyList())
        observePreferences()
    }

    private fun startForegroundNotification(trackedItems: List<TrackedNotificationItem>) {
        val channelId = "reminder_service_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notification Reminder Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps notification reminders active when screen is off"
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val clearIntent = PendingIntent.getBroadcast(
            this,
            2002,
            Intent(this, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_CLEAR_ALL_REMINDERS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText: String
        val contentText: String

        if (trackedItems.isEmpty()) {
            titleText = "Notification Reminders Active"
            contentText = "No active unread alerts"
        } else {
            val appNames = trackedItems.map { it.appName }.distinct().joinToString(", ")
            titleText = "Reminding (${trackedItems.size} unread)"
            contentText = "Active for: $appNames"
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (trackedItems.isNotEmpty()) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Reminders",
                clearIntent
            )
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observePreferences() {
        scope.launch {
            repository.enabledAppsFlow.collect { enabledApps ->
                syncActiveNotifications(enabledApps)
            }
        }
    }

    fun syncActiveNotifications(enabledApps: Set<String>? = null) {
        try {
            val activeNotifs = activeNotifications ?: run {
                TrackedNotificationRepository.clearAll()
                cancelReminder()
                return
            }

            scope.launch {
                val targetEnabledApps = enabledApps ?: repository.enabledAppsFlow.first()
                val pm = packageManager
                val trackedList = mutableListOf<TrackedNotificationItem>()

                for (sbn in activeNotifs) {
                    val pkg = sbn.packageName ?: continue
                    if (pkg == packageName) continue

                    // Ignore ongoing/foreground system notifications or group summaries that aren't user messages
                    val flags = sbn.notification?.flags ?: 0
                    val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                            (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
                            (flags and Notification.FLAG_GROUP_SUMMARY) != 0
                    if (isOngoing) continue

                    if (targetEnabledApps.contains(pkg)) {
                        val extras = sbn.notification?.extras
                        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Notification"
                        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        val appLabel = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (e: Exception) {
                            pkg
                        }

                        trackedList.add(
                            TrackedNotificationItem(
                                key = sbn.key,
                                packageName = pkg,
                                appName = appLabel,
                                title = title,
                                text = text,
                                postTime = sbn.postTime
                            )
                        )
                    }
                }

                Log.d(TAG, "Synced active notifications. Tracked count: ${trackedList.size}")
                TrackedNotificationRepository.updateItems(trackedList)
                startForegroundNotification(trackedList)

                if (trackedList.isNotEmpty()) {
                    scheduleNextReminder()
                } else {
                    cancelReminder()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync active notifications", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        scope.launch {
            val enabledApps = repository.enabledAppsFlow.first()
            if (enabledApps.contains(pkg)) {
                syncActiveNotifications(enabledApps)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        scope.launch {
            val enabledApps = repository.enabledAppsFlow.first()
            syncActiveNotifications(enabledApps)
        }
    }

    private suspend fun scheduleNextReminder() {
        val intervalMinutes = repository.repeatIntervalFlow.first()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REPEAT_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            REMINDER_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = SystemClock.elapsedRealtime() + (intervalMinutes * 60 * 1000L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
        Log.d(TAG, "Scheduled exact reminder alarm in $intervalMinutes minutes.")
    }

    fun cancelReminder() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REPEAT_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            REMINDER_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        TrackedNotificationRepository.clearAll()
        startForegroundNotification(emptyList())
        Log.d(TAG, "Cancelled notification reminder alarm.")
    }

    companion object {
        private const val TAG = "AppNotifListener"
        const val REMINDER_REQ_CODE = 1001
        private const val NOTIFICATION_ID = 9001
        var instance: AppNotificationListenerService? = null
    }
}
