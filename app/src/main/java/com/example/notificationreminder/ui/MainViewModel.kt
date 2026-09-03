package com.example.notificationreminder.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.data.TrackedNotificationItem
import com.example.notificationreminder.data.TrackedNotificationRepository
import com.example.notificationreminder.service.AppNotificationListenerService
import com.example.notificationreminder.service.ReminderAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppSummary(
    val name: String,
    val packageName: String
)

data class MainUiState(
    val enabledApps: Set<String> = emptySet(),
    val repeatIntervalMinutes: Int = 5,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 7,
    val trackedNotifications: List<TrackedNotificationItem> = emptyList(),
    val installedApps: List<InstalledAppSummary> = emptyList(),
    val isLoadingApps: Boolean = true,
    val isListenerPermissionGranted: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PreferencesRepository(application)

    private val _installedApps = MutableStateFlow<List<InstalledAppSummary>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)
    private val _isListenerPermissionGranted = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        repository.enabledAppsFlow,
        repository.repeatIntervalFlow,
        repository.soundEnabledFlow,
        repository.vibrateEnabledFlow,
        repository.quietHoursEnabledFlow,
        repository.quietHoursStartFlow,
        repository.quietHoursEndFlow,
        TrackedNotificationRepository.trackedItems,
        _installedApps,
        _isLoadingApps,
        _isListenerPermissionGranted
    ) { params: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        MainUiState(
            enabledApps = params[0] as Set<String>,
            repeatIntervalMinutes = params[1] as Int,
            soundEnabled = params[2] as Boolean,
            vibrateEnabled = params[3] as Boolean,
            quietHoursEnabled = params[4] as Boolean,
            quietHoursStart = params[5] as Int,
            quietHoursEnd = params[6] as Int,
            trackedNotifications = params[7] as List<TrackedNotificationItem>,
            installedApps = params[8] as List<InstalledAppSummary>,
            isLoadingApps = params[9] as Boolean,
            isListenerPermissionGranted = params[10] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        loadApps()
        checkPermission()
    }

    fun checkPermission() {
        val context = getApplication<Application>()
        val cn = ComponentName(context, AppNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val granted = flat != null && flat.contains(cn.flattenToString())
        _isListenerPermissionGranted.value = granted
        if (granted) {
            refreshActiveNotifications()
        }
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val context = getApplication<Application>()
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val apps = resolveInfos
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg == context.packageName) return@mapNotNull null
                    val name = resolveInfo.loadLabel(pm).toString()
                    InstalledAppSummary(name = name, packageName = pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.name.lowercase() }

            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun toggleAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAppEnabled(packageName, enabled)
        }
    }

    fun setRepeatInterval(minutes: Int) {
        viewModelScope.launch {
            repository.setRepeatInterval(minutes)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSoundEnabled(enabled)
        }
    }

    fun setVibrateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setVibrateEnabled(enabled)
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setQuietHoursEnabled(enabled)
        }
    }

    fun setQuietHoursRange(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            repository.setQuietHours(startHour, endHour)
        }
    }

    fun clearAllReminders() {
        val context = getApplication<Application>()
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_CLEAR_ALL_REMINDERS
        }
        context.sendBroadcast(intent)
    }

    fun refreshActiveNotifications() {
        val context = getApplication<Application>()
        val intent = Intent(context, AppNotificationListenerService::class.java).apply {
            action = AppNotificationListenerService.ACTION_SYNC_NOTIFICATIONS
        }
        context.startService(intent)
        // Also trigger instance directly if bound
        AppNotificationListenerService.instance?.syncActiveNotifications()
    }
}
