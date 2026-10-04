package com.rirdev.rirdrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.NavTab
import com.rirdev.rirdrop.ui.theme.BorderSubtle
import com.rirdev.rirdrop.ui.theme.PillNavActive
import com.rirdev.rirdrop.ui.theme.PillNavActiveText
import com.rirdev.rirdrop.ui.theme.PillNavBg
import com.rirdev.rirdrop.ui.theme.TextMuted

data class NavItem(
    val tab: NavTab,
    val label: String,
    val icon: ImageVector
)

@Composable
fun RirDropBottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem(NavTab.DASHBOARD, "Home", Icons.Default.Dashboard),
        NavItem(NavTab.QUICK_DROP, "Drop", Icons.Default.Bolt),
        NavItem(NavTab.RADAR, "Radar", Icons.Default.Radar),
        NavItem(NavTab.STORAGE, "Storage", Icons.Default.FolderShared),
        NavItem(NavTab.SETTINGS, "Settings", Icons.Default.Settings)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = PillNavBg.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentTab == item.tab
                    val bgAnim by animateColorAsState(
                        targetValue = if (isSelected) PillNavActive else Color.Transparent,
                        animationSpec = tween(220),
                        label = "navBg"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) PillNavActiveText else TextMuted,
                        animationSpec = tween(220),
                        label = "navIcon"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgAnim)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(item.tab) }
                            .padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                            if (isSelected) {
                                Text(
                                    text = item.label,
                                    color = iconTint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
