package com.example.notificationreminder.service

import android.content.Context
import androidx.core.app.NotificationManagerCompat

internal object NotificationAccess {
    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
}
