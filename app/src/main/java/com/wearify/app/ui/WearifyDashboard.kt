package com.wearify.app.ui

import android.graphics.Paint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wearify.app.data.ContactMicAnalysis
import com.wearify.app.data.ContactMicPoint
import com.wearify.app.data.ContactMicQualityVerdict
import com.wearify.app.data.DataLogStatus
import com.wearify.app.data.DEFAULT_LIVE_DISPLAY_METRICS
import com.wearify.app.data.HeartSoundPair
import com.wearify.app.data.LiveDisplayMetric
import com.wearify.app.data.PpgAnalysis
import com.wearify.app.data.PpgAnalyzer
import com.wearify.app.data.SensorSample
import com.wearify.app.data.fuseLiveHeartRate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val OSCILLOSCOPE_WINDOW_MS = 12_000L
private const val CONTACT_MIC_SCOPE_WINDOW_MS = 2_000L
private val BottomNavigationOverlayClearance = 112.dp
private val VitalsHeartColor = ApplePink
private val VitalsTemperatureColor = AppleOrange
private val EnvironmentalSunColor = AppleYellow

data class SensorData(
    val ppgIr: Double?,
    val tempC: Double?,
    val ax: Double?,
    val ay: Double?,
    val az: Double?,
    val gx: Double?,
    val gy: Double?,
    val gz: Double?,
    val uv: Double?,
    val als: Double?,
)

@Composable
fun WearifyUpdateDashboard(
    sensorData: SensorData,
    modifier: Modifier = Modifier,
    samples: List<SensorSample> = emptyList(),
    contactMicPoints: List<ContactMicPoint> = emptyList(),
    contactMicAnalysis: ContactMicAnalysis = ContactMicAnalysis(),
    ppgAnalysis: PpgAnalysis = PpgAnalysis(),
    dataLogStatus: DataLogStatus = DataLogStatus(),
    enabledLiveMetrics: Set<LiveDisplayMetric> = DEFAULT_LIVE_DISPLAY_METRICS,
) {
    var oscilloscopeSection by remember { mutableStateOf<DashboardSection?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp,
                        top = 18.dp,
                        end = 20.dp,
                        bottom = BottomNavigationOverlayClearance,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Data",
                            style = MaterialTheme.typography.headlineMedium,
                            color = WearifyInk,
                        )
                    }
                    PlotIconButton(
                        accent = AppleBlue,
                        contentDescription = "Open all signal waveforms",
                        onClick = { oscilloscopeSection = DashboardSection.All },
                    )
                }
                DataLogStatusPill(dataLogStatus = dataLogStatus)

                LiveMetricGrid(
                    sensorData = sensorData,
                    samples = samples,
                    contactMicAnalysis = contactMicAnalysis,
                    ppgAnalysis = ppgAnalysis,
                    enabledLiveMetrics = enabledLiveMetrics,
                    onOpenVitals = { oscilloscopeSection = DashboardSection.Vitals },
                    onOpenContactMic = { oscilloscopeSection = DashboardSection.ContactMic },
                    onOpenMotion = { oscilloscopeSection = DashboardSection.Motion },
                    onOpenEnvironmental = { oscilloscopeSection = DashboardSection.Environmental },
                )
            }

        }
    }

    oscilloscopeSection?.let { section ->
        OscilloscopeDialog(
            section = section,
            samples = samples,
            contactMicPoints = contactMicPoints,
            contactMicAnalysis = contactMicAnalysis,
            ppgAnalysis = ppgAnalysis,
            onDismiss = { oscilloscopeSection = null },
        )
    }
}

@Composable
fun WearifyDashboard(
    sensorData: SensorData,
    modifier: Modifier = Modifier,
    samples: List<SensorSample> = emptyList(),
    contactMicPoints: List<ContactMicPoint> = emptyList(),
    contactMicAnalysis: ContactMicAnalysis = ContactMicAnalysis(),
    ppgAnalysis: PpgAnalysis = PpgAnalysis(),
    dataLogStatus: DataLogStatus = DataLogStatus(),
    enabledLiveMetrics: Set<LiveDisplayMetric> = DEFAULT_LIVE_DISPLAY_METRICS,
) {
    WearifyUpdateDashboard(
        sensorData = sensorData,
        modifier = modifier,
        samples = samples,
        contactMicPoints = contactMicPoints,
        contactMicAnalysis = contactMicAnalysis,
        ppgAnalysis = ppgAnalysis,
        dataLogStatus = dataLogStatus,
        enabledLiveMetrics = enabledLiveMetrics,
    )
}

