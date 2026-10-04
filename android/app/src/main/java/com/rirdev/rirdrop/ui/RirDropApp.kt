package com.rirdev.rirdrop.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.components.RirDropBottomNavBar
import com.rirdev.rirdrop.ui.components.RirDropTopAppBar
import com.rirdev.rirdrop.ui.modals.InAppMediaPlayerSheet
import com.rirdev.rirdrop.ui.modals.MyQrBottomSheet
import com.rirdev.rirdrop.ui.screens.DashboardScreen
import com.rirdev.rirdrop.ui.screens.DevicesRadarScreen
import com.rirdev.rirdrop.ui.screens.DownloaderScreen
import com.rirdev.rirdrop.ui.screens.DownloadsScreen
import com.rirdev.rirdrop.ui.screens.QuickDropScreen
import com.rirdev.rirdrop.ui.screens.SettingsScreen
import com.rirdev.rirdrop.ui.screens.SpeedTestScreen
import com.rirdev.rirdrop.ui.screens.SplashScreen
import com.rirdev.rirdrop.ui.screens.StorageStreamScreen
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BrandGold
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.RirDropTheme
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary
import com.rirdev.rirdrop.ui.theme.TextSecondary

@Composable
fun RirDropApp(
    viewModel: RirDropViewModel,
    onScanQrClicked: () -> Unit,
    onPickFilesClicked: () -> Unit,
    onPickFolderClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val isBooting by viewModel.isBooting.collectAsState()
    val activeStreamMedia by viewModel.activeStreamMedia.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val showMyQrModal by viewModel.showMyQrModal.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val downloadedFiles by viewModel.downloadedFiles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.initPreferences(context)
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // Hardware back navigation returns to Dashboard tab
    BackHandler(enabled = !isBooting && currentTab != NavTab.DASHBOARD) {
        viewModel.selectTab(NavTab.DASHBOARD)
    }

    RirDropTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
        ) {
            if (isBooting) {
                // Official Material 3 Splash Screen Sequence
                SplashScreen(viewModel = viewModel)
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        RirDropTopAppBar(
                            localIp = localIp,
                            downloadCount = downloadedFiles.size,
                            hasUpdate = updateInfo?.hasUpdate == true,
                            onDownloadsClicked = { viewModel.selectTab(NavTab.DOWNLOADS) },
                            onUpdateClicked = { viewModel.checkForUpdates(silent = false) },
                            onScanQrClicked = onScanQrClicked,
                            onMyQrClicked = { viewModel.setShowMyQrModal(true) }
                        )
                    },
                    bottomBar = {
                        RirDropBottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = BgDark
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(BgDark)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "tabTransition"
                        ) { tab ->
                            when (tab) {
                                NavTab.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    onPickFilesClicked = onPickFilesClicked,
                                    onScanQrClicked = onScanQrClicked
                                )
                                NavTab.QUICK_DROP -> QuickDropScreen(
                                    viewModel = viewModel,
                                    onPickFilesClicked = onPickFilesClicked,
                                    onPickFolderClicked = onPickFolderClicked
                                )
                                NavTab.RADAR -> DevicesRadarScreen(
                                    viewModel = viewModel,
                                    onSendFilesClicked = onPickFilesClicked
                                )
                                NavTab.STORAGE -> StorageStreamScreen(
                                    viewModel = viewModel
                                )
                                NavTab.SPEED_TEST -> SpeedTestScreen(
                                    viewModel = viewModel
                                )
                                NavTab.DOWNLOADER -> DownloaderScreen(
                                    viewModel = viewModel
                                )
                                NavTab.DOWNLOADS -> DownloadsScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.selectTab(NavTab.DASHBOARD) }
                                )
                                NavTab.SETTINGS -> SettingsScreen(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }

                    // Native Modal Bottom Sheet for Pairing QR
                    if (showMyQrModal) {
                        MyQrBottomSheet(
                            viewModel = viewModel,
                            onDismiss = { viewModel.setShowMyQrModal(false) }
                        )
                    }
                }

                // In-App Native ExoPlayer Streaming Sheet Overlay
                AnimatedVisibility(
                    visible = activeStreamMedia != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    activeStreamMedia?.let { mediaItem ->
                        InAppMediaPlayerSheet(
                            item = mediaItem,
                            onClose = { viewModel.closeMedia() }
                        )
                    }
                }

                // Global GitHub Release Update Dialog (Official M3)
                if (updateInfo != null && updateInfo!!.hasUpdate) {
                    val info = updateInfo!!
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissUpdateDialog() },
                        title = {
                            Text(
                                text = "New Release: v${info.latestVersion}",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = "A new version of RirDrop is available with improvements and bug fixes.",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = info.changelog.take(300),
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (!info.apkDownloadUrl.isNullOrBlank()) {
                                        viewModel.downloadToPhone(
                                            context,
                                            info.apkDownloadUrl,
                                            "RirDrop-v${info.latestVersion}.apk"
                                        )
                                    } else {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                                        context.startActivity(browserIntent)
                                    }
                                    viewModel.dismissUpdateDialog()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (!info.apkDownloadUrl.isNullOrBlank()) "Download APK" else "Open Release",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                                Text("Later", color = TextMuted)
                            }
                        },
                        containerColor = Color(0xFF191D28)
                    )
                }
            }
        }
    }
}
