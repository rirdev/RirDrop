package com.rirdev.rirdrop.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.NavTab
import com.rirdev.rirdrop.ui.RirDropViewModel
import com.rirdev.rirdrop.ui.components.DeviceCard
import com.rirdev.rirdrop.ui.components.RadarGraphic
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary
import com.rirdev.rirdrop.ui.theme.TextSecondary

@Composable
fun DevicesRadarScreen(
    viewModel: RirDropViewModel,
    onSendFilesClicked: () -> Unit
) {
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Radar Graphic Canvas
        RadarGraphic(
            modifier = Modifier.padding(top = 10.dp),
            isScanning = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Wi-Fi Network Radar",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "${discoveredPeers.size} peer(s) broadcasting on UDP 53317",
            fontSize = 12.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Scan Action Button
        Button(
            onClick = { viewModel.scanNetworkNow() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonLime,
                contentColor = BgDark
            ),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Scan",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Scan LAN Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Discovered Devices List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Detected Computers & Phones",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Searching for nearby computers...\nMake sure RirDrop is open on your PC or Mac.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(discoveredPeers, key = { it.ip }) { peer ->
                    val isConnected = connectedPeers.any { it.ip == peer.ip }
                    DeviceCard(
                        device = peer,
                        isConnected = isConnected,
                        onConnectClicked = {
                            viewModel.connectToPc(peer.ip, peer.httpPort, peer.alias)
                        },
                        onSendFilesClicked = {
                            viewModel.selectTab(NavTab.QUICK_DROP)
                            onSendFilesClicked()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}
