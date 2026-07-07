package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ColorPaletteScreen(
    currentColor: Color = Color(0xFF16B996),
    onColorChange: (Color) -> Unit = {},
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF161821))) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Cyclone, "Color", tint = Color(0xFF16B996), modifier = Modifier.size(24.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Tune, "Tune", tint = Color.Gray, modifier = Modifier.size(24.dp))
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF262934)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Done", tint = Color.White)
            }
        }
        
        // Color wheel area
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
                val strokeWidth = 24.dp.toPx()
                val radius = size.width / 2 - strokeWidth / 2
                
                // Draw color wheel
                val colors = listOf(
                    Color.Red, Color.Magenta, Color.Blue, Color.Cyan, 
                    Color.Green, Color.Yellow, Color.Red
                )
                val sweepGradient = Brush.sweepGradient(colors, center = Offset(size.width / 2, size.height / 2))
                drawCircle(brush = sweepGradient, radius = radius, style = Stroke(width = strokeWidth))
                
                // Draw dial marks
                val lines = 12
                for (i in 0 until lines) {
                    val angle = (i * 30).toDouble() * Math.PI / 180.0
                    val innerRadius = radius - strokeWidth / 2 - 8.dp.toPx()
                    val outerRadius = innerRadius + 6.dp.toPx()
                    
                    val innerOffsetX = size.width / 2 + innerRadius * cos(angle).toFloat()
                    val innerOffsetY = size.height / 2 + innerRadius * sin(angle).toFloat()
                    val outerOffsetX = size.width / 2 + outerRadius * cos(angle).toFloat()
                    val outerOffsetY = size.height / 2 + outerRadius * sin(angle).toFloat()
                    
                    val markColor = if (i % 3 == 0) Color.Gray else Color.DarkGray
                    drawLine(markColor, Offset(innerOffsetX, innerOffsetY), Offset(outerOffsetX, outerOffsetY), strokeWidth = 1.dp.toPx())
                }
                
                // Draw knob at 240 degrees (blue)
                val knobAngle = -120.0 * Math.PI / 180.0 // Adjusted for visual position
                val knobX = size.width / 2 + radius * cos(knobAngle).toFloat()
                val knobY = size.height / 2 + radius * sin(knobAngle).toFloat()
                drawCircle(Color.White, radius = strokeWidth / 2 + 4.dp.toPx(), center = Offset(knobX, knobY), style = Stroke(width = 3.dp.toPx()))
            }
            
            // Center info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("240°", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Normal)
                Spacer(Modifier.height(32.dp))
                Box(modifier = Modifier.size(72.dp, 48.dp).background(currentColor, RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp))) {
                    Icon(Icons.Default.AddCircleOutline, null, tint = Color.White.copy(0.5f), modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp))
                }
                Spacer(Modifier.height(8.dp))
                val r = (currentColor.red * 255).toInt()
                val g = (currentColor.green * 255).toInt()
                val b = (currentColor.blue * 255).toInt()
                val a = (currentColor.alpha * 100).toInt()
                val hexCode = String.format("#%02X%02X%02X (%d%%)", r, g, b, a)
                Text(hexCode, color = Color.White, fontSize = 14.sp)
            }
        }
        
        // Sliders
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            CustomSliderRow(Icons.Default.Tonality, 0.58f, currentColor, "58%")
            Spacer(Modifier.height(16.dp))
            CustomSliderRow(Icons.Default.WbSunny, 0.68f, currentColor, "68%")
            Spacer(Modifier.height(16.dp))
            CustomSliderRow(Icons.Default.Contrast, 1.0f, Color.White, "100%", isAlpha = true)
            Spacer(Modifier.height(32.dp))
        }
        
        // Swatches
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val colors1 = listOf(
                Color(0xFFF44336), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF4CAF50), 
                Color(0xFF00BCD4), Color(0xFF3F51B5), Color(0xFFE91E63)
            )
            val colors2 = listOf(
                Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF616161), 
                Color(0xFF000000), Color(0xFF81D4FA), Color(0xFFA5D6A7)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                colors1.forEach { color ->
                    val isSelected = color == currentColor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(end = 4.dp)
                            .background(color, RoundedCornerShape(4.dp))
                            .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable { onColorChange(color) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                colors2.forEach { color ->
                    val isSelected = color == currentColor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(end = 4.dp)
                            .background(color, RoundedCornerShape(4.dp))
                            .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color.Gray else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable { onColorChange(color) }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CustomSliderRow(icon: androidx.compose.ui.graphics.vector.ImageVector, progress: Float, trackColor: Color, text: String, isAlpha: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Box(modifier = Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.CenterStart) {
            // Track background
            val trackBg = Color(0xFF262934)
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(trackBg, CircleShape))
            
            if (isAlpha) {
                 Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                      drawRoundRect(color = Color.White.copy(0.3f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                 }
            }
            
            // Active track
            val activeColor = if (isAlpha) Color(0xFF4949AD).copy(alpha = 0.5f) else trackColor
            Box(modifier = Modifier.fillMaxWidth(progress).height(8.dp).background(activeColor, CircleShape))
            
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.fillMaxWidth(progress).padding(end = 24.dp))
                    Box(modifier = Modifier.size(24.dp).background(Color.White, CircleShape).border(4.dp, Color(0xFF282E40), CircleShape))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(40.dp))
    }
}
