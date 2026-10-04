package com.rirdev.rirdrop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rirdev.rirdrop.ui.theme.NeonLime
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarGraphic(
    modifier: Modifier = Modifier,
    isScanning: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "radar")

    val pulseProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f

            // Concentric Rings
            drawCircle(
                color = Color(0xFF232838),
                radius = maxRadius * 0.33f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF232838),
                radius = maxRadius * 0.66f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = NeonLime.copy(alpha = 0.25f),
                radius = maxRadius * 0.95f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Crosshair lines
            drawLine(
                color = Color(0xFF1D2230),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF1D2230),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Expanding Pulse Waves
            if (isScanning) {
                val waveRadius = maxRadius * pulseProgress
                val waveAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.5f
                drawCircle(
                    color = NeonLime.copy(alpha = waveAlpha),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Rotating radar sweep line
                val rad = Math.toRadians(sweepAngle.toDouble())
                val sweepEnd = Offset(
                    x = center.x + (maxRadius * 0.95f * cos(rad)).toFloat(),
                    y = center.y + (maxRadius * 0.95f * sin(rad)).toFloat()
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(NeonLime.copy(alpha = 0.8f), Color.Transparent),
                        start = center,
                        end = sweepEnd
                    ),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Center glowing core
            drawCircle(
                color = NeonLime,
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = NeonLime.copy(alpha = 0.35f),
                radius = 12.dp.toPx(),
                center = center
            )
        }
    }
}