@Composable
private fun DataLogStatusPill(
    dataLogStatus: DataLogStatus,
) {
    if (!dataLogStatus.isRecording && dataLogStatus.lastError == null) {
        return
    }

    val accent = if (dataLogStatus.lastError == null) AppleGreen else AppleRed
    val pulse by rememberInfiniteTransition(label = "recordingPulse").animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordingAlpha",
    )
    val textAlpha = if (dataLogStatus.lastError == null) pulse else 1f
    val statusText = dataLogStatus.lastError?.let {
        "Recording needs attention"
    } ?: "Recording securely"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyGlass,
        contentColor = WearifyInk,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = textAlpha)),
            )
            Text(
                text = statusText,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveMetricGrid(
    sensorData: SensorData,
    samples: List<SensorSample>,
    contactMicAnalysis: ContactMicAnalysis,
    ppgAnalysis: PpgAnalysis,
    enabledLiveMetrics: Set<LiveDisplayMetric>,
    onOpenVitals: () -> Unit,
    onOpenContactMic: () -> Unit,
    onOpenMotion: () -> Unit,
    onOpenEnvironmental: () -> Unit,
) {
    val motionSummary = summarizeMotion(samples)
    val environmentSummary = summarizeEnvironment(samples)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val showHeartRate = LiveDisplayMetric.HeartRate in enabledLiveMetrics
        val showContactMic = LiveDisplayMetric.ContactMic in enabledLiveMetrics
        val showTemperature = LiveDisplayMetric.Temperature in enabledLiveMetrics
        val showMotion = LiveDisplayMetric.Motion in enabledLiveMetrics
        val showUv = LiveDisplayMetric.Uv in enabledLiveMetrics
        val showAmbient = LiveDisplayMetric.AmbientLight in enabledLiveMetrics

        if (enabledLiveMetrics.isEmpty()) {
            EmptyLiveMetricsState()
            return@Column
        }

        if (showHeartRate || showContactMic) {
            LiveSectionLabel("Heart")
        }
        if (showHeartRate) {
            FusedHeartRateWidget(
                ppgAnalysis = ppgAnalysis,
                contactMicAnalysis = contactMicAnalysis,
                hasLiveSamples = samples.isNotEmpty(),
                onOpenOscilloscope = onOpenVitals,
            )
        }

        if (showContactMic) {
            ContactMicWidget(
                contactMicAnalysis = contactMicAnalysis,
                onOpenOscilloscope = onOpenContactMic,
            )
        }

        if (showTemperature || showMotion) {
            LiveSectionLabel("Movement & body")
        }
        MotionAndTemperatureRow(
            showTemperature = showTemperature,
            showMotion = showMotion,
            sensorData = sensorData,
            motionSummary = motionSummary,
            environmentSummary = environmentSummary,
            onOpenVitals = onOpenVitals,
            onOpenMotion = onOpenMotion,
        )

        if (showUv || showAmbient) {
            LiveSectionLabel("Environment")
        }
        EnvironmentRow(
            showUv = showUv,
            showAmbient = showAmbient,
            environmentSummary = environmentSummary,
            onOpenEnvironmental = onOpenEnvironmental,
        )
    }
}

@Composable
private fun LiveSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = WearifyMuted,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun MotionAndTemperatureRow(
    showTemperature: Boolean,
    showMotion: Boolean,
    sensorData: SensorData,
    motionSummary: MotionSummary,
    environmentSummary: EnvironmentSummary,
    onOpenVitals: () -> Unit,
    onOpenMotion: () -> Unit,
) {
    when {
        showTemperature && showMotion -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TemperatureWidget(
                    sensorData = sensorData,
                    environmentSummary = environmentSummary,
                    onOpenOscilloscope = onOpenVitals,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                )
                MotionWidget(
                    summary = motionSummary,
                    onOpenOscilloscope = onOpenMotion,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                )
            }
        }

        showTemperature -> {
            TemperatureWidget(
                sensorData = sensorData,
                environmentSummary = environmentSummary,
                onOpenOscilloscope = onOpenVitals,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp),
            )
        }

        showMotion -> {
            MotionWidget(
                summary = motionSummary,
                onOpenOscilloscope = onOpenMotion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp),
            )
        }
    }
}

@Composable
private fun EnvironmentRow(
    showUv: Boolean,
    showAmbient: Boolean,
    environmentSummary: EnvironmentSummary,
    onOpenEnvironmental: () -> Unit,
) {
    when {
        showUv && showAmbient -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UvWidget(
                    summary = environmentSummary,
                    onOpenOscilloscope = onOpenEnvironmental,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                )
                AmbientLightWidget(
                    summary = environmentSummary,
                    onOpenOscilloscope = onOpenEnvironmental,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                )
            }
        }

        showUv -> {
            UvWidget(
                summary = environmentSummary,
                onOpenOscilloscope = onOpenEnvironmental,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp),
            )
        }

        showAmbient -> {
            AmbientLightWidget(
                summary = environmentSummary,
                onOpenOscilloscope = onOpenEnvironmental,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp),
            )
        }
    }
}

@Composable
private fun TemperatureWidget(
    sensorData: SensorData,
    environmentSummary: EnvironmentSummary,
    onOpenOscilloscope: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SquareMetricWidget(
        title = "Near-body temp",
        value = formatValue(environmentSummary.nearBodyTemperatureC ?: sensorData.tempC, decimals = 1),
        unit = "°C",
        caption = "",
        accent = VitalsTemperatureColor,
        icon = { Icon(Icons.Default.Thermostat, contentDescription = null) },
        onOpenOscilloscope = onOpenOscilloscope,
        modifier = modifier,
    )
}

