package com.example.notificationreminder.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.service.AppNotificationListenerService
import com.example.notificationreminder.ui.screens.MainScreen
import com.example.notificationreminder.ui.theme.NotificationReminderTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: PreferencesRepository
    private var isNotificationListenerGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = PreferencesRepository(applicationContext)

        setContent {
            NotificationReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        repository = repository,
                        isListenerGranted = isNotificationListenerGranted,
                        onOpenListenerSettings = { openNotificationListenerSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationListenerPermission()
    }

    private fun checkNotificationListenerPermission() {
        val cn = ComponentName(this, AppNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        isNotificationListenerGranted = flat != null && flat.contains(cn.flattenToString())
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
