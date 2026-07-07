package com.example
import com.example.model.AppliedEffect
import com.example.model.createDefaultEffect
import com.example.modifier.alightMotionEffects
import com.example.viewmodel.BASE_LAYER_IDS
import com.example.viewmodel.TIMELINE_ZOOM_RANGE
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.effects.EffectBrowserScreen
import com.example.ui.effects.EffectsMenu
import com.example.ui.effects.EffectParameterSlider
import com.example.ui.effects.AppliedEffectCard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp

val LocalDeletedLayers = compositionLocalOf<List<String>> { emptyList() }

data class EditorSnapshot(
    val defaultLayerCount: Map<String, Int>,
    val deletedLayers: List<String>,
    val layerColors: Map<String, Color>,
    val layerOpacities: Map<String, Float>,
    val layerBlendModes: Map<String, String>,
    val layerStartTimes: Map<String, Float>,
    val layerEndTimes: Map<String, Float>,
    val layerTransforms: Map<String, LayerTransform>,
    val layerKeyframes: Map<String, List<LayerKeyframe>>,
    val opacityKeyframes: Map<String, List<OpacityKeyframe>>,
    val layerEffects: Map<String, List<AppliedEffect>>,
    val addedShapes: List<androidx.compose.ui.graphics.vector.ImageVector?>,
    val addedMedia: List<android.net.Uri>,
    val vectorPoints: List<Offset>,
    val pointModes: List<Boolean>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Smoke test — confirms the native (C++) renderer library loaded
        // and the JNI bridge resolves correctly. VideoRenderer.kt's export
        // path now uses NativeRenderer.nativeBitmapToYuv420 when this is
        // available, falling back to its own Kotlin conversion per-frame
        // otherwise (see VideoRenderer.renderAndSaveVideo). Layer
        // compositing (NativeRenderer.compositeFrame) is not wired in yet.
        if (com.example.render.NativeRenderer.isAvailable) {
            android.util.Log.i("NativeRenderer", "Loaded: ${com.example.render.NativeRenderer.nativeEngineVersion()}")
        } else {
            android.util.Log.w("NativeRenderer", "Native renderer unavailable, falling back to Kotlin renderer", com.example.render.NativeRenderer.loadFailureReason())
        }

        setContent {
            MotionStudioTheme {
                AlightMotionTimelineEditor()
            }
        }
    }
}

@Composable
fun SelectionHandles() {
    val handleSize = 12.dp
    val handleOffset = 6.dp
    val stroke = 1.dp
    Box(modifier = Modifier.fillMaxSize().border(1.5.dp, Color.White)) {
        Box(Modifier.align(Alignment.TopStart).offset(x = -handleOffset, y = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.TopCenter).offset(y = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.TopEnd).offset(x = handleOffset, y = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.CenterStart).offset(x = -handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = -handleOffset, y = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.BottomCenter).offset(y = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = handleOffset, y = handleOffset).size(handleSize).background(Color.White, CircleShape).border(stroke, Color.Black, CircleShape))
    }
}

data class LayerTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

// Timeline scale: how many pixels represent one second on the timeline
// strip at 1x zoom. The effective scale is this times
// EditorViewModel.timelineZoom (see rememberTimelinePxPerSecond below),
// since the timeline now supports pinch-to-zoom.
const val TIMELINE_PIXELS_PER_SECOND = 50f
// Total duration of the timeline is now computed dynamically from the
// actual layers' end times — see EditorViewModel.timelineDurationSeconds.
// (Previously this was a fixed `const val TIMELINE_DURATION_SECONDS = 8f`.)

// Default length (in seconds) given to a newly added layer that has no
// inherent duration of its own (shapes, vector drawings, still images).
// This mirrors Alight Motion's default 5s starting clip length.
const val DEFAULT_LAYER_DURATION_SECONDS = 5f

// Reads a media file's real duration in seconds via MediaMetadataRetriever.
// Returns null if the URI has no duration metadata (e.g. it's a still image)
// or if reading fails for any reason, so callers can fall back to
// DEFAULT_LAYER_DURATION_SECONDS instead of crashing.
fun getMediaDurationSecondsOrNull(context: android.content.Context, uri: android.net.Uri): Float? {
    // First attempt: setDataSource(context, uri) directly. This is what
    // most content:// URIs need, but it can throw for some picker/provider
    // URIs (e.g. transient permission issues right after picking).
    try {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (ms != null && ms > 0) return ms / 1000f
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    } catch (e: Exception) {
        android.util.Log.w("MediaDuration", "setDataSource(context, uri) failed for $uri, retrying via fd", e)
    }

    // Fallback: open a raw file descriptor and read metadata from that
    // instead. This works even when the direct context+uri overload fails.
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(pfd.fileDescriptor)
                val ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (ms != null && ms > 0) ms / 1000f else null
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("MediaDuration", "fd fallback also failed for $uri", e)
        null
    }
}

// Parses a frame-rate label like "30 fps" into its numeric value.
// Mirrors the parsing used in VideoRenderer.kt so the on-screen timecode
// (playhead frame counter) matches the frame rate the user picked in
// Project Settings, instead of always assuming 30fps.
fun parseFrameRate(frameRateStr: String): Int = when {
    frameRateStr.contains("60") -> 60
    frameRateStr.contains("30") -> 30
    frameRateStr.contains("24") -> 24
    frameRateStr.contains("15") -> 15
    else -> 30
}