@Composable
private fun UvWidget(
    summary: EnvironmentSummary,
    onOpenOscilloscope: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SquareMetricWidget(
        title = "UV",
        value = summary.uvExposure.label,
        unit = "",
        caption = summary.environment.label,
        accent = EnvironmentalSunColor,
        icon = { Icon(Icons.Default.WbSunny, contentDescription = null) },
        onOpenOscilloscope = onOpenOscilloscope,
        modifier = modifier,
    )
}

@Composable
private fun EmptyLiveMetricsState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WearifyGlass,
        contentColor = WearifyMuted,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.62f)),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "No data cards selected",
                color = WearifyInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Turn on metrics in Settings.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FusedHeartRateWidget(
    ppgAnalysis: PpgAnalysis,
    contactMicAnalysis: ContactMicAnalysis,
    hasLiveSamples: Boolean,
    onOpenOscilloscope: () -> Unit,
) {
    val fusion = fuseLiveHeartRate(ppgAnalysis, contactMicAnalysis)
    var stableHeartRate by remember { mutableStateOf(StableHeartRateState()) }
    val observationKey = ppgAnalysis.processedPoints.lastOrNull()?.timestampMs
    LaunchedEffect(observationKey, fusion.bpm, fusion.state) {
        stableHeartRate = if (hasLiveSamples) {
            updateStableHeartRate(stableHeartRate, fusion.bpm)
        } else {
            StableHeartRateState()
        }
    }
    val heartRate = stableHeartRate.displayedBpm?.let { "%.0f".format(it) } ?: "--"

    GlassWidgetSurface(
        modifier = Modifier.fillMaxWidth(),
        accent = VitalsHeartColor,
        containerColor = WearifySoftPink.copy(alpha = 0.88f),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            WidgetHeader(
                title = "Heart Rate",
                accent = VitalsHeartColor,
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                onOpenOscilloscope = onOpenOscilloscope,
                plotDescription = "Open heart rate plot",
            )
            MetricValue(
                value = heartRate,
                unit = "bpm",
                valueColor = WearifyInk,
                large = true,
            )
        }
    }
}

@Composable
private fun ContactMicWidget(
    contactMicAnalysis: ContactMicAnalysis,
    onOpenOscilloscope: () -> Unit,
) {
    val summary = summarizeHeartSound(contactMicAnalysis)

    GlassWidgetSurface(
        modifier = Modifier.fillMaxWidth(),
        accent = AppleTeal,
        containerColor = WearifyGlass,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WidgetHeader(
                title = "Heart Sound",
                accent = AppleTeal,
                icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                onOpenOscilloscope = onOpenOscilloscope,
                plotDescription = "Open heart sound waveforms",
            )
            MetricValue(
                value = summary.s1ToS2Ms?.toString() ?: "--",
                unit = "ms",
                valueColor = WearifyInk,
                large = false,
            )
            ContactMicPreviewPlot(
                points = contactMicAnalysis.filteredPoints,
                pairs = contactMicAnalysis.s1s2Pairs,
                accent = AppleTeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
            )
        }
    }
}

@Composable
private fun ContactMicPreviewPlot(
    points: List<ContactMicPoint>,
    pairs: List<HeartSoundPair>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawContactMicPreviewPlot(points, pairs, accent)
    }
}

