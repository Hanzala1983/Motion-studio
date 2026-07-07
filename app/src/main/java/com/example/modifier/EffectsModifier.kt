package com.example.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Camera
import androidx.compose.ui.unit.dp
import com.example.model.AppliedEffect

fun Modifier.alightMotionEffects(
    layerId: String,
    layerEffects: Map<String, List<AppliedEffect>>
): Modifier {
    val activeEffects = layerEffects[layerId]?.filter { it.isVisible } ?: return this
    if (activeEffects.isEmpty()) return this

    var resultModifier = this

    // Apply native radius blur if standard radius blur effects are active
    val radiusBlurEffects = activeEffects.filter {
        it.name in listOf("Box Blur", "Precise Box Blur", "Box Blur 3", "Fast Box Blur", "Gaussian Blur", "Inner Blur", "Mask Blur", "Lens Blur")
    }
    if (radiusBlurEffects.isNotEmpty()) {
        val totalStrength = radiusBlurEffects.sumOf { (it.strength * 28f).toDouble() }.toFloat()
        if (totalStrength > 0.5f) {
            resultModifier = resultModifier.blur(totalStrength.dp)
        }
    }

    // Apply dynamic sampling blurs / distortions / color transformations via drawWithContent
    val dynamicEffects = activeEffects.filter {
        it.name in listOf(
            "Directional Blur", "Motion Blur", "Zoom Blur", "Spin Blur",
            "Chromatic Zoom Blur", "Vortex Blur", "Chromatic Vortex Blur", "Warp Blur",
            "Colorize", "Invert", "Brightness / Contrast", "Exposure / Gamma",
            "Saturation / Vibrance", "Color Temperature", "Highlights and Shadows",
            "Threshold", "Hue Shift", "Edge Glow", "Glow", "Gradient Overlay",
            "Halftone Dots", "Long Shadow", "Turbulent Displace", "Vignette",
             "Flip", "Fractal Warp", "Kaleidoscope", "Outline", "Pinch/Bulge", "Pixelate", "Posterize", "Radial Wipe", "Shadow", "Swirl",
        )
    }
    dynamicEffects.forEach { dominant ->
        resultModifier = resultModifier.then(Modifier.drawWithContent {
            
            when (dominant.name) {
                "Directional Blur", "Motion Blur" -> {
                    val strength = if (dominant.name == "Motion Blur") dominant.tune * 0.4f else dominant.strength
                    val angleRad = Math.toRadians(dominant.angle.toDouble()).toFloat()
                    val maxDist = strength * size.width * 0.2f
                    val samples = 7
                    val paint = Paint().apply { alpha = 1f / samples }
                    for (i in 0 until samples) {
                        val factor = (i - samples / 2f) / (samples / 2f)
                        val dx = kotlin.math.cos(angleRad) * maxDist * factor
                        val dy = kotlin.math.sin(angleRad) * maxDist * factor
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(size.toRect(), paint)
                            canvas.translate(dx, dy)
                            drawContent()
                            canvas.restore()
                        }
                    }
                }
                "Zoom Blur" -> {
                    val samples = 7
                    val center = Offset(size.width * 0.5f + dominant.centerX, size.height * 0.5f + dominant.centerY)
                    val paint = Paint().apply { alpha = 1f / samples }
                    for (i in 0 until samples) {
                        val factor = (i / (samples - 1f)) - 0.5f
                        val s = 1f + factor * dominant.strength * 0.6f
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(size.toRect(), paint)
                            canvas.translate(center.x, center.y)
                            canvas.scale(s, s)
                            canvas.translate(-center.x, -center.y)
                            drawContent()
                            canvas.restore()
                        }
                    }
                }
                "Spin Blur" -> {
                    val samples = 7
                    val center = Offset(size.width * 0.5f + dominant.centerX, size.height * 0.5f + dominant.centerY)
                    val paint = Paint().apply { alpha = 1f / samples }
                    for (i in 0 until samples) {
                        val factor = (i / (samples - 1f)) - 0.5f
                        val rot = factor * dominant.angle * 1.5f
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(size.toRect(), paint)
                            canvas.translate(center.x, center.y)
                            canvas.rotate(rot)
                            canvas.translate(-center.x, -center.y)
                            drawContent()
                            canvas.restore()
                        }
                    }
                }
                "Chromatic Zoom Blur" -> {
                    val center = Offset(size.width * 0.5f + dominant.centerX, size.height * 0.5f + dominant.centerY)
                    val sOut = 1f + dominant.strength * 0.18f
                    val sIn = 1f - dominant.strength * 0.18f
                    drawContent()
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            alpha = 0.45f
                            blendMode = BlendMode.Screen
                        }
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(center.x, center.y)
                        canvas.scale(sOut, sOut)
                        canvas.translate(-center.x, -center.y)
                        drawContent()
                        drawRect(Color(0xFFFF1A4A).copy(alpha = 0.3f), blendMode = BlendMode.SrcAtop)
                        canvas.restore()
                    }
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            alpha = 0.45f
                            blendMode = BlendMode.Screen
                        }
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(center.x, center.y)
                        canvas.scale(sIn, sIn)
                        canvas.translate(-center.x, -center.y)
                        drawContent()
                        drawRect(Color(0xFF1A8CFF).copy(alpha = 0.3f), blendMode = BlendMode.SrcAtop)
                        canvas.restore()
                    }
                }
                "Vortex Blur", "Chromatic Vortex Blur" -> {
                    val samples = 7
                    val center = Offset(size.width * 0.5f + dominant.centerX, size.height * 0.5f + dominant.centerY)
                    val paint = Paint().apply { alpha = 1f / samples }
                    for (i in 0 until samples) {
                        val factor = (i / (samples - 1f)) - 0.5f
                        val rot = factor * dominant.strength * 90f
                        val s = 1f + factor * dominant.radius * 0.3f
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(size.toRect(), paint)
                            canvas.translate(center.x, center.y)
                            canvas.scale(s, s)
                            canvas.rotate(rot)
                            canvas.translate(-center.x, -center.y)
                            drawContent()
                            canvas.restore()
                        }
                    }
                }
                "Warp Blur" -> {
                    val samples = 5
                    val paint = Paint().apply { alpha = 1f / samples }
                    for (i in 0 until samples) {
                        val factor = (i - samples / 2f) / (samples / 2f)
                        val dx = kotlin.math.sin((dominant.evolution + i) * dominant.scale) * dominant.intensity * 25f
                        val dy = kotlin.math.cos((dominant.evolution + i) * dominant.scale) * dominant.intensity * 25f
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(size.toRect(), paint)
                            canvas.translate(dx, dy)
                            drawContent()
                            canvas.restore()
                        }
                    }
                }
                "Colorize" -> {
                    drawContent()
                    drawRect(Color(0xFFE53935).copy(alpha = dominant.strength * 0.6f), blendMode = BlendMode.Color)
                }
                "Invert" -> {
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Brightness / Contrast" -> {
                    val b = dominant.brightness
                    val c = dominant.contrast
                    val scale = if (c > 0) 1f + c * 2f else 1f + c
                    val translate = b * 255f + (1f - scale) * 128f
                    val colorMatrix = ColorMatrix(
                        floatArrayOf(
                            scale, 0f, 0f, 0f, translate,
                            0f, scale, 0f, 0f, translate,
                            0f, 0f, scale, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            colorFilter = ColorFilter.colorMatrix(colorMatrix)
                        }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Exposure / Gamma" -> {
                    val exposureValue = Math.pow(2.0, dominant.exposure.toDouble()).toFloat()
                    val translate = dominant.offset * 255f
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        exposureValue, 0f, 0f, 0f, translate,
                        0f, exposureValue, 0f, 0f, translate,
                        0f, 0f, exposureValue, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Saturation / Vibrance" -> {
                    val colorMatrix = ColorMatrix().apply {
                        setToSaturation(1f + dominant.saturation)
                    }
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Color Temperature" -> {
                    val temp = dominant.temperature
                    val diff = temp - 6.5f
                    val strength = dominant.strength
                    val rShift = if (diff < 0) -diff * 10f * strength else diff * 2f * strength
                    val bShift = if (diff > 0) diff * 10f * strength else -diff * 2f * strength
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        1f, 0f, 0f, 0f, rShift,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, bShift,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Highlights and Shadows" -> {
                    val highlights = dominant.highlights
                    val shadows = dominant.shadows
                    val contrast = (highlights + shadows) / 2f
                    val brightness = (shadows - highlights) * 128f
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Threshold" -> {
                    val c = 255f
                    val t = -255f * 255f * dominant.threshold
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Hue Shift" -> {
                    val angleRad = Math.toRadians(dominant.angle.toDouble())
                    val cosA = kotlin.math.cos(angleRad).toFloat()
                    val sinA = kotlin.math.sin(angleRad).toFloat()
                    val lumR = 0.213f
                    val lumG = 0.715f
                    val lumB = 0.072f
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        lumR + cosA * (1f - lumR) + sinA * (-lumR),
                        lumG + cosA * (-lumG) + sinA * (-lumG),
                        lumB + cosA * (-lumB) + sinA * (1f - lumB),
                        0f, 0f,
                        lumR + cosA * (-lumR) + sinA * (0.143f),
                        lumG + cosA * (1f - lumG) + sinA * (0.140f),
                        lumB + cosA * (-lumB) + sinA * (-0.283f),
                        0f, 0f,
                        lumR + cosA * (-lumR) + sinA * (-(1f - lumR)),
                        lumG + cosA * (-lumG) + sinA * (lumG),
                        lumB + cosA * (1f - lumB) + sinA * (lumB),
                        0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Edge Glow", "Glow" -> {
                    val glowRadius = dominant.radius * 20f
                    val glowStrength = dominant.strength
                    val colorFilter = ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 255f, // tint with blue/cyan for glow
                        0f, 0f, 0f, 0f, 255f,
                        0.2126f, 0.7152f, 0.0722f, 0f, 0f
                    ))
                    drawIntoCanvas { canvas ->
                        // Draw glow
                        val paint = Paint().apply { 
                            this.colorFilter = ColorFilter.colorMatrix(colorFilter)
                            this.alpha = glowStrength
                            this.blendMode = BlendMode.Screen
                        }
                        
                        // Fake blur for glow
                        for (i in 1..3) {
                            canvas.saveLayer(size.toRect(), paint)
                            canvas.scale(1f + i * glowRadius * 0.02f, 1f + i * glowRadius * 0.02f)
                            canvas.translate(-size.width * i * glowRadius * 0.01f, -size.height * i * glowRadius * 0.01f)
                            drawContent()
                            canvas.restore()
                        }
                        // Draw original over it
                        drawContent()
                    }
                }
                "Gradient Overlay" -> {
                    val angleRad = Math.toRadians(dominant.angle.toDouble()).toFloat()
                    val dx = kotlin.math.cos(angleRad) * size.width
                    val dy = kotlin.math.sin(angleRad) * size.height
                    
                    drawContent()
                    
                    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(Color(0x88EE5533), Color(0x88FFEE88)),
                        start = Offset(0f, 0f),
                        end = Offset(dx, dy)
                    )
                    drawRect(brush = brush, blendMode = BlendMode.Overlay)
                }
                "Halftone Dots" -> {
                    drawContent()
                    
                    val dotSize = dominant.radius * 10f + 2f
                    val angleRad = Math.toRadians(dominant.angle.toDouble()).toFloat()
                    drawIntoCanvas { canvas ->
                        canvas.save()
                        canvas.rotate(Math.toDegrees(angleRad.toDouble()).toFloat())
                        
                        val paint = Paint().apply { 
                            color = Color(0x33000000)
                            blendMode = BlendMode.Multiply 
                        }
                        
                        var x = -size.width
                        while (x < size.width * 2) {
                            var y = -size.height
                            while (y < size.height * 2) {
                                canvas.drawCircle(Offset(x, y), dotSize * 0.5f, paint)
                                y += dotSize * 2
                            }
                            x += dotSize * 2
                        }
                        canvas.restore()
                    }
                }
                "Long Shadow" -> {
                    val angleRad = Math.toRadians(dominant.angle.toDouble()).toFloat()
                    val length = dominant.radius * size.width * 0.5f
                    val steps = 20
                    
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                                0f, 0f, 0f, 0f, 0f,
                                0f, 0f, 0f, 0f, 0f,
                                0f, 0f, 0f, 0f, 0f,
                                0f, 0f, 0f, dominant.feather, 0f
                            )))
                        }
                        
                        for (i in steps downTo 1) {
                            val factor = i / steps.toFloat()
                            val dx = kotlin.math.cos(angleRad) * length * factor
                            val dy = kotlin.math.sin(angleRad) * length * factor
                            
                            canvas.saveLayer(size.toRect(), paint)
                            canvas.translate(dx, dy)
                            drawContent()
                            canvas.restore()
                        }
                    }
                    drawContent()
                }
                "Turbulent Displace" -> {
                    val intensity = dominant.intensity * 20f
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { alpha = 0.5f }
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(intensity, intensity)
                        drawContent()
                        canvas.restore()
                        
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(-intensity, -intensity)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Vignette" -> {
                    drawContent()
                    val scale = dominant.scale
                    val strength = dominant.strength
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = (kotlin.math.min(size.width, size.height) / 2) * scale
                    val brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0f, 0f, 0f, strength)),
                        center = center,
                        radius = radius
                    )
                    drawRect(brush = brush)
                }
                
                "Flip" -> {
                    drawIntoCanvas { canvas ->
                        canvas.save()
                        val cam = Camera()
                        val matrix = android.graphics.Matrix()
                        
                        cam.save()
                        // Move camera in Z based on zDist (zDist is usually 0 to 10 or similar)
                        // negative translation moves it away
                        cam.translate(0f, 0f, dominant.zDist * 100f)
                        cam.rotateX(dominant.angle)
                        cam.getMatrix(matrix)
                        cam.restore()
                        
                        val finalMatrix = android.graphics.Matrix()
                        finalMatrix.preTranslate(-size.width / 2, -size.height / 2)
                        finalMatrix.postRotate(-dominant.axis)
                        finalMatrix.postConcat(matrix)
                        finalMatrix.postRotate(dominant.axis)
                        finalMatrix.postTranslate(size.width / 2, size.height / 2)
                        
                        canvas.nativeCanvas.concat(finalMatrix)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Fractal Warp" -> {
                    // Mock fractal warp with shifted low-opacity copies
                    val steps = 3
                    val paint = Paint().apply { alpha = 1f / (steps + 1) }
                    drawIntoCanvas { canvas ->
                        for (i in 0..steps) {
                            canvas.saveLayer(size.toRect(), paint)
                            val shift = (i * dominant.amplitude * 10f)
                            canvas.translate(shift, shift * 1.5f)
                            drawContent()
                            canvas.restore()
                        }
                    }
                }
                "Kaleidoscope" -> {
                    val count = dominant.reflections.coerceAtLeast(1)
                    val angleStep = 360f / count
                    val center = Offset(size.width / 2, size.height / 2)
                    for (i in 0 until count) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y)
                            val r = kotlin.math.max(size.width, size.height) * 1.5f
                            val startRad = 0f
                            val endRad = Math.toRadians(angleStep.toDouble()).toFloat()
                            lineTo(center.x + r * kotlin.math.cos(startRad), center.y + r * kotlin.math.sin(startRad))
                            lineTo(center.x + r * kotlin.math.cos(endRad), center.y + r * kotlin.math.sin(endRad))
                            close()
                        }
                        withTransform({
                            translate(center.x, center.y)
                            rotate(i * angleStep + dominant.angle)
                            translate(-center.x, -center.y)
                            clipPath(path)
                        }) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
                "Outline" -> {
                    val r = dominant.radius
                    val colorFilter = ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    val paint = Paint().apply { this.colorFilter = ColorFilter.colorMatrix(colorFilter) }
                    
                    drawIntoCanvas { canvas ->
                        // Draw outline
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(-r, 0f); drawContent(); canvas.restore()
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(r, 0f); drawContent(); canvas.restore()
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(0f, -r); drawContent(); canvas.restore()
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(0f, r); drawContent(); canvas.restore()
                    }
                    drawContent()
                }
                "Pinch/Bulge" -> {
                    val s = if (dominant.strength > 0) 1f + dominant.strength * 0.5f else 1f + dominant.strength * 0.5f
                    withTransform({
                        translate(size.width / 2, size.height / 2)
                        scale(s, s)
                        translate(-size.width / 2, -size.height / 2)
                    }) {
                        this@drawWithContent.drawContent()
                    }
                }
                "Pixelate" -> {
                    drawContent()
                    val s = dominant.size.coerceAtLeast(1f)
                    val paint = Paint().apply { color = Color(0x22000000) }
                    drawIntoCanvas { canvas ->
                        var x = 0f
                        while (x < size.width) {
                            canvas.drawLine(Offset(x, 0f), Offset(x, size.height), paint)
                            x += s
                        }
                        var y = 0f
                        while (y < size.height) {
                            canvas.drawLine(Offset(0f, y), Offset(size.width, y), paint)
                            y += s
                        }
                    }
                }
                "Posterize" -> {
                    // Mock posterize with high contrast color matrix
                    val scale = dominant.steps.toFloat().coerceAtLeast(2f) / 2f
                    val translate = -128f * (scale - 1f)
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    val paint = Paint().apply { this.colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(size.toRect(), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
                "Radial Wipe" -> {
                    val progress = dominant.progress.coerceIn(0f, 360f)
                    val startAngle = dominant.angle
                    val center = Offset(size.width / 2, size.height / 2)
                    if (progress > 0) {
                        val r = kotlin.math.max(size.width, size.height) * 1.5f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y)
                            arcTo(
                                rect = androidx.compose.ui.geometry.Rect(center.x - r, center.y - r, center.x + r, center.y + r),
                                startAngleDegrees = startAngle,
                                sweepAngleDegrees = progress,
                                forceMoveTo = false
                            )
                            close()
                        }
                        withTransform({
                            clipPath(path)
                        }) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
                "Shadow" -> {
                    val colorMatrix = ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0.5f, 0f
                    ))
                    val paint = Paint().apply { this.colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(size.toRect(), paint)
                        canvas.translate(dominant.offsetX, dominant.offsetY)
                        drawContent()
                        canvas.restore()
                    }
                    drawContent()
                }
                "Swirl" -> {
                    val angle = dominant.strength * 180f
                    withTransform({
                        translate(size.width / 2, size.height / 2)
                        rotate(angle)
                        translate(-size.width / 2, -size.height / 2)
                    }) {
                        this@drawWithContent.drawContent()
                    }
                }
                else -> drawContent()

            }
        })
    }

    return resultModifier
}