@Composable
fun AlightMotionTimelineEditor() {
    val viewModel: com.example.viewmodel.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Plain coil-compose has no video decoder, so AsyncImage silently fails
    // to render anything for video URIs (images work, video thumbnails
    // don't). This ImageLoader adds VideoFrameDecoder so video media shows
    // a real frame instead of a blank/broken thumbnail.
    val videoAwareImageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components { add(coil.decode.VideoFrameDecoder.Factory()) }
            .build()
    }
    // Dragging tiny trim handles to reach large durations is awkward on a
    // small screen (the handle can scroll out of reach). This dialog lets
    // the user type an exact duration in seconds instead, as a reliable
    // alternative to dragging.
    var showDurationDialog by remember { mutableStateOf(false) }
    var durationInputText by remember { mutableStateOf("") }
    LaunchedEffect(viewModel.isPlaying) {
        if (viewModel.isPlaying) {
            var lastFrame = withFrameMillis { it }
            while (viewModel.isPlaying) {
                val currentFrame = withFrameMillis { it }
                val dt = (currentFrame - lastFrame) / 1000f // seconds
                lastFrame = currentFrame
                
                viewModel.playheadProgress += dt
                if (viewModel.playheadProgress > viewModel.timelineDurationSeconds) { // Loop after reaching the end
                    viewModel.playheadProgress = 0f
                }
            }
        }
    }

    CompositionLocalProvider(LocalDeletedLayers provides viewModel.deletedLayers) {
    if (viewModel.inEffectBrowser) {
        EffectBrowserScreen(
            onClose = { viewModel.inEffectBrowser = false },
            onSelectEffect = { effectName, category ->
                viewModel.inEffectBrowser = false
                if (!viewModel.selectedLayer.isNullOrEmpty()) {
                    viewModel.pushUndo()
                    val currentEffects = viewModel.layerEffects[viewModel.selectedLayer] ?: emptyList()
                    viewModel.layerEffects = viewModel.layerEffects + (viewModel.selectedLayer!! to (currentEffects + createDefaultEffect(effectName, category)))
                }
            }
        )
    } else if (viewModel.inColorPalette) {
        val layerId = viewModel.selectedLayer ?: ""
        val currentColor = viewModel.layerColors[layerId] ?: Color(0xFF16B996)
        ColorPaletteScreen(
            currentColor = currentColor,
            onColorChange = { newColor ->
                viewModel.pushUndo()
                val newColors = viewModel.layerColors.toMutableMap()
                newColors[layerId] = newColor
                viewModel.layerColors = newColors
            },
            onClose = { viewModel.inColorPalette = false }
        )
    } else if (viewModel.inVectorDrawing) {
        VectorDrawingEditor(
            points = viewModel.vectorPoints,
            onPointsChange = { viewModel.pushUndo(); viewModel.vectorPoints = it },
            selectedPointIndex = viewModel.selectedPointIndex,
            onSelectedPointChange = { viewModel.selectedPointIndex = it },
            pointModes = viewModel.pointModes,
            onPointModesChange = { viewModel.pushUndo(); viewModel.pointModes = it },
            playheadProgress = viewModel.playheadProgress,
            isPlaying = viewModel.isPlaying,
            onPlayPauseToggle = { viewModel.isPlaying = !viewModel.isPlaying },
            onClose = { viewModel.inVectorDrawing = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF181A20)).systemBarsPadding()) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.inMoveTransform) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inMoveTransform = false }.padding(end=16.dp))
                Text("Move & Transform", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.inColorFill) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inColorFill = false }.padding(end=16.dp))
                Text("Color & Fill", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.inEffects) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inEffects = false }.padding(end=16.dp))
                Text("Effects", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.inBlendingOpacity) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.inBlendingOpacity = false }.padding(end=16.dp))
                Text("Blending & Opacity", color = Color.White, fontSize = 16.sp)
            } else if (viewModel.selectedLayer == null) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("New Project 3", color = Color.White, fontSize = 16.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=0.3f)))
                }
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (viewModel.undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.undoStack.isNotEmpty()) { viewModel.performUndo(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (viewModel.redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.redoStack.isNotEmpty()) { viewModel.performRedo(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.showSettings = true })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.IosShare, contentDescription = "Export", tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.showExport = true })
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.clickable { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false; viewModel.inBlendingOpacity = false }.padding(end=16.dp))
                Column(modifier = Modifier.weight(1f)) {
                     Text(viewModel.selectedLayer ?: "", color = Color.White, fontSize = 14.sp, maxLines = 1)
                     Spacer(Modifier.height(4.dp))
                     Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha=0.3f)))
                }
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (viewModel.undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.undoStack.isNotEmpty()) { viewModel.performUndo(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (viewModel.redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.redoStack.isNotEmpty()) { viewModel.performRedo(context) })
                Spacer(Modifier.width(16.dp))
                Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.clickable {
                    viewModel.selectedLayer?.let { layerId ->
                        viewModel.pushUndo()
                        // Duplicate a "Shape N" or "Media N" layer by appending a
                        // copy to the matching backing list, then copying over
                        // its color/transform/timing onto the new layer id.
                        val newLayerId: String? = when {
                            layerId.startsWith("Shape ") -> {
                                val idx = layerId.removePrefix("Shape ").toIntOrNull()?.minus(1)
                                if (idx != null && idx in viewModel.addedShapes.indices) {
                                    val icon = viewModel.addedShapes[idx]
                                    viewModel.addedShapes = viewModel.addedShapes + listOf(icon)
                                    "Shape ${viewModel.addedShapes.size}"
                                } else null
                            }
                            layerId.startsWith("Media ") -> {
                                val idx = layerId.removePrefix("Media ").toIntOrNull()?.minus(1)
                                if (idx != null && idx in viewModel.addedMedia.indices) {
                                    val uri = viewModel.addedMedia[idx]
                                    viewModel.addedMedia = viewModel.addedMedia + listOf(uri)
                                    "Media ${viewModel.addedMedia.size}"
                                } else null
                            }
                            else -> null
                        }
                        if (newLayerId != null) {
                            val start = viewModel.layerStartTimes[layerId] ?: viewModel.playheadProgress
                            val end = viewModel.layerEndTimes[layerId]?.takeIf { it != Float.MAX_VALUE } ?: (start + DEFAULT_LAYER_DURATION_SECONDS)
                            viewModel.layerStartTimes = viewModel.layerStartTimes.toMutableMap().apply { put(newLayerId, viewModel.playheadProgress) }
                            viewModel.layerEndTimes = viewModel.layerEndTimes.toMutableMap().apply { put(newLayerId, viewModel.playheadProgress + (end - start)) }
                            viewModel.layerColors[layerId]?.let { c -> viewModel.layerColors = viewModel.layerColors.toMutableMap().apply { put(newLayerId, c) } }
                            viewModel.layerTransforms[layerId]?.let { t -> viewModel.layerTransforms[newLayerId] = t.copy() }
                            viewModel.selectedLayer = newLayerId
                            Toast.makeText(context, "Layer duplicated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                   Icon(Icons.Default.ContentCopy, "Duplicate", tint = Color.White, modifier = Modifier.size(20.dp).padding(top=2.dp, end=2.dp))
                   Text("TRY", color=Color(0xFF16171D), fontSize=6.sp, fontWeight=FontWeight.Black, modifier = Modifier.background(Color(0xFF16B996), RoundedCornerShape(2.dp)).padding(horizontal=2.dp, vertical=1.dp).align(Alignment.TopEnd).offset(x=8.dp, y=(-6).dp))
                }
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.Delete, "Delete", tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.performDeleteLayer(context) })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.Timer, "Set duration", tint = Color.White, modifier = Modifier.size(20.dp).clickable {
                    viewModel.selectedLayer?.let { layerId ->
                        val start = viewModel.layerStartTimes[layerId] ?: 0f
                        val end = viewModel.layerEndTimes[layerId]?.takeIf { it != Float.MAX_VALUE } ?: (start + DEFAULT_LAYER_DURATION_SECONDS)
                        durationInputText = String.format("%.2f", end - start)
                        showDurationDialog = true
                    }
                })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        
        // Canvas Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF181A20))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val parentWidth = maxWidth
            val parentHeight = maxHeight
            val parentRatio = if (parentHeight.value > 0) parentWidth.value / parentHeight.value else 1f
            val ratioVal = when (viewModel.selectedAspectRatio) {
                "16:9" -> 16f / 9f
                "9:16" -> 9f / 16f
                "4:5" -> 4f / 5f
                "1:1" -> 1f
                "4:3" -> 4f / 3f
                else -> 4f / 5f
            }
            
            val childWidth: androidx.compose.ui.unit.Dp
            val childHeight: androidx.compose.ui.unit.Dp
            if (parentRatio > ratioVal) {
                // Parent is wider than the target ratio; height is the limiting factor
                childHeight = parentHeight * 0.98f
                childWidth = childHeight * ratioVal
            } else {
                // Parent is taller than the target ratio; width is the limiting factor
                childWidth = parentWidth * 0.98f
                childHeight = childWidth / ratioVal
            }

            val canvasBgModifier = when (viewModel.selectedBackground) {
                "Black" -> Modifier.background(Color.Black)
                "White" -> Modifier.background(Color.White)
                "Light Grey" -> Modifier.background(Color(0xFFE2E2E2))
                "Green" -> Modifier.background(Color(0xFF107C41))
                "Blue" -> Modifier.background(Color(0xFF1F4E79))
                "Transparent" -> Modifier.checkerboard(gridSize = 8.dp)
                else -> Modifier.background(Color(0xFFE2E2E2))
            }

            // Whiteish canvas
            Box(
                modifier = Modifier
                    .size(width = childWidth, height = childHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .then(canvasBgModifier)
                    .onSizeChanged { size ->
                        viewModel.previewWidthPx = size.width.toFloat()
                        viewModel.previewHeightPx = size.height.toFloat()
                    }
                    .clickable { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false }
            ) {
               // watermark
               Box(
                   modifier = Modifier
                       .align(Alignment.CenterStart)
                       .offset(x = (-32).dp, y = 0.dp)
                       .background(Color.Black.copy(alpha=0.25f), RoundedCornerShape(percent = 50))
                       .padding(horizontal = 12.dp, vertical = 4.dp)
                       .rotate(-90f)
               ) {
                   Text("Alight Motion ×", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
               }
               
               // (Removed hardcoded demo layers: Triangle 1, image, Circle 1, Callout 1 preview rendering.
               // Real layers now render below via addedShapes/addedMedia.forEachIndexed.)
                viewModel.addedShapes.forEachIndexed { index, shapeIcon ->
                    val layerId = "Shape ${index + 1}"
                    val shapeStartTime = viewModel.layerStartTimes[layerId] ?: 0f
                    val shapeEndTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE
                    if (!viewModel.deletedLayers.contains(layerId) && viewModel.playheadProgress >= shapeStartTime && viewModel.playheadProgress <= shapeEndTime) {
                        val shapeColor = viewModel.layerColors[layerId] ?: Color(0xFF16B996)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = (index * 20 - 40).dp, x = (index * 20 - 40).dp)
                                .graphicsLayer {
                                    val transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes)
                                    translationX = transform.offsetX
                                    translationY = transform.offsetY
                                    rotationZ = transform.rotation
                                    scaleX = transform.scaleX
                                    scaleY = transform.scaleY
                                }
                                .alightMotionLayerBlend(layerId, viewModel.layerOpacities + (layerId to getActiveOpacity(layerId, viewModel.playheadProgress, viewModel.layerOpacities, viewModel.opacityKeyframes)), viewModel.layerBlendModes)
                                .alightMotionEffects(layerId, viewModel.layerEffects)
                                .layerTransformGestures(
                                    layerId = layerId,
                                    isSelected = viewModel.selectedLayer == layerId,
                                    onSelect = { viewModel.selectedLayer = layerId },
                                    transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes),
                                    onTransformChange = { updateLayerTransform(layerId, it, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes) },
                                    onGestureStart = { viewModel.pushUndo() }
                                )
                        ) {
                            if (shapeIcon != null) {
                                Icon(shapeIcon, contentDescription = null, modifier = Modifier.size(80.dp), tint = shapeColor)
                            } else {
                                if (layerId == "Shape 2") {
                                    Canvas(modifier = Modifier.size(120.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        val polyPath = Path().apply {
                                            if (viewModel.vectorPoints.isNotEmpty()) {
                                                val first = viewModel.vectorPoints[0]
                                                moveTo(first.x * w, first.y * h)
                                                for (i in 1 until viewModel.vectorPoints.size) {
                                                    val current = viewModel.vectorPoints[i]
                                                    if (viewModel.pointModes.getOrElse(i) { false }) {
                                                        val prev = viewModel.vectorPoints[i - 1]
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
                                        }
                                        drawPath(polyPath, Color(0xFF4EF293))
                                        drawPath(polyPath, Color.Black, style = Stroke(width = 2.dp.toPx()))
                                    }
                                } else {
                                    Canvas(modifier = Modifier.size(80.dp)) {
                                        drawArc(color = shapeColor, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 16f))
                                    }
                                }
                            }
                            if (viewModel.selectedLayer == layerId) {
                                Box(modifier = Modifier.matchParentSize()) { SelectionHandles() }
                            }
                        }
                    }
                }
                 viewModel.addedMedia.forEachIndexed { index, uri ->
                     val layerId = "Media ${index + 1}"
                     val mediaStartTime = viewModel.layerStartTimes[layerId] ?: 0f
                     val mediaEndTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE
                     if (!viewModel.deletedLayers.contains(layerId) && viewModel.playheadProgress >= mediaStartTime && viewModel.playheadProgress <= mediaEndTime) {
                         Box(
                             modifier = Modifier
                                 .align(Alignment.Center)
                                 .offset(y = (index * 20 - 40).dp, x = (index * 20 - 40).dp)
                                 .graphicsLayer {
                                     val transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes)
                                     translationX = transform.offsetX
                                     translationY = transform.offsetY
                                     rotationZ = transform.rotation
                                     scaleX = transform.scaleX
                                     scaleY = transform.scaleY
                                 }
                                 .alightMotionLayerBlend(layerId, viewModel.layerOpacities + (layerId to getActiveOpacity(layerId, viewModel.playheadProgress, viewModel.layerOpacities, viewModel.opacityKeyframes)), viewModel.layerBlendModes)
                                 .alightMotionEffects(layerId, viewModel.layerEffects)
                                 .layerTransformGestures(
                                     layerId = layerId,
                                     isSelected = viewModel.selectedLayer == layerId,
                                     onSelect = { viewModel.selectedLayer = layerId },
                                     transform = getActiveTransform(layerId, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes),
                                     onTransformChange = { updateLayerTransform(layerId, it, viewModel.playheadProgress, viewModel.layerTransforms, viewModel.layerKeyframes) },
                                     onGestureStart = { viewModel.pushUndo() }
                                 )
                         ) {
                             val mimeType = remember(uri) { context.contentResolver.getType(uri) }
                             val isVideo = mimeType?.startsWith("video/") == true
                             if (isVideo) {
                                 VideoLayerPreview(
                                     uri = uri,
                                     playheadProgressSeconds = viewModel.playheadProgress,
                                     layerStartSeconds = mediaStartTime,
                                     isPlaying = viewModel.isPlaying,
                                     modifier = Modifier.size(160.dp),
                                     onDurationKnownSeconds = { realDurationSeconds ->
                                         // Only auto-apply the real duration the first time it's
                                         // discovered for this layer. Otherwise, every time the
                                         // player fires this event (which can happen more than
                                         // once) it would clobber any manual trim the user made
                                         // afterward.
                                         if (!viewModel.autoResizedMediaLayers.contains(layerId)) {
                                             viewModel.autoResizedMediaLayers.add(layerId)
                                             val updatedEndTimes = viewModel.layerEndTimes.toMutableMap()
                                             updatedEndTimes[layerId] = mediaStartTime + realDurationSeconds
                                             viewModel.layerEndTimes = updatedEndTimes
                                         }
                                     }
                                 )
                             } else {
                                 AsyncImage(
                                     model = uri,
                                     contentDescription = null,
                                     imageLoader = videoAwareImageLoader,
                                     modifier = Modifier.size(160.dp)
                                 )
                             }
                             if (viewModel.selectedLayer == layerId) {
                                 Box(modifier = Modifier.matchParentSize()) { SelectionHandles() }
                             }
                         }
                     }
                 }
            }
        }
        
        // Toolbar controls below canvas
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF1B1D25)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Undo, null, tint = if (viewModel.undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.undoStack.isNotEmpty()) { viewModel.performUndo(context) })
            Icon(Icons.AutoMirrored.Filled.Redo, null, tint = if (viewModel.redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(20.dp).clickable(enabled = viewModel.redoStack.isNotEmpty()) { viewModel.performRedo(context) })
            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(24.dp).clickable { viewModel.playheadProgress = 0f; viewModel.isPlaying = false })
            Icon(
                if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                null, 
                tint = Color.White, 
                modifier = Modifier.size(28.dp).clickable { viewModel.isPlaying = !viewModel.isPlaying }
            )
            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(24.dp).clickable { viewModel.playheadProgress = viewModel.timelineDurationSeconds; viewModel.isPlaying = false })
            Icon(Icons.Default.LibraryAdd, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.showAddMenu = true })
            Icon(Icons.Default.CropFree, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { viewModel.selectedAspectRatio = if (viewModel.selectedAspectRatio == "9:16") "16:9" else if (viewModel.selectedAspectRatio == "16:9") "1:1" else "9:16" })
        }
        
        // Timeline zoom controls — pinch-to-zoom works directly on the
        // timeline below, but a visible +/- and reset-to-fit affordance is
        // what every professional NLE also offers, since not everyone
        // discovers pinch gestures on a touch timeline.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF181A20)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ZoomOut, null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clickable {
                    viewModel.timelineZoom = (viewModel.timelineZoom / 1.4f).coerceIn(TIMELINE_ZOOM_RANGE.start, TIMELINE_ZOOM_RANGE.endInclusive)
                }
            )
            Text(
                "${(viewModel.timelineZoom * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .clickable { viewModel.timelineZoom = 1f } // tap to reset to 100%
            )
            Icon(
                Icons.Default.ZoomIn, null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp).clickable {
                    viewModel.timelineZoom = (viewModel.timelineZoom * 1.4f).coerceIn(TIMELINE_ZOOM_RANGE.start, TIMELINE_ZOOM_RANGE.endInclusive)
                }
            )
            Spacer(Modifier.width(12.dp))
        }

        // Timeline Work Area

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color(0xFF1C1E26))
                .pointerInput(Unit) {
                    // Pinch-to-zoom: two-finger gesture scales the timeline's
                    // pixels-per-second, like scrubbing precision in a real NLE.
                    // Pans from the same gesture are ignored (single-finger
                    // pan/scrub is handled separately below) — only the zoom
                    // component of the transform is applied.
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) {
                            viewModel.timelineZoom = (viewModel.timelineZoom * zoomChange)
                                .coerceIn(TIMELINE_ZOOM_RANGE.start, TIMELINE_ZOOM_RANGE.endInclusive)
                        }
                    }
                }
                .pointerInput(Unit) {
                    // Playhead scrubbing: dragging or tapping anywhere on the timeline
                    // (ruler or tracks) moves the playhead. The timeline content is
                    // pinned so that `playheadX` always marks the current time; dragging
                    // the content by `dragAmount` is equivalent to moving the playhead
                    // by the opposite amount in time.
                    val wasPlayingBeforeScrub = booleanArrayOf(false)
                    detectDragGestures(
                        onDragStart = {
                            wasPlayingBeforeScrub[0] = viewModel.isPlaying
                            viewModel.isPlaying = false
                        },
                        onDragEnd = {
                            if (wasPlayingBeforeScrub[0]) viewModel.isPlaying = true
                        },
                        onDragCancel = {
                            if (wasPlayingBeforeScrub[0]) viewModel.isPlaying = true
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val pxPerSecond = TIMELINE_PIXELS_PER_SECOND * viewModel.timelineZoom
                        val deltaSeconds = dragAmount.x / pxPerSecond.dp.toPx()
                        val newProgress = (viewModel.playheadProgress - deltaSeconds)
                            .coerceIn(0f, viewModel.timelineDurationSeconds)
                        viewModel.playheadProgress = newProgress
                    }
                }
                .pointerInput(Unit) {
                    // Tap-to-seek: tapping a point on the ruler/track jumps the
                    // playhead directly to that time.
                    detectTapGestures { tapOffset ->
                        viewModel.isPlaying = false
                        val pxPerSecond = TIMELINE_PIXELS_PER_SECOND * viewModel.timelineZoom
                        val playheadXPx = (size.width + 64.dp.toPx()) / 2f
                        val deltaSeconds = (tapOffset.x - playheadXPx) / pxPerSecond.dp.toPx()
                        val newProgress = (viewModel.playheadProgress + deltaSeconds)
                            .coerceIn(0f, viewModel.timelineDurationSeconds)
                        viewModel.playheadProgress = newProgress
                    }
                }
        ) {
            
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val playheadX = (screenWidth + 64.dp) / 2
            val pxPerSecond = TIMELINE_PIXELS_PER_SECOND * viewModel.timelineZoom
            val scrollOffset = playheadX - 64.dp - (viewModel.playheadProgress * pxPerSecond).dp
            val density = LocalDensity.current

            // While dragging a trim handle, keep it reachable: the timeline only
            // scrolls in response to the playhead (see `scrollOffset` above), and
            // trimming alone never moved the playhead, so a handle dragged more
            // than a couple of seconds' worth of pixels from center would walk
            // straight off the physical screen with no way to bring it back into
            // view. Once the handle gets within a small margin of either screen
            // edge, nudge the playhead forward/back so the whole timeline scrolls
            // and the handle stays reachable — the same auto-scroll behavior
            // used while dragging near the edge of any scrollable list.
            val edgeMarginPx = with(density) { 24.dp.toPx() }
            val screenWidthPx = with(density) { screenWidth.toPx() }
            val onTrimHandleMoved: (Float) -> Unit = { xPx ->
                val overflowRight = xPx - (screenWidthPx - edgeMarginPx)
                val overflowLeft = edgeMarginPx - xPx
                val overflowSeconds = when {
                    overflowRight > 0f -> overflowRight / pxPerSecond
                    overflowLeft > 0f -> -overflowLeft / pxPerSecond
                    else -> 0f
                }
                if (overflowSeconds != 0f) {
                    viewModel.playheadProgress = (viewModel.playheadProgress + overflowSeconds)
                        .coerceIn(0f, viewModel.timelineDurationSeconds)
                }
            }

            // Snapping: collects every "interesting" timeline instant (0, every
            // other layer's start/end, and the playhead) so drag operations can
            // snap to them, the same as clip edges/playhead snapping in a real
            // NLE. Only OTHER layers' times are included for a given layer's own
            // drag so a clip never snaps to itself.
            fun snapPointsExcluding(excludeLayerId: String?): List<Float> {
                val points = mutableListOf(0f)
                viewModel.layerStartTimes.forEach { (id, t) ->
                    if (id != excludeLayerId && id !in viewModel.deletedLayers) points.add(t)
                }
                viewModel.layerEndTimes.forEach { (id, t) ->
                    if (id != excludeLayerId && id !in viewModel.deletedLayers && t.isFinite() && t != Float.MAX_VALUE) points.add(t)
                }
                points.add(viewModel.playheadProgress)
                return points
            }
            // Snap catch radius in seconds — converts a fixed pixel radius so
            // snapping feels equally "sticky" regardless of zoom level.
            val snapRadiusSeconds = with(density) { 10.dp.toPx() } / pxPerSecond
            fun applySnap(value: Float, excludeLayerId: String?): Float {
                val candidates = snapPointsExcluding(excludeLayerId)
                val nearest = candidates.minByOrNull { kotlin.math.abs(it - value) }
                return if (nearest != null && kotlin.math.abs(nearest - value) <= snapRadiusSeconds) nearest else value
            }

            // Content
            if (!viewModel.showAddMenu) {
                if (viewModel.selectedLayer == null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Ruler
                        Box(modifier = Modifier.height(32.dp).fillMaxWidth()) // Ruler space
                        
                        // (Removed hardcoded demo track rows: Triangle 1, image, Callout 1, YTDown video, Circle 1.
                        // Real tracks now render below via addedShapes/addedMedia.forEachIndexed.)
                        viewModel.addedShapes.forEachIndexed { index, shapeIcon ->
                            val layerId = "Shape ${index + 1}"
                            val shapeColor = viewModel.layerColors[layerId] ?: Color.LightGray
                            TrackRow(
                                icon = {
                                    if (shapeIcon != null) {
                                        Icon(shapeIcon, contentDescription = null, tint = shapeColor, modifier = Modifier.size(12.dp))
                                    } else {
                                        Canvas(modifier = Modifier.size(12.dp)) {
                                            drawArc(color = shapeColor, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2f))
                                        }
                                    }
                                },
                                stripColor = Color(0xFF75B69E),
                                title = layerId,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[layerId] ?: 0f,
                                endTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(layerId, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                onClick = { viewModel.selectedLayer = layerId }
                            )
                        }
                        viewModel.addedMedia.forEachIndexed { index, uri ->
                            val layerId = "Media ${index + 1}"
                            TrackRow(
                                icon = { Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(12.dp)) },
                                stripColor = Color(0xFFE2B06F),
                                title = layerId,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[layerId] ?: 0f,
                                endTime = viewModel.layerEndTimes[layerId] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(layerId, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                onClick = { viewModel.selectedLayer = layerId }
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.height(32.dp).fillMaxWidth()) // Ruler space
                        // NOTE: this used to be an if/else-if chain matching 5 hardcoded
                        // demo layer names (Triangle 1, Callout 1, Circle 1, a literal
                        // jpg filename, YTDown_YouTube_LIFE_FORCE...). Those demo layers
                        // were removed (BASE_LAYER_IDS is now empty — see EditorViewModel),
                        // so those branches were permanently unreachable dead code. Real
                        // layers are only ever named "Shape N" or "Media N".
                        if (viewModel.selectedLayer?.startsWith("Shape ") == true) {
                            val shapeIndex = viewModel.selectedLayer?.removePrefix("Shape ")?.toIntOrNull()?.minus(1)
                            if (shapeIndex != null && shapeIndex in viewModel.addedShapes.indices) {
                                val shapeIcon = viewModel.addedShapes[shapeIndex]
                                val shapeColor = viewModel.layerColors[viewModel.selectedLayer] ?: Color.LightGray
                                TrackRow(
                                    icon = {
                                        if (shapeIcon != null) Icon(shapeIcon, null, tint = shapeColor, modifier = Modifier.size(12.dp))
                                        else Canvas(modifier = Modifier.size(12.dp)) { drawArc(color = shapeColor, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2f)) }
                                    },
                                    stripColor = Color(0xFF75B69E),
                                    title = viewModel.selectedLayer!!,
                                    selected = true,
                                    scrollOffset = scrollOffset,
                                    startTime = viewModel.layerStartTimes[viewModel.selectedLayer] ?: 0f,
                                    endTime = viewModel.layerEndTimes[viewModel.selectedLayer] ?: Float.MAX_VALUE,
                                    keyframes = combinedKeyframeTimes(viewModel.selectedLayer!!, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                    pxPerSecond = pxPerSecond,
                                    onClick = { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false; viewModel.inBlendingOpacity = false },
                                    onTrimGestureStart = { viewModel.pushUndo() },
                                    onTrimStart = { delta ->
                                        val id = viewModel.selectedLayer!!
                                        val raw = (viewModel.layerStartTimes[id] ?: 0f) + delta
                                        val snapped = applySnap(raw, id)
                                        viewModel.updateLayerStartTime(id, snapped, viewModel.timelineDurationSeconds)
                                    },
                                    onTrimEnd = { delta ->
                                        val id = viewModel.selectedLayer!!
                                        val current = viewModel.layerEndTimes[id]?.takeIf { it != Float.MAX_VALUE } ?: (viewModel.layerStartTimes[id] ?: 0f) + DEFAULT_LAYER_DURATION_SECONDS
                                        val snapped = applySnap(current + delta, id)
                                        viewModel.updateLayerEndTime(id, snapped, viewModel.timelineDurationSeconds)
                                    },
                                    onTrimHandleMoved = onTrimHandleMoved
                                )
                            }
                        } else if (viewModel.selectedLayer?.startsWith("Media ") == true) {
                            TrackRow(
                                icon = { Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(12.dp)) },
                                stripColor = Color(0xFFE2B06F),
                                title = viewModel.selectedLayer!!,
                                selected = true,
                                scrollOffset = scrollOffset,
                                startTime = viewModel.layerStartTimes[viewModel.selectedLayer] ?: 0f,
                                endTime = viewModel.layerEndTimes[viewModel.selectedLayer] ?: Float.MAX_VALUE,
                                keyframes = combinedKeyframeTimes(viewModel.selectedLayer!!, viewModel.layerKeyframes, viewModel.opacityKeyframes),
                                pxPerSecond = pxPerSecond,
                                onClick = { viewModel.selectedLayer = null; viewModel.inMoveTransform = false; viewModel.inColorFill = false; viewModel.inBlendingOpacity = false },
                                onTrimGestureStart = { viewModel.pushUndo() },
                                onTrimStart = { delta ->
                                    val id = viewModel.selectedLayer!!
                                    // A manual drag-trim is an explicit choice — mark this
                                    // layer as already "resized" so a still-pending async
                                    // duration fetch (MediaMetadataRetriever in onAddMedia,
                                    // or the ExoPlayer callback in VideoLayerPreview) can't
                                    // silently clobber it once that fetch resolves.
                                    if (!viewModel.autoResizedMediaLayers.contains(id)) {
                                        viewModel.autoResizedMediaLayers.add(id)
                                    }
                                    val raw = (viewModel.layerStartTimes[id] ?: 0f) + delta
                                    val snapped = applySnap(raw, id)
                                    viewModel.updateLayerStartTime(id, snapped, viewModel.timelineDurationSeconds)
                                },
                                onTrimEnd = { delta ->
                                    val id = viewModel.selectedLayer!!
                                    if (!viewModel.autoResizedMediaLayers.contains(id)) {
                                        viewModel.autoResizedMediaLayers.add(id)
                                    }
                                    val current = viewModel.layerEndTimes[id]?.takeIf { it != Float.MAX_VALUE } ?: (viewModel.layerStartTimes[id] ?: 0f) + DEFAULT_LAYER_DURATION_SECONDS
                                    val snapped = applySnap(current + delta, id)
                                    viewModel.updateLayerEndTime(id, snapped, viewModel.timelineDurationSeconds)
                                },
                                onTrimHandleMoved = onTrimHandleMoved
                            )
                        }
                        
                        if (viewModel.inMoveTransform) {
                            val layerId = viewModel.selectedLayer ?: ""
                            SelectedLayerMoveTransformMenu(
                                layerId = layerId,
                                playheadProgress = viewModel.playheadProgress,
                                layerTransforms = viewModel.layerTransforms,
                                layerKeyframes = viewModel.layerKeyframes,
                                onBeforeTransformChange = { viewModel.pushUndo() },
                                onBack = { viewModel.inMoveTransform = false }
                            )
                        } else if (viewModel.inColorFill) {
                            val layerId = viewModel.selectedLayer ?: ""
                            val currentColor = viewModel.layerColors[layerId] ?: Color(0xFF16B996)
                            ColorFillMenu(
                                currentColor = currentColor,
                                onColorChange = { newColor ->
                                    viewModel.pushUndo()
                                    val newColors = viewModel.layerColors.toMutableMap()
                                    newColors[layerId] = newColor
                                    viewModel.layerColors = newColors
                                },
                                onBack = { viewModel.inColorFill = false }, 
                                onPaletteClick = { viewModel.inColorPalette = true }
                            )
                        } else if (viewModel.inEffects) {
                            val layerId = viewModel.selectedLayer ?: ""
                            EffectsMenu(
                                layerId = layerId,
                                appliedEffects = viewModel.layerEffects[layerId] ?: emptyList(),
                                onAddEffectClick = { viewModel.inEffectBrowser = true },
                                onUpdateEffect = { updatedEffect ->
                                    viewModel.pushUndo()
                                    val currentEffects = viewModel.layerEffects[layerId] ?: emptyList()
                                    viewModel.layerEffects = viewModel.layerEffects + (layerId to currentEffects.map { if (it.id == updatedEffect.id) updatedEffect else it })
                                },
                                onRemoveEffect = { removedId ->
                                    viewModel.pushUndo()
                                    val currentEffects = viewModel.layerEffects[layerId] ?: emptyList()
                                    viewModel.layerEffects = viewModel.layerEffects + (layerId to currentEffects.filter { it.id != removedId })
                                },
                                onBack = { viewModel.inEffects = false }
                            )
                        } else if (viewModel.inBlendingOpacity) {
                            val layerId = viewModel.selectedLayer ?: ""
                            val currentOpacity = getActiveOpacity(layerId, viewModel.playheadProgress, viewModel.layerOpacities, viewModel.opacityKeyframes)
                            val currentBlendMode = viewModel.layerBlendModes[layerId] ?: "Normal"
                            BlendingOpacityMenu(
                                currentOpacity = currentOpacity,
                                currentBlendMode = currentBlendMode,
                                layerId = layerId,
                                playheadProgress = viewModel.playheadProgress,
                                opacityKeyframes = viewModel.opacityKeyframes[layerId] ?: emptyList(),
                                onOpacityChange = { newOpacity ->
                                    viewModel.pushUndo()
                                    val appliedToKeyframe = updateLayerOpacityKeyframeIfPresent(layerId, newOpacity, viewModel.playheadProgress, viewModel.opacityKeyframes)
                                    if (!appliedToKeyframe) {
                                        val map = viewModel.layerOpacities.toMutableMap()
                                        map[layerId] = newOpacity
                                        viewModel.layerOpacities = map
                                    }
                                },
                                onToggleOpacityKeyframe = {
                                    viewModel.pushUndo()
                                    val existing = viewModel.opacityKeyframes[layerId] ?: emptyList()
                                    val index = existing.indexOfFirst { kotlin.math.abs(it.time - viewModel.playheadProgress) < 0.05f }
                                    val updated = existing.toMutableList()
                                    if (index != -1) {
                                        updated.removeAt(index)
                                    } else {
                                        updated.add(OpacityKeyframe(time = viewModel.playheadProgress, opacity = currentOpacity))
                                        updated.sortBy { it.time }
                                    }
                                    viewModel.opacityKeyframes[layerId] = updated
                                },
                                onBlendModeChange = { newMode ->
                                    viewModel.pushUndo()
                                    val map = viewModel.layerBlendModes.toMutableMap()
                                    map[layerId] = newMode
                                    viewModel.layerBlendModes = map
                                },
                                onBack = { viewModel.inBlendingOpacity = false }
                            )
                        } else {
                            LayerPropertiesMenu(
                                onMoveTransformClick = { viewModel.inMoveTransform = true },
                                onColorFillClick = { viewModel.inColorFill = true },
                                onEffectsClick = { viewModel.inEffects = true },
                                onBlendingOpacityClick = { viewModel.inBlendingOpacity = true },
                                onEditShapeClick = { viewModel.inVectorDrawing = true },
                                onSplitClick = {
                                    viewModel.selectedLayer?.let { layerId ->
                                        viewModel.pushUndo()
                                        // End current layer
                                        val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                                        val originalEndTime = newEndTimes[layerId] ?: Float.MAX_VALUE
                                        newEndTimes[layerId] = viewModel.playheadProgress
                                        viewModel.layerEndTimes = newEndTimes
                                        
                                        var newLayerId: String? = null
                                        
                                        // Duplicate layer
                                        if (layerId.startsWith("Shape ")) {
                                            val shapeIndex = layerId.removePrefix("Shape ").toIntOrNull()?.minus(1)
                                            if (shapeIndex != null && shapeIndex in viewModel.addedShapes.indices) {
                                                val shapeIcon = viewModel.addedShapes[shapeIndex]
                                                val newSize = viewModel.addedShapes.size + 1
                                                newLayerId = "Shape $newSize"
                                                viewModel.addedShapes = viewModel.addedShapes + listOf(shapeIcon)
                                            }
                                        } else if (layerId.startsWith("Media ")) {
                                            val mediaIndex = layerId.removePrefix("Media ").toIntOrNull()?.minus(1)
                                            if (mediaIndex != null && mediaIndex in viewModel.addedMedia.indices) {
                                                val uri = viewModel.addedMedia[mediaIndex]
                                                val newSize = viewModel.addedMedia.size + 1
                                                newLayerId = "Media $newSize"
                                                viewModel.addedMedia = viewModel.addedMedia + listOf(uri)
                                            }
                                        } else {
                                            val baseLayer = BASE_LAYER_IDS.find { layerId.startsWith(it) }
                                            if (baseLayer != null) {
                                                val count = (viewModel.defaultLayerCount[baseLayer] ?: 1) + 1
                                                val newCounts = viewModel.defaultLayerCount.toMutableMap()
                                                newCounts[baseLayer] = count
                                                viewModel.defaultLayerCount = newCounts
                                                newLayerId = "${baseLayer}_split_$count"
                                            }
                                        }

                                        newLayerId?.let { newId ->
                                            val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                                            newStartTimes[newId] = viewModel.playheadProgress
                                            viewModel.layerStartTimes = newStartTimes
                                            
                                            val newEndTimesForNew = viewModel.layerEndTimes.toMutableMap()
                                            newEndTimesForNew[newId] = originalEndTime
                                            viewModel.layerEndTimes = newEndTimesForNew
                                            
                                            viewModel.layerColors[layerId]?.let { color ->
                                                val newColors = viewModel.layerColors.toMutableMap()
                                                newColors[newId] = color
                                                viewModel.layerColors = newColors
                                            }
                                            viewModel.layerTransforms[layerId]?.let { transform ->
                                                viewModel.layerTransforms[newId] = transform.copy()
                                            }
                                            viewModel.selectedLayer = newId
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                 // The top ruler area still needs to exist to match padding, 
                 // but the screenshot shows the menu starting right below the ruler.
                 Column(modifier = Modifier.fillMaxSize()) {
                     Box(modifier = Modifier.height(32.dp).fillMaxWidth())
                     AddElementsMenu(
                         onClose = { viewModel.showAddMenu = false },
                         onAddShape = { icon -> 
                             viewModel.pushUndo()
                             val newSize = viewModel.addedShapes.size + 1
                             val newLayerId = "Shape $newSize"
                             viewModel.addedShapes = viewModel.addedShapes + listOf(icon)
                             
                             val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                             newStartTimes[newLayerId] = viewModel.playheadProgress
                             viewModel.layerStartTimes = newStartTimes

                             val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                             newEndTimes[newLayerId] = viewModel.playheadProgress + DEFAULT_LAYER_DURATION_SECONDS
                             viewModel.layerEndTimes = newEndTimes
                             
                             viewModel.selectedLayer = newLayerId
                             viewModel.showAddMenu = false
                         },
                         onVectorDrawingClick = {
                             viewModel.pushUndo()
                             while (viewModel.addedShapes.size < 2) {
                                 viewModel.addedShapes = viewModel.addedShapes + listOf<androidx.compose.ui.graphics.vector.ImageVector?>(null)
                             }
                             val layerId = "Shape 2"
                             val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                             newStartTimes[layerId] = viewModel.playheadProgress
                             viewModel.layerStartTimes = newStartTimes

                             val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                             newEndTimes[layerId] = viewModel.playheadProgress + DEFAULT_LAYER_DURATION_SECONDS
                             viewModel.layerEndTimes = newEndTimes

                             viewModel.selectedLayer = layerId
                             viewModel.showAddMenu = false
                             viewModel.inVectorDrawing = true
                         },
                         onAddMedia = { uri ->
                             viewModel.pushUndo()
                             val newSize = viewModel.addedMedia.size + 1
                             val newLayerId = "Media $newSize"
                             viewModel.addedMedia = viewModel.addedMedia + listOf(uri)
                             
                             val newStartTimes = viewModel.layerStartTimes.toMutableMap()
                             newStartTimes[newLayerId] = viewModel.playheadProgress
                             viewModel.layerStartTimes = newStartTimes

                             // Give the clip a default length immediately so it's
                             // usable right away, then refine it with the real
                             // media duration once that's known. MediaMetadataRetriever
                             // can take noticeable time on large video files, so it
                             // runs off the main thread to avoid UI jank/stutter.
                             val newEndTimes = viewModel.layerEndTimes.toMutableMap()
                             newEndTimes[newLayerId] = viewModel.playheadProgress + DEFAULT_LAYER_DURATION_SECONDS
                             viewModel.layerEndTimes = newEndTimes

                             coroutineScope.launch {
                                 var mediaDuration = withContext(Dispatchers.IO) {
                                     getMediaDurationSecondsOrNull(context, uri)
                                 }
                                 if (mediaDuration == null) {
                                     // Some content:// URIs aren't immediately readable right
                                     // after being returned from the picker. One short retry
                                     // avoids permanently leaving the clip stuck at the 5s
                                     // fallback duration.
                                     kotlinx.coroutines.delay(300)
                                     mediaDuration = withContext(Dispatchers.IO) {
                                         getMediaDurationSecondsOrNull(context, uri)
                                     }
                                 }
                                 // Only apply this if nothing has already claimed the layer's
                                 // duration in the meantime — a manual trim, the "Set duration"
                                 // dialog, or the ExoPlayer callback in VideoLayerPreview (which
                                 // is usually more reliable for content:// URIs than
                                 // MediaMetadataRetriever anyway). Without this check, this
                                 // slower fetch could resolve after the user already edited the
                                 // clip and silently overwrite that edit.
                                 if (mediaDuration != null && !viewModel.autoResizedMediaLayers.contains(newLayerId)) {
                                     viewModel.autoResizedMediaLayers.add(newLayerId)
                                     val updatedEndTimes = viewModel.layerEndTimes.toMutableMap()
                                     updatedEndTimes[newLayerId] = (viewModel.layerStartTimes[newLayerId] ?: 0f) + mediaDuration
                                     viewModel.layerEndTimes = updatedEndTimes
                                 } else if (mediaDuration == null) {
                                     android.widget.Toast.makeText(
                                         context,
                                         "Couldn't read video length — you can still trim the clip manually.",
                                         android.widget.Toast.LENGTH_SHORT
                                     ).show()
                                 }
                             }
                             
                             viewModel.selectedLayer = newLayerId
                             viewModel.showAddMenu = false
                         }
                     )
                 }
            }
            
            // Playhead Visuals Overlay (Ruler & Line)
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                // Draw ruler ticks + second labels. Tick spacing adapts to zoom:
                // at 1x, one second = pxPerSecond px, with a minor tick every
                // 0.2s and a labeled major tick every 1s — as the timeline
                // zooms out, major ticks widen to every 5s/10s/30s so labels
                // never overlap; zoomed in, they narrow for frame-level work.
                val startX = 64.dp.toPx() 
                val scrollPx = scrollOffset.toPx()

                // Choose a "nice" major-tick interval (in seconds) so there's
                // always comfortable label spacing regardless of zoom.
                val minMajorPxGap = 56.dp.toPx()
                val niceIntervals = listOf(0.2f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f)
                val majorIntervalSeconds = niceIntervals.firstOrNull { it * pxPerSecond >= minMajorPxGap } ?: niceIntervals.last()
                val minorIntervalSeconds = majorIntervalSeconds / 5f
                val tickSpx = minorIntervalSeconds * pxPerSecond
                val zeroOffset = startX + scrollPx
                
                val firstTickIndex = if (startX > zeroOffset) {
                    ((startX - zeroOffset) / tickSpx).toInt()
                } else {
                     -((zeroOffset - startX) / tickSpx).toInt()
                }
                
                var tickIndex = maxOf(0, firstTickIndex) // no negative time
                var drawX = zeroOffset + tickIndex * tickSpx
                
                while(drawX < size.width) {
                    if (drawX >= startX) {
                        val tickSeconds = tickIndex * minorIntervalSeconds
                        val isMajor = kotlin.math.abs(tickSeconds % majorIntervalSeconds) < (minorIntervalSeconds / 2f)
                        val tickHeight = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                        drawLine(
                            Color.White.copy(alpha=0.2f),
                            Offset(drawX, 32.dp.toPx() - tickHeight),
                            Offset(drawX, 32.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                        if (isMajor) {
                            val label = formatRulerLabel(tickSeconds)
                            val measured = textMeasurer.measure(
                                label,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            drawText(measured, topLeft = Offset(drawX + 3.dp.toPx(), 2.dp.toPx()))
                        }
                    }
                    tickIndex++
                    drawX += tickSpx
                }
                
                // Playhead line
                drawLine(
                    Color(0xFFF96085),
                    Offset(playheadX.toPx(), 12.dp.toPx()), 
                    Offset(playheadX.toPx(), size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            
            // Playhead Time Box
            val sec = viewModel.playheadProgress.toInt()
            val fps = parseFrameRate(viewModel.selectedFrameRate)
            val frames = ((viewModel.playheadProgress - sec) * fps).toInt()
            val timeStr = String.format("00:%02d:%02d", sec, frames)

            Box(
                modifier = Modifier
                    .offset(x = playheadX - 35.dp, y = 8.dp)
                    .background(Color(0xFF1C1E26))
                    .border(1.dp, Color(0xFFF96085), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(timeStr, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            
            // Floating Button Add (+)
            if (!viewModel.showAddMenu && viewModel.selectedLayer == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(56.dp)
                        .background(Color(0xFF181A20), CircleShape)
                        .border(2.dp, Color(0xFF16B996), CircleShape)
                        .clickable { viewModel.showAddMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF16B996), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
        
    if (viewModel.showSettings) {
        ProjectSettingsMenu(
            selectedRatio = viewModel.selectedAspectRatio,
            onRatioSelected = { viewModel.selectedAspectRatio = it },
            selectedResolution = viewModel.selectedResolution,
            onResolutionSelected = { viewModel.selectedResolution = it },
            selectedFrameRate = viewModel.selectedFrameRate,
            onFrameRateSelected = { viewModel.selectedFrameRate = it },
            selectedBackground = viewModel.selectedBackground,
            onBackgroundSelected = { viewModel.selectedBackground = it },
            onClose = { viewModel.showSettings = false }
        )
    }
    
    if (viewModel.showExport) {
        ExportShareMenu(
            selectedResolution = viewModel.selectedResolution,
            onResolutionSelected = { viewModel.selectedResolution = it },
            selectedFrameRate = viewModel.selectedFrameRate,
            onFrameRateSelected = { viewModel.selectedFrameRate = it },
            selectedAspectRatio = viewModel.selectedAspectRatio,
            selectedBackground = viewModel.selectedBackground,
            vectorPoints = viewModel.vectorPoints,
            pointModes = viewModel.pointModes,
            layerColors = viewModel.layerColors,
            defaultLayerCount = viewModel.defaultLayerCount,
            addedMedia = viewModel.addedMedia,
            addedShapes = viewModel.addedShapes,
            deletedLayers = viewModel.deletedLayers.toList(),
            layerStartTimes = viewModel.layerStartTimes,
            layerEndTimes = viewModel.layerEndTimes,
            layerTransforms = viewModel.layerTransforms.toMap(),
            layerKeyframes = viewModel.layerKeyframes.toMap(),
            previewWidthPx = viewModel.previewWidthPx,
            previewHeightPx = viewModel.previewHeightPx,
            timelineDurationSeconds = viewModel.timelineDurationSeconds,
            onClose = { viewModel.showExport = false }
        )
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("Set clip duration") },
            text = {
                Column {
                    Text("Duration in seconds", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = durationInputText,
                        onValueChange = { durationInputText = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val layerId = viewModel.selectedLayer
                    val seconds = durationInputText.toFloatOrNull()
                    if (layerId != null && seconds != null && seconds > 0f) {
                        viewModel.pushUndo()
                        val start = viewModel.layerStartTimes[layerId] ?: 0f
                        val updatedEndTimes = viewModel.layerEndTimes.toMutableMap()
                        updatedEndTimes[layerId] = start + seconds
                        viewModel.layerEndTimes = updatedEndTimes
                        // A manually-entered duration is an explicit, intentional
                        // choice — don't let a later media-duration callback
                        // silently override it.
                        if (!viewModel.autoResizedMediaLayers.contains(layerId)) {
                            viewModel.autoResizedMediaLayers.add(layerId)
                        }
                        showDurationDialog = false
                    } else {
                        Toast.makeText(context, "Enter a valid number of seconds", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) { Text("Cancel") }
            }
        )
    }

    }
}
}
}

@Composable
fun AddElementsMenu(
    onClose: () -> Unit,
    onAddShape: (androidx.compose.ui.graphics.vector.ImageVector?) -> Unit = {},
    onAddMedia: (android.net.Uri) -> Unit = {},
    onVectorDrawingClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("Shape") }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onAddMedia(uri)
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF222634))) {
        // Left side: Tabs & Shapes Grid
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Tabs 
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                 MenuTabItem("Shape", Icons.Default.Category, selected = selectedTab == "Shape", onClick = { selectedTab = "Shape" })
                 MenuTabItem("Media", Icons.Default.Image, selected = selectedTab == "Media", onClick = { selectedTab = "Media" })
                 MenuTabItem("Audio", Icons.Default.Audiotrack, selected = selectedTab == "Audio", onClick = { selectedTab = "Audio" })
                 MenuTabItem("Object / Element", Icons.Default.Dashboard, selected = selectedTab == "Object", onClick = { selectedTab = "Object" }) 
                 MenuTabItem("Template", Icons.Default.WebStories, selected = selectedTab == "Template", onClick = { selectedTab = "Template" })
            }
            // Divider
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.1f)))
            
            if (selectedTab == "Shape") {
                // Shapes Grid
                Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ShapeIcon(Icons.Default.Circle) { onAddShape(Icons.Default.Circle) }
                        ShapeIcon(Icons.Default.Square) { onAddShape(Icons.Default.Square) }
                        ShapeIcon(Icons.Default.Add) { onAddShape(Icons.Default.Add) }
                        ShapeIcon(null) { onAddShape(null) } // Arc/Crescent placeholder
                        ShapeIcon(Icons.Default.ChangeHistory) { onAddShape(Icons.Default.ChangeHistory) } // Triangle
                        ShapeIcon(Icons.Default.ChatBubble) { onAddShape(Icons.Default.ChatBubble) }
                        ShapeIcon(Icons.Default.WaterDrop) { onAddShape(Icons.Default.WaterDrop) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ShapeIcon(Icons.Default.PieChart) { onAddShape(Icons.Default.PieChart) }
                        ShapeIcon(Icons.Default.Hexagon) { onAddShape(Icons.Default.Hexagon) }
                        ShapeIcon(Icons.Default.Star) { onAddShape(Icons.Default.Star) }
                        ShapeIcon(Icons.Default.NorthEast) { onAddShape(Icons.Default.NorthEast) }
                        ShapeIcon(Icons.Default.Rectangle) { onAddShape(Icons.Default.Rectangle) } // Trapezoid-ish
                        ShapeIcon(Icons.Default.NightsStay) { onAddShape(Icons.Default.NightsStay) } // Crescent
                        ShapeIcon(Icons.Default.Cloud) { onAddShape(Icons.Default.Cloud) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ShapeIcon(Icons.Default.Stop) { onAddShape(Icons.Default.Stop) }
                        ShapeIcon(Icons.Default.StarBorder) { onAddShape(Icons.Default.StarBorder) }
                        ShapeIcon(Icons.Default.HorizontalRule) { onAddShape(Icons.Default.HorizontalRule) }
                        ShapeIcon(Icons.Default.ArrowOutward) { onAddShape(Icons.Default.ArrowOutward) }
                        ShapeIcon(Icons.Default.Details) { onAddShape(Icons.Default.Details) }
                        ShapeIcon(Icons.Default.Apps) { onAddShape(Icons.Default.Apps) }
                        ShapeIcon(Icons.Default.Favorite) { onAddShape(Icons.Default.Favorite) }
                    }
                }
            } else if (selectedTab == "Media") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16B996))
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Image")
                    }
                }
            }
        }
        
        // Right side: Vertical column 
        // Background slightly darker
        Column(
            modifier = Modifier.width(72.dp).fillMaxHeight().background(Color(0xFF1B1D25)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            ActionItem("Freehand\nDrawing", Icons.Default.Edit)
            Spacer(Modifier.height(16.dp))
            ActionItem("Vector\nDrawing", Icons.Default.InvertColors, onClick = onVectorDrawingClick)
            Spacer(Modifier.height(16.dp))
            ActionItem("Text", Icons.Default.TextFields)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Close, 
                contentDescription = "Close", 
                tint = Color.White, 
                modifier = Modifier
                    .padding(16.dp)
                    .size(24.dp)
                    .clickable { onClose() }
            )
        }
    }
}