private fun DrawScope.drawContactMicPreviewPlot(
    points: List<ContactMicPoint>,
    pairs: List<HeartSoundPair>,
    accent: Color,
) {
    val newestTimestamp = points.lastOrNull()?.timestampMs ?: System.currentTimeMillis()
    val windowStart = newestTimestamp - CONTACT_MIC_SCOPE_WINDOW_MS
    val visible = points.filter { it.timestampMs in windowStart..newestTimestamp }
    val plot = Rect(
        left = 0f,
        top = 4.dp.toPx(),
        right = size.width,
        bottom = size.height - 4.dp.toPx(),
    )

    drawLine(
        color = WearifySeparator.copy(alpha = 0.45f),
        start = Offset(plot.left, plot.bottom),
        end = Offset(plot.right, plot.bottom),
        strokeWidth = 1.dp.toPx(),
    )

    if (visible.size < 2) {
        return
    }

    val signedWaveform = visible.any { it.amplitude < 0 }
    val maxAmplitude = if (signedWaveform) {
        max(visible.maxOf { abs(it.amplitude) }.toDouble(), 1.0)
    } else {
        max(visible.maxOf { it.amplitude }.toDouble(), 1.0)
    }
    val baselineY = if (signedWaveform) plot.center.y else plot.bottom
    val verticalScale = if (signedWaveform) plot.height * 0.42f else plot.height * 0.88f

    drawLine(
        color = WearifySeparator.copy(alpha = 0.32f),
        start = Offset(plot.left, baselineY),
        end = Offset(plot.right, baselineY),
        strokeWidth = 1.dp.toPx(),
    )
    val path = Path()
    val maxPlotPoints = (size.width * 2f).toInt().coerceAtLeast(180)
    val step = max(1, visible.size / maxPlotPoints)

    visible.forEachIndexed { index, point ->
        if (index % step != 0 && index != visible.lastIndex) return@forEachIndexed
        val xNorm = ((point.timestampMs - windowStart).toFloat() / CONTACT_MIC_SCOPE_WINDOW_MS.toFloat())
            .coerceIn(0f, 1f)
        val yNorm = (point.amplitude / maxAmplitude).toFloat().coerceIn(-1f, 1f)
        val x = plot.left + xNorm * plot.width
        val y = baselineY - yNorm * verticalScale
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = accent.copy(alpha = 0.18f),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = accent.copy(alpha = 0.95f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
    )
    val markerPaint = Paint().apply {
        color = WearifyMuted.toArgb()
        textSize = 10.dp.toPx()
        isAntiAlias = true
    }
    pairs.flatMap { pair -> listOf("S1" to pair.s1TimestampMs, "S2" to pair.s2TimestampMs) }
        .filter { (_, timestamp) -> timestamp in windowStart..newestTimestamp }
        .takeLast(4)
        .forEach { (label, timestamp) ->
            val xNorm = ((timestamp - windowStart).toFloat() / CONTACT_MIC_SCOPE_WINDOW_MS.toFloat())
                .coerceIn(0f, 1f)
            val x = plot.left + xNorm * plot.width
            drawLine(
                color = accent.copy(alpha = 0.28f),
                start = Offset(x, plot.top + 12.dp.toPx()),
                end = Offset(x, plot.bottom),
                strokeWidth = 1.dp.toPx(),
            )
            drawContext.canvas.nativeCanvas.drawText(label, x + 3.dp.toPx(), plot.top + 10.dp.toPx(), markerPaint)
        }
}

@Composable
private fun SquareMetricWidget(
    title: String,
    value: String,
    unit: String,
    caption: String,
    accent: Color,
    icon: @Composable () -> Unit,
    onOpenOscilloscope: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassWidgetSurface(
        modifier = modifier,
        accent = accent,
        containerColor = WearifyGlass,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CompactWidgetHeader(
                title = title,
                accent = accent,
                icon = icon,
                onOpenOscilloscope = onOpenOscilloscope,
                plotDescription = "Open $title plot",
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                MetricValue(
                    value = value,
                    unit = unit,
                    valueColor = WearifyInk,
                    large = false,
                )
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        color = WearifyMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionWidget(
    summary: MotionSummary,
    onOpenOscilloscope: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassWidgetSurface(
        modifier = modifier,
        accent = AppleBlue,
        containerColor = WearifyGlass,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CompactWidgetHeader(
                title = "Motion",
                accent = AppleBlue,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                    )
                },
                onOpenOscilloscope = onOpenOscilloscope,
                plotDescription = "Open motion plot",
            )
            Text(
                text = summary.level.label,
                color = WearifyInk,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AmbientLightWidget(
    summary: EnvironmentSummary,
    onOpenOscilloscope: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (summary.environment == EnvironmentEstimate.Outdoor) EnvironmentalSunColor else AppleTeal
    val icon = if (summary.environment == EnvironmentEstimate.Outdoor) Icons.Default.WbSunny else Icons.Default.Home

    SquareMetricWidget(
        title = "Light",
        value = summary.light.label,
        unit = "",
        caption = summary.environment.label,
        accent = accent,
        icon = { Icon(icon, contentDescription = null) },
        onOpenOscilloscope = onOpenOscilloscope,
        modifier = modifier,
    )
}

@Composable
private fun GlassWidgetSurface(
    modifier: Modifier = Modifier,
    accent: Color,
    containerColor: Color,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.18f)), shape),
    ) {
        content()
    }
}

@Composable
private fun WidgetHeader(
    title: String,
    accent: Color,
    icon: @Composable () -> Unit,
    onOpenOscilloscope: () -> Unit,
    plotDescription: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetIcon(accent = accent, content = icon)
            Text(
                text = title,
                color = WearifyInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlotIconButton(
            accent = accent,
            contentDescription = plotDescription,
            onClick = onOpenOscilloscope,
        )
    }
}

@Composable
private fun CompactWidgetHeader(
    title: String,
    accent: Color,
    icon: @Composable () -> Unit,
    onOpenOscilloscope: () -> Unit,
    plotDescription: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetIcon(accent = accent, content = icon)
            PlotIconButton(
                accent = accent,
                contentDescription = plotDescription,
                onClick = onOpenOscilloscope,
            )
        }
        Text(
            text = title,
            color = WearifyInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WidgetIcon(
    accent: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(34.dp),
        color = accent.copy(alpha = 0.12f),
        contentColor = accent,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun PlotIconButton(
    accent: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = accent.copy(alpha = 0.10f),
        contentColor = accent,
        shape = CircleShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ShowChart,
            contentDescription = contentDescription,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun MetricValue(
    value: String,
    unit: String,
    valueColor: Color,
    large: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = value,
            color = valueColor,
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = unit,
            color = WearifyMuted,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = if (large) 7.dp else 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun OscilloscopeDialog(
    section: DashboardSection,
    samples: List<SensorSample>,
    contactMicPoints: List<ContactMicPoint>,
    contactMicAnalysis: ContactMicAnalysis,
    ppgAnalysis: PpgAnalysis,
    onDismiss: () -> Unit,
) {
    val palette = OscilloscopePalette(
        red = ApplePink,
        green = AppleGreen,
        blue = AppleBlue,
        accent = AppleOrange,
    )
    val panels = section.panels(
        samples,
        contactMicPoints,
        contactMicAnalysis,
        ppgAnalysis,
        palette,
    )
    val sharedEndTimestampMs = sharedOscilloscopeEndTimestampMs(
        panels.flatMap { panel ->
            panel.series.map { it.points } + panel.markers.map { it.points }
        },
    )
    val pointCount = panels.sumOf { panel ->
        if (sharedEndTimestampMs == null) {
            panel.series.sumOf { it.points.size }
        } else {
            val windowStart = sharedEndTimestampMs - panel.windowMs
            panel.series.sumOf { series ->
                series.points.count { it.first in windowStart..sharedEndTimestampMs }
            }
        }
    }
    val windowLabel = panels.maxOfOrNull { panel -> panel.windowMs }
        ?.let(::formatWindowLabel)
        ?: formatWindowLabel(OSCILLOSCOPE_WINDOW_MS)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            color = WearifyBackground,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(30.dp),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${section.title} Oscilloscope",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${section.axisSummary} · ${pointCount} pts · $windowLabel",
                            color = WearifySecondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (section == DashboardSection.All) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        panels.forEach { panel ->
                            OscilloscopePanel(
                                panel = panel,
                                referenceEndTimestampMs = sharedEndTimestampMs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                            )
                        }
                    }
                } else {
                    panels.forEach { panel ->
                        OscilloscopePanel(
                            panel = panel,
                            referenceEndTimestampMs = sharedEndTimestampMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OscilloscopePanel(
    panel: OscilloscopePanelData,
    referenceEndTimestampMs: Long?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = WearifySurface,
        contentColor = WearifyMuted,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, WearifySeparator.copy(alpha = 0.62f)),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = panel.title,
                    color = WearifyInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    panel.series.forEach { series ->
                        Surface(
                            color = series.color.copy(alpha = 0.10f),
                            contentColor = series.color,
                            shape = RoundedCornerShape(50.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(series.color),
                                )
                                Text(
                                    text = series.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    panel.markers.forEach { markerSet ->
                        Surface(
                            color = markerSet.color.copy(alpha = 0.10f),
                            contentColor = markerSet.color,
                            shape = RoundedCornerShape(50.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, markerSet.color, CircleShape),
                                )
                                Text(
                                    text = markerSet.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            MultiSeriesOscilloscopeChart(
                series = panel.series,
                markers = panel.markers,
                yAxisLabel = panel.yAxisLabel,
                windowMs = panel.windowMs,
                referenceEndTimestampMs = referenceEndTimestampMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun MultiSeriesOscilloscopeChart(
    series: List<ScopeSeries>,
    markers: List<ScopeMarkerSet>,
    yAxisLabel: String,
    windowMs: Long,
    referenceEndTimestampMs: Long?,
    modifier: Modifier = Modifier,
) {
    val gridColor = WearifySeparator
    val axisColor = WearifySecondaryText
    val labelColor = WearifyMuted

    Canvas(modifier = modifier) {
        val newestTimestamp = referenceEndTimestampMs ?: series
            .asSequence()
            .flatMap { it.points.asSequence() }
            .maxOfOrNull { it.first }
            ?: markers
                .asSequence()
                .flatMap { it.points.asSequence() }
                .maxOfOrNull { it.first }
            ?: System.currentTimeMillis()
        val windowStart = newestTimestamp - windowMs
        val visibleSeries = series.map { scopeSeries ->
            scopeSeries.copy(points = scopeSeries.points.filter { it.first in windowStart..newestTimestamp })
        }
        val values = visibleSeries.flatMap { scopeSeries -> scopeSeries.points.map { it.second } }
        val yMin: Double
        val yMax: Double
        if (values.isEmpty()) {
            yMin = -1.0
            yMax = 1.0
        } else {
            val minValue = values.min()
            val maxValue = values.max()
            val padding = max((maxValue - minValue) * 0.14, 1e-6)
            yMin = minValue - padding
            yMax = maxValue + padding
        }

        val plot = Rect(
            left = 58.dp.toPx(),
            top = 20.dp.toPx(),
            right = size.width - 12.dp.toPx(),
            bottom = size.height - 42.dp.toPx(),
        )

        drawScopeGrid(plot, gridColor)
        drawScopeAxes(
            plot = plot,
            yMin = yMin,
            yMax = yMax,
            axisColor = axisColor,
            labelColor = labelColor,
            yAxisLabel = yAxisLabel,
            windowMs = windowMs,
        )

        visibleSeries.forEach { scopeSeries ->
            drawScopeSeries(
                points = scopeSeries.points,
                lineColor = scopeSeries.color,
                plot = plot,
                windowStart = windowStart,
                windowMs = windowMs,
                yMin = yMin,
                yMax = yMax,
            )
        }
        markers.forEach { markerSet ->
            drawScopeMarkers(
                points = markerSet.points.filter { it.first in windowStart..newestTimestamp },
                markerColor = markerSet.color,
                plot = plot,
                windowStart = windowStart,
                windowMs = windowMs,
                yMin = yMin,
                yMax = yMax,
            )
        }
    }
}

private fun DrawScope.drawScopeMarkers(
    points: List<Pair<Long, Double>>,
    markerColor: Color,
    plot: Rect,
    windowStart: Long,
    windowMs: Long,
    yMin: Double,
    yMax: Double,
) {
    points.forEach { point ->
        val xNorm = ((point.first - windowStart).toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
        val yNorm = ((point.second - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
        val center = Offset(
            x = plot.left + xNorm * plot.width,
            y = plot.bottom - yNorm * plot.height,
        )
        drawCircle(markerColor.copy(alpha = 0.22f), radius = 6.dp.toPx(), center = center)
        drawCircle(markerColor, radius = 3.1.dp.toPx(), center = center)
        drawCircle(WearifySurface, radius = 1.25.dp.toPx(), center = center)
    }
}

private fun DrawScope.drawScopeSeries(
    points: List<Pair<Long, Double>>,
    lineColor: Color,
    plot: Rect,
    windowStart: Long,
    windowMs: Long,
    yMin: Double,
    yMax: Double,
) {
    if (points.size < 2) return

    val path = Path()
    val maxPlotPoints = (plot.width * 2f).toInt().coerceAtLeast(180)
    val step = max(1, points.size / maxPlotPoints)
    points.forEachIndexed { index, point ->
        if (index % step != 0 && index != points.lastIndex) return@forEachIndexed
        val xNorm = ((point.first - windowStart).toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
        val yNorm = ((point.second - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
        val x = plot.left + xNorm * plot.width
        val y = plot.bottom - yNorm * plot.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = lineColor.copy(alpha = 0.13f),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
    )

    points.lastOrNull()?.let { point ->
        val xNorm = ((point.first - windowStart).toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
        val yNorm = ((point.second - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
        val x = plot.left + xNorm * plot.width
        val y = plot.bottom - yNorm * plot.height
        drawCircle(lineColor.copy(alpha = 0.18f), radius = 6.dp.toPx(), center = Offset(x, y))
        drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(x, y))
    }
}

private fun DrawScope.drawScopeGrid(
    plot: Rect,
    gridColor: Color,
) {
    for (i in 0..4) {
        val y = plot.top + plot.height * i / 4f
        drawLine(
            color = gridColor.copy(alpha = if (i == 2) 0.58f else 0.34f),
            start = Offset(plot.left, y),
            end = Offset(plot.right, y),
            strokeWidth = if (i == 2) 1.dp.toPx() else 0.8.dp.toPx(),
        )
    }
    for (i in 0..6) {
        val x = plot.left + plot.width * i / 6f
        drawLine(
            color = gridColor.copy(alpha = if (i == 6) 0.58f else 0.30f),
            start = Offset(x, plot.top),
            end = Offset(x, plot.bottom),
            strokeWidth = if (i == 6) 1.dp.toPx() else 0.8.dp.toPx(),
        )
    }
}

private fun DrawScope.drawScopeAxes(
    plot: Rect,
    yMin: Double,
    yMax: Double,
    axisColor: Color,
    labelColor: Color,
    yAxisLabel: String,
    windowMs: Long,
) {
    val labelPaint = Paint().apply {
        color = labelColor.toArgb()
        textSize = 10.5.dp.toPx()
        isAntiAlias = true
    }
    val axisStroke = 1.dp.toPx()
    drawLine(axisColor.copy(alpha = 0.58f), Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), axisStroke)
    drawLine(axisColor.copy(alpha = 0.58f), Offset(plot.left, plot.top), Offset(plot.left, plot.bottom), axisStroke)

    val yLabels = listOf(yMax, (yMin + yMax) / 2.0, yMin)
    val yPositions = listOf(plot.top + 4.dp.toPx(), plot.center.y + 4.dp.toPx(), plot.bottom)
    yLabels.zip(yPositions).forEach { (value, y) ->
        drawContext.canvas.nativeCanvas.drawText(
            compactNumber(value),
            2.dp.toPx(),
            y,
            labelPaint,
        )
    }

    val xLabels = listOf(
        "-${formatWindowLabel(windowMs)}" to plot.left,
        "-${formatWindowLabel(windowMs / 2)}" to plot.left + plot.width / 2f,
        "now" to plot.right - 24.dp.toPx(),
    )
    xLabels.forEach { (label, x) ->
        drawContext.canvas.nativeCanvas.drawText(
            label,
            x,
            plot.bottom + 20.dp.toPx(),
            labelPaint,
        )
    }
    drawContext.canvas.nativeCanvas.drawText(
        oscilloscopeTimeAxisTitle(windowMs),
        plot.left + plot.width * 0.38f,
        plot.bottom + 36.dp.toPx(),
        labelPaint,
    )
    drawContext.canvas.nativeCanvas.drawText(
        yAxisLabel,
        plot.left,
        plot.top - 6.dp.toPx(),
        labelPaint,
    )
}

private enum class DashboardSection(
    val title: String,
    val axisSummary: String,
) {
    All("All signals", "garment signals"),
    Vitals("Vitals", "PPG / Temp"),
    ContactMic("Heart Sound", "Filtered / envelope"),
    Motion("Motion", "Accel / Gyro"),
    Environmental("Environmental", "UV / ALS");

    fun panels(
        samples: List<SensorSample>,
        contactMicPoints: List<ContactMicPoint>,
        contactMicAnalysis: ContactMicAnalysis,
        ppgAnalysis: PpgAnalysis,
        palette: OscilloscopePalette,
    ): List<OscilloscopePanelData> =
        when (this) {
            All -> {
                val vitals = Vitals.panels(
                    samples,
                    contactMicPoints,
                    contactMicAnalysis,
                    ppgAnalysis,
                    palette,
                )
                vitals.take(1) +
                    ContactMic.panels(samples, contactMicPoints, contactMicAnalysis, ppgAnalysis, palette) +
                    Motion.panels(samples, contactMicPoints, contactMicAnalysis, ppgAnalysis, palette) +
                    vitals.drop(1) +
                    Environmental.panels(samples, contactMicPoints, contactMicAnalysis, ppgAnalysis, palette)
            }

            Vitals -> listOf(
                OscilloscopePanelData(
                    title = "PPG",
                    yAxisLabel = "Normalized",
                    series = listOf(
                        ScopeSeries(
                            "Processed",
                            palette.red,
                            ppgAnalysis.processedPoints.map { it.timestampMs to it.normalized },
                        ),
                    ),
                    markers = listOf(
                        ScopeMarkerSet(
                            label = "Candidate peaks",
                            color = palette.accent,
                            points = ppgOscilloscopePeakPoints(ppgAnalysis),
                        ),
                    ),
                ),
                OscilloscopePanelData(
                    title = "Temperature",
                    yAxisLabel = "Temp, °C",
                    series = listOf(ScopeSeries("Temp", palette.accent, samples.points { it.tempC })),
                ),
            )

            ContactMic -> listOf(
                OscilloscopePanelData(
                    title = "Heart Thump",
                    yAxisLabel = "Normalized",
                    windowMs = CONTACT_MIC_SCOPE_WINDOW_MS,
                    series = listOf(
                        ScopeSeries("18-140 Hz", AppleBlue, contactMicAnalysis.filteredPoints.pcmPoints()),
                        ScopeSeries("Envelope", AppleOrange, contactMicAnalysis.envelopePoints.pcmPoints()),
                    ),
                ),
            )

            Motion -> listOf(
                OscilloscopePanelData(
                    title = "Acceleration",
                    yAxisLabel = "Accel, g",
                    series = listOf(
                        ScopeSeries("Ax", palette.red, samples.points { it.ax }),
                        ScopeSeries("Ay", palette.green, samples.points { it.ay }),
                        ScopeSeries("Az", palette.blue, samples.points { it.az }),
                    ),
                ),
                OscilloscopePanelData(
                    title = "Gyroscope",
                    yAxisLabel = "Gyro, deg/s",
                    series = listOf(
                        ScopeSeries("Gx", palette.red, samples.points { it.gx }),
                        ScopeSeries("Gy", palette.green, samples.points { it.gy }),
                        ScopeSeries("Gz", palette.blue, samples.points { it.gz }),
                    ),
                ),
            )

            Environmental -> listOf(
                OscilloscopePanelData(
                    title = "UV",
                    yAxisLabel = "UV raw",
                    series = listOf(ScopeSeries("UV", palette.accent, samples.points { it.uv })),
                ),
                OscilloscopePanelData(
                    title = "Ambient Light",
                    yAxisLabel = "ALS raw",
                    series = listOf(ScopeSeries("ALS", palette.blue, samples.points { it.als })),
                ),
            )

        }
}

internal fun allOscilloscopePanelTitles(): List<String> =
    DashboardSection.All.panels(
        samples = emptyList(),
        contactMicPoints = emptyList(),
        contactMicAnalysis = ContactMicAnalysis(),
        ppgAnalysis = PpgAnalysis(),
        palette = OscilloscopePalette(
            red = ApplePink,
            green = AppleGreen,
            blue = AppleBlue,
            accent = AppleOrange,
        ),
    ).map { it.title }

private data class OscilloscopePalette(
    val red: Color,
    val green: Color,
    val blue: Color,
    val accent: Color,
)

private data class OscilloscopePanelData(
    val title: String,
    val yAxisLabel: String,
    val series: List<ScopeSeries>,
    val markers: List<ScopeMarkerSet> = emptyList(),
    val windowMs: Long = OSCILLOSCOPE_WINDOW_MS,
)

private data class ScopeSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>,
)

private data class ScopeMarkerSet(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>,
)

internal fun ppgOscilloscopePeakPoints(ppgAnalysis: PpgAnalysis): List<Pair<Long, Double>> =
    ppgAnalysis.peaks.map { peak -> peak.timestampMs to peak.normalized }

private fun List<SensorSample>.points(selector: (SensorSample) -> Double?): List<Pair<Long, Double>> =
    mapNotNull { sample ->
        selector(sample)
            ?.takeIf { it.isFinite() }
            ?.let { sample.timestampMs to it }
    }

private fun List<ContactMicPoint>.pcmPoints(): List<Pair<Long, Double>> =
    map { point -> point.timestampMs to point.amplitude.toDouble() }

fun SensorSample?.toSensorData(): SensorData =
    SensorData(
        ppgIr = this?.ir,
        tempC = this?.tempC,
        ax = this?.ax,
        ay = this?.ay,
        az = this?.az,
        gx = this?.gx,
        gy = this?.gy,
        gz = this?.gz,
        uv = this?.uv,
        als = this?.als,
    )

private fun formatValue(value: Double?, decimals: Int = 2): String =
    value?.let { "%.${decimals}f".format(it) } ?: "--"

private fun compactNumber(value: Double): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 100_000 -> "%.0fk".format(value / 1_000)
        abs >= 10_000 -> "%.0fk".format(value / 1_000)
        abs >= 1_000 -> "%.1fk".format(value / 1_000)
        abs >= 100 -> "%.0f".format(value)
        abs >= 10 -> "%.1f".format(value)
        else -> "%.2f".format(value)
    }
}

private fun formatWindowLabel(windowMs: Long): String =
    if (windowMs % 1_000L == 0L) {
        "${windowMs / 1_000L}s"
    } else {
        "${windowMs}ms"
    }

internal fun oscilloscopeTimeAxisTitle(windowMs: Long): String =
    if (windowMs % 1_000L == 0L) "Time, s" else "Time, ms"

internal fun sharedOscilloscopeEndTimestampMs(
    pointGroups: Iterable<Iterable<Pair<Long, Double>>>,
): Long? = pointGroups.asSequence()
    .flatMap { it.asSequence() }
    .maxOfOrNull { it.first }

@Preview(showBackground = true)
@Composable
private fun WearifyUpdateDashboardPreview() {
    val previewSamples = previewSensorSamples()
    val previewMicPoints = previewContactMicPoints()
    val previewMicAnalysis = previewContactMicAnalysis(previewMicPoints)
    val previewPpgAnalysis = PpgAnalyzer.analyze(previewSamples)
    WearifyTheme {
        WearifyUpdateDashboard(
            sensorData = previewSamples.lastOrNull().toSensorData(),
            samples = previewSamples,
            contactMicPoints = previewMicPoints,
            contactMicAnalysis = previewMicAnalysis,
            ppgAnalysis = previewPpgAnalysis,
        )
    }
}

private fun previewSensorSamples(): List<SensorSample> {
    val now = 100_000L
    return List(180) { index ->
        val t = now - (179 - index) * 80L
        val phase = index / 8.0
        SensorSample(
            timestampMs = t,
            deviceUptimeMs = t,
            als = 500.0 + sin(phase / 3.0) * 80.0,
            uv = 10.0 + sin(phase / 4.0) * 4.0,
            tempC = 24.0 + sin(phase / 8.0) * 0.6,
            ax = -0.12 + sin(phase) * 0.05,
            ay = -0.10 + sin(phase * 0.8) * 0.06,
            az = 0.98 + sin(phase * 0.7) * 0.03,
            gx = -7.0 + sin(phase * 1.2) * 2.0,
            gy = -2.3 + sin(phase * 0.9) * 1.4,
            gz = 2.1 + sin(phase * 1.4) * 1.0,
            ir = 86_000.0 + sin(phase * 1.5) * 4_200.0,
        )
    }
}

private fun previewContactMicPoints(): List<ContactMicPoint> {
    val now = 100_000L
    return List(1_200) { index ->
        val sampleIndex = (1_199 - index).toLong()
        val timestamp = now - sampleIndex / 4L
        val phase = index / 8.0
        ContactMicPoint(
            timestampMs = timestamp,
            amplitude = ((sin(phase * 1.7) * 9_000.0) + (sin(phase * 0.17) * 2_400.0)).toInt(),
        )
    }
}

private fun previewContactMicAnalysis(points: List<ContactMicPoint>): ContactMicAnalysis {
    val filtered = points.mapIndexed { index, point ->
        ContactMicPoint(
            timestampMs = point.timestampMs,
            amplitude = (sin(index / 11.0) * 5_600.0).toInt(),
        )
    }
    val envelope = points.mapIndexed { index, point ->
        ContactMicPoint(
            timestampMs = point.timestampMs,
            amplitude = (kotlin.math.abs(sin(index / 38.0)) * 4_800.0).toInt(),
        )
    }
    val lastTimestamp = points.lastOrNull()?.timestampMs ?: 0L
    return ContactMicAnalysis(
        filteredPoints = filtered,
        envelopePoints = envelope,
        s1s2Pairs = listOf(
            HeartSoundPair(
                s1TimestampMs = lastTimestamp - 720L,
                s2TimestampMs = lastTimestamp - 480L,
                gapMs = 240L,
            ),
            HeartSoundPair(
                s1TimestampMs = lastTimestamp - 260L,
                s2TimestampMs = lastTimestamp - 20L,
                gapMs = 240L,
            ),
        ),
        bpm = 72.0,
        regularityCvPct = 4.5,
        qualityScore = 86.0,
        verdict = ContactMicQualityVerdict.GoodContact,
        reasons = listOf("none"),
    )
}
