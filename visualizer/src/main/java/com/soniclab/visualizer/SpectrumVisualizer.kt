package com.soniclab.visualizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Glowing bar visualizer for the player screen.
 */
@Composable
fun SpectrumVisualizer(
    buckets: FloatArray,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF7C4DFF),
    accentColor: Color = Color(0xFF00E5FF)
) {
    val animated by animateFloatAsState(
        targetValue = if (buckets.any { it > 0.02f }) 1f else 0.15f,
        animationSpec = tween(durationMillis = 250),
        label = "vis"
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (buckets.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (buckets.size - 1)) / buckets.size
        buckets.forEachIndexed { index, value ->
            val h = (value * animated * size.height).coerceIn(2.dp.toPx(), size.height)
            val x = index * (barWidth + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(accentColor, barColor),
                    startY = size.height - h,
                    endY = size.height
                ),
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}
