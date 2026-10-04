package com.rirdev.rirdrop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Official Material 3 Expressive Wavy Linear Progress Indicator
 * As specified in https://m3.material.io/components/progress-indicators/overview
 * Features:
 * - Travelling sinusoidal wave on the active segment
 * - Rounded stroke caps (StrokeCap.Round)
 * - Subdued background track line
 * - Official Material 3 stop dot at the end of the track
 */
@Composable
fun M3WavyLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier
        .width(240.dp)
        .height(14.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color(0xFF262A36),
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 22.dp,
    waveSpeedMillis: Int = 1200
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3WavyWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waveSpeedMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3WavyPhase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokePx = strokeWidth.toPx()
        val amplitudePx = amplitude.toPx()
        val wavelengthPx = wavelength.toPx()
        val stopDotRadius = strokePx / 1.4f

        val currentProgress = progress().coerceIn(0f, 1f)
        val activeWidth = (width * currentProgress).coerceAtLeast(0f)

        // 1. Draw Background Track (straight line)
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width - stopDotRadius * 3, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        // 2. Draw Official Material 3 End Stop Dot
        drawCircle(
            color = trackColor.copy(alpha = 0.9f),
            radius = stopDotRadius,
            center = Offset(width - stopDotRadius - 1f, centerY)
        )

        // 3. Draw Active Wavy Segment
        if (activeWidth > 2f) {
            val wavePath = Path()
            val step = 2f
            var x = 0f

            wavePath.moveTo(0f, centerY)

            while (x <= activeWidth) {
                // Sinusoidal wave: y = centerY + amplitude * sin(2 * PI * (x / wavelength) - phase)
                val y = centerY + amplitudePx * sin((2 * PI * (x / wavelengthPx) - phase).toFloat())
                wavePath.lineTo(x, y)
                x += step
            }

            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Indeterminate Material 3 Expressive Wavy Progress Indicator (for loading states)
 */
@Composable
fun M3WavyIndeterminateProgressIndicator(
    modifier: Modifier = Modifier
        .width(240.dp)
        .height(14.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color(0xFF262A36),
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 22.dp,
    waveSpeedMillis: Int = 1000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3WavyIndet")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waveSpeedMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3WavyIndetPhase"
    )

    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "m3WavyIndetSweep"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokePx = strokeWidth.toPx()
        val amplitudePx = amplitude.toPx()
        val wavelengthPx = wavelength.toPx()
        val stopDotRadius = strokePx / 1.4f

        val startX = (width * (sweepProgress - 0.25f)).coerceAtLeast(0f)
        val endX = (width * sweepProgress).coerceAtMost(width)

        // 1. Draw Background Track
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width - stopDotRadius * 3, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        // 2. End Stop Dot
        drawCircle(
            color = trackColor.copy(alpha = 0.85f),
            radius = stopDotRadius,
            center = Offset(width - stopDotRadius - 1f, centerY)
        )

        // 3. Draw Traveling Wavy Segment
        if (endX > startX + 4f) {
            val wavePath = Path()
            val step = 2f
            var x = startX

            val startY = centerY + amplitudePx * sin((2 * PI * (startX / wavelengthPx) - phase).toFloat())
            wavePath.moveTo(startX, startY)

            while (x <= endX) {
                val y = centerY + amplitudePx * sin((2 * PI * (x / wavelengthPx) - phase).toFloat())
                wavePath.lineTo(x, y)
                x += step
            }

            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
