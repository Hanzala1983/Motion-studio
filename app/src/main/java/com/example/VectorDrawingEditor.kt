package com.example

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VectorDrawingEditor(
    points: List<Offset>,
    onPointsChange: (List<Offset>) -> Unit,
    selectedPointIndex: Int,
    onSelectedPointChange: (Int) -> Unit,
    pointModes: List<Boolean>, // false = straight corner, true = bezier curve
    onPointModesChange: (List<Boolean>) -> Unit,
    playheadProgress: Float,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    
    val currentPointsState = rememberUpdatedState(points)
    val currentSelectedIndexState = rememberUpdatedState(selectedPointIndex)
    val currentOnPointsChangeState = rememberUpdatedState(onPointsChange)
    val currentOnSelectedPointChangeState = rememberUpdatedState(onSelectedPointChange)
    
    // Track cursor/crosshair location (normalized 0..1)
    var cursorPosition by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    
    // Synchronize cursor position with selected point
    LaunchedEffect(selectedPointIndex, points) {
        if (selectedPointIndex in points.indices) {
            cursorPosition = points[selectedPointIndex]
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181A20)) // Match Alight Motion main dark background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR (Header Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF1F222B))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Points",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Keyframe/Diamond Icon
                    IconButton(onClick = {
                        Toast.makeText(context, "Keyframe added at ${String.format("%.2f", playheadProgress)}s", Toast.LENGTH_SHORT).show()
                    }) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(1.5.dp, Color.White, RoundedCornerShape(2.dp))
                                .rotate(45f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Plus Circle Icon to Add/Insert point at cursor
                    IconButton(onClick = {
                        val newPoints = points.toMutableList()
                        newPoints.add(cursorPosition)
                        onPointsChange(newPoints)
                        
                        val newModes = pointModes.toMutableList()
                        newModes.add(false)
                        onPointModesChange(newModes)
                        
                        onSelectedPointChange(newPoints.size - 1)
                        Toast.makeText(context, "Added point at cursor!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Add Point",
                            tint = Color(0xFF16B996),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. CANVAS AREA (Middle)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF131418)) // Dark editing canvas outline background
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Main visual editor container with 9:16 or standard aspect ratio
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFD2D4D9)) // Gray canvas background from screenshot
                        .onGloballyPositioned { coordinates ->
                            canvasSize = coordinates.size
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var activeDragIndex = -1
                                var dragGrabOffset = Offset.Zero
                                var dragTriggered = false
                                
                                val w = canvasSize.width
                                val h = canvasSize.height
                                if (w > 0 && h > 0) {
                                    val normX = down.position.x / w
                                    val normY = down.position.y / h
                                    val touchPt = Offset(normX, normY)
                                    
                                    val pts = currentPointsState.value
                                    var closestIndex = -1
                                    var minDistance = Float.MAX_VALUE
                                    pts.forEachIndexed { index, pt ->
                                        val dist = (pt - touchPt).getDistance()
                                        if (dist < minDistance && dist < 0.12f) { // Grab/Select radius
                                            minDistance = dist
                                            closestIndex = index
                                        }
                                    }
                                    
                                    if (closestIndex != -1) {
                                        activeDragIndex = closestIndex
                                        val grabbedPt = pts[closestIndex]
                                        dragGrabOffset = Offset(normX - grabbedPt.x, normY - grabbedPt.y)
                                    } else {
                                        activeDragIndex = currentSelectedIndexState.value
                                        if (activeDragIndex in pts.indices) {
                                            val grabbedPt = pts[activeDragIndex]
                                            dragGrabOffset = Offset(normX - grabbedPt.x, normY - grabbedPt.y)
                                        }
                                    }
                                }
                                
                                var pointer = down
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val anyPressed = event.changes.any { it.pressed }
                                    if (!anyPressed) {
                                        // Release! If we didn't drag, treat it as a tap/click
                                        if (!dragTriggered) {
                                            val upPosition = pointer.position
                                            if (w > 0 && h > 0) {
                                                val normX = upPosition.x / w
                                                val normY = upPosition.y / h
                                                val tappedPoint = Offset(normX, normY)
                                                
                                                val pts = currentPointsState.value
                                                if (activeDragIndex != -1) {
                                                    currentOnSelectedPointChangeState.value(activeDragIndex)
                                                } else {
                                                    // Tap on empty space: Add a new point!
                                                    val updated = pts + tappedPoint
                                                    currentOnPointsChangeState.value(updated)
                                                    
                                                    val newModes = pointModes.toMutableList()
                                                    newModes.add(false)
                                                    onPointModesChange(newModes)
                                                    
                                                    currentOnSelectedPointChangeState.value(updated.size - 1)
                                                    cursorPosition = tappedPoint
                                                    Toast.makeText(context, "Point added!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        break
                                    }
                                    
                                    val dragChange = event.changes.firstOrNull { it.id == pointer.id }
                                    if (dragChange != null) {
                                        val dragDistance = (dragChange.position - down.position).getDistance()
                                        if (!dragTriggered && dragDistance > 8f) { // 8 pixels threshold for drag
                                            dragTriggered = true
                                            if (activeDragIndex != -1) {
                                                currentOnSelectedPointChangeState.value(activeDragIndex)
                                            }
                                        }
                                        
                                        if (dragTriggered && activeDragIndex != -1 && w > 0 && h > 0) {
                                            dragChange.consume()
                                            val pts = currentPointsState.value
                                            if (activeDragIndex in pts.indices) {
                                                val normX = dragChange.position.x / w
                                                val normY = dragChange.position.y / h
                                                
                                                val newX = (normX - dragGrabOffset.x).coerceIn(0f, 1f)
                                                val newY = (normY - dragGrabOffset.y).coerceIn(0f, 1f)
                                                
                                                val updated = pts.toMutableList()
                                                updated[activeDragIndex] = Offset(newX, newY)
                                                currentOnPointsChangeState.value(updated)
                                            }
                                        }
                                        pointer = dragChange
                                    }
                                }
                            }
                        }
                ) {
                    // Render Vector drawing canvas contents
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        // DRAW BACKGROUND ILLUSTRATIVE SHAPES (to match the screenshot context beautifully!)
                        // A. Dark gray cloud shape
                        val cloudPath = Path().apply {
                            moveTo(w * 0.4f, h * 0.35f)
                            cubicTo(w * 0.35f, h * 0.32f, w * 0.35f, h * 0.42f, w * 0.42f, h * 0.42f)
                            cubicTo(w * 0.4f, h * 0.45f, w * 0.6f, h * 0.45f, w * 0.58f, h * 0.4f)
                            cubicTo(w * 0.65f, h * 0.4f, w * 0.65f, h * 0.32f, w * 0.55f, h * 0.32f)
                            cubicTo(w * 0.52f, h * 0.28f, w * 0.42f, h * 0.28f, w * 0.4f, h * 0.35f)
                            close()
                        }
                        drawPath(cloudPath, Color(0xFF4C4A45))
                        
                        // B. Yellow C-shaped outline over the cloud
                        drawArc(
                            color = Color(0xFFC0D050),
                            startAngle = 45f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(w * 0.5f, h * 0.33f),
                            size = Size(w * 0.12f, w * 0.12f),
                            style = Stroke(width = w * 0.015f)
                        )
                        
                        // C. Blue Arrow pointing up-right
                        val arrowPath = Path().apply {
                            moveTo(w * 0.45f, h * 0.33f)
                            lineTo(w * 0.58f, h * 0.25f)
                            // Arrow head
                            moveTo(w * 0.58f, h * 0.25f)
                            lineTo(w * 0.52f, h * 0.25f)
                            moveTo(w * 0.58f, h * 0.25f)
                            lineTo(w * 0.58f, h * 0.31f)
                        }
                        drawPath(
                            path = arrowPath,
                            color = Color(0xFF90CAF9),
                            style = Stroke(width = w * 0.03f)
                        )

                        // D. RENDER THE VECTOR CUSTOM POLYGON (Shape 2)
                        if (points.isNotEmpty()) {
                            val polyPath = Path().apply {
                                val first = points[0]
                                moveTo(first.x * w, first.y * h)
                                
                                for (i in 1 until points.size) {
                                    val current = points[i]
                                    if (pointModes.getOrElse(i) { false }) {
                                        // Bezier control curves
                                        val prev = points[i - 1]
                                        val ctrl1X = prev.x * w + (current.x * w - prev.x * w) * 0.5f
                                        val ctrl1Y = prev.y * h
                                        val ctrl2X = prev.x * w + (current.x * w - prev.x * w) * 0.5f
                                        val ctrl2Y = current.y * h
                                        cubicTo(ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, current.x * w, current.y * h)
                                    } else {
                                        lineTo(current.x * w, current.y * h)
                                    }
                                }
                                close()
                            }
                            
                            // Fill the custom shape with green color (from screenshot)
                            drawPath(polyPath, Color(0xFF4EF293))
                            
                            // Draw outline of polygon
                            drawPath(polyPath, Color.Black, style = Stroke(width = 2.dp.toPx()))
                            
                            // DRAW VERTICES/EDIT HANDLES
                            points.forEachIndexed { index, pt ->
                                val ptX = pt.x * w
                                val ptY = pt.y * h
                                
                                // Point handle circle: gray filled, thin black border
                                drawCircle(
                                    color = Color(0xFFB0B3BC),
                                    radius = 7.dp.toPx(),
                                    center = Offset(ptX, ptY)
                                )
                                drawCircle(
                                    color = Color.Black,
                                    radius = 7.dp.toPx(),
                                    center = Offset(ptX, ptY),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                
                                // If selected, draw crosshair target icon around/over it!
                                if (index == selectedPointIndex) {
                                    // Target circle
                                    drawCircle(
                                        color = Color.White,
                                        radius = 12.dp.toPx(),
                                        center = Offset(ptX, ptY),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                    // Crosshairs + lines
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(ptX - 18.dp.toPx(), ptY),
                                        end = Offset(ptX - 6.dp.toPx(), ptY),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(ptX + 6.dp.toPx(), ptY),
                                        end = Offset(ptX + 18.dp.toPx(), ptY),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(ptX, ptY - 18.dp.toPx()),
                                        end = Offset(ptX, ptY - 6.dp.toPx()),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(ptX, ptY + 6.dp.toPx()),
                                        end = Offset(ptX, ptY + 18.dp.toPx()),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                            }
                        }
                    }

                    if (points.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Gesture,
                                    contentDescription = null,
                                    tint = Color.Black.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap anywhere to add points\n& draw your custom vector shape",
                                    color = Color.Black.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    
                    // Watermark / Indicator
                    Text(
                        text = "Edit Points Mode",
                        color = Color.Black.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            }

            // 3. TIMELINE HIGH-LIGHT ROW (Shape 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF1B1D25))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eye/Visibility Icon
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Visibility",
                    tint = Color(0xFF16B996),
                    modifier = Modifier.size(18.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Track Label / Selected Layer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF3399FF).copy(alpha = 0.35f)) // Highlight blue timeline bar
                        .border(1.dp, Color(0xFF3399FF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shape 2",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Timeline status text
                        Text(
                            text = "00:00:00",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 4. LOWER CONTROL PANEL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFF1F222B))
                    .padding(12.dp)
            ) {
                // Left Column: Vector tool buttons
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Close/Cancel Back Button
                    VectorToolButton(
                        icon = Icons.Default.Close,
                        onClick = onClose,
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                    
                    // Crosshair / Position Mode
                    VectorToolButton(
                        icon = Icons.Default.MyLocation,
                        onClick = {
                            Toast.makeText(context, "Crosshair navigation active", Toast.LENGTH_SHORT).show()
                        },
                        tint = Color(0xFF16B996)
                    )
                    
                    // Curve type Bezier toggle
                    val isBezier = pointModes.getOrElse(selectedPointIndex) { false }
                    VectorToolButton(
                        icon = Icons.Default.Gesture,
                        onClick = {
                            if (selectedPointIndex in points.indices) {
                                val newModes = pointModes.toMutableList()
                                val currentMode = newModes.getOrElse(selectedPointIndex) { false }
                                newModes[selectedPointIndex] = !currentMode
                                onPointModesChange(newModes)
                                Toast.makeText(
                                    context, 
                                    if (!currentMode) "Changed to Bezier Curve" else "Changed to Sharp Corner", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        tint = if (isBezier) Color(0xFF16B996) else Color.White.copy(alpha = 0.7f)
                    )
                    
                    // Plus icon (insert/add point)
                    VectorToolButton(
                        icon = Icons.Default.Add,
                        onClick = {
                            val newPoints = points.toMutableList()
                            // Insert a new point slightly offset from the currently selected point
                            if (selectedPointIndex in points.indices) {
                                val current = points[selectedPointIndex]
                                val insertOffset = Offset(current.x + 0.05f, current.y + 0.05f)
                                newPoints.add(selectedPointIndex + 1, insertOffset)
                                
                                val newModes = pointModes.toMutableList()
                                newModes.add(selectedPointIndex + 1, false)
                                onPointModesChange(newModes)
                                
                                onSelectedPointChange(selectedPointIndex + 1)
                                Toast.makeText(context, "Point inserted!", Toast.LENGTH_SHORT).show()
                            } else {
                                newPoints.add(Offset(0.5f, 0.5f))
                                val newModes = pointModes.toMutableList()
                                newModes.add(false)
                                onPointModesChange(newModes)
                                onSelectedPointChange(newPoints.size - 1)
                            }
                            onPointsChange(newPoints)
                        },
                        tint = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Delete icon (remove selected point)
                    VectorToolButton(
                        icon = Icons.Default.Delete,
                        onClick = {
                            if (selectedPointIndex in points.indices) {
                                val newPoints = points.toMutableList()
                                newPoints.removeAt(selectedPointIndex)
                                onPointsChange(newPoints)
                                
                                val newModes = pointModes.toMutableList()
                                if (selectedPointIndex in newModes.indices) {
                                    newModes.removeAt(selectedPointIndex)
                                }
                                onPointModesChange(newModes)
                                
                                val nextIndex = if (newPoints.isEmpty()) -1 else (selectedPointIndex - 1).coerceAtLeast(0)
                                onSelectedPointChange(nextIndex)
                                Toast.makeText(context, "Point deleted!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No point selected to delete", Toast.LENGTH_SHORT).show()
                            }
                        },
                        tint = if (selectedPointIndex in points.indices) Color(0xFFE57373) else Color.White.copy(alpha = 0.3f)
                    )
                }

                // Center Column: Interactive Touchpad Trackpad
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .background(Color(0xFF131418), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val idx = currentSelectedIndexState.value
                                    val pts = currentPointsState.value
                                    if (idx in pts.indices) {
                                        val currentPt = pts[idx]
                                        // Update position using dragAmount scaled down
                                        val sensitivity = 0.004f
                                        val newX = (currentPt.x + dragAmount.x * sensitivity).coerceIn(0f, 1f)
                                        val newY = (currentPt.y + dragAmount.y * sensitivity).coerceIn(0f, 1f)
                                        
                                        val updated = pts.toMutableList()
                                        updated[idx] = Offset(newX, newY)
                                        currentOnPointsChangeState.value(updated)
                                    }
                                }
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Swipe here to position next point,\nthen tap here to place it",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Right Column: Fine-tuning sliders/arrows
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Fine-tune up
                    IconButton(onClick = {
                        adjustSelectedPoint(points, selectedPointIndex, 0f, -0.01f, onPointsChange)
                    }) {
                        Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White)
                    }
                    
                    // Value read-out / indicator
                    val xStr = if (selectedPointIndex in points.indices) {
                        (points[selectedPointIndex].x * 100).toInt().toString()
                    } else "0"
                    val yStr = if (selectedPointIndex in points.indices) {
                        (points[selectedPointIndex].y * 100).toInt().toString()
                    } else "0"
                    
                    Text(
                        text = "X:$xStr\nY:$yStr",
                        color = Color(0xFF16B996),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    // Fine-tune down
                    IconButton(onClick = {
                        adjustSelectedPoint(points, selectedPointIndex, 0f, 0.01f, onPointsChange)
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun adjustSelectedPoint(
    points: List<Offset>,
    index: Int,
    dx: Float,
    dy: Float,
    onPointsChange: (List<Offset>) -> Unit
) {
    if (index in points.indices) {
        val updated = points.toMutableList()
        val current = points[index]
        updated[index] = Offset(
            (current.x + dx).coerceIn(0f, 1f),
            (current.y + dy).coerceIn(0f, 1f)
        )
        onPointsChange(updated)
    }
}

@Composable
fun VectorToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
