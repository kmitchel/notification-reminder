package com.example.notificationreminder.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.example.notificationreminder.data.TrackedNotificationItem
import com.example.notificationreminder.ui.InstalledAppSummary
import com.example.notificationreminder.ui.MainUiState
import com.example.notificationreminder.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap.asImageBitmap()
    }
    val w = if (intrinsicWidth > 0) intrinsicWidth else 48
    val h = if (intrinsicHeight > 0) intrinsicHeight else 48
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        iconBitmap = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val drawable = try {
                pm.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
            drawable?.toImageBitmap()
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Spacer(modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenListenerSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notification Reminders", fontWeight = FontWeight.Bold)
                        val statusText = if (!uiState.isListenerPermissionGranted) {
                            "Permission Required"
                        } else if (uiState.remindersPaused) {
                            "Paused"
                        } else {
                            if (uiState.trackedNotifications.isNotEmpty()) {
                                "Active • ${uiState.trackedNotifications.size} Unread Alert(s)"
                            } else {
                                "Active • No unread alerts"
                            }
                        }
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = if (uiState.isListenerPermissionGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (!uiState.isListenerPermissionGranted) {
                item {
                    PermissionWarningCard(onOpenListenerSettings = onOpenListenerSettings)
                }
            }

            if (!uiState.canScheduleExactAlarms) {
                item {
                    ExactAlarmWarningCard(onOpenExactAlarmSettings)
                }
            }

            item {
                ActiveRemindersCard(
                    trackedItems = uiState.trackedNotifications,
                    remindersPaused = uiState.remindersPaused,
                    onClearAll = { viewModel.clearAllReminders() }
                )
            }

            item {
                IntervalSelectorCard(
                    currentInterval = uiState.repeatIntervalMinutes,
                    onIntervalSelected = { viewModel.setRepeatInterval(it) }
                )
            }

            item {
                AlertSettingsCard(
                    soundEnabled = uiState.soundEnabled,
                    vibrateEnabled = uiState.vibrateEnabled,
                    onSoundToggle = { viewModel.setSoundEnabled(it) },
                    onVibrateToggle = { viewModel.setVibrateEnabled(it) }
                )
            }

            item {
                QuietHoursCard(
                    quietHoursEnabled = uiState.quietHoursEnabled,
                    startHour = uiState.quietHoursStart,
                    endHour = uiState.quietHoursEnd,
                    onToggle = { viewModel.setQuietHoursEnabled(it) },
                    onSetRange = { start, end -> viewModel.setQuietHoursRange(start, end) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Apps to Remind (${uiState.installedApps.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (uiState.isLoadingApps) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            items(uiState.installedApps, key = { it.packageName }) { app ->
                val isChecked = uiState.enabledApps.contains(app.packageName)
                AppRowCard(
                    app = app,
                    isChecked = isChecked,
                    onToggle = { viewModel.toggleAppEnabled(app.packageName, it) }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ExactAlarmWarningCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Precise Timing Disabled",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Android may delay reminders to conserve battery. Allow exact alarms for the selected interval.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenSettings) {
                Text("Allow Precise Timing")
            }
        }
    }
}

@Composable
fun PermissionWarningCard(onOpenListenerSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Notification Access Needed",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "To repeat missed alerts from your apps, please grant Notification Access permission.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenListenerSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Grant Permission", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

@Composable
fun ActiveRemindersCard(
    trackedItems: List<TrackedNotificationItem>,
    remindersPaused: Boolean,
    onClearAll: () -> Unit
) {
    val containerColor = if (trackedItems.isNotEmpty()) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Currently Reminding (${trackedItems.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (trackedItems.isNotEmpty()) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (trackedItems.isNotEmpty()) {
                    Button(
                        onClick = onClearAll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Pause Current", fontSize = 12.sp, color = MaterialTheme.colorScheme.onError)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (trackedItems.isEmpty()) {
                Text(
                    if (remindersPaused) {
                        "Paused until the next notification from a selected app."
                    } else {
                        "No unread notifications are currently being reminded."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    trackedItems.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        item.appName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        timeFormat.format(Date(item.postTime)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.title.isNotEmpty()) {
                                    Text(
                                        item.title,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (item.text.isNotEmpty()) {
                                    Text(
                                        item.text,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntervalSelectorCard(
    currentInterval: Int,
    onIntervalSelected: (Int) -> Unit
) {
    val intervalOptions = listOf(1, 2, 5, 10, 15)

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Repeat Interval", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Remind again every:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                intervalOptions.forEach { interval ->
                    FilterChip(
                        selected = currentInterval == interval,
                        onClick = { onIntervalSelected(interval) },
                        label = { Text("${interval}m") }
                    )
                }
            }
        }
    }
}

@Composable
fun AlertSettingsCard(
    soundEnabled: Boolean,
    vibrateEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    onVibrateToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Alert Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sound Alert")
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onSoundToggle
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vibration")
                Switch(
                    checked = vibrateEnabled,
                    onCheckedChange = onVibrateToggle
                )
            }
        }
    }
}

@Composable
fun QuietHoursCard(
    quietHoursEnabled: Boolean,
    startHour: Int,
    endHour: Int,
    onToggle: (Boolean) -> Unit,
    onSetRange: (Int, Int) -> Unit
) {
    fun formatHour(hour: Int): String {
        val h = if (hour == 0 || hour == 24) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour in 12..23) "PM" else "AM"
        return "$h:00 $amPm"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Quiet Hours", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Silence alerts between ${formatHour(startHour)} and ${formatHour(endHour)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = quietHoursEnabled,
                    onCheckedChange = onToggle
                )
            }

            if (quietHoursEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("Adjust Schedule:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        Triple("10PM - 7AM", 22, 7),
                        Triple("11PM - 8AM", 23, 8),
                        Triple("12AM - 7AM", 0, 7)
                    )
                    presets.forEach { (label, s, e) ->
                        FilterChip(
                            selected = startHour == s && endHour == e,
                            onClick = { onSetRange(s, e) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppRowCard(
    app: InstalledAppSummary,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AppIcon(
                    packageName = app.packageName,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 12.dp)
                )
                Column {
                    Text(app.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(
                        app.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onToggle
            )
        }
    }
}
