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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rirdev.rirdrop.ui.PcFolderItem
import com.rirdev.rirdrop.ui.PcSharedFolder
import com.rirdev.rirdrop.ui.RirDropViewModel
import com.rirdev.rirdrop.ui.StreamMediaItem
import com.rirdev.rirdrop.ui.components.M3WavyLinearProgressIndicator
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
    val isApprovalPending by viewModel.isApprovalPending.collectAsState()
    val pcSharedFolders by viewModel.pcSharedFolders.collectAsState()
    val currentBrowsingFolder by viewModel.currentBrowsingFolder.collectAsState()
    val pcFolderItems by viewModel.pcFolderItems.collectAsState()
    val isBrowsingFolderLoading by viewModel.isBrowsingFolderLoading.collectAsState()
    val sharedFiles by viewModel.sharedFiles.collectAsState()

    var showManualConnectDialog by remember { mutableStateOf(false) }

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
                IconButton(onClick = { viewModel.browsePcFolder(folder) }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = NeonLime
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        M3WavyLinearProgressIndicator(
                            progress = { 0.65f },
                            color = NeonLime,
                            modifier = Modifier.width(180.dp).height(14.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Reading folder files from PC...", fontSize = 12.sp, color = TextMuted)
                    }
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
                            },
                            onFolderClick = {
                                viewModel.browsePcFolder(folder, item.relPath)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Manual Connect by IP Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.clickable { showManualConnectDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = "Connect by IP",
                                tint = BrandGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Connect IP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandGold)
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { viewModel.refreshPcShared() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = NeonLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pending Approval Banner Card
                if (isApprovalPending) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, BrandGold.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF231E12))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Approval Pending",
                                        tint = BrandGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "PC Connection Approval Required",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BrandGold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "A pairing request was sent to your computer. Please click 'Accept' on your PC screen to unlock all 3 shared folders.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Official M3 Wavy Progress Indicator for waiting state
                                M3WavyLinearProgressIndicator(
                                    progress = { 0.75f },
                                    color = BrandGold,
                                    modifier = Modifier.fillMaxWidth().height(14.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelApprovalRequest() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Cancel", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.refreshPcShared() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Check Now", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. PC Shared Folders Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PC Shared Folders (${pcSharedFolders.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        if (pcSharedFolders.isNotEmpty()) {
                            Text(
                                text = "Tap to Stream In-App",
                                fontSize = 11.sp,
                                color = NeonLime,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (pcSharedFolders.isEmpty() && !isApprovalPending) {
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
                                    text = "0 Folders Visible",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (activePairedPc != null) {
                                        "Connected to PC, but no folders were returned. If your PC requires a password or approval, tap 'Connect IP' above or tap 'Refresh'."
                                    } else {
                                        "No PC connection active yet. Scan the PC QR code or tap 'Connect IP' above."
                                    },
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showManualConnectDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Pair with PC by IP", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
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
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Serving on LAN • ${viewModel.formatBytes(file.size)}",
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
    }

    // Manual IP & Password Connect Dialog
    if (showManualConnectDialog) {
        var ipInput by remember { mutableStateOf(activePairedPc?.ip ?: "192.168.") }
        var portInput by remember { mutableStateOf("53318") }
        var passwordInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualConnectDialog = false },
            title = {
                Text("Connect to PC Directly", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your PC's Wi-Fi IP address shown on the RirDrop desktop window.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("PC IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Port (Default 53318)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Master Password (Optional)") },
                        placeholder = { Text("Leave blank if none") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = portInput.toIntOrNull() ?: 53318
                        viewModel.connectToPc(ipInput.trim(), p, password = passwordInput.ifBlank { null })
                        showManualConnectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime)
                ) {
                    Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualConnectDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF191D28)
        )
    }
}

@Composable
private fun PcSharedFolderCard(
    folder: PcSharedFolder,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
            if (!folder.previewThumb.isNullOrBlank()) {
                AsyncImage(
                    model = folder.previewThumb,
                    contentDescription = folder.name,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StreamCyan.copy(alpha = 0.15f))
                        .border(1.dp, StreamCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = "Folder",
                        tint = StreamCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StreamCyan.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "OPEN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StreamCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderItemCard(
    item: PcFolderItem,
    onStream: () -> Unit,
    onDownload: () -> Unit,
    onFolderClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardMatteDarkBorder, RoundedCornerShape(14.dp))
            .clickable {
                if (item.isDirectory) {
                    onFolderClick()
                } else if (item.isVideo || item.isAudio) {
                    onStream()
                } else {
                    onDownload()
                }
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardMatteDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Thumbnail
            if (!item.thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.thumbUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                val (icon, tint) = when {
                    item.isDirectory -> Icons.Default.Folder to BrandGold
                    item.isVideo -> Icons.Default.VideoFile to StreamCyan
                    item.isAudio -> Icons.Default.AudioFile to Color(0xFFC084FC)
                    item.isImage -> Icons.Default.Image to NeonLime
                    else -> Icons.Default.Description to TextMuted
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isDirectory) "Directory" else item.sizeFormatted,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.isVideo || item.isAudio) {
                    IconButton(onClick = onStream, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Stream In-App",
                            tint = StreamCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (!item.isDirectory) {
                    IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = NeonLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
