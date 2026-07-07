package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import com.example.EditorSnapshot
import com.example.LayerKeyframe
import com.example.LayerTransform
import com.example.OpacityKeyframe
import com.example.model.AppliedEffect

// Timeline zoom bounds, applied as a multiplier on TIMELINE_PIXELS_PER_SECOND.
// 0.25x shows roughly 4x more time on screen (good for scanning a long
// project); 4x shows fine frame-level detail for precise trims.
val TIMELINE_ZOOM_RANGE = 0.25f..4f

// Minimum timeline length shown even for an empty/short project, so the
// timeline strip is never zero-width and there's still room to work with.
const val MIN_TIMELINE_DURATION_SECONDS = 8f

// Single source of truth for the demo project's base layer IDs.
// Previously this held 5 hardcoded demo layers ("Triangle 1", "Circle 1",
// "Callout 1", a sample image, and a sample video) that always appeared on
// first launch. It is now empty so a fresh project starts blank; layers
// only appear once the user actually adds them.
val BASE_LAYER_IDS = emptyList<String>()

class EditorViewModel : ViewModel() {
    var isPlaying by mutableStateOf(false)
    var playheadProgress by mutableFloatStateOf(0f)
    var selectedLayer by mutableStateOf<String?>(null)

    // Timeline zoom level: multiplies the base pixels-per-second scale
    // (see TIMELINE_PIXELS_PER_SECOND). 1x is the default/original density;
    // pinch-to-zoom on the timeline adjusts this within ZOOM_RANGE.
    var timelineZoom by mutableFloatStateOf(1f)

    // The live preview canvas's actual on-screen pixel size (varies by
    // device). Layer transform offsets (from drag gestures) are captured in
    // this same pixel space, so export needs this to proportionally scale
    // those offsets to the export resolution, which is usually a different
    // pixel size than the on-screen preview.
    var previewWidthPx by mutableStateOf(1f)
    var previewHeightPx by mutableStateOf(1f)
    var inMoveTransform by mutableStateOf(false)
    var inColorFill by mutableStateOf(false)
    var inBlendingOpacity by mutableStateOf(false)
    var inColorPalette by mutableStateOf(false)
    var inEffects by mutableStateOf(false)
    var inEffectBrowser by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showExport by mutableStateOf(false)
    var showAddMenu by mutableStateOf(false)
    var selectedAspectRatio by mutableStateOf("9:16")
    var selectedResolution by mutableStateOf("1080p (FHD)")
    var selectedFrameRate by mutableStateOf("30 fps")
    var selectedBackground by mutableStateOf("Light Grey")
    var inVectorDrawing by mutableStateOf(false)
    var vectorPoints by mutableStateOf(listOf<Offset>())
    var selectedPointIndex by mutableIntStateOf(-1)
    var pointModes by mutableStateOf(listOf<Boolean>())
    var addedShapes by mutableStateOf(listOf<ImageVector?>())
    var addedMedia by mutableStateOf(listOf<Uri>())

    val layerTransforms = mutableStateMapOf<String, LayerTransform>()
    // Previously pre-populated with hardcoded demo keyframes for "Circle 1".
    // Now starts empty; keyframes are added as the user actually animates layers.
    val layerKeyframes = mutableStateMapOf<String, List<LayerKeyframe>>()
    // Independent opacity keyframe track — see OpacityKeyframe in MoveTransform.kt.
    val opacityKeyframes = mutableStateMapOf<String, List<OpacityKeyframe>>()
    
    var layerColors by mutableStateOf(mapOf<String, Color>())
    var layerOpacities by mutableStateOf(mapOf<String, Float>())
    var layerBlendModes by mutableStateOf(mapOf<String, String>())
    var layerEffects by mutableStateOf(mapOf<String, List<AppliedEffect>>())
    var layerEndTimes by mutableStateOf(mapOf<String, Float>())
    var layerStartTimes by mutableStateOf(mapOf<String, Float>())
    var defaultLayerCount by mutableStateOf(BASE_LAYER_IDS.associateWith { 1 })

