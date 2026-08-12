package com.example.notificationreminder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reminder_preferences")

class PreferencesRepository(private val context: Context) {

    companion object {
        val ENABLED_APPS = stringSetPreferencesKey("enabled_apps")
        val REPEAT_INTERVAL_MINUTES = intPreferencesKey("repeat_interval_minutes")
        val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
    }

    val enabledAppsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[ENABLED_APPS] ?: emptySet()
    }

    val repeatIntervalFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[REPEAT_INTERVAL_MINUTES] ?: 5
    }

    val vibrateEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VIBRATE_ENABLED] ?: true
    }

    val soundEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SOUND_ENABLED] ?: true
    }

    val quietHoursEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[QUIET_HOURS_ENABLED] ?: false
    }

    val quietHoursStartFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[QUIET_HOURS_START] ?: 22
    }

    val quietHoursEndFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[QUIET_HOURS_END] ?: 7
    }

    suspend fun toggleAppEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[ENABLED_APPS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(packageName) else current.remove(packageName)
            prefs[ENABLED_APPS] = current
        }
    }

    suspend fun setRepeatInterval(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[REPEAT_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun setVibrateEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VIBRATE_ENABLED] = enabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SOUND_ENABLED] = enabled
        }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[QUIET_HOURS_ENABLED] = enabled
        }
    }

    suspend fun setQuietHours(startHour: Int, endHour: Int) {
        context.dataStore.edit { prefs ->
            prefs[QUIET_HOURS_START] = startHour
            prefs[QUIET_HOURS_END] = endHour
        }
    }
}
