package com.example.notificationreminder.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.data.TrackedNotificationRepository
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CLEAR_ALL_REMINDERS -> clearReminders(context)
            ACTION_REPEAT_REMINDER -> deliverReminder(context)
        }
    }

    private fun clearReminders(context: Context) {
        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PreferencesRepository(applicationContext).setRemindersPaused(true)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to persist paused reminder state", error)
            } finally {
                try {
                    ReminderAlarmScheduler.cancel(applicationContext)
                } catch (error: RuntimeException) {
                    Log.e(TAG, "Unable to cancel the scheduled reminder", error)
                }
                TrackedNotificationRepository.clearAll()
                try {
                    AppNotificationListenerService.instance?.cancelReminder()
                } catch (error: RuntimeException) {
                    Log.e(TAG, "Unable to update the listener service", error)
                }
                pendingResult.finish()
            }
        }
    }

    private fun deliverReminder(context: Context) {
        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        val wakeLock = context.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::Delivery"
        ).apply {
            acquire(WAKE_LOCK_TIMEOUT.toMillis())
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = PreferencesRepository(applicationContext)
                val preferences = repository.preferencesFlow.first()
                if (preferences.remindersPaused) {
                    ReminderAlarmScheduler.cancel(applicationContext)
                    return@launch
                }

                val service = AppNotificationListenerService.instance
                if (service == null) {
                    recoverListenerService(applicationContext)
                    return@launch
                }

                val trackedItems = service.queryTrackedNotifications()
                if (trackedItems == null) {
                    recoverListenerService(applicationContext)
                    return@launch
                }
                TrackedNotificationRepository.updateItems(trackedItems)
                if (trackedItems.isEmpty()) {
                    ReminderAlarmScheduler.cancel(applicationContext)
                    return@launch
                }

                val quietHoursActive = preferences.quietHoursEnabled &&
                    QuietHoursPolicy.contains(
                        currentHour = LocalTime.now().hour,
                        startHour = preferences.quietHoursStart,
                        endHour = preferences.quietHoursEnd
                    )
                if (!quietHoursActive) {
                    if (preferences.soundEnabled) playNotificationChime(applicationContext)
                    if (preferences.vibrateEnabled) triggerVibration(applicationContext)
                }

                ReminderAlarmScheduler.schedule(
                    applicationContext,
                    Duration.ofMinutes(preferences.repeatIntervalMinutes.toLong())
                )
            } catch (error: Exception) {
                Log.e(TAG, "Unable to deliver notification reminder", error)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private fun recoverListenerService(context: Context) {
        if (!NotificationAccess.isGranted(context)) {
            ReminderAlarmScheduler.cancel(context)
            return
        }

        NotificationListenerService.requestRebind(
            ComponentName(context, AppNotificationListenerService::class.java)
        )
        ReminderAlarmScheduler.schedule(context, LISTENER_RETRY_DELAY)
    }

    private fun playNotificationChime(context: Context) {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(context, alertUri)?.play()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to play notification sound", error)
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)
                    .vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
                    .vibrate(effect)
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to vibrate", error)
        }
    }

    companion object {
        const val ACTION_REPEAT_REMINDER =
            "com.example.notificationreminder.ACTION_REPEAT_REMINDER"
        const val ACTION_CLEAR_ALL_REMINDERS =
            "com.example.notificationreminder.ACTION_CLEAR_ALL_REMINDERS"

        private const val TAG = "ReminderAlarmReceiver"
        private val LISTENER_RETRY_DELAY = Duration.ofSeconds(30)
        private val WAKE_LOCK_TIMEOUT = Duration.ofSeconds(10)
    }
}
