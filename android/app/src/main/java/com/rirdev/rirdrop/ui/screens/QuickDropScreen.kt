package com.rirdev.rirdrop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.server.SharedItem
import com.rirdev.rirdrop.ui.PcQuickDropFile
import com.rirdev.rirdrop.ui.RirDropViewModel
import com.rirdev.rirdrop.ui.StreamMediaItem
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BorderSubtle
import com.rirdev.rirdrop.ui.theme.BrandGold
import com.rirdev.rirdrop.ui.theme.CardMatteDark
import com.rirdev.rirdrop.ui.theme.CardMatteDarkBorder
import com.rirdev.rirdrop.ui.theme.CardPastelLime
import com.rirdev.rirdrop.ui.theme.CardPastelLimeText
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.StreamCyan
import com.rirdev.rirdrop.ui.theme.SurfaceDark
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary
import com.rirdev.rirdrop.ui.theme.TextSecondary

@Composable
fun QuickDropScreen(
    viewModel: RirDropViewModel,
    onPickFilesClicked: () -> Unit
) {
    val context = LocalContext.current
    val sharedFiles by viewModel.sharedFiles.collectAsState()
    val pcQuickDropFiles by viewModel.pcQuickDropFiles.collectAsState()
    val activePairedPc by viewModel.activePairedPc.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("PC Files (${pcQuickDropFiles.size})", "Phone Drops (${sharedFiles.size})")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Switch Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = SurfaceDark,
            contentColor = NeonLime,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = NeonLime,
                    height = 3.dp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (selectedTabIndex == index) TextPrimary else TextMuted
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTabIndex == 0) {
            // TAB 0: PC Quick Drop Files (Live Received)
            Column(modifier = Modifier.weight(1f)) {
                // Info Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activePairedPc != null) "Live from ${activePairedPc?.name}" else "PC Live Drop",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (activePairedPc != null) NeonLime.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (activePairedPc != null) "TUNNEL ACTIVE" else "CONNECT PC",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (activePairedPc != null) NeonLime else TextMuted
                        )
                    }
                }

                if (pcQuickDropFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceDark.copy(alpha = 0.5f))
                            .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(NeonLime.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Drop",
                                    tint = NeonLime,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Waiting for PC Drops",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (activePairedPc != null) {
                                    "Drop any video, music, or file into the RirDrop Quick Drop window on your computer to stream or save here instantly."
                                } else {
                                    "Connect to your PC via Wi-Fi Radar or QR scan to sync dropped files in real time."
                                },
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pcQuickDropFiles, key = { it.id }) { pcFile ->
                            PcQuickDropFileCard(
                                file = pcFile,
                                onStream = {
                                    viewModel.playMedia(
                                        StreamMediaItem(
                                            title = pcFile.name,
                                            mimeType = pcFile.mimeType,
                                            url = pcFile.streamUrl,
                                            isVideo = pcFile.isVideo
                                        )
                                    )
                                },
                                onDownload = {
                                    viewModel.downloadToPhone(context, pcFile.downloadUrl, pcFile.name)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // TAB 1: Phone Shares (Local host on :53318)
            Column(modifier = Modifier.weight(1f)) {
                // Dropzone Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickFilesClicked() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardPastelLime)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CardPastelLimeText.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Upload",
                                tint = CardPastelLimeText,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Tap to Pick Files & Media",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = CardPastelLimeText
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Hosts files on phone port 53318 for paired PC & peers",
                            fontSize = 11.sp,
                            color = CardPastelLimeText.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phone Shares (${sharedFiles.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )

                    if (sharedFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.clearAllSharedFiles() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear All",
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (sharedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No files selected on phone yet.\nTap above to select files to share.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sharedFiles, key = { it.id }) { item ->
                            SharedItemCard(
                                item = item,
                                onRemove = { viewModel.removeSharedFile(item) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(68.dp)) // Floating nav bottom space
    }
}

@Composable
fun PcQuickDropFileCard(
    file: PcQuickDropFile,
    onStream: () -> Unit,
    onDownload: () -> Unit
) {
    val icon = when {
        file.isVideo -> Icons.Default.VideoFile
        file.isAudio -> Icons.Default.AudioFile
        file.mimeType.startsWith("image/") -> Icons.Default.Image
        else -> Icons.Default.Description
    }

    val iconColor = when {
        file.isVideo -> Color(0xFFC084FC)
        file.isAudio -> BrandGold
        file.mimeType.startsWith("image/") -> StreamCyan
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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "File",
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = file.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${file.sizeFormatted} • PC Synced",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file.isVideo || file.isAudio) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StreamCyan.copy(alpha = 0.2f),
                        modifier = Modifier.clickable { onStream() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Stream",
                                tint = StreamCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Stream", fontSize = 11.sp, color = StreamCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonLime.copy(alpha = 0.2f),
                    modifier = Modifier.clickable { onDownload() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save",
                            tint = NeonLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Save", fontSize = 11.sp, color = NeonLime, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SharedItemCard(
    item: SharedItem,
    onRemove: () -> Unit
) {
    val icon = when {
        item.mimeType.startsWith("image/") -> Icons.Default.Image
        item.mimeType.startsWith("video/") -> Icons.Default.VideoFile
        item.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        else -> Icons.Default.Description
    }

    val iconColor = when {
        item.mimeType.startsWith("image/") -> StreamCyan
        item.mimeType.startsWith("video/") -> Color(0xFFC084FC)
        item.mimeType.startsWith("audio/") -> BrandGold
        else -> NeonLime
    }

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
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "File",
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
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
                        text = "${formatFileSize(item.size)} • Hosted on LAN :53318",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

