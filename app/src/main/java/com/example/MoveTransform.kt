package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned

data class LayerKeyframe(
    val time: Float,
    val transform: LayerTransform,
    val cp1: Offset = Offset(0.42f, 0f), // Ease-In-Out default: P1(0.42, 0.0)
    val cp2: Offset = Offset(0.58f, 1f)  // Ease-In-Out default: P2(0.58, 1.0)
)

fun evaluateBezier(x: Float, cp1: Offset, cp2: Offset): Float {
    val x1 = cp1.x.coerceIn(0f, 1f)
    val y1 = cp1.y
    val x2 = cp2.x.coerceIn(0f, 1f)
    val y2 = cp2.y
    
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    if (x1 == y1 && x2 == y2) return x
    
    fun getX(u: Float): Float {
        return 3f * (1f - u) * (1f - u) * u * x1 + 3f * (1f - u) * u * u * x2 + u * u * u
    }
    
    fun getY(u: Float): Float {
        return 3f * (1f - u) * (1f - u) * u * y1 + 3f * (1f - u) * u * u * y2 + u * u * u
    }
    
    fun getXDerivative(u: Float): Float {
        return 3f * (1f - 3f * u + 3f * u * u) * x1 + 3f * (2f * u - 3f * u * u) * x2 + 3f * u * u
    }
    
    var u = x
    for (i in 0 until 8) {
        val currentX = getX(u) - x
        val derivative = getXDerivative(u)
        if (kotlin.math.abs(derivative) < 1e-6) break
        u -= currentX / derivative
    }
    
    if (u < 0f || u > 1f) {
        var low = 0f
        var high = 1f
        u = x
        for (i in 0 until 12) {
            val currentX = getX(u)
            if (kotlin.math.abs(currentX - x) < 1e-4) break
            if (currentX < x) {
                low = u
            } else {
                high = u
            }
            u = (low + high) / 2f
        }
    }
    
    return getY(u)
}

fun getActiveTransform(
    layerId: String,
    playheadProgress: Float,
    layerTransforms: Map<String, LayerTransform>,
    layerKeyframes: Map<String, List<LayerKeyframe>>
): LayerTransform {
    val keyframes = layerKeyframes[layerId]
    if (keyframes.isNullOrEmpty()) {
        return layerTransforms[layerId] ?: LayerTransform()
    }
    
    val sorted = keyframes.sortedBy { it.time }
    if (playheadProgress <= sorted.first().time) {
        return sorted.first().transform
    }
    if (playheadProgress >= sorted.last().time) {
        return sorted.last().transform
    }
    
    for (i in 0 until sorted.size - 1) {
        val k1 = sorted[i]
        val k2 = sorted[i+1]
        if (playheadProgress >= k1.time && playheadProgress <= k2.time) {
            val duration = k2.time - k1.time
            val t = if (duration > 0f) (playheadProgress - k1.time) / duration else 0f
            val easedT = evaluateBezier(t, k1.cp1, k1.cp2)
            
            return LayerTransform(
                offsetX = k1.transform.offsetX + (k2.transform.offsetX - k1.transform.offsetX) * easedT,
                offsetY = k1.transform.offsetY + (k2.transform.offsetY - k1.transform.offsetY) * easedT,
                rotation = k1.transform.rotation + (k2.transform.rotation - k1.transform.rotation) * easedT,
                scaleX = k1.transform.scaleX + (k2.transform.scaleX - k1.transform.scaleX) * easedT,
                scaleY = k1.transform.scaleY + (k2.transform.scaleY - k1.transform.scaleY) * easedT
            )
        }
    }
    
    return sorted.first().transform
}

// --- Opacity keyframes: a separate, independent property track from
// Move/Rotate/Scale (LayerKeyframe above). This lets a layer's opacity
// animate on its own timeline without needing a transform keyframe at the
// same times. ---
data class OpacityKeyframe(
    val time: Float,
    val opacity: Float,
    val cp1: Offset = Offset(0.42f, 0f),
    val cp2: Offset = Offset(0.58f, 1f)
)