    // Total timeline length in seconds, computed from the furthest-reaching
    // layer end time instead of a fixed constant. Falls back to
    // MIN_TIMELINE_DURATION_SECONDS only when there are no layers at all
    // (a fresh/empty project) — it is NOT a floor, so a project whose
    // only layer is shorter than 8s will correctly show a shorter timeline.
    //
    // Deleted layers are explicitly excluded: layerEndTimes is a soft-delete
    // map (entries stick around to support undo), so without this filter a
    // layer the user removed (or an old layer from before a trim) could keep
    // contributing its leftover end time to this max forever, making
    // playback run longer than every currently-visible layer would suggest.
    val timelineDurationSeconds: Float
        get() {
            val finiteEnds = layerEndTimes
                .filterKeys { it !in deletedLayers }
                .values
                .filter { it.isFinite() && it != Float.MAX_VALUE }
            return finiteEnds.maxOrNull() ?: MIN_TIMELINE_DURATION_SECONDS
        }
    
    val deletedLayers = mutableStateListOf<String>()

    // Tracks which media layers have already had their placeholder duration
    // replaced by the real, player-reported duration. Without this, the
    // duration-known callback (which can fire more than once as the player
    // reports state) would keep resetting the clip length, undoing any
    // manual trim the user made after the real duration was first applied.
    val autoResizedMediaLayers = mutableStateListOf<String>()

    val undoStack = mutableStateListOf<EditorSnapshot>()
    val redoStack = mutableStateListOf<EditorSnapshot>()

    fun captureSnapshot(): EditorSnapshot {
        return EditorSnapshot(
            defaultLayerCount = defaultLayerCount.toMap(),
            deletedLayers = deletedLayers.toList(),
            layerColors = layerColors.toMap(),
            layerOpacities = layerOpacities.toMap(),
            layerBlendModes = layerBlendModes.toMap(),
            layerStartTimes = layerStartTimes.toMap(),
            layerEndTimes = layerEndTimes.toMap(),
            layerTransforms = layerTransforms.toMap(),
            layerKeyframes = layerKeyframes.toMap(),
            opacityKeyframes = opacityKeyframes.toMap(),
            layerEffects = layerEffects.toMap(),
            addedShapes = addedShapes.toList(),
            addedMedia = addedMedia.toList(),
            vectorPoints = vectorPoints.toList(),
            pointModes = pointModes.toList()
        )
    }

