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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.NavTab
import com.rirdev.rirdrop.ui.RirDropViewModel
import com.rirdev.rirdrop.ui.StreamMediaItem
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BorderSubtle
import com.rirdev.rirdrop.ui.theme.CardMatteDark
import com.rirdev.rirdrop.ui.theme.CardMatteDarkBorder
import com.rirdev.rirdrop.ui.theme.CardPastelAmber
import com.rirdev.rirdrop.ui.theme.CardPastelAmberText
import com.rirdev.rirdrop.ui.theme.CardPastelLime
import com.rirdev.rirdrop.ui.theme.CardPastelLimeText
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.StatusOnline
import com.rirdev.rirdrop.ui.theme.StreamCyan
import com.rirdev.rirdrop.ui.theme.SurfaceDark
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary
import com.rirdev.rirdrop.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    viewModel: RirDropViewModel,
    onPickFilesClicked: () -> Unit,
    onScanQrClicked: () -> Unit
) {
    val context = LocalContext.current
    val speedStats by viewModel.speedStats.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val sharedFiles by viewModel.sharedFiles.collectAsState()
    val activePairedPc by viewModel.activePairedPc.collectAsState()
    val pcQuickDropFiles by viewModel.pcQuickDropFiles.collectAsState()
    val pcSharedFolders by viewModel.pcSharedFolders.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Bar / Hero Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StatusOnline)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = deviceName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "RirDrop Studio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }

            // Quick Scan Action Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.clickable { onScanQrClicked() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan PC",
                        tint = NeonLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Scan PC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // 2. Mockup Hero Card 1: Pastel Lime Telemetry & Drop
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickFilesClicked() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardPastelLime)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ultra-Fast LAN Beam",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = CardPastelLimeText
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardPastelLimeText.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (activePairedPc != null) "SYNCED" else "READY",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = CardPastelLimeText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Upload Metric
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CardPastelLimeText.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Upload",
                                tint = CardPastelLimeText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "UPLOAD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CardPastelLimeText.copy(alpha = 0.7f)
                            )
                            Text(
                                text = speedStats.upSpeedStr,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = CardPastelLimeText
                            )
                        }
                    }

                    // Download Metric
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CardPastelLimeText.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Download",
                                tint = CardPastelLimeText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "DOWNLOAD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CardPastelLimeText.copy(alpha = 0.7f)
                            )
                            Text(
                                text = speedStats.downSpeedStr,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = CardPastelLimeText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Pill Inside Lime Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardPastelLimeText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickFilesClicked() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Send Files from Phone (${sharedFiles.size} Active)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Mockup Hero Card 2: Pastel Amber PC Pairing & Link
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (activePairedPc == null) {
                        viewModel.selectTab(NavTab.RADAR)
                    }
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardPastelAmber)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CardPastelAmberText.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "PC",
                            tint = CardPastelAmberText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (activePairedPc != null) "Paired: ${activePairedPc?.name}" else "No PC Connected",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = CardPastelAmberText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activePairedPc != null) "${activePairedPc?.ip}:${activePairedPc?.port} • Tunnel Active" else "Tap to open Radar or Scan QR",
                            fontSize = 11.sp,
                            color = CardPastelAmberText.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardPastelAmberText,
                    modifier = Modifier.clickable {
                        if (activePairedPc != null) {
                            viewModel.selectTab(NavTab.STORAGE)
                        } else {
                            viewModel.selectTab(NavTab.RADAR)
                        }
                    }
                ) {
                    Text(
                        text = if (activePairedPc != null) "Storage" else "Radar",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 4. Matte Dark Card: Live PC Quick Drop Glance
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardMatteDark)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Drop",
                            tint = NeonLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PC Quick Drop (Live Sync)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${pcQuickDropFiles.size} Files",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonLime
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (pcQuickDropFiles.isEmpty()) {
                    Text(
                        text = if (activePairedPc != null) {
                            "Drop any video, music, or document onto your PC RirDrop screen to stream or download instantly on this phone!"
                        } else {
                            "Pair your PC to see Quick Drop files synced in real-time."
                        },
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 17.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pcQuickDropFiles.take(3).forEach { pcFile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceDark)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pcFile.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pcFile.sizeFormatted} • ${if (pcFile.isVideo) "Video" else if (pcFile.isAudio) "Audio" else "File"}",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (pcFile.isVideo || pcFile.isAudio) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = StreamCyan.copy(alpha = 0.2f),
                                            modifier = Modifier.clickable {
                                                viewModel.playMedia(
                                                    StreamMediaItem(
                                                        title = pcFile.name,
                                                        mimeType = pcFile.mimeType,
                                                        url = pcFile.streamUrl,
                                                        isVideo = pcFile.isVideo
                                                    )
                                                )
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Stream",
                                                    tint = StreamCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(text = "Stream", fontSize = 11.sp, color = StreamCyan, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = NeonLime.copy(alpha = 0.2f),
                                        modifier = Modifier.clickable {
                                            viewModel.downloadToPhone(context, pcFile.downloadUrl, pcFile.name)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download",
                                                tint = NeonLime,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(text = "Save", fontSize = 11.sp, color = NeonLime, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Shared Storage & Radar Row Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Storage Tile
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(20.dp))
                    .clickable { viewModel.selectTab(NavTab.STORAGE) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardMatteDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFC084FC).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = "Folders",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "PC Storage",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${pcSharedFolders.size} Folders Shared",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Radar Tile
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(20.dp))
                    .clickable { viewModel.selectTab(NavTab.RADAR) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardMatteDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StreamCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = StreamCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "LAN Radar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${discoveredPeers.size} Devices Found",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp)) // Padding for floating bottom pill
    }
}