fun getActiveOpacity(
    layerId: String,
    playheadProgress: Float,
    layerOpacities: Map<String, Float>,
    opacityKeyframes: Map<String, List<OpacityKeyframe>>
): Float {
    val keyframes = opacityKeyframes[layerId]
    if (keyframes.isNullOrEmpty()) {
        return layerOpacities[layerId] ?: 1f
    }

    val sorted = keyframes.sortedBy { it.time }
    if (playheadProgress <= sorted.first().time) return sorted.first().opacity
    if (playheadProgress >= sorted.last().time) return sorted.last().opacity

    for (i in 0 until sorted.size - 1) {
        val k1 = sorted[i]
        val k2 = sorted[i + 1]
        if (playheadProgress >= k1.time && playheadProgress <= k2.time) {
            val duration = k2.time - k1.time
            val t = if (duration > 0f) (playheadProgress - k1.time) / duration else 0f
            val easedT = evaluateBezier(t, k1.cp1, k1.cp2)
            return k1.opacity + (k2.opacity - k1.opacity) * easedT
        }
    }
    return sorted.first().opacity
}

// Applies an opacity change to the layer's keyframe at the current playhead
// time IF that layer already has opacity keyframes, returning true. Returns
// false if the layer has no opacity keyframes yet, in which case the caller
// should update the plain layerOpacities map instead (opacityKeyframes is a
// true SnapshotStateMap, but layerOpacities is stored as a plain immutable
// Map behind mutableStateOf, so it can't be mutated in place the same way).
fun updateLayerOpacityKeyframeIfPresent(
    layerId: String,
    newOpacity: Float,
    playheadProgress: Float,
    opacityKeyframes: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<OpacityKeyframe>>
): Boolean {
    val keyframes = opacityKeyframes[layerId]
    if (keyframes.isNullOrEmpty()) return false
    val updated = keyframes.toMutableList()
    val index = updated.indexOfFirst { kotlin.math.abs(it.time - playheadProgress) < 0.05f }
    if (index != -1) {
        updated[index] = updated[index].copy(opacity = newOpacity)
    } else {
        updated.add(OpacityKeyframe(time = playheadProgress, opacity = newOpacity))
        updated.sortBy { it.time }
    }
    opacityKeyframes[layerId] = updated
    return true
}

// Returns the combined set of keyframe times across every animatable
// property track for a layer (currently Move/Rotate/Scale + Opacity), used
// to show a single set of keyframe diamond markers on the timeline track.
fun combinedKeyframeTimes(
    layerId: String,
    layerKeyframes: Map<String, List<LayerKeyframe>>,
    opacityKeyframes: Map<String, List<OpacityKeyframe>>
): List<Float> {
    val transformTimes = layerKeyframes[layerId]?.map { it.time } ?: emptyList()
    val opacityTimes = opacityKeyframes[layerId]?.map { it.time } ?: emptyList()
    return (transformTimes + opacityTimes).distinct()
}

fun updateLayerTransform(
    layerId: String,
    newTransform: LayerTransform,
    playheadProgress: Float,
    layerTransforms: androidx.compose.runtime.snapshots.SnapshotStateMap<String, LayerTransform>,
    layerKeyframes: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<LayerKeyframe>>
) {
    val keyframes = layerKeyframes[layerId]
    if (!keyframes.isNullOrEmpty()) {
        val updated = keyframes.toMutableList()
        val index = updated.indexOfFirst { kotlin.math.abs(it.time - playheadProgress) < 0.05f }
        if (index != -1) {
            updated[index] = updated[index].copy(transform = newTransform)
        } else {
            updated.add(LayerKeyframe(time = playheadProgress, transform = newTransform))
            updated.sortBy { it.time }
        }
        layerKeyframes[layerId] = updated
    } else {
        layerTransforms[layerId] = newTransform
    }
}

