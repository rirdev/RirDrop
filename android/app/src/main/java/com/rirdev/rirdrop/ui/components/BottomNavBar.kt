package com.rirdev.rirdrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.NavTab
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BorderSubtle
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.SurfaceDark
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary

data class NavItem(
    val tab: NavTab,
    val label: String,
    val icon: ImageVector
)

@Composable
fun RirDropBottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    val items = listOf(
        NavItem(NavTab.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
        NavItem(NavTab.QUICK_DROP, "Quick Drop", Icons.Default.Bolt),
        NavItem(NavTab.RADAR, "Radar", Icons.Default.Radar),
        NavItem(NavTab.STORAGE, "Storage", Icons.Default.FolderShared),
        NavItem(NavTab.SETTINGS, "Settings", Icons.Default.Settings)
    )

    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .border(width = 1.dp, color = BorderSubtle),
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val isSelected = currentTab == item.tab
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) NeonLime else TextMuted,
                animationSpec = tween(200),
                label = "iconTint"
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.tab) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) NeonLime else TextMuted
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonLime,
                    selectedTextColor = NeonLime,
                    indicatorColor = Color(0xFF1B2A20),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}
