package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AutomationSchedule
import com.example.data.ShortStatus
import com.example.data.UiState
import com.example.ui.DuskLoreViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortDetailScreen(
    shortId: String,
    viewModel: DuskLoreViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val shortsState by viewModel.shortsState.collectAsStateWithLifecycle()
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()

    val project = (shortsState as? UiState.Success)?.data?.firstOrNull { it.id == shortId }
    val schedule = (scheduleState as? UiState.Success)?.data ?: AutomationSchedule()

    if (project == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Short Tafsilotlari") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.cd_back)
                            )
                        }
                    }
                )
            }
        ) { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Text("Short topilmadi yoki o'chirilgan.")
            }
        }
        return
    }

    var editableTitle by remember(project.id, project.youtubeTitle) {
        mutableStateOf(project.youtubeTitle)
    }
    var editableDesc by remember(project.id, project.youtubeDescription) {
        mutableStateOf(project.youtubeDescription)
    }
    var editableTags by remember(project.id, project.hashtags) {
        mutableStateOf(project.hashtags.joinToString(" "))
    }
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    val videoFile = project.localVideoPath?.let { File(it) }?.takeIf { it.exists() }
    val isTerminalPublished = project.parsedStatus == ShortStatus.PUBLISHED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column {
                        Text(
                            text = project.folkloreTheme,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${project.parsedStatus.labelUz} • 00:${project.durationSeconds}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (videoFile != null) {
                        IconButton(
                            onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    videoFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, project.youtubeTitle)
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${project.youtubeTitle}\n\n${project.youtubeDescription}\n\n${project.hashtags.joinToString(" ")}"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "YouTube Shorts'ga ulashish")
                                )
                            },
                            modifier = Modifier.testTag("share_mp4_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(id = R.string.cd_share_video)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteShort(project)
                            onBack()
                        },
                        modifier = Modifier.testTag("delete_short_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(id = R.string.cd_delete_short),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Vertical 9:16 Video Preview Player
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                val vv = videoViewRef
                                if (vv != null) {
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (videoFile != null) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoPath(videoFile.absolutePath)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                            isPlaying = true
                                        }
                                        videoViewRef = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(id = R.string.cd_play_pause),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(22.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "MP4 video ushbu qurilmada saqlanmagan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. YouTube Upload & 7:00 Schedule Controls
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "YouTube'ga Joylash (Google API)",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (uploadProgress != null) {
                            LinearProgressIndicator(
                                progress = { uploadProgress!!.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = "YouTube'ga yuklanmoqda: ${(uploadProgress!! * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (!project.youtubeVideoId.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val url = "https://youtube.com/shorts/${project.youtubeVideoId}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_youtube_link_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("YouTube'da ochish (${project.youtubeVideoId})")
                            }
                        }

                        if (!project.errorMessage.isNullOrBlank()) {
                            Text(
                                text = project.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (!isTerminalPublished && videoFile != null) {
                            Button(
                                onClick = { viewModel.uploadShortToYouTube(project, scheduleForSeven = true) },
                                enabled = uploadProgress == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("upload_schedule_7_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ScheduleSend,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Soat ${schedule.formattedPublishTime} ga YouTube'ga joylash")
                            }

                            OutlinedButton(
                                onClick = { viewModel.uploadShortToYouTube(project, scheduleForSeven = false) },
                                enabled = uploadProgress == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("upload_now_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hozir darhol ommaga joylash")
                            }
                        }
                    }
                }
            }

            // 3. American English YouTube Title, Description & Hashtags SEO Editor
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "YouTube Nomi, Tavsifi va Heshteglar (EN-US)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = editableTitle,
                            onValueChange = { editableTitle = it.take(100) },
                            enabled = !isTerminalPublished,
                            label = { Text("Video Nomi (Title)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seo_title_input")
                        )
                        OutlinedTextField(
                            value = editableDesc,
                            onValueChange = { editableDesc = it.take(1500) },
                            enabled = !isTerminalPublished,
                            label = { Text("Video Tavsifi (Description)") },
                            minLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seo_description_input")
                        )
                        OutlinedTextField(
                            value = editableTags,
                            onValueChange = { editableTags = it },
                            enabled = !isTerminalPublished,
                            label = { Text("Heshteglar (#Shorts #FolkloreHorror ...)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seo_hashtags_input")
                        )
                        if (!isTerminalPublished) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateShortSeo(
                                        shortId = project.id,
                                        title = editableTitle,
                                        description = editableDesc,
                                        hashtagsRaw = editableTags
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("save_seo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SEO ni saqlash")
                            }
                        }
                    }
                }
            }

            // 4. 15-Second Storyboard Scene Breakdown
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "15 Soniyalik Ssenariy va Subtitrlar",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        project.scenes.forEachIndexed { idx, scene ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "SAHNA ${idx + 1} • ${(scene.durationMs / 1000.0)}s • ${scene.cameraMotion}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = scene.narrationLine,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Subtitr: ${scene.subtitleText}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
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
