package com.wearify.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

private const val OSCILLOSCOPE_WINDOW_MS = 12_000L

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>,
)

@Composable
fun LiveChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
) {
    val newestTimestamp = series
        .asSequence()
        .flatMap { it.points.asSequence() }
        .maxOfOrNull { it.first }
        ?: System.currentTimeMillis()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            series.forEach {
                Text(
                    text = it.label,
                    color = it.color,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            drawOscilloscopeChart(
                series = series,
                newestTimestamp = newestTimestamp,
            )
        }
    }
}

private fun DrawScope.drawOscilloscopeChart(
    series: List<ChartSeries>,
    newestTimestamp: Long,
) {
    val labelWidth = 48.dp.toPx()
    val bottomAxisHeight = 24.dp.toPx()
    val topPadding = 4.dp.toPx()
    val plot = Rect(
        left = labelWidth,
        top = topPadding,
        right = size.width,
        bottom = size.height - bottomAxisHeight,
    )

    val visibleStart = newestTimestamp - OSCILLOSCOPE_WINDOW_MS
    val visibleSeries = series.map { chartSeries ->
        chartSeries.copy(points = chartSeries.points.filter { it.first >= visibleStart && it.first <= newestTimestamp })
    }
    val allVisiblePoints = visibleSeries.flatMap { it.points }

    val range = if (allVisiblePoints.isEmpty()) {
        -1.0 to 1.0
    } else {
        val minValue = allVisiblePoints.minOf { it.second }
        val maxValue = allVisiblePoints.maxOf { it.second }
        val padding = max((maxValue - minValue) * 0.12, 1e-6)
        (minValue - padding) to (maxValue + padding)
    }

    drawGrid(plot)
    drawAxes(plot, range)

    visibleSeries.forEach { chartSeries ->
        drawSeries(
            series = chartSeries,
            plot = plot,
            newestTimestamp = newestTimestamp,
            yMin = range.first,
            yMax = range.second,
        )
    }
}

private fun DrawScope.drawGrid(plot: Rect) {
    val minor = WearifyGrid.copy(alpha = 0.58f)
    val major = WearifyGrid
    for (i in 0..4) {
        val y = plot.top + plot.height * i / 4f
        drawLine(
            color = if (i == 2) major else minor,
            start = Offset(plot.left, y),
            end = Offset(plot.right, y),
            strokeWidth = if (i == 2) 1.2.dp.toPx() else 0.8.dp.toPx(),
        )
    }
    for (i in 0..6) {
        val x = plot.left + plot.width * i / 6f
        drawLine(
            color = if (i == 6) major else minor,
            start = Offset(x, plot.top),
            end = Offset(x, plot.bottom),
            strokeWidth = if (i == 6) 1.2.dp.toPx() else 0.8.dp.toPx(),
        )
    }
}

private fun DrawScope.drawAxes(
    plot: Rect,
    range: Pair<Double, Double>,
) {
    val axisColor = WearifyMuted.copy(alpha = 0.56f)
    drawLine(axisColor, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), 1.dp.toPx())
    drawLine(axisColor, Offset(plot.left, plot.top), Offset(plot.left, plot.bottom), 1.dp.toPx())

    val yLabels = listOf(range.second, (range.first + range.second) / 2.0, range.first)
    val yPositions = listOf(plot.top + 10.dp.toPx(), plot.center.y + 4.dp.toPx(), plot.bottom - 2.dp.toPx())
    yLabels.zip(yPositions).forEach { (value, y) ->
        drawContext.canvas.nativeCanvas.drawText(
            compactNumber(value),
            4.dp.toPx(),
            y,
            android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(95, 99, 104)
                textSize = 10.dp.toPx()
                isAntiAlias = true
            },
        )
    }

    val xLabels = listOf("-12s" to plot.left, "-6s" to plot.left + plot.width / 2f, "now" to plot.right - 24.dp.toPx())
    xLabels.forEach { (label, x) ->
        drawContext.canvas.nativeCanvas.drawText(
            label,
            x,
            plot.bottom + 18.dp.toPx(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(95, 99, 104)
                textSize = 10.dp.toPx()
                isAntiAlias = true
            },
        )
    }
}

private fun DrawScope.drawSeries(
    series: ChartSeries,
    plot: Rect,
    newestTimestamp: Long,
    yMin: Double,
    yMax: Double,
) {
    val points = downsample(series.points, plot.width)
    if (points.size < 2) return

    val path = Path()
    points.forEachIndexed { index, point ->
        val ageMs = newestTimestamp - point.first
        val xNorm = 1f - (ageMs.toFloat() / OSCILLOSCOPE_WINDOW_MS.toFloat())
        val x = plot.left + xNorm.coerceIn(0f, 1f) * plot.width
        val yNorm = ((point.second - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
        val y = plot.bottom - yNorm * plot.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = series.color.copy(alpha = 0.96f),
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
    )

    points.lastOrNull()?.let { last ->
        val ageMs = newestTimestamp - last.first
        val xNorm = 1f - (ageMs.toFloat() / OSCILLOSCOPE_WINDOW_MS.toFloat())
        val x = plot.left + xNorm.coerceIn(0f, 1f) * plot.width
        val yNorm = ((last.second - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
        val y = plot.bottom - yNorm * plot.height
        drawCircle(series.color, radius = 3.dp.toPx(), center = Offset(x, y))
    }
}

private fun downsample(points: List<Pair<Long, Double>>, widthPx: Float): List<Pair<Long, Double>> {
    if (points.size.toFloat() <= widthPx || widthPx <= 0f) return points
    val step = ceil(points.size / widthPx).toInt().coerceAtLeast(1)
    val sampled = mutableListOf<Pair<Long, Double>>()
    var index = 0
    while (index < points.size) {
        sampled += points[index]
        index += step
    }
    if (sampled.lastOrNull() != points.lastOrNull()) {
        sampled += points.last()
    }
    return sampled
}

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