@Composable
fun MoveTransformMenu(
    currentTransform: LayerTransform,
    playheadProgress: Float,
    layerId: String,
    layerKeyframes: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<LayerKeyframe>>,
    layerTransforms: androidx.compose.runtime.snapshots.SnapshotStateMap<String, LayerTransform>,
    onBeforeTransformChange: () -> Unit = {},
    onTransformChange: (LayerTransform) -> Unit,
    onBack: () -> Unit
) {
    var showCurveEditor by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val keyframes = layerKeyframes[layerId] ?: emptyList()
    val hasKeyframes = keyframes.isNotEmpty()
    val currentKeyframeIndex = keyframes.indexOfFirst { kotlin.math.abs(it.time - playheadProgress) < 0.05f }
    val isCurrentKeyframe = currentKeyframeIndex != -1
    
    val activeSegmentIndex = remember(keyframes, playheadProgress) {
        var foundIndex = -1
        val sorted = keyframes.sortedBy { it.time }
        for (i in 0 until sorted.size - 1) {
            if (playheadProgress >= sorted[i].time && playheadProgress <= sorted[i+1].time) {
                foundIndex = keyframes.indexOf(sorted[i])
                break
            }
        }
        foundIndex
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF262C3A))) {
        // Left Column: Navigation, Keyframe Diamond, Curve Toggle
        Column(
            modifier = Modifier.width(48.dp).fillMaxHeight().background(Color(0xFF1B1D25)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, 
                "Back", 
                tint = Color.White, 
                modifier = Modifier.size(24.dp).clickable(onClick = onBack)
            )
            
            // Interactive Diamond Keyframe Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable {
                        onBeforeTransformChange()
                        val updated = keyframes.toMutableList()
                        if (isCurrentKeyframe) {
                            updated.removeAt(currentKeyframeIndex)
                        } else {
                            updated.add(LayerKeyframe(time = playheadProgress, transform = currentTransform))
                            updated.sortBy { it.time }
                        }
                        layerKeyframes[layerId] = updated
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .background(
                            if (isCurrentKeyframe) Color(0xFFF96085) 
                            else if (hasKeyframes) Color(0xFF16B996).copy(alpha = 0.4f) 
                            else Color.Transparent
                        )
                        .border(
                            width = 1.5.dp, 
                            color = if (isCurrentKeyframe) Color(0xFFF96085) else Color.White, 
                            shape = RoundedCornerShape(1.dp)
                        )
                )
                Icon(
                    imageVector = if (isCurrentKeyframe) Icons.Default.Remove else Icons.Default.Add, 
                    contentDescription = null, 
                    tint = Color.White, 
                    modifier = Modifier.size(10.dp)
                )
            }
            
            Icon(
                Icons.AutoMirrored.Filled.ShowChart, 
                "Curve", 
                tint = if (showCurveEditor) Color(0xFF16B996) else Color.White, 
                modifier = Modifier.size(24.dp).clickable { showCurveEditor = !showCurveEditor }
            )
            Icon(Icons.Default.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        
        // Center Area: Trackpad / Curve Editor Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            if (showCurveEditor) {
                if (activeSegmentIndex != -1) {
                    val activeKf = keyframes[activeSegmentIndex]
                    CurveEditorPad(
                        cp1 = activeKf.cp1,
                        cp2 = activeKf.cp2,
                        onDragStart = { onBeforeTransformChange() },
                        onCpChange = { newCp1, newCp2 ->
                            val updated = keyframes.toMutableList()
                            updated[activeSegmentIndex] = updated[activeSegmentIndex].copy(cp1 = newCp1, cp2 = newCp2)
                            layerKeyframes[layerId] = updated
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF282E40)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = Color.Gray.copy(alpha=0.6f), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Add at least 2 keyframes\nand place playhead between them\nto edit Easing Curve!",
                                color = Color.LightGray.copy(alpha=0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> SwipePad(
                        currentTransform = currentTransform,
                        onDragStart = { onBeforeTransformChange() },
                        onDrag = { delta ->
                            onTransformChange(currentTransform.copy(
                                offsetX = currentTransform.offsetX + delta.x,
                                offsetY = currentTransform.offsetY + delta.y
                            ))
                        }
                    )
                    1 -> RotationPad(
                        rotation = currentTransform.rotation,
                        onDragStart = { onBeforeTransformChange() },
                        onRotationChange = { delta ->
                            onTransformChange(currentTransform.copy(
                                rotation = currentTransform.rotation + delta
                            ))
                        }
                    )
                    2 -> ScalePad(
                        scaleX = currentTransform.scaleX,
                        scaleY = currentTransform.scaleY,
                        onDragStart = { onBeforeTransformChange() },
                        onScaleChange = { sx, sy ->
                            onTransformChange(currentTransform.copy(
                                scaleX = sx,
                                scaleY = sy
                            ))
                        }
                    )
                    else -> SwipePad(
                        currentTransform = currentTransform,
                        onDragStart = { onBeforeTransformChange() },
                        onDrag = { delta ->
                            onTransformChange(currentTransform.copy(
                                offsetX = currentTransform.offsetX + delta.x,
                                offsetY = currentTransform.offsetY + delta.y
                            ))
                        }
                    )
                }
            }
        }
        
        // Right Column: Presets Column or Transform Navigation
        Column(
            modifier = Modifier.width(80.dp).fillMaxHeight().background(Color(0xFF1B1D25)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            if (showCurveEditor) {
                if (activeSegmentIndex != -1) {
                    val activeKf = keyframes[activeSegmentIndex]
                    CurvePresetsColumn(
                        cp1 = activeKf.cp1,
                        cp2 = activeKf.cp2,
                        onPresetSelect = { newCp1, newCp2 ->
                            onBeforeTransformChange()
                            val updated = keyframes.toMutableList()
                            updated[activeSegmentIndex] = updated[activeSegmentIndex].copy(cp1 = newCp1, cp2 = newCp2)
                            layerKeyframes[layerId] = updated
                        }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1B1D25))) {
                        repeat(4) { idx ->
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CurvePresetIcon(idx + 1, active = false)
                            }
                        }
                    }
                }
            } else {
                MoveTransformRightColumn(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
        }
    }
}

