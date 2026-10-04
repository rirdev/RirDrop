package com.rirdev.rirdrop.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.RirDropViewModel
import com.rirdev.rirdrop.ui.theme.BgDark
import com.rirdev.rirdrop.ui.theme.BrandGold
import com.rirdev.rirdrop.ui.theme.CardPastelLime
import com.rirdev.rirdrop.ui.theme.NeonLime
import com.rirdev.rirdrop.ui.theme.TextMuted
import com.rirdev.rirdrop.ui.theme.TextPrimary
import com.rirdev.rirdrop.ui.theme.TextSecondary

@Composable
fun SplashScreen(
    viewModel: RirDropViewModel
) {
    val progress by viewModel.bootProgress.collectAsState()
    val statusText by viewModel.bootStatusText.collectAsState()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "bootProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated Brand Squircle
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(logoScale)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF1E212B))
                    .border(2.dp, BrandGold.copy(alpha = 0.7f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RD",
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                    color = BrandGold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "RirDrop",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Pure Native LAN Transfer & Streaming",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Official Material 3 LinearProgressIndicator
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .width(220.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CardPastelLime,
                trackColor = Color(0xFF262A36),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusText,
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