@Composable
fun MenuTabItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean = false, onClick: () -> Unit = {}) {
    val color = if (selected) Color(0xFF16B996) else Color.White.copy(alpha=0.8f)
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = color, fontSize = 10.sp, fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ShapeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit = {}) {
    Box(modifier = Modifier.size(32.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
        } else {
            // Fallback generic shape
            Canvas(modifier = Modifier.size(28.dp)) {
                drawArc(color = Color.LightGray, startAngle = 45f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 8f))
            }
        }
    }
}

@Composable
fun ActionItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = Color.White, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 11.sp)
    }
}

@Composable
fun LayerPropertiesMenu(
    onMoveTransformClick: () -> Unit = {},
    onColorFillClick: () -> Unit = {},
    onEffectsClick: () -> Unit = {},
    onBlendingOpacityClick: () -> Unit = {},
    onSplitClick: () -> Unit = {},
    onEditShapeClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1B1D25))) {
        // Top slim row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniToolIcon(Icons.Default.Speed)
            MiniToolIcon(Icons.AutoMirrored.Filled.FormatAlignLeft)
            MiniToolIcon(Icons.Default.ContentCut, onClick = onSplitClick)
            MiniToolIcon(Icons.AutoMirrored.Filled.FormatAlignRight)
            MiniToolIcon(Icons.AutoMirrored.Filled.VolumeOff)
        }
        
        // Grid
        val buttonBg = Color(0xFF262C3A)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PropertyBlock(Icons.Default.FormatColorFill, "Color & Fill", buttonBg, Modifier.weight(1f), onClick = onColorFillClick)
                PropertyBlock(Icons.Default.CropSquare, "Border & Shadow", buttonBg, Modifier.weight(1f))
                PropertyBlock(Icons.Default.Layers, "Blending & Opacity", buttonBg, Modifier.weight(1f), onClick = onBlendingOpacityClick)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PropertyBlock(Icons.Default.OpenWith, "Move & Transform", buttonBg, Modifier.weight(1f), onClick = onMoveTransformClick)
                PropertyBlock(Icons.Default.Category, "Edit Shape", buttonBg, Modifier.weight(1f), onClick = onEditShapeClick)
                PropertyBlock(Icons.Default.AutoAwesome, "Effects", buttonBg, Modifier.weight(1f), onClick = onEffectsClick)
            }
        }
    }
}