@Composable
fun SwipePad(
    currentTransform: LayerTransform,
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)

    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Trackpad area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF222634), RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { _ -> currentOnDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOnDrag(dragAmount)
                        }
                    )
                }
        ) {
            // Coordinate displays
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // X, Y
                Column(
                    modifier = Modifier.background(Color(0xFF2D3243), RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("X: ${String.format("%.1f", currentTransform.offsetX)}", color = Color(0xFF16B996), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Y: ${String.format("%.1f", currentTransform.offsetY)}", color = Color(0xFF16B996), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            // Grid background inside trackpad
            Canvas(Modifier.fillMaxSize().padding(12.dp).clipToBounds()) {
                val len = 12.dp.toPx()
                val st = 2.dp.toPx()
                val col = Color.Gray.copy(alpha=0.3f)
                
                // Draw crosshairs that move with the offset
                val cx = (size.width / 2) + currentTransform.offsetX
                val cy = (size.height / 2) + currentTransform.offsetY
                
                // Draw infinite-looking grid lines by repeating them
                val gridSpacing = 50.dp.toPx()
                val shiftX = currentTransform.offsetX % gridSpacing
                val shiftY = currentTransform.offsetY % gridSpacing
                
                for (i in -10..10) {
                    val x = size.width / 2 + shiftX + i * gridSpacing
                    drawLine(Color.White.copy(alpha=0.05f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
                for (i in -10..10) {
                    val y = size.height / 2 + shiftY + i * gridSpacing
                    drawLine(Color.White.copy(alpha=0.05f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                
                // Main crosshairs
                drawLine(col, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                drawLine(col, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                
                // Draw origin indicator
                drawCircle(Color.White.copy(alpha=0.5f), radius = 4.dp.toPx(), center = Offset(cx, cy))
                
                // Corners
                // Top left
                drawLine(col, Offset(0f, 0f), Offset(len, 0f), strokeWidth = st)
                drawLine(col, Offset(0f, 0f), Offset(0f, len), strokeWidth = st)
                // Top right
                drawLine(col, Offset(size.width, 0f), Offset(size.width - len, 0f), strokeWidth = st)
                drawLine(col, Offset(size.width, 0f), Offset(size.width, len), strokeWidth = st)
                // Bottom left
                drawLine(col, Offset(0f, size.height), Offset(len, size.height), strokeWidth = st)
                drawLine(col, Offset(0f, size.height), Offset(0f, size.height - len), strokeWidth = st)
                // Bottom right
                drawLine(col, Offset(size.width, size.height), Offset(size.width - len, size.height), strokeWidth = st)
                drawLine(col, Offset(size.width, size.height), Offset(size.width, size.height - len), strokeWidth = st)
            }
            
            Text("SWIPE TRACKPAD TO MOVE", color = Color.White.copy(alpha=0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
        
        Spacer(Modifier.height(4.dp))
        
        // Precision D-Pad Buttons
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FINE TUNE:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 2.dp))
            PrecisionAdjustButton(Icons.Default.KeyboardArrowLeft) { onDrag(Offset(-1f, 0f)) }
            PrecisionAdjustButton(Icons.Default.KeyboardArrowUp) { onDrag(Offset(0f, -1f)) }
            PrecisionAdjustButton(Icons.Default.KeyboardArrowDown) { onDrag(Offset(0f, 1f)) }
            PrecisionAdjustButton(Icons.Default.KeyboardArrowRight) { onDrag(Offset(1f, 0f)) }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFE11D48), RoundedCornerShape(4.dp))
                    .clickable { onDrag(Offset(-currentTransform.offsetX, -currentTransform.offsetY)) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("RESET", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MoveTransformRightColumn(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f).background(if (selectedTab == 0) Color(0xFF262934) else Color.Transparent).clickable { onTabSelected(0) }, contentAlignment=Alignment.Center) {
            Icon(Icons.Default.OpenWith, null, tint = if (selectedTab == 0) Color(0xFF16B996) else Color.White, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(if (selectedTab == 1) Color(0xFF262934) else Color.Transparent).clickable { onTabSelected(1) }, contentAlignment=Alignment.Center) {
            Icon(Icons.Default.RestartAlt, null, tint = if (selectedTab == 1) Color(0xFF16B996) else Color.White, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(if (selectedTab == 2) Color(0xFF262934) else Color.Transparent).clickable { onTabSelected(2) }, contentAlignment=Alignment.Center) {
            Icon(Icons.Default.ZoomOutMap, null, tint = if (selectedTab == 2) Color(0xFF16B996) else Color.White, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(if (selectedTab == 3) Color(0xFF262934) else Color.Transparent).clickable { onTabSelected(3) }, contentAlignment=Alignment.Center) {
            Icon(Icons.Default.Transform, null, tint = if (selectedTab == 3) Color(0xFF16B996) else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun RotationPad(
    rotation: Float,
    onDragStart: () -> Unit = {},
    onRotationChange: (Float) -> Unit
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnRotationChange by rememberUpdatedState(onRotationChange)

    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var center by remember { mutableStateOf(Offset.Zero) }

        // Dial Pad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF222634), RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    center = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { _ -> currentOnDragStart() },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            val prevPos = change.previousPosition
                            
                            val angle1 = Math.toDegrees(kotlin.math.atan2((pos.y - center.y).toDouble(), (pos.x - center.x).toDouble()))
                            val angle2 = Math.toDegrees(kotlin.math.atan2((prevPos.y - center.y).toDouble(), (prevPos.x - center.x).toDouble()))
                            
                            var delta = angle1 - angle2
                            if (delta > 180) delta -= 360
                            if (delta < -180) delta += 360
                            
                            currentOnRotationChange(delta.toFloat())
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Visual Dial Wheel with ticks
            Canvas(modifier = Modifier.size(130.dp)) {
                drawCircle(Color.White.copy(alpha=0.03f), radius = size.width / 2)
                drawCircle(Color(0xFF16B996).copy(alpha=0.1f), radius = size.width / 2, style = Stroke(width = 4.dp.toPx()))
                
                rotate(rotation) {
                    // Ticks along the circle
                    val tickCount = 24
                    for (i in 0 until tickCount) {
                        val angleDeg = (i * (360f / tickCount))
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val tickLen = if (i == 0) 12.dp.toPx() else 6.dp.toPx()
                        val color = if (i == 0) Color(0xFF16B996) else Color.White.copy(alpha = 0.25f)
                        val stroke = if (i == 0) 2.dp.toPx() else 1.dp.toPx()
                        val x1 = (size.width / 2) + (size.width / 2 - tickLen) * kotlin.math.cos(angleRad).toFloat()
                        val y1 = (size.height / 2) + (size.height / 2 - tickLen) * kotlin.math.sin(angleRad).toFloat()
                        val x2 = (size.width / 2) + (size.width / 2) * kotlin.math.cos(angleRad).toFloat()
                        val y2 = (size.height / 2) + (size.height / 2) * kotlin.math.sin(angleRad).toFloat()
                        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke)
                    }

                    // White knob
                    val r = size.width / 2
                    val cx = r + r * kotlin.math.cos(0.0).toFloat()
                    val cy = r + r * kotlin.math.sin(0.0).toFloat()
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color(0xFF1B1D25), radius = 4.dp.toPx(), center = Offset(cx, cy))
                }
            }
            
            Box(modifier = Modifier.background(Color(0xFF1B1D25), RoundedCornerShape(4.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("${String.format("%.1f", rotation)}°", color = Color(0xFF16B996), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Text("SPIN TO ROTATE", color = Color.White.copy(alpha=0.3f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
        
        Spacer(Modifier.height(4.dp))
        
        // Preset and step buttons
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PresetButton("-90°") { onRotationChange(-90f - rotation) }
            PresetButton("0°") { onRotationChange(-rotation) }
            PresetButton("90°") { onRotationChange(90f - rotation) }
            PresetButton("180°") { onRotationChange(180f - rotation) }
            Spacer(Modifier.width(4.dp))
            PresetButton("-1°") { onRotationChange(-1f) }
            PresetButton("+1°") { onRotationChange(1f) }
        }
    }
}

@Composable
fun ScalePad(
    scaleX: Float,
    scaleY: Float,
    onDragStart: () -> Unit = {},
    onScaleChange: (Float, Float) -> Unit
) {
    var scaleMode by remember { mutableIntStateOf(0) } // 0: Both, 1: Width Only, 2: Height Only
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentScaleX by rememberUpdatedState(scaleX)
    val currentScaleY by rememberUpdatedState(scaleY)

    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Selector: [ Both ] [ Width ] [ Height ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B1D25), RoundedCornerShape(4.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScaleModeTab("BOTH", scaleMode == 0) { scaleMode = 0 }
            ScaleModeTab("WIDTH", scaleMode == 1) { scaleMode = 1 }
            ScaleModeTab("HEIGHT", scaleMode == 2) { scaleMode = 2 }
        }
        
        Spacer(Modifier.height(4.dp))
        
        // Slideway display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF222634), RoundedCornerShape(8.dp))
                .pointerInput(scaleMode) {
                    detectHorizontalDragGestures(
                        onDragStart = { _ -> currentOnDragStart() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = dragAmount * 0.005f
                            when (scaleMode) {
                                0 -> {
                                    val s = (currentScaleX + delta).coerceIn(0.1f, 10f)
                                    currentOnScaleChange(s, s)
                                }
                                1 -> {
                                    val sx = (currentScaleX + delta).coerceIn(0.1f, 10f)
                                    currentOnScaleChange(sx, currentScaleY)
                                }
                                2 -> {
                                    val sy = (currentScaleY + delta).coerceIn(0.1f, 10f)
                                    currentOnScaleChange(currentScaleX, sy)
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Ruler Lines
            Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                val strokeWidth = 1.dp.toPx()
                val spacing = 10.dp.toPx()
                val col = Color.White.copy(alpha=0.15f)
                
                val currentScale = if (scaleMode == 0 || scaleMode == 1) scaleX else scaleY
                // Shift the ruler visually based on the scale value. 
                // Since delta = dragAmount * 0.005f, 1 unit of scale = 200 pixels.
                val shiftX = (currentScale * 200f) % spacing
                
                val startX = -spacing
                val endX = size.width + spacing
                var x = startX + shiftX
                var count = 0
                while (x < endX) {
                    val heightMultiplier = if (count % 10 == 0) 0.8f else if (count % 5 == 0) 0.5f else 0.3f
                    drawLine(col, Offset(x, size.height * (1f - heightMultiplier) / 2), Offset(x, size.height - (size.height * (1f - heightMultiplier) / 2)), strokeWidth = strokeWidth)
                    x += spacing
                    count++
                }
                
                // Center accent indicator (static)
                drawLine(Color(0xFF16B996), Offset(size.width / 2, size.height * 0.1f), Offset(size.width / 2, size.height * 0.9f), strokeWidth = 2.dp.toPx())
            }
            
            // Value text overlay
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("W: ${String.format("%.2f", scaleX)}", color = if (scaleMode == 0 || scaleMode == 1) Color(0xFF16B996) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("H: ${String.format("%.2f", scaleY)}", color = if (scaleMode == 0 || scaleMode == 2) Color(0xFF16B996) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("SWIPE HORIZONTALLY TO RESIZE", color = Color.White.copy(alpha=0.3f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        
        Spacer(Modifier.height(4.dp))
        
        // Scaling Presets
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PresetButton("0.5x") { onScaleChange(0.5f, 0.5f) }
            PresetButton("1.0x") { onScaleChange(1.0f, 1.0f) }
            PresetButton("1.5x") { onScaleChange(1.5f, 1.5f) }
            PresetButton("2.0x") { onScaleChange(2.0f, 2.0f) }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFE11D48), RoundedCornerShape(4.dp))
                    .clickable { onScaleChange(1f, 1f) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("RESET", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PrecisionAdjustButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(Color(0xFF2D3243), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PresetButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF2D3243), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScaleModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) Color(0xFF16B996) else Color.Transparent, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.Black else Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}




fun Modifier.layerTransformGestures(
    layerId: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    transform: LayerTransform,
    onTransformChange: (LayerTransform) -> Unit,
    onGestureStart: () -> Unit = {}
): Modifier = composed {
    val currentTransform by rememberUpdatedState(transform)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val currentOnGestureStart by rememberUpdatedState(onGestureStart)
    var lastGestureTime by remember { mutableLongStateOf(0L) }
    
    this.then(
        if (isSelected) {
            Modifier.pointerInput(layerId) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    val now = System.currentTimeMillis()
                    if (now - lastGestureTime > 350L) {
                        currentOnGestureStart()
                    }
                    lastGestureTime = now
                    currentOnTransformChange(
                        currentTransform.copy(
                            offsetX = currentTransform.offsetX + pan.x,
                            offsetY = currentTransform.offsetY + pan.y,
                            scaleX = (currentTransform.scaleX * zoom).coerceIn(0.1f, 10f),
                            scaleY = (currentTransform.scaleY * zoom).coerceIn(0.1f, 10f),
                            rotation = currentTransform.rotation + rotation
                        )
                    )
                }
            }
        } else {
            Modifier.clickable { onSelect() }
        }
    )
}

@Composable
fun CurveEditorPad(
    cp1: Offset,
    cp2: Offset,
    onDragStart: () -> Unit = {},
    onCpChange: (Offset, Offset) -> Unit
) {
    var activeHandle by remember { mutableIntStateOf(0) } // 0 = none, 1 = cp1, 2 = cp2
    val currentOnDragStart by rememberUpdatedState(onDragStart)

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF282E40)).padding(12.dp)) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cp1, cp2) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val p1Real = Offset(cp1.x * widthPx, (1f - cp1.y) * heightPx)
                                val p2Real = Offset(cp2.x * widthPx, (1f - cp2.y) * heightPx)
                                val dist1 = (offset - p1Real).getDistance()
                                val dist2 = (offset - p2Real).getDistance()
                                
                                val threshold = 36.dp.toPx() // generous touch targets!
                                activeHandle = when {
                                    dist1 < threshold && dist1 < dist2 -> 1
                                    dist2 < threshold -> 2
                                    else -> 0
                                }
                                if (activeHandle != 0) {
                                    currentOnDragStart()
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (activeHandle != 0) {
                                    change.consume()
                                    val currentPos = change.position
                                    val nx = (currentPos.x / widthPx).coerceIn(0f, 1f)
                                    val ny = (1f - currentPos.y / heightPx).coerceIn(-1.5f, 2.5f) // allow overshoot like alight motion!
                                    
                                    if (activeHandle == 1) {
                                        onCpChange(Offset(nx, ny), cp2)
                                    } else {
                                        onCpChange(cp1, Offset(nx, ny))
                                    }
                                }
                            },
                            onDragEnd = {
                                activeHandle = 0
                            }
                        )
                    }
            ) {
                val gridColor = Color.White.copy(alpha = 0.08f)
                val strokeWidth = 1.dp.toPx()
                
                // Grid coordinates
                val cellsX = 10
                val cellsY = 8
                val cellWidth = size.width / cellsX
                val cellHeight = size.height / cellsY
                
                for (i in 0..cellsX) {
                    drawLine(gridColor, Offset(i * cellWidth, 0f), Offset(i * cellWidth, size.height), strokeWidth = strokeWidth)
                }
                for (i in 0..cellsY) {
                    drawLine(gridColor, Offset(0f, i * cellHeight), Offset(size.width, i * cellHeight), strokeWidth = strokeWidth)
                }
                
                // Draw 0 and 1 base dashed lines
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.5.dp.toPx()
                )
                
                // Map control points to pixels
                val p1Real = Offset(cp1.x * size.width, (1f - cp1.y) * size.height)
                val p2Real = Offset(cp2.x * size.width, (1f - cp2.y) * size.height)
                
                // Draw bezier path
                val path = Path().apply {
                    moveTo(0f, size.height)
                    cubicTo(p1Real.x, p1Real.y, p2Real.x, p2Real.y, size.width, 0f)
                }
                
                val primaryColor = Color(0xFF16B996)
                drawPath(path, primaryColor, style = Stroke(width = 3.5.dp.toPx()))
                
                // Draw point handles
                // Handle 1 (Bottom Left to CP1)
                drawLine(
                    color = Color(0xFFF96085),
                    start = Offset(0f, size.height),
                    end = p1Real,
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(Color.White, radius = 9.dp.toPx(), center = p1Real)
                drawCircle(Color(0xFFF96085), radius = 6.dp.toPx(), center = p1Real)
                
                // Handle 2 (Top Right to CP2)
                drawLine(
                    color = Color(0xFF3B82F6),
                    start = Offset(size.width, 0f),
                    end = p2Real,
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(Color.White, radius = 9.dp.toPx(), center = p2Real)
                drawCircle(Color(0xFF3B82F6), radius = 6.dp.toPx(), center = p2Real)
                
                // End points
                drawCircle(primaryColor, radius = 4.dp.toPx(), center = Offset(0f, size.height))
                drawCircle(primaryColor, radius = 4.dp.toPx(), center = Offset(size.width, 0f))
            }
        }
        
        // Label/Readout of values
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "P1: (${String.format("%.2f", cp1.x)}, ${String.format("%.2f", cp1.y)})", 
                color = Color(0xFFF96085), 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Bold
            )
            Text(
                "CUBIC BEZIER EASING", 
                color = Color.LightGray, 
                fontSize = 9.sp, 
                fontWeight = FontWeight.Bold
            )
            Text(
                "P2: (${String.format("%.2f", cp2.x)}, ${String.format("%.2f", cp2.y)})", 
                color = Color(0xFF3B82F6), 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CurvePresetsColumn(
    cp1: Offset,
    cp2: Offset,
    onPresetSelect: (Offset, Offset) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Preset 1: Ease In Out
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(if (cp1 == Offset(0.42f, 0f) && cp2 == Offset(0.58f, 1f)) Color(0xFF262934) else Color.Transparent)
                .clickable { onPresetSelect(Offset(0.42f, 0f), Offset(0.58f, 1f)) },
            contentAlignment = Alignment.Center
        ) {
            CurvePresetIcon(1, active = cp1 == Offset(0.42f, 0f) && cp2 == Offset(0.58f, 1f))
        }
        // Preset 2: Ease In (Slow-Fast)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(if (cp1 == Offset(0.42f, 0f) && cp2 == Offset(1f, 1f)) Color(0xFF262934) else Color.Transparent)
                .clickable { onPresetSelect(Offset(0.42f, 0f), Offset(1f, 1f)) },
            contentAlignment = Alignment.Center
        ) {
            CurvePresetIcon(2, active = cp1 == Offset(0.42f, 0f) && cp2 == Offset(1f, 1f))
        }
        // Preset 3: Ease Out (Fast-Slow)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(if (cp1 == Offset(0f, 0f) && cp2 == Offset(0.58f, 1f)) Color(0xFF262934) else Color.Transparent)
                .clickable { onPresetSelect(Offset(0f, 0f), Offset(0.58f, 1f)) },
            contentAlignment = Alignment.Center
        ) {
            CurvePresetIcon(3, active = cp1 == Offset(0f, 0f) && cp2 == Offset(0.58f, 1f))
        }
        // Preset 4: Linear
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(if (cp1 == Offset(0f, 0f) && cp2 == Offset(1f, 1f)) Color(0xFF262934) else Color.Transparent)
                .clickable { onPresetSelect(Offset(0f, 0f), Offset(1f, 1f)) },
            contentAlignment = Alignment.Center
        ) {
            CurvePresetIcon(4, active = cp1 == Offset(0f, 0f) && cp2 == Offset(1f, 1f))
        }
    }
}

@Composable
fun CurvePresetIcon(type: Int, active: Boolean) {
    Canvas(modifier = Modifier.size(32.dp)) {
        val st = 2.dp.toPx()
        val p = Path()
        when (type) {
            1 -> { // S-curve / Ease In Out
                p.moveTo(0f, size.height)
                p.cubicTo(size.width * 0.42f, size.height, size.width * 0.58f, 0f, size.width, 0f)
            }
            2 -> { // Ease in
                p.moveTo(0f, size.height)
                p.cubicTo(size.width * 0.42f, size.height, size.width, size.height, size.width, 0f)
            }
            3 -> { // Ease out
                p.moveTo(0f, size.height)
                p.cubicTo(0f, 0f, size.width * 0.58f, 0f, size.width, 0f)
            }
            4 -> { // Linear
                p.moveTo(0f, size.height)
                p.lineTo(size.width, 0f)
            }
        }
        drawPath(p, if (active) Color(0xFF16B996) else Color.White, style = Stroke(width = st))
        drawCircle(if (active) Color(0xFF16B996) else Color.White, radius = 2.5.dp.toPx(), center = Offset(0f, size.height))
        drawCircle(if (active) Color(0xFF16B996) else Color.White, radius = 2.5.dp.toPx(), center = Offset(size.width, 0f))
    }
}

@Composable
fun SelectedLayerMoveTransformMenu(
    layerId: String,
    playheadProgress: Float,
    layerTransforms: androidx.compose.runtime.snapshots.SnapshotStateMap<String, LayerTransform>,
    layerKeyframes: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<LayerKeyframe>>,
    onBeforeTransformChange: () -> Unit = {},
    onBack: () -> Unit
) {
    val currentTransform = getActiveTransform(layerId, playheadProgress, layerTransforms, layerKeyframes)
    MoveTransformMenu(
        currentTransform = currentTransform,
        playheadProgress = playheadProgress,
        layerId = layerId,
        layerKeyframes = layerKeyframes,
        layerTransforms = layerTransforms,
        onBeforeTransformChange = onBeforeTransformChange,
        onTransformChange = { newTransform ->
            updateLayerTransform(layerId, newTransform, playheadProgress, layerTransforms, layerKeyframes)
        },
        onBack = onBack
    )
}