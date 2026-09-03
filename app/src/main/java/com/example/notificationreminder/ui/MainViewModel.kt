package com.example.notificationreminder.ui

import android.app.AlarmManager
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.data.TrackedNotificationItem
import com.example.notificationreminder.data.TrackedNotificationRepository
import com.example.notificationreminder.service.AppNotificationListenerService
import com.example.notificationreminder.service.NotificationAccess
import com.example.notificationreminder.service.ReminderAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val remindersPaused: Boolean = false,
    val trackedNotifications: List<TrackedNotificationItem> = emptyList(),
    val installedApps: List<InstalledAppSummary> = emptyList(),
    val isLoadingApps: Boolean = true,
    val isListenerPermissionGranted: Boolean = false,
    val canScheduleExactAlarms: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    private val installedApps = MutableStateFlow<List<InstalledAppSummary>>(emptyList())
    private val isLoadingApps = MutableStateFlow(true)
    private val permissions = MutableStateFlow(PermissionState())

    val uiState: StateFlow<MainUiState> = combine(
        preferencesRepository.preferencesFlow,
        TrackedNotificationRepository.trackedItems,
        installedApps,
        isLoadingApps,
        permissions
    ) { preferences, trackedNotifications, apps, loading, permissionState ->
        MainUiState(
            enabledApps = preferences.enabledApps,
            repeatIntervalMinutes = preferences.repeatIntervalMinutes,
            soundEnabled = preferences.soundEnabled,
            vibrateEnabled = preferences.vibrateEnabled,
            quietHoursEnabled = preferences.quietHoursEnabled,
            quietHoursStart = preferences.quietHoursStart,
            quietHoursEnd = preferences.quietHoursEnd,
            remindersPaused = preferences.remindersPaused,
            trackedNotifications = trackedNotifications,
            installedApps = apps,
            isLoadingApps = loading,
            isListenerPermissionGranted = permissionState.notificationListenerGranted,
            canScheduleExactAlarms = permissionState.exactAlarmsGranted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    init {
        loadApps()
        checkPermissions()
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        permissions.value = PermissionState(
            notificationListenerGranted = NotificationAccess.isGranted(context),
            exactAlarmsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
        )

        if (permissions.value.notificationListenerGranted) refreshActiveNotifications()
    }

    fun toggleAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.toggleAppEnabled(packageName, enabled)
        }
    }

    fun setRepeatInterval(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setRepeatInterval(minutes) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundEnabled(enabled) }
    }

    fun setVibrateEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVibrateEnabled(enabled) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setQuietHoursEnabled(enabled) }
    }

    fun setQuietHoursRange(startHour: Int, endHour: Int) {
        viewModelScope.launch { preferencesRepository.setQuietHours(startHour, endHour) }
    }

    fun clearAllReminders() {
        val context = getApplication<Application>()
        context.sendBroadcast(
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_CLEAR_ALL_REMINDERS
            }
        )
    }

    private fun refreshActiveNotifications() {
        AppNotificationListenerService.instance?.requestSync() ?: run {
            val context = getApplication<Application>()
            NotificationListenerService.requestRebind(
                ComponentName(context, AppNotificationListenerService::class.java)
            )
        }
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingApps.value = true
            try {
                val context = getApplication<Application>()
                val packageManager = context.packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(launcherIntent, 0)
                }

                installedApps.value = activities
                    .mapNotNull { activity ->
                        val packageName = activity.activityInfo.packageName
                        if (packageName == context.packageName) return@mapNotNull null
                        InstalledAppSummary(
                            name = activity.loadLabel(packageManager).toString(),
                            packageName = packageName
                        )
                    }
                    .distinctBy(InstalledAppSummary::packageName)
                    .sortedBy { it.name.lowercase() }
            } catch (error: RuntimeException) {
                Log.e(TAG, "Unable to load installed apps", error)
                installedApps.value = emptyList()
            } finally {
                isLoadingApps.value = false
            }
        }
    }

    private data class PermissionState(
        val notificationListenerGranted: Boolean = false,
        val exactAlarmsGranted: Boolean = true
    )

    private companion object {
        const val TAG = "MainViewModel"
    }
}
