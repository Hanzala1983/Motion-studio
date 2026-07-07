package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.render.NativeRenderer
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

object VideoRenderer {
    private const val TAG = "VideoRenderer"

    // How close (in microseconds) two export frame timestamps must be to
    // reuse the same decoded video frame instead of seeking again.
    // ~83ms ≈ one frame at 12fps — video motion is sampled at up to 12fps
    // even when exporting at 30/60fps, trading a bit of motion smoothness
    // for a large cut in expensive MediaMetadataRetriever seeks (this was
    // the main export slowdown: a costly seek+decode on every single frame).
    private const val VIDEO_FRAME_CACHE_THRESHOLD_US = 83_000L

    // Holds a pre-prepared added-media layer, ready to be drawn into export
    // frames without re-decoding the source file every frame.
    private data class ExportMediaLayer(
        val layerId: String,
        val uri: Uri,
        val isVideo: Boolean,
        val imageBitmap: Bitmap?,
        val videoRetriever: android.media.MediaMetadataRetriever?,
        // Cache of the last frame fetched via getFrameAtTime for this video
        // layer, so consecutive export frames whose timestamps land close
        // together (see VIDEO_FRAME_CACHE_THRESHOLD_US) can reuse it instead
        // of paying for another expensive seek+decode each time.
        var cachedFrameTimeUs: Long = -1L,
        var cachedFrameBitmap: Bitmap? = null
    )

