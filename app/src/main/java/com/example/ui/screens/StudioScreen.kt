package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AutomationSchedule
import com.example.data.FolkloreCategory
import com.example.data.PipelineProgress
import com.example.data.PipelineStage
import com.example.data.ShortStatus
import com.example.data.UiState
import com.example.ui.DuskLoreViewModel
import com.example.ui.components.UpcomingShortPreviewCard
import com.example.ui.components.UpcomingShortPreviewData
import com.example.worker.AutomationScheduler

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    viewModel: DuskLoreViewModel,
    onOpenShortDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheduleUiState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val shortsUiState by viewModel.shortsState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val customTopic by viewModel.customTopic.collectAsStateWithLifecycle()
    val autoUploadAfterGen by viewModel.autoUploadAfterGenerate.collectAsStateWithLifecycle()
    val pipeline by viewModel.pipelineProgress.collectAsStateWithLifecycle()
    val ytToken by viewModel.youtubeAccessToken.collectAsStateWithLifecycle()

    val schedule = (scheduleUiState as? UiState.Success)?.data ?: AutomationSchedule()
    val shortsList = (shortsUiState as? UiState.Success)?.data.orEmpty()
    val upcomingShort = shortsList.firstOrNull {
        it.parsedStatus == ShortStatus.SCHEDULED || it.parsedStatus == ShortStatus.READY
    } ?: shortsList.firstOrNull()
    val countdown = AutomationScheduler.formatCountdownToNextPublish(
        schedule.publishHour.toInt(),
        schedule.publishMinute.toInt()
    )
    val publishSevenLabel = if (schedule.publishHour == 7L) {
        "Publish at 7:00 AM"
    } else {
        "Publish at 7:00 PM"
    }
    val previewData = if (upcomingShort != null) {
        UpcomingShortPreviewData(
            shortId = upcomingShort.id,
            title = upcomingShort.youtubeTitle,
            hashtags = upcomingShort.hashtags.ifEmpty {
                listOf("#Shorts", "#FolkloreHorror", "#HorrorStories", "#ScaryShorts")
            },
            folkloreTheme = upcomingShort.folkloreTheme,
            captionHook = upcomingShort.scenes.firstOrNull()?.subtitleText
                ?: "DO NOT LOOK BACK AT MIDNIGHT",
            publishTimeLabel = publishSevenLabel,
            countdownLabel = countdown,
            durationSeconds = upcomingShort.durationSeconds,
            thumbnailPath = upcomingShort.localThumbPath,
            simulatedStatus = upcomingShort.parsedStatus != ShortStatus.SCHEDULED
        )
    } else {
        UpcomingShortPreviewData(
            shortId = null,
            title = "Never Answer The Red Lantern At Midnight Crossroads #Shorts",
            hashtags = listOf(
                "#Shorts",
                "#FolkloreHorror",
                "#HorrorStories",
                "#ScaryShorts",
                "#CreepyLegends",
                "#MidnightMyth"
            ),
            folkloreTheme = selectedCategory.id,
            captionHook = "NEVER FOLLOW THE RED LANTERN",
            publishTimeLabel = publishSevenLabel,
            countdownLabel = countdown,
            durationSeconds = 15L,
            thumbnailPath = null,
            simulatedStatus = true
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Folklore Studio Banner with Next 7:00 Countdown
        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_folklore_hero_1795196451708),
                    contentDescription = stringResource(id = R.string.cd_hero_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.28f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "KEYINGI ${schedule.formattedPublishTime} • $countdown",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "EN-US • 15s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "DUSKLORE STUDIYA",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mistik xalq afsonalari • Amerika inglizcha dahshatli ovoz, fon musiqasi va subtitrlar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. YouTube Channel Connection Status Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (!ytToken.isNullOrBlank()) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (!ytToken.isNullOrBlank()) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (!ytToken.isNullOrBlank()) Icons.Filled.CheckCircle else Icons.Filled.Link,
                                contentDescription = null,
                                tint = if (!ytToken.isNullOrBlank()) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = schedule.connectedChannelTitle ?: "YouTube Kanal Ulanmagan",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!ytToken.isNullOrBlank()) {
                                    "YouTube API tayyor • Soat ${schedule.formattedPublishTime} avto-yuklash faol"
                                } else {
                                    "Avtomatik yuklash uchun Google YouTube ruxsatini ulang"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.connectYouTubeChannel() },
                        modifier = Modifier.testTag("connect_youtube_button")
                    ) {
                        Text(
                            text = if (!ytToken.isNullOrBlank()) "Yangilash" else "Ulash",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 2b. Upcoming 15-Second Horror Short Preview Card
        item {
            UpcomingShortPreviewCard(
                preview = previewData,
                onPreviewClick = {
                    val existingId = previewData.shortId
                    if (!existingId.isNullOrBlank()) {
                        onOpenShortDetail(existingId)
                    } else if (!pipeline.running) {
                        viewModel.generateHorrorShort()
                    }
                },
                modifier = Modifier.widthIn(max = 600.dp)
            )
        }

        // 3. Mystical Folklore Horror Generator Configuration Card
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.MovieCreation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mistik Afsona Yo'nalishi",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FolkloreCategory.entries.forEach { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectCategory(cat) },
                                enabled = !pipeline.running,
                                label = {
                                    Text(
                                        text = cat.labelUz,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("category_${cat.name.lowercase()}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customTopic,
                        onValueChange = { viewModel.updateCustomTopic(it) },
                        enabled = !pipeline.running,
                        label = { Text("Ixtiyoriy afsona mavzusi (masalan: The Bell at Midnight)") },
                        placeholder = { Text("Bo'sh qoldirsangiz AI o'zi yangi mistik afsona yaratadi") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_topic_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Soat ${schedule.formattedPublishTime} ga avtomatik YouTube'ga qo'yish",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Video tayyor bo'lishi bilan nomi, tavsifi va heshteglari bilan yuklanadi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoUploadAfterGen,
                            onCheckedChange = { viewModel.setAutoUploadAfterGenerate(it) },
                            enabled = !pipeline.running,
                            modifier = Modifier.testTag("auto_upload_switch")
                        )
                    }

                    Button(
                        onClick = { viewModel.generateHorrorShort() },
                        enabled = !pipeline.running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("generate_short_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (pipeline.running) {
                                "15s Horror Short Yaratilmoqda..."
                            } else {
                                "15s Horror Short Yaratish"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // 4. Live 5-Stage Pipeline Monitor / Completion / Error Card
        item {
            AnimatedVisibility(
                visible = pipeline.stage != PipelineStage.IDLE || !pipeline.errorMessage.isNullOrBlank()
            ) {
                PipelineStatusCard(
                    pipeline = pipeline,
                    onOpenShort = { id -> onOpenShortDetail(id) },
                    onRetry = { viewModel.generateHorrorShort() },
                    onDismiss = { viewModel.resetPipeline() }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun PipelineStatusCard(
    pipeline: PipelineProgress,
    onOpenShort: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ember_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("pipeline_status_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (!pipeline.errorMessage.isNullOrBlank()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            1.dp,
            if (!pipeline.errorMessage.isNullOrBlank()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!pipeline.errorMessage.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yaratishda xatolik yuz berdi",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = pipeline.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag("pipeline_retry_button")
                    ) {
                        Text("Qayta urinish")
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text("Yopish")
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pipeline.running) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .alpha(pulseAlpha)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = pipeline.stage.labelUz,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${(pipeline.progressFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                LinearProgressIndicator(
                    progress = { pipeline.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (pipeline.stage == PipelineStage.DONE) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )

                if (pipeline.detailMessage.isNotBlank()) {
                    Text(
                        text = pipeline.detailMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pipeline.stage == PipelineStage.DONE && !pipeline.activeShortId.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onOpenShort(pipeline.activeShortId) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_generated_short_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Videoni ko'rish & YouTube")
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Yangi")
                        }
                    }
                }
            }
        }
    }
}
