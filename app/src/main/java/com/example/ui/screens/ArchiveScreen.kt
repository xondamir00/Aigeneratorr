package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.HorrorShortProject
import com.example.data.ShortStatus
import com.example.data.UiState
import com.example.ui.DuskLoreViewModel
import java.io.File

private enum class ArchiveFilter(val labelUz: String) {
    ALL("Barchasi"),
    READY("Tayyor"),
    SCHEDULED("7:00 Rejada"),
    PUBLISHED("Joylangan")
}

@Composable
fun ArchiveScreen(
    viewModel: DuskLoreViewModel,
    onOpenShortDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortsState by viewModel.shortsState.collectAsStateWithLifecycle()
    var activeFilter by remember { mutableStateOf(ArchiveFilter.ALL) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SHORTS ARXIVI",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Yaratilgan 15 soniyalik mistik horror videolaringiz va YouTube holati",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ArchiveFilter.entries.size) { index ->
                val filter = ArchiveFilter.entries[index]
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(filter.labelUz, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.testTag("archive_filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (val state = shortsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val filtered = state.data.filter { item ->
                    when (activeFilter) {
                        ArchiveFilter.ALL -> true
                        ArchiveFilter.READY -> item.parsedStatus == ShortStatus.READY || item.parsedStatus == ShortStatus.GENERATING
                        ArchiveFilter.SCHEDULED -> item.parsedStatus == ShortStatus.SCHEDULED
                        ArchiveFilter.PUBLISHED -> item.parsedStatus == ShortStatus.PUBLISHED
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MovieFilter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Hozircha Horror Short yo'q",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Studiya bo'limidan 15 soniyalik birinchi mistik xalq afsonasini yarating.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { project ->
                            ShortPosterCard(
                                project = project,
                                onClick = { onOpenShortDetail(project.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortPosterCard(
    project: HorrorShortProject,
    onClick: () -> Unit
) {
    val status = project.parsedStatus
    val badgeColor = when (status) {
        ShortStatus.SCHEDULED -> MaterialTheme.colorScheme.secondaryContainer
        ShortStatus.PUBLISHED -> MaterialTheme.colorScheme.tertiaryContainer
        ShortStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeTextColor = when (status) {
        ShortStatus.SCHEDULED -> MaterialTheme.colorScheme.onSecondaryContainer
        ShortStatus.PUBLISHED -> MaterialTheme.colorScheme.onTertiaryContainer
        ShortStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 14.5f)
            .clickable(onClick = onClick)
            .testTag("short_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumbFile = project.localThumbPath?.let { File(it) }?.takeIf { it.exists() }
            if (thumbFile != null) {
                AsyncImage(
                    model = thumbFile,
                    contentDescription = stringResource(id = R.string.cd_short_poster),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.10f),
                                1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            // Top badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = badgeColor) {
                    Text(
                        text = status.labelUz,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f)
                ) {
                    Text(
                        text = "00:${project.durationSeconds}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Bottom title & folklore category
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = project.folkloreTheme.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = project.youtubeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!project.scheduledPublishAtIso.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = project.scheduledPublishAtIso.take(16).replace("T", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
