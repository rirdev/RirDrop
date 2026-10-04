package com.rirdev.rirdrop.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rirdev.rirdrop.ui.components.RirDropBottomNavBar
import com.rirdev.rirdrop.ui.components.RirDropTopAppBar
import com.rirdev.rirdrop.ui.modals.InAppMediaPlayerSheet
import com.rirdev.rirdrop.ui.modals.MyQrBottomSheet
import com.rirdev.rirdrop.ui.screens.DashboardScreen
import com.rirdev.rirdrop.ui.screens.DevicesRadarScreen
import com.rirdev.rirdrop.ui.screens.QuickDropScreen
import com.rirdev.rirdrop.ui.screens.SettingsScreen
import com.rirdev.rirdrop.ui.screens.SplashScreen
import com.rirdev.rirdrop.ui.screens.StorageStreamScreen
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.RirDropTheme

@Composable
fun RirDropApp(
    viewModel: RirDropViewModel,
    onScanQrClicked: () -> Unit,
    onPickFilesClicked: () -> Unit
) {
    val isBooting by viewModel.isBooting.collectAsState()
    val activeStreamMedia by viewModel.activeStreamMedia.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val showMyQrModal by viewModel.showMyQrModal.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
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
                                    onPickFilesClicked = onPickFilesClicked
                                )
                                NavTab.RADAR -> DevicesRadarScreen(
                                    viewModel = viewModel,
                                    onSendFilesClicked = onPickFilesClicked
                                )
                                NavTab.STORAGE -> StorageStreamScreen(
                                    viewModel = viewModel
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
            }
        }
    }
}
