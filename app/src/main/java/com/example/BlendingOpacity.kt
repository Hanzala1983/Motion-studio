package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun Modifier.alightMotionLayerBlend(
    layerId: String,
    layerOpacities: Map<String, Float>,
    layerBlendModes: Map<String, String>
): Modifier = this.drawWithContent {
    val alpha = layerOpacities[layerId] ?: 1f
    val blendMode = getComposeBlendMode(layerBlendModes[layerId] ?: "Normal")
    if (blendMode == BlendMode.SrcOver && alpha == 1f) {
        drawContent()
    } else {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.alpha = alpha
                this.blendMode = blendMode
            }
            canvas.saveLayer(size.toRect(), paint)
            drawContent()
            canvas.restore()
        }
    }
}

fun getComposeBlendMode(mode: String): BlendMode {
    return when (mode) {
        "Normal" -> BlendMode.SrcOver
        "Multiply" -> BlendMode.Multiply
        "Screen" -> BlendMode.Screen
        "Overlay" -> BlendMode.Overlay
        "Darken" -> BlendMode.Darken
        "Lighten" -> BlendMode.Lighten
        "Color Dodge" -> BlendMode.ColorDodge
        "Color Burn" -> BlendMode.ColorBurn
        "Hard Light" -> BlendMode.Hardlight
        "Soft Light" -> BlendMode.Softlight
        "Difference" -> BlendMode.Difference
        "Exclusion" -> BlendMode.Exclusion
        "Hue" -> BlendMode.Hue
        "Saturation" -> BlendMode.Saturation
        "Color" -> BlendMode.Color
        "Luminosity" -> BlendMode.Luminosity
        "Linear Burn" -> BlendMode.Plus
        "Linear Dodge" -> BlendMode.Plus
        "Darker Color" -> BlendMode.Darken
        "Lighter Color" -> BlendMode.Lighten
        "Vivid Light" -> BlendMode.Hardlight
        "Linear Light" -> BlendMode.Hardlight
        "Pin Light" -> BlendMode.Softlight
        "Hard Mix" -> BlendMode.Difference
        "Subtract" -> BlendMode.Difference
        "Divide" -> BlendMode.ColorDodge
        "Mask" -> BlendMode.DstIn
        "Exclude" -> BlendMode.DstOut
        else -> BlendMode.SrcOver
    }
}

data class BlendModeCategory(
    val title: String,
    val modes: List<String>
)

val blendModeCategories = listOf(
    BlendModeCategory("Normal", listOf("Normal")),
    BlendModeCategory("Darken", listOf("Multiply", "Darken", "Darker Color", "Color Burn", "Linear Burn")),
    BlendModeCategory("Lighten", listOf("Screen", "Lighten", "Lighter Color", "Color Dodge", "Linear Dodge")),
    BlendModeCategory("Contrast", listOf("Overlay", "Soft Light", "Hard Light", "Vivid Light", "Linear Light", "Pin Light", "Hard Mix")),
    BlendModeCategory("Difference", listOf("Difference", "Exclusion", "Subtract", "Divide")),
    BlendModeCategory("Color", listOf("Hue", "Saturation", "Color", "Luminosity")),
    BlendModeCategory("Mask", listOf("Mask", "Exclude"))
)

