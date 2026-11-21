package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AutomationSchedule
import com.example.data.FolkloreCategory
import com.example.data.UiState
import com.example.ui.DuskLoreViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    viewModel: DuskLoreViewModel,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheduleUiState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val ytToken by viewModel.youtubeAccessToken.collectAsStateWithLifecycle()
    val schedule = (scheduleUiState as? UiState.Success)?.data ?: AutomationSchedule()
    val currentUser = Firebase.auth.currentUser

    var autoEnabled by remember(schedule.autoEnabled) { mutableStateOf(schedule.autoEnabled) }
    var selectedHour by remember(schedule.publishHour) { mutableLongStateOf(schedule.publishHour) }
    var defaultCategory by remember(schedule.defaultFolkloreCategory) {
        mutableStateOf(FolkloreCategory.fromId(schedule.defaultFolkloreCategory))
    }

    var notifStatusText by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifStatusText = if (granted) {
            "Bildirishnomalar yoqildi — har kuni 7:00 hisoboti keladi."
        } else {
            "Bildirishnoma ruxsati berilmadi."
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "SOAT 7:00 AVTO-REJA",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Har kuni avtomatik 15s Horror Short yaratib YouTube'ga soat 7:00 da joylash",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Master Daily 7:00 Auto-Pilot Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kunlik Avto-Joylash",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "WorkManager soat 7:00 dan 30 daqiqa oldin videoni tayyorlab YouTube publishAt'ga qo'yadi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoEnabled,
                            onCheckedChange = { autoEnabled = it },
                            modifier = Modifier.testTag("schedule_master_switch")
                        )
                    }

                    Text(
                        text = "Soat 7:00 vaqtini tanlang",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TimeSlotCard(
                            title = "19:00",
                            subtitle = "Kechki 7:00 (Tavsiya)",
                            selected = selectedHour == 19L,
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.NightsStay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            onClick = { selectedHour = 19L },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slot_19_00")
                        )
                        TimeSlotCard(
                            title = "07:00",
                            subtitle = "Ertalabki 7:00",
                            selected = selectedHour == 7L,
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.WbTwilight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            onClick = { selectedHour = 7L },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slot_07_00")
                        )
                    }

                    Text(
                        text = "Kunlik asosiy mistik mavzu",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FolkloreCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = defaultCategory == cat,
                                onClick = { defaultCategory = cat },
                                label = { Text(cat.labelUz) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.updateAutomationSchedule(
                                autoEnabled = autoEnabled,
                                publishHour = selectedHour,
                                publishMinute = 0L,
                                defaultCategory = defaultCategory
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_schedule_button")
                    ) {
                        Text("Soat %02d:00 Jadvalini Saqlash".format(selectedHour.toInt()))
                    }
                }
            }
        }

        // 2. Notification Permission (Android 13+) & Last Automated Run Log
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Oxirgi Avtomatik Holat",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = schedule.lastRunStatus ?: "Hali avtomatik ishga tushirish bajarilmagan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!schedule.lastRunAtIso.isNullOrBlank()) {
                        Text(
                            text = "Vaqt: ${schedule.lastRunAtIso}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.testTag("enable_notifications_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("7:00 Bildirishnomalarini yoqish")
                        }
                        if (!notifStatusText.isNullOrBlank()) {
                            Text(
                                text = notifStatusText!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        // 3. Google Account & YouTube OAuth Connection Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName ?: "Google Foydalanuvchi",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentUser?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.connectYouTubeChannel() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("schedule_connect_yt_button")
                        ) {
                            if (!ytToken.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(if (!ytToken.isNullOrBlank()) "YouTube Ulangan" else "YouTube Ulash")
                        }
                        OutlinedButton(
                            onClick = onSignOutClick,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(id = R.string.cd_sign_out),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chiqish")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
