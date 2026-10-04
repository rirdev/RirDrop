package com.rirdev.rirdrop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import com.rirdev.rirdrop.service.ActiveDownloadTask
import com.rirdev.rirdrop.service.TransferProgressManager
import com.rirdev.rirdrop.ui.components.M3WavyLinearProgressIndicator
import com.rirdev.rirdrop.ui.theme.StatusOnline
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.DownloadedFileItem
import com.rirdev.rirdrop.ui.NavTab
import com.rirdev.rirdrop.ui.RirDropViewModel
import com.rirdev.rirdrop.ui.StreamMediaItem
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BorderSubtle
import com.rirdev.rirdrop.ui.theme.BrandGold
import com.rirdev.rirdrop.ui.theme.CardMatteDark
import com.rirdev.rirdrop.ui.theme.CardMatteDarkBorder
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.StreamCyan
import com.rirdev.rirdrop.ui.theme.SurfaceDark
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary
import com.rirdev.rirdrop.ui.theme.TextSecondary
import java.io.File

@Composable
fun DownloadsScreen(
    viewModel: RirDropViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloadedFiles by viewModel.downloadedFiles.collectAsState()
    val activeDownloads by TransferProgressManager.activeDownloads.collectAsState()
    var fileToDelete by remember { mutableStateOf<DownloadedFileItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshDownloadedFiles(context)
    }

    LaunchedEffect(activeDownloads) {
        if (activeDownloads.any { it.isFinished }) {
            viewModel.refreshDownloadedFiles(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloads Manager",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${downloadedFiles.size} file(s) saved in /Download/RirDrop",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            IconButton(onClick = { viewModel.refreshDownloadedFiles(context) }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = NeonLime
                )
            }
        }

        // Active Downloads Section (Animated)
        if (activeDownloads.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardMatteDark),
                border = BorderStroke(1.dp, CardMatteDarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Downloads (${activeDownloads.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonLime.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "DOWNLOADING",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonLime,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    activeDownloads.forEach { task ->
                        ActiveDownloadTaskCard(task = task, viewModel = viewModel)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (downloadedFiles.isEmpty() && activeDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2230))
                            .border(1.dp, BorderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = NeonLime,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Downloads Yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Files downloaded from PC shared folders or Quick Drop will appear here for instant opening, playback, and sharing.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloadedFiles) { item ->
                    DownloadedFileCard(
                        item = item,
                        onOpen = { viewModel.openDownloadedFile(context, item) },
                        onPlay = {
                            viewModel.playMedia(
                                StreamMediaItem(
                                    title = item.name,
                                    mimeType = item.mimeType,
                                    url = item.file.absolutePath,
                                    isVideo = item.isVideo
                                )
                            )
                        },
                        onShare = { viewModel.shareDownloadedFile(context, item) },
                        onDelete = { fileToDelete = item }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        val f = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Are you sure you want to delete '${f.name}'? This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownloadedFile(context, f)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF191D28)
        )
    }
}

@Composable
private fun DownloadedFileCard(
    item: DownloadedFileItem,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.isVideo || item.isAudio) onPlay() else onOpen()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = CardMatteDark),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = when {
                item.isVideo -> Icons.Default.VideoFile to StreamCyan
                item.isAudio -> Icons.Default.AudioFile to Color(0xFFC084FC)
                item.isImage -> Icons.Default.Image to NeonLime
                item.name.endsWith(".apk", true) -> Icons.Default.Android to NeonLime
                item.name.endsWith(".zip", true) -> Icons.Default.FolderZip to BrandGold
                else -> Icons.Default.Description to TextMuted
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.15f))
                    .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.sizeFormatted} • ${item.lastModifiedFormatted}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (item.isVideo || item.isAudio) {
                    IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Play",
                            tint = StreamCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadTaskCard(
    task: ActiveDownloadTask,
    viewModel: RirDropViewModel
) {
    val downloadedFormatted = viewModel.formatBytes(task.bytesDownloaded)
    val totalFormatted = if (task.totalBytes > 0) viewModel.formatBytes(task.totalBytes) else "..."
    val percentInt = (task.progress * 100).toInt()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161922),
        border = BorderStroke(1.dp, if (task.isFinished) StatusOnline.copy(alpha = 0.4f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (task.isFinished) StatusOnline.copy(alpha = 0.15f) else NeonLime.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (task.isFinished) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = null,
                            tint = if (task.isFinished) StatusOnline else NeonLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = task.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (task.isFinished) "✔ Saved to /Download/RirDrop" else "$downloadedFormatted / $totalFormatted • ${task.speedFormatted}",
                            fontSize = 11.sp,
                            color = if (task.isFinished) StatusOnline else TextMuted
                        )
                    }
                }

                Text(
                    text = if (task.isFinished) "Done" else "$percentInt%",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = if (task.isFinished) StatusOnline else NeonLime
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            M3WavyLinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = if (task.isFinished) StatusOnline else NeonLime,
                trackColor = Color(0xFF262C38)
            )
        }
    }
}