@Composable
fun BlendThumbnailCanvas(mode: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        // Base sunset sky gradient customized per blend mode
        val skyColors = when (mode) {
            "Multiply", "Darken", "Color Burn", "Linear Burn", "Darker Color" -> 
                listOf(Color(0xFF9C3D64), Color(0xFF86436E), Color(0xFF332A5E))
            "Lighten", "Screen", "Color Dodge", "Linear Dodge", "Lighter Color" -> 
                listOf(Color(0xFFFF9EAA), Color(0xFFFFC39A), Color(0xFF8A6DC8))
            "Overlay", "Soft Light", "Hard Light", "Vivid Light", "Linear Light", "Pin Light", "Hard Mix" -> 
                listOf(Color(0xFFE24177), Color(0xFFF39252), Color(0xFF452E7E))
            "Difference", "Exclusion", "Subtract", "Divide" -> 
                listOf(Color(0xFF47B2A8), Color(0xFF9852B8), Color(0xFF243B6E))
            "Hue", "Saturation", "Color", "Luminosity" -> 
                listOf(Color(0xFF6B8CE0), Color(0xFFD469A3), Color(0xFF423B68))
            else -> listOf(Color(0xFFD3557E), Color(0xFFD88265), Color(0xFF3C3168))
        }
        
        drawRect(brush = Brush.verticalGradient(skyColors))
        
        // Sun glowing horizon
        drawCircle(
            color = Color(0xFFFFD49E).copy(alpha = 0.8f),
            radius = w * 0.22f,
            center = Offset(w * 0.5f, h * 0.6f)
        )
        
        // Silhouette bridge / towers
        val silhouetteColor = when (mode) {
            "Screen", "Color Dodge", "Lighter Color" -> Color(0xFF5D4275)
            "Difference", "Exclusion" -> Color(0xFF1B4E47)
            else -> Color(0xFF1C1330)
        }
        
        // Water at bottom
        drawRect(
            color = silhouetteColor.copy(alpha = 0.9f),
            topLeft = Offset(0f, h * 0.76f),
            size = Size(w, h * 0.24f)
        )
        
        // Left tower
        val path1 = Path().apply {
            moveTo(w * 0.18f, h * 0.76f)
            lineTo(w * 0.23f, h * 0.32f)
            lineTo(w * 0.35f, h * 0.32f)
            lineTo(w * 0.40f, h * 0.76f)
            close()
        }
        drawPath(path1, silhouetteColor)
        
        // Right tower
        val path2 = Path().apply {
            moveTo(w * 0.60f, h * 0.76f)
            lineTo(w * 0.65f, h * 0.32f)
            lineTo(w * 0.77f, h * 0.32f)
            lineTo(w * 0.82f, h * 0.76f)
            close()
        }
        drawPath(path2, silhouetteColor)
        
        // Bridge deck line
        drawLine(
            color = silhouetteColor,
            start = Offset(0f, h * 0.66f),
            end = Offset(w, h * 0.66f),
            strokeWidth = 2.5f
        )
        
        // Suspension cables
        val cablePath = Path().apply {
            moveTo(0f, h * 0.48f)
            quadraticBezierTo(w * 0.1f, h * 0.58f, w * 0.29f, h * 0.34f)
            quadraticBezierTo(w * 0.5f, h * 0.66f, w * 0.71f, h * 0.34f)
            quadraticBezierTo(w * 0.9f, h * 0.58f, w, h * 0.48f)
        }
        drawPath(cablePath, silhouetteColor, style = Stroke(width = 1.5f))
        
        // Apply actual blending mode overlay over the thumbnail
        val composeBlend = getComposeBlendMode(mode)
        if (mode != "Normal") {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2BC1E0).copy(alpha = 0.85f), Color(0xFFFF4D88).copy(alpha = 0.85f)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                blendMode = composeBlend
            )
        } else {
            drawRect(
                color = Color(0xFF2BC1E0).copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun BlendingOpacityMenu(
    currentOpacity: Float = 1.0f,
    currentBlendMode: String = "Normal",
    onOpacityChange: (Float) -> Unit = {},
    onBlendModeChange: (String) -> Unit = {},
    layerId: String = "",
    playheadProgress: Float = 0f,
    opacityKeyframes: List<OpacityKeyframe> = emptyList(),
    onToggleOpacityKeyframe: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val initialCategory = remember(currentBlendMode) {
        blendModeCategories.find { it.modes.contains(currentBlendMode) }?.title ?: "Darken"
    }
    var expandedCategories by remember { mutableStateOf(setOf("Darken", initialCategory)) }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF1B1D25))) {
        // Left Action Column
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(Color(0xFF1B1D25)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack)
            )
            
            // Keyframe Add Button
            val isCurrentOpacityKeyframe = opacityKeyframes.any { kotlin.math.abs(it.time - playheadProgress) < 0.05f }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onToggleOpacityKeyframe() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(if (isCurrentOpacityKeyframe) Color(0xFF16B996) else Color.Transparent)
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                )
                if (!isCurrentOpacityKeyframe) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Keyframe",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            
            Icon(
                Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = "Curve",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = "More",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Right Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF181A20))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top Opacity Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Striped/Transparency Circle Icon
                Canvas(modifier = Modifier.size(22.dp)) {
                    drawCircle(color = Color(0xFF262C3A))
                    drawCircle(color = Color.White.copy(alpha = currentOpacity), radius = size.minDimension / 2f * 0.7f)
                    drawCircle(color = Color.White, style = Stroke(width = 1.5.dp.toPx()))
                }
                
                Slider(
                    value = currentOpacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF16B996),
                        activeTrackColor = Color(0xFF16B996),
                        inactiveTrackColor = Color(0xFF262C3A)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(30.dp)
                        .background(Color(0xFF262C3A), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(currentOpacity * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable Accordion Categories
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(blendModeCategories) { category ->
                    val isExpanded = expandedCategories.contains(category.title)
                    val isCategorySelected = category.modes.contains(currentBlendMode)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF232634), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        // Category Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (category.title == "Normal") {
                                        onBlendModeChange("Normal")
                                    } else {
                                        expandedCategories = if (isExpanded) {
                                            expandedCategories - category.title
                                        } else {
                                            expandedCategories + category.title
                                        }
                                    }
                                }
                                .padding(vertical = 11.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (category.title != "Normal") {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = category.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCategorySelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            if (isCategorySelected) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentBlendMode,
                                        color = Color(0xFF16B996),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16B996),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Child Modes when expanded (Horizontal thumbnails)
                        if (isExpanded && category.title != "Normal") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(category.modes) { mode ->
                                    val isSelected = currentBlendMode == mode
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(64.dp)
                                            .clickable { onBlendModeChange(mode) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .then(
                                                    if (isSelected) Modifier.border(2.dp, Color(0xFF16B996), RoundedCornerShape(12.dp))
                                                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                )
                                        ) {
                                            BlendThumbnailCanvas(mode = mode)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = mode,
                                            color = if (isSelected) Color(0xFF16B996) else Color(0xFF9EABA6).copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

