package com.soniclab.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Static waveform display for analyzed files.
 */
@Composable
fun WaveformVisualizer(
    waveform: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF7C4DFF),
    heightDp: Int = 120
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        if (waveform.isEmpty()) return@Canvas
        val midY = size.height / 2
        val step = size.width / waveform.size
        waveform.forEachIndexed { index, (min, max) ->
            val x = index * step + step / 2
            val top = midY - max * midY
            val bottom = midY - min * midY
            drawLine(
                color = color,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = step.coerceIn(1f, 4f),
                cap = StrokeCap.Round
            )
        }
    }
}
