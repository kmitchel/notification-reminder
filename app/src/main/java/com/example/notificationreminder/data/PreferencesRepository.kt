package com.example.notificationreminder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reminder_preferences")

data class ReminderPreferences(
    val enabledApps: Set<String> = emptySet(),
    val repeatIntervalMinutes: Int = DEFAULT_REPEAT_INTERVAL_MINUTES,
    val vibrateEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = DEFAULT_QUIET_HOURS_START,
    val quietHoursEnd: Int = DEFAULT_QUIET_HOURS_END,
    val remindersPaused: Boolean = false
) {
    companion object {
        const val DEFAULT_REPEAT_INTERVAL_MINUTES = 5
        const val DEFAULT_QUIET_HOURS_START = 22
        const val DEFAULT_QUIET_HOURS_END = 7
        val ALLOWED_REPEAT_INTERVALS = setOf(1, 2, 5, 10, 15)
    }
}

class PreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val preferencesFlow: Flow<ReminderPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            ReminderPreferences(
                enabledApps = preferences[ENABLED_APPS].orEmpty(),
                repeatIntervalMinutes = preferences[REPEAT_INTERVAL_MINUTES]
                    ?.takeIf(ReminderPreferences.ALLOWED_REPEAT_INTERVALS::contains)
                    ?: ReminderPreferences.DEFAULT_REPEAT_INTERVAL_MINUTES,
                vibrateEnabled = preferences[VIBRATE_ENABLED] ?: true,
                soundEnabled = preferences[SOUND_ENABLED] ?: true,
                quietHoursEnabled = preferences[QUIET_HOURS_ENABLED] ?: false,
                quietHoursStart = preferences[QUIET_HOURS_START]
                    ?.takeIf(::isValidHour)
                    ?: ReminderPreferences.DEFAULT_QUIET_HOURS_START,
                quietHoursEnd = preferences[QUIET_HOURS_END]
                    ?.takeIf(::isValidHour)
                    ?: ReminderPreferences.DEFAULT_QUIET_HOURS_END,
                remindersPaused = preferences[REMINDERS_PAUSED] ?: false
            )
        }

    suspend fun toggleAppEnabled(packageName: String, enabled: Boolean) {
        require(packageName.isNotBlank()) { "Package name must not be blank" }
        dataStore.edit { preferences ->
            val enabledApps = preferences[ENABLED_APPS].orEmpty().toMutableSet()
            if (enabled) enabledApps.add(packageName) else enabledApps.remove(packageName)
            preferences[ENABLED_APPS] = enabledApps
        }
    }

    suspend fun setRepeatInterval(minutes: Int) {
        require(minutes in ReminderPreferences.ALLOWED_REPEAT_INTERVALS) {
            "Unsupported repeat interval: $minutes"
        }
        dataStore.edit { it[REPEAT_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setVibrateEnabled(enabled: Boolean) {
        dataStore.edit { it[VIBRATE_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SOUND_ENABLED] = enabled }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        dataStore.edit { it[QUIET_HOURS_ENABLED] = enabled }
    }

    suspend fun setQuietHours(startHour: Int, endHour: Int) {
        require(isValidHour(startHour) && isValidHour(endHour)) {
            "Quiet hours must be between 0 and 23"
        }
        dataStore.edit { preferences ->
            preferences[QUIET_HOURS_START] = startHour
            preferences[QUIET_HOURS_END] = endHour
        }
    }

    suspend fun setRemindersPaused(paused: Boolean) {
        dataStore.edit { it[REMINDERS_PAUSED] = paused }
    }

    private companion object {
        val ENABLED_APPS = stringSetPreferencesKey("enabled_apps")
        val REPEAT_INTERVAL_MINUTES = intPreferencesKey("repeat_interval_minutes")
        val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
        val REMINDERS_PAUSED = booleanPreferencesKey("reminders_paused")

        fun isValidHour(hour: Int) = hour in 0..23
    }
}