@Composable
fun MiniToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.size(48.dp, 36.dp).background(Color(0xFF262C3A), RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha=0.6f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PropertyBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, bgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(72.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
fun TrackRow(
    icon: @Composable () -> Unit, 
    stripColor: Color, 
    title: String, 
    textColor: Color = Color.Black,
    selected: Boolean = false,
    scrollOffset: androidx.compose.ui.unit.Dp = 0.dp,
    startTime: Float = 0f,
    endTime: Float = Float.MAX_VALUE,
    keyframes: List<Float> = emptyList(),
    pxPerSecond: Float = TIMELINE_PIXELS_PER_SECOND,
    onClick: () -> Unit = {},
    onTrimStart: ((deltaSeconds: Float) -> Unit)? = null,
    onTrimEnd: ((deltaSeconds: Float) -> Unit)? = null,
    onTrimGestureStart: (() -> Unit)? = null,
    // Fires while dragging either trim handle, with that handle's current
    // absolute X position on screen (px). Lets the caller auto-scroll the
    // timeline (by nudging the playhead) when the handle nears/passes the
    // edge of the visible viewport, since the timeline itself only scrolls
    // in response to playhead changes — trimming alone never moved it,
    // so a handle dragged past ~a few seconds' worth of pixels would walk
    // off the physical screen with no way to bring it back into view.
    onTrimHandleMoved: ((absoluteXPx: Float) -> Unit)? = null
) {
    if (LocalDeletedLayers.current.contains(title)) return
    val bgColor = if (selected) Color(0xFF2E3246) else Color.Transparent
    // While either trim handle is actively being dragged, show a small
    // floating time readout above that edge — the same "what time am I at"
    // feedback a professional NLE gives instead of making you guess from
    // pixel position alone.
    var trimTooltipSeconds by remember { mutableStateOf<Float?>(null) }
    var trimTooltipIsStart by remember { mutableStateOf(true) }

    Row(modifier = Modifier.fillMaxWidth().height(36.dp).padding(bottom = 2.dp).background(bgColor).clickable(onClick = onClick)) {
        Box(modifier = Modifier.width(64.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier
                    .background(Color(0xFF262934), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Visibility, null, tint = if(selected) Color(0xFFE25B5B) else Color.White.copy(alpha=0.7f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                icon()
            }
        }
        
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val stripStart = scrollOffset + (startTime * pxPerSecond).dp
            val stripModifier = if (endTime != Float.MAX_VALUE) {
                // requiredWidth (not width!) is essential here: this strip lives
                // inside a Box(Modifier.weight(1f)) whose max-width constraint is
                // bounded by the visible screen. A plain .width(...) modifier
                // respects/coerces to that incoming max constraint, silently
                // shrinking the strip back down to screen width no matter how
                // long the clip's actual duration is — which is exactly why clips
                // visually appeared capped at a few seconds even though the
                // underlying start/end time state was already correct.
                // requiredWidth ignores the incoming constraint and renders the
                // strip (and its trim handles) at its true, correct size.
                Modifier.offset(x = stripStart).requiredWidth(((endTime - startTime) * pxPerSecond).dp)
            } else {
                Modifier.offset(x = stripStart).fillMaxWidth()
            }
            Box(
                modifier = stripModifier
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(stripColor)
                    .then(
                        if (selected) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        else Modifier
                    )
            ) {
                // Keyframe Diamonds drawing block
                keyframes.forEach { kfTime ->
                    val kfOffset = ((kfTime - startTime) * pxPerSecond).dp
                    Box(
                        modifier = Modifier
                            .offset(x = kfOffset - 5.dp, y = 11.dp)
                            .size(10.dp)
                            .rotate(45f)
                            .background(Color.White, RoundedCornerShape(1.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
                    )
                }

                Row(
                   modifier = Modifier.padding(start=4.dp, end=4.dp).fillMaxHeight().fillMaxWidth(),
                   verticalAlignment = Alignment.CenterVertically,
                   horizontalArrangement = Arrangement.SpaceBetween
                ) {
                   if (selected) {
                       Box(Modifier.width(16.dp).fillMaxHeight().background(Color.Black.copy(0.2f)), contentAlignment=Alignment.Center) {
                          Icon(Icons.Default.ChevronLeft, null, tint=Color.White, modifier=Modifier.size(12.dp))
                       }
                   }
                   Text(title, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                   if (selected) {
                       // hidden right arrow container
                   } else {
                       Icon(Icons.Default.Menu, null, tint = textColor.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                   }
                }

                // Trim handles are only shown (and draggable) for the selected clip,
                // so casual taps on other tracks can't accidentally resize them.
                if (selected && onTrimStart != null) {
                    // Left edge handle: drag to change the clip's start time.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(16.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .onGloballyPositioned { coords ->
                                onTrimHandleMoved?.invoke(coords.positionInRoot().x)
                            }
                            .pointerInput(title) {
                                detectDragGestures(
                                    onDragStart = {
                                        trimTooltipIsStart = true
                                        trimTooltipSeconds = startTime
                                        onTrimGestureStart?.invoke()
                                    },
                                    onDragEnd = { trimTooltipSeconds = null },
                                    onDragCancel = { trimTooltipSeconds = null }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val deltaSeconds = dragAmount.x / pxPerSecond.dp.toPx()
                                    onTrimStart(deltaSeconds)
                                    trimTooltipSeconds = (trimTooltipSeconds ?: startTime) + deltaSeconds
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Grip dots: the small vertical dot pair is the universal
                        // "drag me" affordance used by every mainstream editor's
                        // trim handles, instead of a single plain bar.
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) {
                                Box(Modifier.size(3.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                }
                if (selected && onTrimEnd != null) {
                    // Right edge handle: drag to change the clip's end time.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(16.dp)
                            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .onGloballyPositioned { coords ->
                                onTrimHandleMoved?.invoke(coords.positionInRoot().x)
                            }
                            .pointerInput(title) {
                                detectDragGestures(
                                    onDragStart = {
                                        trimTooltipIsStart = false
                                        trimTooltipSeconds = endTime.takeIf { it != Float.MAX_VALUE } ?: startTime
                                        onTrimGestureStart?.invoke()
                                    },
                                    onDragEnd = { trimTooltipSeconds = null },
                                    onDragCancel = { trimTooltipSeconds = null }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val deltaSeconds = dragAmount.x / pxPerSecond.dp.toPx()
                                    onTrimEnd(deltaSeconds)
                                    trimTooltipSeconds = (trimTooltipSeconds ?: endTime) + deltaSeconds
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) {
                                Box(Modifier.size(3.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                }

                trimTooltipSeconds?.let { seconds ->
                    val tooltipX = if (trimTooltipIsStart) 0.dp else ((endTime - startTime).let { if (it.isFinite()) (it * pxPerSecond).dp else 0.dp })
                    Box(
                        modifier = Modifier
                            .offset(x = tooltipX, y = (-28).dp)
                            .background(Color(0xFF1C1E26), RoundedCornerShape(4.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(formatTimecode(seconds), color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// Formats seconds as mm:ss.f for compact trim tooltips.
fun formatTimecode(totalSeconds: Float): String {
    val clamped = totalSeconds.coerceAtLeast(0f)
    val minutes = (clamped / 60).toInt()
    val seconds = clamped % 60
    return String.format("%d:%04.1f", minutes, seconds)
}

// Formats a ruler major-tick label. Sub-second intervals show one decimal
// (e.g. "0.5s"); whole-second-and-above intervals show mm:ss so long
// projects stay readable once zoomed out.
fun formatRulerLabel(totalSeconds: Float): String {
    val rounded = Math.round(totalSeconds * 10) / 10f
    return if (rounded < 60f && rounded % 1f != 0f) {
        String.format("%.1fs", rounded)
    } else {
        val minutes = (rounded / 60).toInt()
        val seconds = (rounded % 60).toInt()
        if (minutes > 0) String.format("%d:%02d", minutes, seconds) else "${seconds}s"
    }
}