    private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image for export", e)
            null
        }
    }

    // Converts an ImageVector's path nodes into an android.graphics.Path,
    // scaled from the icon's own viewport units to the target pixel size.
    // Covers the path node types Material Icons commonly use (move/line/
    // horizontal/vertical/cubic/quad + close). ArcTo nodes (used by some
    // rounded/circular icons) are approximated as a straight line to the
    // arc's end point, since Android's Canvas has no direct equivalent of
    // an SVG-style elliptical arc without a full arc-to-bezier conversion —
    // affected icons may look slightly faceted instead of perfectly round.
    private fun pathNodesToAndroidPath(
        nodes: List<androidx.compose.ui.graphics.vector.PathNode>,
        scaleX: Float,
        scaleY: Float
    ): AndroidPath {
        val path = AndroidPath()
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        for (node in nodes) {
            when (node) {
                is androidx.compose.ui.graphics.vector.PathNode.MoveTo -> {
                    curX = node.x * scaleX; curY = node.y * scaleY
                    startX = curX; startY = curY
                    path.moveTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.RelativeMoveTo -> {
                    curX += node.dx * scaleX; curY += node.dy * scaleY
                    startX = curX; startY = curY
                    path.moveTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.LineTo -> {
                    curX = node.x * scaleX; curY = node.y * scaleY
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.RelativeLineTo -> {
                    curX += node.dx * scaleX; curY += node.dy * scaleY
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.HorizontalTo -> {
                    curX = node.x * scaleX
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.RelativeHorizontalTo -> {
                    curX += node.dx * scaleX
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.VerticalTo -> {
                    curY = node.y * scaleY
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.RelativeVerticalTo -> {
                    curY += node.dy * scaleY
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.CurveTo -> {
                    path.cubicTo(node.x1 * scaleX, node.y1 * scaleY, node.x2 * scaleX, node.y2 * scaleY, node.x3 * scaleX, node.y3 * scaleY)
                    curX = node.x3 * scaleX; curY = node.y3 * scaleY
                }
                is androidx.compose.ui.graphics.vector.PathNode.RelativeCurveTo -> {
                    val x1 = curX + node.dx1 * scaleX; val y1 = curY + node.dy1 * scaleY
                    val x2 = curX + node.dx2 * scaleX; val y2 = curY + node.dy2 * scaleY
                    val x3 = curX + node.dx3 * scaleX; val y3 = curY + node.dy3 * scaleY
                    path.cubicTo(x1, y1, x2, y2, x3, y3)
                    curX = x3; curY = y3
                }
                is androidx.compose.ui.graphics.vector.PathNode.QuadTo -> {
                    path.quadTo(node.x1 * scaleX, node.y1 * scaleY, node.x2 * scaleX, node.y2 * scaleY)
                    curX = node.x2 * scaleX; curY = node.y2 * scaleY
                }
                is androidx.compose.ui.graphics.vector.PathNode.RelativeQuadTo -> {
                    val x1 = curX + node.dx1 * scaleX; val y1 = curY + node.dy1 * scaleY
                    val x2 = curX + node.dx2 * scaleX; val y2 = curY + node.dy2 * scaleY
                    path.quadTo(x1, y1, x2, y2)
                    curX = x2; curY = y2
                }
                is androidx.compose.ui.graphics.vector.PathNode.ArcTo -> {
                    curX = node.arcStartX * scaleX; curY = node.arcStartY * scaleY
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.RelativeArcTo -> {
                    curX += node.arcStartDx * scaleX; curY += node.arcStartDy * scaleY
                    path.lineTo(curX, curY)
                }
                is androidx.compose.ui.graphics.vector.PathNode.Close -> {
                    path.close()
                    curX = startX; curY = startY
                }
                else -> { /* ReflectiveCurveTo / ReflectiveQuadTo: rare in Material icons, skipped */ }
            }
        }
        return path
    }

    // Rasterizes an ImageVector (a Material icon) into a solid-tinted Bitmap
    // of the given pixel size, so it can be drawn onto the raw export Canvas
    // the same way an image/video frame is.
    private fun renderImageVectorToBitmap(imageVector: androidx.compose.ui.graphics.vector.ImageVector, sizePx: Int, tintColor: Int): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val viewportW = imageVector.viewportWidth
            val viewportH = imageVector.viewportHeight
            if (viewportW <= 0f || viewportH <= 0f) return null
            val scaleX = sizePx / viewportW
            val scaleY = sizePx / viewportH
            val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = tintColor }

            fun drawGroup(group: androidx.compose.ui.graphics.vector.VectorGroup) {
                canvas.save()
                // Apply this group's own transform (translate, then rotate/scale
                // around its pivot) so icons built from nested transformed
                // groups position correctly instead of rendering with raw,
                // un-transformed local coordinates (which can push their paths
                // outside the visible area, making them appear to not render).
                canvas.translate(group.translationX * scaleX, group.translationY * scaleY)
                canvas.translate(group.pivotX * scaleX, group.pivotY * scaleY)
                canvas.rotate(group.rotation)
                canvas.scale(group.scaleX, group.scaleY)
                canvas.translate(-group.pivotX * scaleX, -group.pivotY * scaleY)
                for (child in group) {
                    when (child) {
                        is androidx.compose.ui.graphics.vector.VectorPath -> {
                            val androidPath = pathNodesToAndroidPath(child.pathData, scaleX, scaleY)
                            paint.alpha = (child.fillAlpha * 255).toInt().coerceIn(0, 255)
                            canvas.drawPath(androidPath, paint)
                        }
                        is androidx.compose.ui.graphics.vector.VectorGroup -> drawGroup(child)
                        else -> {}
                    }
                }
                canvas.restore()
            }
            drawGroup(imageVector.root)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rasterize shape icon for export", e)
            null
        }
    }

    suspend fun renderAndSaveVideo(
        context: Context,
        resolution: String,
        aspectRatio: String,
        frameRateStr: String,
        backgroundStr: String,
        vectorPoints: List<Offset>,
        pointModes: List<Boolean>,
        layerColors: Map<String, androidx.compose.ui.graphics.Color>,
        defaultLayerCount: Map<String, Int>,
        addedMedia: List<Uri> = emptyList(),
        addedShapes: List<androidx.compose.ui.graphics.vector.ImageVector?> = emptyList(),
        deletedLayers: List<String> = emptyList(),
        layerStartTimes: Map<String, Float> = emptyMap(),
        layerEndTimes: Map<String, Float> = emptyMap(),
        layerTransforms: Map<String, LayerTransform> = emptyMap(),
        layerKeyframes: Map<String, List<LayerKeyframe>> = emptyMap(),
        previewWidthPx: Float = 1f,
        previewHeightPx: Float = 1f,
        timelineDurationSeconds: Float = 4.0f,
        onProgress: (Float) -> Unit
    ): Pair<Uri?, String?> = withContext(Dispatchers.IO) {
        try {
            val fps = when {
                frameRateStr.contains("60") -> 60
                frameRateStr.contains("30") -> 30
                frameRateStr.contains("24") -> 24
                frameRateStr.contains("15") -> 15
                else -> 30
            }

            val (width, height) = getExportDimensions(resolution, aspectRatio)
            val bitrate = width * height * 4 // Solid bitrate for high quality

            val tempFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}.mp4")
            if (tempFile.exists()) tempFile.delete()

            // Prepare each added media layer for drawing into export frames.
            // Images are decoded once up-front; videos get a single reusable
            // MediaMetadataRetriever so we can pull a frame at the right
            // in-clip timestamp for each exported frame without re-opening
            // the file every time.
            val mediaLayers = addedMedia.mapIndexed { index, uri ->
                val layerId = "Media ${index + 1}"
                val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
                val isVideo = mimeType?.startsWith("video/") == true
                ExportMediaLayer(
                    layerId = layerId,
                    uri = uri,
                    isVideo = isVideo,
                    imageBitmap = if (!isVideo) decodeBitmapFromUri(context, uri) else null,
                    videoRetriever = if (isVideo) android.media.MediaMetadataRetriever().apply {
                        try { setDataSource(context, uri) } catch (e: Exception) { Log.e(TAG, "Failed to open video for export", e) }
                    } else null
                )
            }

            // Rasterize each added shape (Material icon) into a tinted bitmap
            // sized relative to the export resolution, ready to be drawn each
            // frame the same way media layers are.
            val shapeSizePx = (minOf(width, height) * 0.3f).toInt().coerceAtLeast(1)
            val shapeLayers = addedShapes.mapIndexedNotNull { index, icon ->
                if (icon == null) return@mapIndexedNotNull null // null icon = vector-drawing path, handled separately
                val layerId = "Shape ${index + 1}"
                val tintColor = layerColors[layerId]?.let {
                    AndroidColor.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
                } ?: AndroidColor.rgb(22, 185, 150)
                val bitmap = renderImageVectorToBitmap(icon, shapeSizePx, tintColor)
                if (bitmap != null) layerId to bitmap else null
            }

            // Find valid color format
            val mime = MediaFormat.MIMETYPE_VIDEO_AVC
            val codecInfo = selectCodec(mime) ?: throw RuntimeException("No H264 encoder found")
            val caps = codecInfo.getCapabilitiesForType(mime)
            var colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            var useSemiPlanar = true

            for (fmt in caps.colorFormats) {
                if (fmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    colorFormat = fmt
                    useSemiPlanar = true
                    break
                } else if (fmt == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    colorFormat = fmt
                    useSemiPlanar = false
                }
            }

            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createByCodecName(codecInfo.name)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val totalDurationSec = timelineDurationSeconds
            val totalFrames = (totalDurationSec * fps).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val pixels = IntArray(width * height)

            // Reused across every exported frame so we don't re-allocate a
            // width*height*3/2 direct buffer per frame. Only actually
            // needed when the native path is available (see below), but
            // cheap enough to always allocate up front.
            val nativeYuvBuffer: java.nio.ByteBuffer? = if (NativeRenderer.isAvailable) {
                java.nio.ByteBuffer.allocateDirect(width * height * 3 / 2)
            } else null

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputDone = false
            var frameIndex = 0

            while (!isInputDone || drainEncoder(encoder, muxer, bufferInfo, false, muxerStarted).also { if (it.first != -1) { videoTrackIndex = it.first; muxerStarted = it.second } }.third) {
                if (!isInputDone) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000L)
                    if (inputBufferIndex >= 0) {
                        if (frameIndex >= totalFrames) {
                            encoder.queueInputBuffer(inputBufferIndex, 0, 0, (frameIndex * 1000000L) / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputDone = true
                        } else {
                            val timeSec = frameIndex.toFloat() / fps
                            drawTimelineFrame(canvas, width, height, timeSec, backgroundStr, vectorPoints, pointModes, layerColors, defaultLayerCount)
                            drawMediaLayers(canvas, width, height, timeSec, mediaLayers, deletedLayers, layerStartTimes, layerEndTimes, layerTransforms, layerKeyframes, previewWidthPx, previewHeightPx)
                            drawShapeLayers(canvas, width, height, timeSec, shapeLayers, deletedLayers, layerStartTimes, layerEndTimes, layerTransforms, layerKeyframes, previewWidthPx, previewHeightPx)

                            val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                            inputBuffer?.clear()

                            // Prefer the native compositor's YUV conversion
                            // (see NativeRenderer.nativeBitmapToYuv420) —
                            // it's the same math as bitmapToYuv below but
                            // without the per-pixel JVM loop, which was the
                            // single biggest export bottleneck. Fall back to
                            // the pure-Kotlin path per-frame if the native
                            // library isn't loaded or the call fails, so a
                            // native issue on a given device degrades to the
                            // old (slower but proven) path instead of
                            // breaking export entirely.
                            val usedNative = nativeYuvBuffer != null && try {
                                nativeYuvBuffer.clear()
                                NativeRenderer.nativeBitmapToYuv420(bitmap, nativeYuvBuffer, useSemiPlanar)
                                nativeYuvBuffer.rewind()
                                inputBuffer?.put(nativeYuvBuffer)
                                true
                            } catch (e: Throwable) {
                                Log.e(TAG, "Native YUV conversion failed for frame $frameIndex, falling back to Kotlin path", e)
                                false
                            }

                            if (!usedNative) {
                                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                                inputBuffer?.put(bitmapToYuv(pixels, width, height, useSemiPlanar))
                            }

                            val presentationTimeUs = (frameIndex * 1000000L) / fps
                            encoder.queueInputBuffer(inputBufferIndex, 0, width * height * 3 / 2, presentationTimeUs, 0)

                            frameIndex++
                            onProgress(frameIndex.toFloat() / totalFrames)
                        }
                    }
                }

                val drainRes = drainEncoder(encoder, muxer, bufferInfo, isInputDone, muxerStarted)
                if (drainRes.first != -1) videoTrackIndex = drainRes.first
                muxerStarted = drainRes.second
                if (isInputDone && !drainRes.third) break
            }

            encoder.stop()
            encoder.release()
            muxer.stop()
            muxer.release()
            bitmap.recycle()
            mediaLayers.forEach { layer ->
                layer.imageBitmap?.recycle()
                layer.cachedFrameBitmap?.recycle()
                try { layer.videoRetriever?.release() } catch (e: Exception) {}
            }
            shapeLayers.forEach { (_, bitmap) -> bitmap.recycle() }

            // Save to MediaStore (Gallery / Movies)
            val uri = saveVideoToDevice(context, tempFile, resolution)
            val savedPath = tempFile.absolutePath
            Pair(uri, savedPath)
        } catch (e: Exception) {
            Log.e(TAG, "Video rendering failed", e)
            Pair(null, null)
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        currentMuxerStarted: Boolean
    ): Triple<Int, Boolean, Boolean> {
        var trackIdx = -1
        var muxerStarted = currentMuxerStarted
        var keepProcessing = true

        val outIndex = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10000L else 0L)
        if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            if (!endOfStream) keepProcessing = false
        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val newFormat = encoder.outputFormat
            trackIdx = muxer.addTrack(newFormat)
            muxer.start()
            muxerStarted = true
        } else if (outIndex >= 0) {
            val encodedData = encoder.getOutputBuffer(outIndex)
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0
            }
            if (bufferInfo.size != 0 && muxerStarted) {
                encodedData?.position(bufferInfo.offset)
                encodedData?.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIdx.takeIf { it != -1 } ?: 0, encodedData!!, bufferInfo)
            }
            encoder.releaseOutputBuffer(outIndex, false)
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                keepProcessing = false
            }
        }
        return Triple(trackIdx, muxerStarted, keepProcessing)
    }

    private fun getExportDimensions(resolution: String, aspectRatio: String): Pair<Int, Int> {
        val baseSize = when {
            resolution.contains("1440") -> 1080 // limit to 1080p for fast device encoding
            resolution.contains("1080") -> 1080
            resolution.contains("720") -> 720
            resolution.contains("540") -> 540
            else -> 360
        }
        val (rawW, rawH) = when (aspectRatio) {
            "16:9" -> Pair((baseSize * 16f / 9f).toInt(), baseSize)
            "9:16" -> Pair(baseSize, (baseSize * 16f / 9f).toInt())
            "1:1" -> Pair(baseSize, baseSize)
            "4:5" -> Pair(baseSize, (baseSize * 5f / 4f).toInt())
            "4:3" -> Pair((baseSize * 4f / 3f).toInt(), baseSize)
            else -> Pair(baseSize, (baseSize * 16f / 9f).toInt())
        }
        // Align to multiple of 16 for H264
        val w = (rawW + 15) / 16 * 16
        val h = (rawH + 15) / 16 * 16
        return Pair(w, h)
    }

    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        val numCodecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.size
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    return info
                }
            }
        }
        return null
    }

    private fun bitmapToYuv(pixels: IntArray, width: Int, height: Int, useSemiPlanar: Boolean): ByteArray {
        val frameSize = width * height
        val yuv = ByteArray(frameSize * 3 / 2)
        var yIndex = 0
        var uvIndex = frameSize
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = pixels[j * width + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                var y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                var u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                var v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                y = y.coerceIn(0, 255)
                u = u.coerceIn(0, 255)
                v = v.coerceIn(0, 255)

                yuv[yIndex++] = y.toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    if (useSemiPlanar) {
                        yuv[uvIndex++] = u.toByte()
                        yuv[uvIndex++] = v.toByte()
                    } else {
                        yuv[uIndex++] = u.toByte()
                        yuv[vIndex++] = v.toByte()
                    }
                }
            }
        }
        return yuv
    }

    // Draws each added media layer (image or video-frame) that is visible at
    // the given export time, respecting its start/end trim just like the
    // editor preview does. Media is centered and scaled to fit within most
    // of the frame; per-layer position/rotation/scale transforms are not
    // yet applied here (the editor's Move & Transform values only affect
    // the live preview so far, not the export) — that's a follow-up.
    private fun drawShapeLayers(
        canvas: Canvas,
        w: Int,
        h: Int,
        timeSec: Float,
        shapeLayers: List<Pair<String, Bitmap>>,
        deletedLayers: List<String>,
        layerStartTimes: Map<String, Float>,
        layerEndTimes: Map<String, Float>,
        layerTransforms: Map<String, LayerTransform>,
        layerKeyframes: Map<String, List<LayerKeyframe>>,
        previewWidthPx: Float,
        previewHeightPx: Float
    ) {
        val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
        val scaleFactor = if (previewWidthPx > 0f) w / previewWidthPx else 1f

        for ((layerId, bitmap) in shapeLayers) {
            if (deletedLayers.contains(layerId)) continue
            val startTime = layerStartTimes[layerId] ?: 0f
            val endTime = layerEndTimes[layerId] ?: Float.MAX_VALUE
            if (timeSec < startTime || timeSec > endTime) continue

            val transform = getActiveTransform(layerId, timeSec, layerTransforms, layerKeyframes)
            val centerX = w / 2f + transform.offsetX * scaleFactor
            val centerY = h / 2f + transform.offsetY * scaleFactor
            val destRect = RectF(centerX - bitmap.width / 2f, centerY - bitmap.height / 2f, centerX + bitmap.width / 2f, centerY + bitmap.height / 2f)

            canvas.save()
            canvas.rotate(transform.rotation, centerX, centerY)
            canvas.scale(transform.scaleX, transform.scaleY, centerX, centerY)
            canvas.drawBitmap(bitmap, null, destRect, paint)
            canvas.restore()
        }
    }

    private fun drawMediaLayers(
        canvas: Canvas,
        w: Int,
        h: Int,
        timeSec: Float,
        mediaLayers: List<ExportMediaLayer>,
        deletedLayers: List<String>,
        layerStartTimes: Map<String, Float>,
        layerEndTimes: Map<String, Float>,
        layerTransforms: Map<String, LayerTransform>,
        layerKeyframes: Map<String, List<LayerKeyframe>>,
        previewWidthPx: Float,
        previewHeightPx: Float
    ) {
        val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
        // Layer transform offsets were captured in the on-screen preview
        // canvas's own pixel space, which is usually a different size than
        // the export resolution — scale proportionally so a drag that moved
        // a layer halfway across the preview also moves it halfway across
        // the exported frame.
        val scaleFactor = if (previewWidthPx > 0f) w / previewWidthPx else 1f

        for (layer in mediaLayers) {
            if (deletedLayers.contains(layer.layerId)) continue
            val startTime = layerStartTimes[layer.layerId] ?: 0f
            val endTime = layerEndTimes[layer.layerId] ?: Float.MAX_VALUE
            if (timeSec < startTime || timeSec > endTime) continue

            val frameBitmap = if (layer.isVideo) {
                val localTimeUs = ((timeSec - startTime).coerceAtLeast(0f) * 1_000_000L).toLong()
                if (layer.cachedFrameBitmap != null && kotlin.math.abs(localTimeUs - layer.cachedFrameTimeUs) < VIDEO_FRAME_CACHE_THRESHOLD_US) {
                    // Close enough to the last fetched frame — reuse it
                    // instead of paying for another seek+decode.
                    layer.cachedFrameBitmap
                } else {
                    val fresh = try {
                        layer.videoRetriever?.getFrameAtTime(localTimeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (e: Exception) {
                        null
                    }
                    if (fresh != null) {
                        layer.cachedFrameBitmap?.recycle()
                        layer.cachedFrameBitmap = fresh
                        layer.cachedFrameTimeUs = localTimeUs
                    }
                    layer.cachedFrameBitmap
                }
            } else {
                layer.imageBitmap
            }
            if (frameBitmap == null) continue

            val transform = getActiveTransform(layer.layerId, timeSec, layerTransforms, layerKeyframes)

            val maxW = w * 0.6f
            val maxH = h * 0.6f
            val baseScale = minOf(maxW / frameBitmap.width, maxH / frameBitmap.height)
            val drawW = frameBitmap.width * baseScale
            val drawH = frameBitmap.height * baseScale
            val centerX = w / 2f + transform.offsetX * scaleFactor
            val centerY = h / 2f + transform.offsetY * scaleFactor
            val destRect = RectF(centerX - drawW / 2f, centerY - drawH / 2f, centerX + drawW / 2f, centerY + drawH / 2f)

            canvas.save()
            canvas.rotate(transform.rotation, centerX, centerY)
            canvas.scale(transform.scaleX, transform.scaleY, centerX, centerY)
            canvas.drawBitmap(frameBitmap, null, destRect, paint)
            canvas.restore()
            // Note: video frame bitmaps are cached on the layer (see
            // cachedFrameBitmap above) and reused across nearby frames, so
            // they are NOT recycled here — only when replaced by a fresher
            // frame, or at final cleanup in renderAndSaveVideo.
        }
    }

    private fun drawTimelineFrame(
        canvas: Canvas,
        w: Int,
        h: Int,
        timeSec: Float,
        backgroundStr: String,
        vectorPoints: List<Offset>,
        pointModes: List<Boolean>,
        layerColors: Map<String, androidx.compose.ui.graphics.Color>,
        defaultLayerCount: Map<String, Int>
    ) {
        // 1. Background
        val bgColor = when (backgroundStr) {
            "Black" -> AndroidColor.BLACK
            "White" -> AndroidColor.WHITE
            "Light Grey" -> AndroidColor.rgb(226, 226, 226)
            "Green" -> AndroidColor.rgb(16, 124, 65)
            "Blue" -> AndroidColor.rgb(31, 78, 121)
            else -> AndroidColor.rgb(30, 34, 42)
        }
        canvas.drawColor(bgColor)

        val paint = Paint().apply { isAntiAlias = true }

        // Watermark
        paint.color = AndroidColor.argb(80, 0, 0, 0)
        canvas.drawRoundRect(RectF(16f, 16f, 220f, 60f), 20f, 20f, paint)
        paint.color = AndroidColor.WHITE
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Alight Motion ×", 118f, 44f, paint)

        // Animated movement based on timeSec
        val moveY = sin(timeSec * 2f) * (h * 0.05f)

        // 2. Triangle Layer
        if ((defaultLayerCount["Triangle 1"] ?: 0) > 0) {
            val tColor = layerColors["Triangle 1"]?.let { AndroidColor.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) } ?: AndroidColor.rgb(43, 193, 224)
            paint.color = tColor
            val tSize = w * 0.3f
            val tCenter = RectF(w / 2f - tSize / 2f, h / 2f - tSize / 2f, w / 2f + tSize / 2f, h / 2f + tSize / 2f)
            canvas.save()
            canvas.rotate(timeSec * 45f, w / 2f, h / 2f)
            canvas.drawRoundRect(tCenter, 30f, 30f, paint)
            
            val tPath = AndroidPath().apply {
                moveTo(w / 2f, tCenter.top + tSize * 0.15f)
                lineTo(tCenter.right - tSize * 0.15f, tCenter.bottom - tSize * 0.15f)
                lineTo(tCenter.left + tSize * 0.15f, tCenter.bottom - tSize * 0.15f)
                close()
            }
            paint.color = AndroidColor.rgb(165, 204, 43)
            canvas.drawPath(tPath, paint)
            canvas.restore()
        }

        // 3. Image Box (Red box with circle)
        if ((defaultLayerCount["20260609_092832.jpg"] ?: 0) > 0) {
            val imgColor = layerColors["20260609_092832.jpg"]?.let { AndroidColor.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) } ?: AndroidColor.rgb(255, 26, 26)
            paint.color = imgColor
            val imgSize = w * 0.35f
            val imgRect = RectF(w / 2f - imgSize / 2f, h * 0.15f + moveY, w / 2f + imgSize / 2f, h * 0.15f + imgSize + moveY)
            canvas.drawRoundRect(imgRect, 20f, 20f, paint)
            paint.color = AndroidColor.rgb(253, 224, 71)
            canvas.drawCircle(imgRect.centerX(), imgRect.centerY(), imgSize * 0.35f, paint)
        }

        // 4. Purple Circle Layer
        if ((defaultLayerCount["Circle 1"] ?: 0) > 0) {
            val cColor = layerColors["Circle 1"]?.let { AndroidColor.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) } ?: AndroidColor.rgb(192, 90, 192)
            paint.color = cColor
            val cSize = w * 0.25f
            canvas.drawCircle(w / 2f, h * 0.75f - moveY, cSize / 2f, paint)
        }

        // 5. Vector Drawing Path
        if (vectorPoints.isNotEmpty()) {
            val vPath = AndroidPath().apply {
                moveTo(vectorPoints[0].x * w, vectorPoints[0].y * h)
                for (i in 1 until vectorPoints.size) {
                    val current = vectorPoints[i]
                    if (pointModes.getOrElse(i) { false }) {
                        val prev = vectorPoints[i - 1]
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
            paint.color = AndroidColor.rgb(78, 242, 147)
            paint.style = Paint.Style.FILL
            canvas.drawPath(vPath, paint)
            paint.color = AndroidColor.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawPath(vPath, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun saveVideoToDevice(context: Context, tempFile: File, resolution: String): Uri? {
        val fileName = "AlightMotion_${resolution.replace(" ", "_").replace("(", "").replace(")", "")}_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AlightMotion")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving video to MediaStore", e)
            return null
        }
    }
}

