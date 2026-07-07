package com.example.render

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer

/**
 * Kotlin-side entry point into the native (C++) rendering engine.
 *
 * This is the first slice of migrating VideoRenderer.kt's export pipeline
 * off pure-Kotlin CPU compositing and onto native code — see
 * app/src/main/cpp/ for the C++ side. Scope of this slice:
 *
 *  - [compositeFrame] replaces the layer-compositing loop currently done
 *    with android.graphics.Canvas in drawShapeLayers/drawMediaLayers.
 *  - [bitmapToYuv420] replaces VideoRenderer.kt's bitmapToYuv, which was
 *    the single largest per-frame cost in the old pipeline (a per-pixel
 *    Kotlin loop run once per exported frame).
 *
 * Nothing here touches OpenGL/EGL yet — this first pass is a native CPU
 * compositor (still much faster than the Kotlin version thanks to no JVM
 * overhead per pixel and real SIMD-friendly C++), which is the safer,
 * incremental first step. GPU (OpenGL ES) compositing is a natural next
 * slice once this path is verified correct, without needing to change the
 * JNI surface much further.
 *
 * IMPORTANT — this is a starting scaffold, not a drop-in replacement yet:
 * VideoRenderer.kt still needs to be updated to call these functions
 * instead of its existing Kotlin code paths. Do that migration
 * incrementally and verify exported video against the old pipeline's
 * output before removing the old Kotlin code, in case the native path
 * needs a fix.
 */
object NativeRenderer {

    private var loaded = false
    private var loadError: Throwable? = null

    init {
        try {
            System.loadLibrary("motionstudio_renderer")
            loaded = true
        } catch (t: Throwable) {
            // Keep the app usable (falling back to the Kotlin renderer)
            // even if the native library failed to load on a given device/
            // ABI, rather than crashing the whole app at class-init time.
            loadError = t
            Log.e("NativeRenderer", "Failed to load native renderer library", t)
        }
    }

    /** True if the native library loaded successfully and is safe to call into. */
    val isAvailable: Boolean get() = loaded

    /** The error that occurred while loading the native library, if any. */
    fun loadFailureReason(): Throwable? = loadError

    /**
     * Returns a version string from the native side, e.g.
     * "motionstudio-native-renderer/0.1". Useful as a smoke test that the
     * JNI bridge is wired correctly before trusting it with real frames.
     */
    external fun nativeEngineVersion(): String

    /**
     * Composites [layers] onto [outBitmap] in place, back-to-front. Both
     * [outBitmap] and every bitmap in [layers] must be [Bitmap.Config.ARGB_8888].
     *
     * [opacities], [offsetsX], [offsetsY], [rotationsDegrees], [scalesX],
     * [scalesY] must each have the same size as [layers], one entry per
     * layer in the same order, mirroring that layer's LayerTransform plus
     * its current opacity-keyframe value.
     */
    private external fun nativeCompositeFrame(
        outBitmap: Bitmap,
        layerBitmaps: Array<Bitmap>,
        opacities: FloatArray,
        offsetsX: FloatArray,
        offsetsY: FloatArray,
        rotationsDegrees: FloatArray,
        scalesX: FloatArray,
        scalesY: FloatArray
    )

    fun compositeFrame(
        outBitmap: Bitmap,
        layers: List<RenderLayer>
    ) {
        require(loaded) { "Native renderer library is not loaded; check isAvailable before calling." }
        nativeCompositeFrame(
            outBitmap,
            Array(layers.size) { layers[it].bitmap },
            FloatArray(layers.size) { layers[it].opacity },
            FloatArray(layers.size) { layers[it].offsetX },
            FloatArray(layers.size) { layers[it].offsetY },
            FloatArray(layers.size) { layers[it].rotationDegrees },
            FloatArray(layers.size) { layers[it].scaleX },
            FloatArray(layers.size) { layers[it].scaleY }
        )
    }

    /**
     * Converts [bitmap] (must be ARGB_8888) to YUV420 into [outYuv], a
     * **direct** ByteBuffer of at least width*height*3/2 bytes — an
     * indirect (heap-backed) ByteBuffer will fail silently on the native
     * side (GetDirectBufferAddress returns null), so always allocate with
     * ByteBuffer.allocateDirect.
     *
     * Pass [semiPlanar] = true for NV12 output, false for I420 — this must
     * match whichever color format VideoRenderer.kt's selectCodec chose,
     * exactly as the old bitmapToYuv's useSemiPlanar parameter did.
     */
    external fun nativeBitmapToYuv420(
        bitmap: Bitmap,
        outYuv: ByteBuffer,
        semiPlanar: Boolean
    )
}

/** One layer's worth of input to [NativeRenderer.compositeFrame]. */
data class RenderLayer(
    val bitmap: Bitmap,
    val opacity: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)
