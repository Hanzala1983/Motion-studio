package com.example.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EffectParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatString: String = "%.2f",
    formatAsPercent: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label Box
        Box(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .background(Color(0xFF262C3A), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, color = Color(0xFF00E676), fontSize = 11.sp, maxLines = 1)
        }
        
        // Slider Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF1B1E28))
                .border(0.5.dp, Color(0xFF2C3242))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onValueChange(range.start + frac * (range.endInclusive - range.start))
                        },
                        onDrag = { change, _ ->
                            val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                            onValueChange(range.start + frac * (range.endInclusive - range.start))
                        }
                    )
                }
        ) {
            // Tick marks
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tickCount = 40
                val tickSpacing = size.width / tickCount
                for (i in 0..tickCount) {
                    val x = i * tickSpacing
                    val height = if (i == tickCount / 2) size.height else size.height * 0.4f
                    val y = (size.height - height) / 2
                    drawLine(
                        color = Color(0xFF3B4456),
                        start = Offset(x, y),
                        end = Offset(x, y + height),
                        strokeWidth = 1f
                    )
                }
                
                // Thumb indicator
                val fraction = (value - range.start) / (range.endInclusive - range.start)
                val thumbX = fraction * size.width
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(thumbX, 0f),
                    end = Offset(thumbX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        // Value Box
        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight()
                .background(Color(0xFF262C3A), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val displayValue = if (formatAsPercent) {
                val pct = (value * 100).toInt()
                if (pct > 0) "+$pct%" else "$pct%"
            } else {
                String.format(formatString, value)
            }
            Text(displayValue, color = Color(0xFF90A4AE), fontSize = 12.sp)
        }
    }
}