    fun pushUndo() {
        undoStack.add(captureSnapshot())
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun performUndo(context: Context? = null) {
        if (undoStack.isNotEmpty()) {
            val current = captureSnapshot()
            redoStack.add(current)
            val snapshot = undoStack.removeAt(undoStack.lastIndex)
            defaultLayerCount = snapshot.defaultLayerCount
            deletedLayers.clear()
            deletedLayers.addAll(snapshot.deletedLayers)
            layerColors = snapshot.layerColors
            layerOpacities = snapshot.layerOpacities
            layerBlendModes = snapshot.layerBlendModes
            layerStartTimes = snapshot.layerStartTimes
            layerEndTimes = snapshot.layerEndTimes
            layerTransforms.clear()
            layerTransforms.putAll(snapshot.layerTransforms)
            layerKeyframes.clear()
            layerKeyframes.putAll(snapshot.layerKeyframes)
            opacityKeyframes.clear()
            opacityKeyframes.putAll(snapshot.opacityKeyframes)
            layerEffects = snapshot.layerEffects
            addedShapes = snapshot.addedShapes
            addedMedia = snapshot.addedMedia
            vectorPoints = snapshot.vectorPoints
            pointModes = snapshot.pointModes
            context?.let { Toast.makeText(it, "Undo performed", Toast.LENGTH_SHORT).show() }
        } else {
            context?.let { Toast.makeText(it, "Nothing to undo", Toast.LENGTH_SHORT).show() }
        }
    }

    fun performRedo(context: Context? = null) {
        if (redoStack.isNotEmpty()) {
            val current = captureSnapshot()
            undoStack.add(current)
            val snapshot = redoStack.removeAt(redoStack.lastIndex)
            defaultLayerCount = snapshot.defaultLayerCount
            deletedLayers.clear()
            deletedLayers.addAll(snapshot.deletedLayers)
            layerColors = snapshot.layerColors
            layerOpacities = snapshot.layerOpacities
            layerBlendModes = snapshot.layerBlendModes
            layerStartTimes = snapshot.layerStartTimes
            layerEndTimes = snapshot.layerEndTimes
            layerTransforms.clear()
            layerTransforms.putAll(snapshot.layerTransforms)
            layerKeyframes.clear()
            layerKeyframes.putAll(snapshot.layerKeyframes)
            opacityKeyframes.clear()
            opacityKeyframes.putAll(snapshot.opacityKeyframes)
            layerEffects = snapshot.layerEffects
            addedShapes = snapshot.addedShapes
            addedMedia = snapshot.addedMedia
            vectorPoints = snapshot.vectorPoints
            pointModes = snapshot.pointModes
            context?.let { Toast.makeText(it, "Redo performed", Toast.LENGTH_SHORT).show() }
        } else {
            context?.let { Toast.makeText(it, "Nothing to redo", Toast.LENGTH_SHORT).show() }
        }
    }

    fun performDeleteLayer(context: Context? = null) {
        selectedLayer?.let { layerId ->
            pushUndo()
            if (!deletedLayers.contains(layerId)) {
                deletedLayers.add(layerId)
            }
            defaultLayerCount = defaultLayerCount.toMutableMap().apply { put(layerId, 0) }
            selectedLayer = null
            inMoveTransform = false
            inColorFill = false
            inEffects = false
            inBlendingOpacity = false
            context?.let { Toast.makeText(it, "Layer deleted", Toast.LENGTH_SHORT).show() }
        }
    }

    /** Minimum clip duration (seconds) enforced while trimming, so a clip can't be dragged to zero/negative length. */
    private val minClipDurationSeconds = 0.2f

    /**
     * Updates the start time of a clip while trimming its left edge.
     * Clamped so it never goes below 0, never crosses the end time (minus the
     * minimum duration), and the timeline's undo history is preserved.
     */
    fun updateLayerStartTime(layerId: String, newStartTime: Float, timelineDurationSeconds: Float) {
        val currentEnd = layerEndTimes[layerId]?.takeIf { it != Float.MAX_VALUE } ?: timelineDurationSeconds
        val clamped = newStartTime.coerceIn(0f, currentEnd - minClipDurationSeconds)
        val updated = layerStartTimes.toMutableMap()
        updated[layerId] = clamped
        layerStartTimes = updated
    }

    /**
     * Updates the end time of a clip while trimming its right edge.
     * Clamped so it never exceeds the timeline duration and never crosses the
     * start time (minus the minimum duration).
     */
    fun updateLayerEndTime(layerId: String, newEndTime: Float, timelineDurationSeconds: Float) {
        val currentStart = layerStartTimes[layerId] ?: 0f
        // NOTE: no longer clamped to an upper bound of `timelineDurationSeconds`.
        // That value is now DERIVED from layer end times (see
        // EditorViewModel.timelineDurationSeconds), so clamping to it here
        // would trap the end time at wherever it currently is — you could
        // never drag the right edge further out, only shrink it. Growth is
        // otherwise unbounded, same as dragging a clip's edge in a real editor.
        val clamped = newEndTime.coerceAtLeast(currentStart + minClipDurationSeconds)
        val updated = layerEndTimes.toMutableMap()
        updated[layerId] = clamped
        layerEndTimes = updated
    }
}

