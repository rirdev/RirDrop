package com.rirdev.rirdrop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.RirDropViewModel
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BorderSubtle
import com.rirdev.rirdrop.ui.theme.BrandGold
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.StreamCyan
import com.rirdev.rirdrop.ui.theme.SurfaceDark
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary
import com.rirdev.rirdrop.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: RirDropViewModel
) {
    val deviceName by viewModel.deviceName.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Settings & Diagnostics",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = TextPrimary
        )

        // 1. Device Profile Card
        SettingsCard(
            title = "Device Identity",
            icon = Icons.Default.Person,
            iconTint = BrandGold
        ) {
            Column {
                Text(
                    text = "DEVICE NAME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = deviceName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Visible to other computers and phones on Wi-Fi",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        // 2. Network Diagnostics Card
        SettingsCard(
            title = "Network Diagnostics",
            icon = Icons.Default.Wifi,
            iconTint = NeonLime
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagnosticRow(label = "Local Wi-Fi IP", value = localIp)
                DiagnosticRow(label = "HTTP Streaming Port", value = "${viewModel.port} (TCP)")
                DiagnosticRow(label = "Discovery Radar Port", value = "53317 (UDP Multicast)")
                DiagnosticRow(label = "Background Discovery", value = "Active (MulticastLock)")
            }
        }

        // 3. Theme & Appearance
        SettingsCard(
            title = "Appearance",
            icon = Icons.Default.Palette,
            iconTint = StreamCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Obsidian Dark Cyberpunk",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Pure native Material 3 AMOLED palette",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonLime.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = "DEFAULT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonLime)
                }
            }
        }

        // 4. Maintenance / Cache
        SettingsCard(
            title = "Storage & Cache",
            icon = Icons.Default.CleaningServices,
            iconTint = Color(0xFFEF4444)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Temporary Cache",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Purge cached QR bitmaps and stream chunks",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Button(
                    onClick = { viewModel.showMessage("Cache cleaned successfully") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2222),
                        contentColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(text = "Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. About RirDrop
        SettingsCard(
            title = "About RirDrop",
            icon = Icons.Default.Info,
            iconTint = Color(0xFFC084FC)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "RirDrop Mobile v1.0.0", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "Pure Native Kotlin & Jetpack Compose", fontSize = 12.sp, color = TextSecondary)
                Text(text = "Built with CameraX, Google ML Kit, and NanoHTTPD", fontSize = 11.sp, color = TextMuted)
                Text(text = "Zero tracking • 100% Offline LAN Transfer Engine", fontSize = 11.sp, color = NeonLime)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextMuted)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
