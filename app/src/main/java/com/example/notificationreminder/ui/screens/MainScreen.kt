package com.example.notificationreminder.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notificationreminder.data.PreferencesRepository
import com.example.notificationreminder.data.TrackedNotificationRepository
import com.example.notificationreminder.service.AppNotificationListenerService
import com.example.notificationreminder.service.ReminderAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val iconBitmap: ImageBitmap? = null
)

fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && this.bitmap != null) {
        return this.bitmap.asImageBitmap()
    }
    val w = if (intrinsicWidth > 0) intrinsicWidth else 48
    val h = if (intrinsicHeight > 0) intrinsicHeight else 48
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

suspend fun loadInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
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

    resolveInfos
        .mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val name = resolveInfo.loadLabel(pm).toString()
            val drawable = try {
                resolveInfo.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
            val bitmap = drawable?.toImageBitmap()
            InstalledAppInfo(name = name, packageName = pkg, iconBitmap = bitmap)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: PreferencesRepository,
    isListenerGranted: Boolean,
    onOpenListenerSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val enabledApps by repository.enabledAppsFlow.collectAsState(initial = emptySet())
    val repeatInterval by repository.repeatIntervalFlow.collectAsState(initial = 5)
    val vibrateEnabled by repository.vibrateEnabledFlow.collectAsState(initial = true)
    val soundEnabled by repository.soundEnabledFlow.collectAsState(initial = true)
    val quietHoursEnabled by repository.quietHoursEnabledFlow.collectAsState(initial = false)

    val trackedItems by TrackedNotificationRepository.trackedItems.collectAsState()

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps(context)
        isLoadingApps = false
        AppNotificationListenerService.instance?.syncActiveNotifications()
    }

    val intervalOptions = listOf(1, 2, 5, 10, 15)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notification Reminders", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isListenerGranted) {
                                if (trackedItems.isNotEmpty()) "Active • ${trackedItems.size} Unread Alert(s)" else "Active • No unread alerts"
                            } else "Permission Required",
                            fontSize = 12.sp,
                            color = if (isListenerGranted) {
                                if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                            } else {
                                if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
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

            // Permission Warning Card
            if (!isListenerGranted) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Notification Access Needed",
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "To repeat missed alerts from your apps, please grant Notification Access permission.",
                                fontSize = 14.sp,
                                color = if (isDark) Color(0xFFFFD180) else Color(0xFF5D4037)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenListenerSettings,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xFFFF8F00) else Color(0xFFE65100)
                                )
                            ) {
                                Text("Grant Permission", color = Color.White)
                            }
                        }
                    }
                }
            }

            // Live Active Reminders Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (trackedItems.isNotEmpty()) {
                            if (isDark) Color(0xFF1B382B) else Color(0xFFE8F5E9)
                        } else MaterialTheme.colorScheme.surfaceVariant
                    ),
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
                                    if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                } else MaterialTheme.colorScheme.onSurface
                            )
                            if (trackedItems.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                                            action = ReminderAlarmReceiver.ACTION_CLEAR_ALL_REMINDERS
                                        }
                                        context.sendBroadcast(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Clear All", fontSize = 12.sp, color = MaterialTheme.colorScheme.onError)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (trackedItems.isEmpty()) {
                            Text(
                                "No unread notifications are currently being reminded.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                trackedItems.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
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
                                                    color = if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20)
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

            // Interval Selector
            item {
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
                                    selected = repeatInterval == interval,
                                    onClick = {
                                        scope.launch { repository.setRepeatInterval(interval) }
                                    },
                                    label = { Text("${interval}m") }
                                )
                            }
                        }
                    }
                }
            }

            // Alert Preferences
            item {
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
                                onCheckedChange = { scope.launch { repository.setSoundEnabled(it) } }
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
                                onCheckedChange = { scope.launch { repository.setVibrateEnabled(it) } }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Quiet Hours")
                                Text("Silence during 10:00 PM - 7:00 AM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = quietHoursEnabled,
                                onCheckedChange = { scope.launch { repository.setQuietHoursEnabled(it) } }
                            )
                        }
                    }
                }
            }

            // Installed Apps Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Apps to Remind (${installedApps.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (isLoadingApps) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            items(installedApps, key = { it.packageName }) { app ->
                val isChecked = enabledApps.contains(app.packageName)
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
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = app.name,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(end = 12.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(36.dp))
                            }
                            Column {
                                Text(app.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    repository.toggleAppEnabled(app.packageName, enabled)
                                }
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
