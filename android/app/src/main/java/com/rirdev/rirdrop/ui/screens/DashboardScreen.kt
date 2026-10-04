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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rirdev.rirdrop.ui.components.M3WavyLinearProgressIndicator
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BorderSubtle
import com.rirdev.rirdrop.ui.theme.BrandGold
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
    val profileName by viewModel.profileName.collectAsState()
    val localIp by viewModel.localIp.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Bar / Hero Greeting Header (Matching Desktop greetingUser)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showEditProfileDialog = true }
                ) {
                    Text(
                        text = "Hello, $profileName",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        tint = BrandGold,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(StatusOnline)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$deviceName • LAN $localIp",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Scan Action Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
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
                                text = "UPLOAD SPEED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CardPastelLimeText.copy(alpha = 0.7f)
                            )
                            Text(
                                text = speedStats.upSpeedStr,
                                fontSize = 16.sp,
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
                                text = "DOWNLOAD SPEED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CardPastelLimeText.copy(alpha = 0.7f)
                            )
                            Text(
                                text = speedStats.downSpeedStr,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = CardPastelLimeText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Official M3 Expressive Wavy Progress Indicator on Card
                M3WavyLinearProgressIndicator(
                    progress = { if (activePairedPc != null || pcQuickDropFiles.isNotEmpty()) 0.8f else 0.25f },
                    color = CardPastelLimeText,
                    trackColor = CardPastelLimeText.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().height(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onPickFilesClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardPastelLimeText,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Drop Files",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Drop Files from Phone",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 3. Quick Action Feature Tiles: Speed Test & PC Storage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Speed Test Tile
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectTab(NavTab.SPEED_TEST) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardMatteDark),
                border = BorderStroke(1.dp, CardMatteDarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonLime.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = NeonLime, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Speed Test", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Text(text = "Live throughput & latency", fontSize = 11.sp, color = TextMuted)
                }
            }

            // PC Storage Tile
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectTab(NavTab.STORAGE) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardMatteDark),
                border = BorderStroke(1.dp, CardMatteDarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(StreamCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.FolderShared, contentDescription = null, tint = StreamCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "PC Storage", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Text(text = "${pcSharedFolders.size} Shared Folders", fontSize = 11.sp, color = TextMuted)
                }
            }
        }

        // 4. Hero Card 2: Pastel Amber PC Paired Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardPastelAmber)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CardPastelAmberText.copy(alpha = 0.14f)),
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
                            text = if (activePairedPc != null) "${activePairedPc?.ip}:${activePairedPc?.port} • Tunnel Active" else "Tap to open Storage or Scan QR",
                            fontSize = 11.sp,
                            color = CardPastelAmberText.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardPastelAmberText,
                    modifier = Modifier.clickable {
                        viewModel.selectTab(NavTab.STORAGE)
                    }
                ) {
                    Text(
                        text = if (activePairedPc != null) "Storage" else "Connect",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 5. Matte Dark Card: Live PC Quick Drop Glance
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
                            "No files dropped on PC yet. Drag files into your PC RirDrop Quick Drop zone to see them here immediately."
                        } else {
                            "Connect to your PC to see files dropped from desktop."
                        },
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                } else {
                    pcQuickDropFiles.take(3).forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = file.sizeFormatted,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            if (file.isVideo || file.isAudio) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StreamCyan.copy(alpha = 0.15f),
                                    modifier = Modifier.clickable {
                                        viewModel.playMedia(
                                            StreamMediaItem(
                                                title = file.name,
                                                mimeType = file.mimeType,
                                                url = file.streamUrl,
                                                isVideo = file.isVideo
                                            )
                                        )
                                    }
                                ) {
                                    Text(
                                        text = "STREAM",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StreamCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    // Name Change Dialog
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(profileName) }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Change Display Name", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setProfileName(context, tempName)
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGold)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF191D28)
        )
    }
}
