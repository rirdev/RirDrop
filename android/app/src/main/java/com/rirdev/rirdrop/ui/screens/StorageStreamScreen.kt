package com.rirdev.rirdrop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rirdev.rirdrop.ui.NavTab
import com.rirdev.rirdrop.ui.PcFolderItem
import com.rirdev.rirdrop.ui.PcSharedFolder
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

@Composable
fun StorageStreamScreen(
    viewModel: RirDropViewModel
) {
    val context = LocalContext.current
    val activePairedPc by viewModel.activePairedPc.collectAsState()
    val pcSharedFolders by viewModel.pcSharedFolders.collectAsState()
    val currentBrowsingFolder by viewModel.currentBrowsingFolder.collectAsState()
    val pcFolderItems by viewModel.pcFolderItems.collectAsState()
    val isBrowsingFolderLoading by viewModel.isBrowsingFolderLoading.collectAsState()
    val sharedFiles by viewModel.sharedFiles.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (currentBrowsingFolder != null) {
            // FOLDER EXPLORER VIEW (Drill-down into PC folder)
            val folder = currentBrowsingFolder!!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.closeBrowsingFolder() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = folder.path,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isBrowsingFolderLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonLime, modifier = Modifier.size(36.dp))
                }
            } else if (pcFolderItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This folder is empty or contains no supported files.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pcFolderItems) { item ->
                        FolderItemCard(
                            item = item,
                            onStream = {
                                viewModel.playMedia(
                                    StreamMediaItem(
                                        title = item.name,
                                        mimeType = if (item.isVideo) "video/*" else if (item.isAudio) "audio/*" else "*/*",
                                        url = item.streamUrl,
                                        isVideo = item.isVideo
                                    )
                                )
                            },
                            onDownload = {
                                viewModel.downloadToPhone(context, item.downloadUrl, item.name)
                            }
                        )
                    }
                }
            }
        } else {
            // TOP STORAGE OVERVIEW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Storage & Streams",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (activePairedPc != null) "Connected: ${activePairedPc?.name}" else "No PC Paired",
                        fontSize = 12.sp,
                        color = if (activePairedPc != null) NeonLime else TextMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StreamCyan.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StreamCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IN-APP PLAYER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = StreamCyan
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. PC Shared Folders Section
                item {
                    Text(
                        text = "PC Shared Folders (${pcSharedFolders.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                if (pcSharedFolders.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardMatteDark)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                    .background(Color(0xFFC084FC).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderShared,
                                    contentDescription = "Folders",
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No PC Folders Shared Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (activePairedPc != null) {
                                    "On your PC RirDrop window, click '+ Add Folder' under Storage to stream entire movie and music collections here."
                                } else {
                                    "Connect to your PC using QR Scan or Radar to browse shared folders."
                                },
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
                } else {
                    items(pcSharedFolders) { folder ->
                        PcSharedFolderCard(
                            folder = folder,
                            onClick = { viewModel.browsePcFolder(folder) }
                        )
                    }
                }

                // 2. Phone Media Server Section
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Phone Media Server (:53318)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                if (sharedFiles.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardMatteDark)
                        ) {
                            Text(
                                text = "No phone files currently shared. Go to Quick Drop to share files.",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    items(sharedFiles) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Folder",
                                    tint = BrandGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatFileSize(file.size)} • Hosted on Phone",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(68.dp)) // Floating pill padding
    }
}

@Composable
fun PcSharedFolderCard(
    folder: PcSharedFolder,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardMatteDark)
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFC084FC).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = folder.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = folder.path,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = "Explore",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonLime
                )
            }
        }
    }
}

@Composable
fun FolderItemCard(
    item: PcFolderItem,
    onStream: () -> Unit,
    onDownload: () -> Unit
) {
    val icon = when {
        item.isVideo -> Icons.Default.VideoFile
        item.isAudio -> Icons.Default.AudioFile
        item.isImage -> Icons.Default.Image
        else -> Icons.Default.Description
    }

    val iconColor = when {
        item.isVideo -> Color(0xFFC084FC)
        item.isAudio -> BrandGold
        item.isImage -> StreamCyan
        else -> NeonLime
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardMatteDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (item.thumbUrl != null) {
                    AsyncImage(
                        model = item.thumbUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = item.name,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.sizeFormatted} • PC Storage",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.isVideo || item.isAudio) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StreamCyan.copy(alpha = 0.2f),
                        modifier = Modifier.clickable { onStream() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Stream",
                                tint = StreamCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "Stream", fontSize = 11.sp, color = StreamCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonLime.copy(alpha = 0.2f),
                    modifier = Modifier.clickable { onDownload() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save",
                            tint = NeonLime,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Save", fontSize = 11.sp, color = NeonLime, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

