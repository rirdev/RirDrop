package com.rirdev.rirdrop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rirdev.rirdrop.ui.BenchmarkPhase
import com.rirdev.rirdrop.ui.RirDropViewModel
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun SpeedTestScreen(
    viewModel: RirDropViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Live Network, 1 = Benchmark Test
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Network & Speed",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Real-time throughput & benchmark",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Wi-Fi",
                        tint = NeonLime,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "5 GHz LAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonLime
                    )
                }
            }
        }

        // Material 3 Filter Chips for Mode Selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("Live Monitor", fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonLime.copy(alpha = 0.2f),
                    selectedLabelColor = NeonLime,
                    selectedLeadingIconColor = NeonLime
                )
            )

            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("Speed Benchmark", fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StreamCyan.copy(alpha = 0.2f),
                    selectedLabelColor = StreamCyan,
                    selectedLeadingIconColor = StreamCyan
                )
            )
        }

        // Tab Content
        if (selectedTab == 0) {
            LiveNetworkMonitorTab(viewModel)
        } else {
            SpeedBenchmarkTab(viewModel)
        }
    }
}

@Composable
private fun LiveNetworkMonitorTab(viewModel: RirDropViewModel) {
    val speedStats by viewModel.speedStats.collectAsState()
    val historyDl by viewModel.liveHistoryDl.collectAsState()
    val historyUl by viewModel.liveHistoryUl.collectAsState()
    val peakDl by viewModel.livePeakDl.collectAsState()
    val peakUl by viewModel.livePeakUl.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Speedometer Dial Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = CardMatteDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ACTIVE INTERFACE THROUGHPUT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Canvas Circular Gauge
                val currentDlMbps = (speedStats.downBytesPerSec * 8f) / 1000000f
                val animatedVal by animateFloatAsState(
                    targetValue = currentDlMbps,
                    animationSpec = tween(400),
                    label = "gauge"
                )

                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SpeedometerCanvas(
                        value = animatedVal,
                        maxValue = max(peakDl, 35f),
                        colorA = StreamCyan,
                        colorB = NeonLime
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = speedStats.downSpeedStr.replace(" MB/s", ""),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Text(
                            text = "MB/s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StreamCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Up / Down Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Down",
                            tint = StreamCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Down: ${speedStats.downSpeedStr}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Up",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Up: ${speedStats.upSpeedStr}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
            }
        }

        // Live Rolling Graph Card (Matching Desktop 60fps graph)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = CardMatteDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rolling Throughput (Last 30s)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StreamCyan))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Download", fontSize = 10.sp, color = TextMuted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFC084FC)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Upload", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Rolling Graph Canvas
                RollingGraphCanvas(
                    dataDl = historyDl,
                    dataUl = historyUl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Peak DL: ${String.format("%.1f", peakDl)} Mbps", fontSize = 11.sp, color = StreamCyan)
                    Text(text = "Peak UL: ${String.format("%.1f", peakUl)} Mbps", fontSize = 11.sp, color = Color(0xFFC084FC))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SpeedBenchmarkTab(viewModel: RirDropViewModel) {
    val isBenchmarking by viewModel.isBenchmarking.collectAsState()
    val phase by viewModel.benchmarkPhase.collectAsState()
    val progress by viewModel.benchmarkProgress.collectAsState()
    val statusText by viewModel.benchmarkStatusText.collectAsState()
    val pingMs by viewModel.benchPingMs.collectAsState()
    val jitterMs by viewModel.benchJitterMs.collectAsState()
    val dlMbps by viewModel.benchDownloadMbps.collectAsState()
    val ulMbps by viewModel.benchUploadMbps.collectAsState()
    val gaugeTarget by viewModel.benchGaugeTarget.collectAsState()

    val animatedGauge by animateFloatAsState(
        targetValue = gaugeTarget,
        animationSpec = tween(250),
        label = "benchGauge"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Active / Idle Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = CardMatteDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (phase) {
                        BenchmarkPhase.IDLE -> "READY FOR BENCHMARK"
                        BenchmarkPhase.PING -> "STEP 1/3: LATENCY CHECK"
                        BenchmarkPhase.DOWNLOAD -> "STEP 2/3: DOWNLOAD THROUGHPUT"
                        BenchmarkPhase.UPLOAD -> "STEP 3/3: UPLOAD THROUGHPUT"
                        BenchmarkPhase.DONE -> "BENCHMARK COMPLETED"
                        BenchmarkPhase.ERROR -> "TEST STOPPED"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (phase) {
                        BenchmarkPhase.DONE -> NeonLime
                        BenchmarkPhase.ERROR -> Color(0xFFEF4444)
                        else -> StreamCyan
                    },
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Big Dial Gauge
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SpeedometerCanvas(
                        value = if (isBenchmarking) animatedGauge else if (phase == BenchmarkPhase.DONE) dlMbps else 0f,
                        maxValue = 150f,
                        colorA = NeonLime,
                        colorB = BrandGold
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBenchmarking) {
                                String.format("%.1f", animatedGauge)
                            } else if (phase == BenchmarkPhase.DONE) {
                                String.format("%.1f", dlMbps)
                            } else {
                                "0.0"
                            },
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Text(
                            text = "Mbps",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status text
                Text(
                    text = statusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                // Official Material 3 Expressive Wavy Progress Bar during active test
                if (isBenchmarking) {
                    Spacer(modifier = Modifier.height(14.dp))
                    M3WavyLinearProgressIndicator(
                        progress = { progress },
                        color = NeonLime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                if (!isBenchmarking) {
                    Button(
                        onClick = { viewModel.startBenchmarkTest() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (phase == BenchmarkPhase.DONE) "Run Test Again" else "Start Speed Benchmark",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.cancelBenchmarkTest() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Cancel Test", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Results Card (Ping, Jitter, Download, Upload)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = CardMatteDark),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Benchmark Metrics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "PING",
                        value = if (pingMs > 0) "${pingMs.toInt()} ms" else "--",
                        accentColor = BrandGold,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "JITTER",
                        value = if (jitterMs > 0) "${String.format("%.1f", jitterMs)} ms" else "--",
                        accentColor = Color(0xFFC084FC),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "DOWNLOAD",
                        value = if (dlMbps > 0) "${String.format("%.1f", dlMbps)} Mbps" else "--",
                        accentColor = NeonLime,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "UPLOAD",
                        value = if (ulMbps > 0) "${String.format("%.1f", ulMbps)} Mbps" else "--",
                        accentColor = StreamCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (phase == BenchmarkPhase.DONE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeonLime.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NeonLime)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (dlMbps > 100) "Gigabit Class Connection" else if (dlMbps > 30) "High-Speed Wi-Fi" else "Standard Broadband",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NeonLime
                                )
                                Text(
                                    text = "Excellent for real-time 4K/HDR movie streaming & large drops",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, CardMatteDarkBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}

@Composable
private fun SpeedometerCanvas(
    value: Float,
    maxValue: Float,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (size.minDimension / 2f) - 16.dp.toPx()
        val strokeWidth = 10.dp.toPx()

        val startAngle = 135f
        val totalSweep = 270f

        // Track Ring
        drawArc(
            color = Color(0xFF232736),
            startAngle = startAngle,
            sweepAngle = totalSweep,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Progress Arc
        val frac = (value / max(maxValue, 1f)).coerceIn(0f, 1f)
        if (frac > 0.01f) {
            val sweep = frac * totalSweep
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(colorA, colorB),
                    center = Offset(cx, cy)
                ),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun RollingGraphCanvas(
    dataDl: List<Float>,
    dataUl: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftMargin = 30.dp.toPx()
        val bottomMargin = 16.dp.toPx()
        val plotW = w - leftMargin - 8.dp.toPx()
        val plotH = h - bottomMargin - 4.dp.toPx()

        val peakVal = max((dataDl + dataUl).maxOrNull() ?: 20f, 15f)
        val maxY = peakVal * 1.25f

        // Draw horizontal grid lines
        val divs = 3
        for (i in 0..divs) {
            val y = (h - bottomMargin) - (i.toFloat() / divs) * plotH
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(leftMargin, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun drawSeries(data: List<Float>, strokeColor: Color, fillAlpha: Float) {
            if (data.size < 2) return
            val step = plotW / (data.size - 1)
            val baseY = h - bottomMargin

            val path = Path()
            path.moveTo(leftMargin, baseY - (data[0] / maxY) * plotH)

            for (i in 0 until data.size - 1) {
                val x0 = leftMargin + i * step
                val y0 = baseY - (data[i] / maxY) * plotH
                val x1 = leftMargin + (i + 1) * step
                val y1 = baseY - (data[i + 1] / maxY) * plotH
                val cpX = (x0 + x1) / 2f
                path.cubicTo(cpX, y0, cpX, y1, x1, y1)
            }

            // Stroke line
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Fill area under curve
            path.lineTo(leftMargin + plotW, baseY)
            path.lineTo(leftMargin, baseY)
            path.close()

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(strokeColor.copy(alpha = fillAlpha), Color.Transparent),
                    startY = 0f,
                    endY = h
                )
            )
        }

        // Upload (Purple)
        drawSeries(dataUl, Color(0xFFC084FC), 0.18f)

        // Download (Cyan)
        drawSeries(dataDl, StreamCyan, 0.22f)
    }
}
