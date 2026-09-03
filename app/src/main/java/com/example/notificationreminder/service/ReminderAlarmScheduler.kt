package com.example.notificationreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.time.Duration

internal object ReminderAlarmScheduler {
    private const val TAG = "ReminderAlarmScheduler"

    fun schedule(context: Context, delay: Duration) {
        require(!delay.isNegative && !delay.isZero) { "Alarm delay must be positive" }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAtMillis = SystemClock.elapsedRealtime() + delay.toMillis()
        val operation = reminderPendingIntent(context)
        val hasExactAlarmAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (ReminderSchedulePolicy.canUseExactAlarm(Build.VERSION.SDK_INT, hasExactAlarmAccess)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    operation
                )
                return
            } catch (error: SecurityException) {
                Log.w(TAG, "Exact alarm access changed; scheduling an inexact reminder", error)
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            operation
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(reminderPendingIntent(context))
    }

    private fun reminderPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE,
        Intent(context.applicationContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REPEAT_REMINDER
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private const val REQUEST_CODE = 1001
}
