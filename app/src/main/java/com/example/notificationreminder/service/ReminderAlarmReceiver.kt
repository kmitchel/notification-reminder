package com.example.notificationreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.data.TrackedNotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CLEAR_ALL_REMINDERS -> {
                Log.d(TAG, "Received ACTION_CLEAR_ALL_REMINDERS. Stopping all active reminders.")
                AppNotificationListenerService.instance?.cancelReminder()
                TrackedNotificationRepository.clearAll()
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val repeatIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                    action = ACTION_REPEAT_REMINDER
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    AppNotificationListenerService.REMINDER_REQ_CODE,
                    repeatIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                return
            }
            ACTION_REPEAT_REMINDER -> {
                handleRepeatReminder(context)
            }
        }
    }

    private fun handleRepeatReminder(context: Context) {
        val service = AppNotificationListenerService.instance
        if (service != null) {
            service.syncActiveNotifications()
        }

        val trackedItems = TrackedNotificationRepository.trackedItems.value
        if (trackedItems.isEmpty()) {
            Log.d(TAG, "No active tracked notifications found during alarm trigger. Suppressing chime and stopping alarm loop.")
            return
        }

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotificationReminder::ChimeWakeLock"
        )
        wakeLock.acquire(5000L)

        val repository = PreferencesRepository(context.applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val quietEnabled = repository.quietHoursEnabledFlow.first()
                if (quietEnabled) {
                    val startHour = repository.quietHoursStartFlow.first()
                    val endHour = repository.quietHoursEndFlow.first()
                    if (isCurrentTimeInQuietHours(startHour, endHour)) {
                        Log.d(TAG, "Skipping reminder chime due to active Quiet Hours ($startHour:00 - $endHour:00).")
                        reschedule(context, repository)
                        return@launch
                    }
                }

                val soundEnabled = repository.soundEnabledFlow.first()
                val vibrateEnabled = repository.vibrateEnabledFlow.first()

                if (soundEnabled) {
                    playNotificationChime(context)
                }

                if (vibrateEnabled) {
                    triggerVibration(context)
                }

                reschedule(context, repository)
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }

    private fun playNotificationChime(context: Context) {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(context, alertUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play notification chime", e)
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val pattern = longArrayOf(0, 300, 200, 300)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibratorManager.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger vibration", e)
        }
    }

    private suspend fun reschedule(context: Context, repository: PreferencesRepository) {
        val intervalMinutes = repository.repeatIntervalFlow.first()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REPEAT_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AppNotificationListenerService.REMINDER_REQ_CODE,
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
    }

    private fun isCurrentTimeInQuietHours(startHour: Int, endHour: Int): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (startHour < endHour) {
            currentHour in startHour until endHour
        } else {
            currentHour >= startHour || currentHour < endHour
        }
    }

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val ACTION_REPEAT_REMINDER = "com.example.notificationreminder.ACTION_REPEAT_REMINDER"
        const val ACTION_CLEAR_ALL_REMINDERS = "com.example.notificationreminder.ACTION_CLEAR_ALL_REMINDERS"
    }
}
