package com.example.model

import java.util.UUID

data class AppliedEffect(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "Blur",
    val isVisible: Boolean = true,
    val isExpanded: Boolean = true,
    val strength: Float = 0.15f,
    val angle: Float = 45f,
    val iterations: Int = 2,
    val radius: Float = 0.3f,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val aberration: Float = 0.15f,
    val tune: Float = 1.0f,
    val intensity: Float = 0.25f,
    val scale: Float = 1.0f,
    val evolution: Float = 0.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 0.0f,
    val exposure: Float = 0.0f,
    val gamma: Float = 1.0f,
    val offset: Float = 0.0f,
    val saturation: Float = 0.0f,
    val vibrance: Float = 1.0f,
    val temperature: Float = 6.5f,
    val highlights: Float = 1.0f,
    val shadows: Float = 1.0f,
    val threshold: Float = 0.4f,
    val feather: Float = 0.0f,
    val axis: Float = 0f,
    val crop: Float = 1.0f,
    val zDist: Float = 0f,
    val gain: Float = 0.452f,
    val lacunarity: Float = 4.012f,
    val amplitude: Float = -0.5f,
    val speed: Float = 1.0f,
    val frequency: Float = 1.352f,
    val luma: Float = 1.172f,
    val reflections: Int = 6,
    val size: Float = 20f,
    val stretch: Float = 0f,
    val steps: Int = 10,
    val progress: Float = 45f,
    val offsetX: Float = 5f,
    val offsetY: Float = 5f
)

fun createDefaultEffect(name: String, category: String): AppliedEffect {
    return when (name) {
        "Directional Blur" -> AppliedEffect(name = name, category = category, strength = 0.25f, angle = 45f)
        "Box Blur", "Precise Box Blur", "Fast Box Blur", "Inner Blur" -> AppliedEffect(name = name, category = category, strength = 0.20f, iterations = 3)
        "Gaussian Blur", "Box Blur 3", "Mask Blur" -> AppliedEffect(name = name, category = category, strength = 0.25f)
        "Zoom Blur", "Chromatic Zoom Blur" -> AppliedEffect(name = name, category = category, strength = 0.30f, centerX = 0f, centerY = 0f)
        "Spin Blur" -> AppliedEffect(name = name, category = category, angle = 15f, centerX = 0f, centerY = 0f)
        "Lens Blur" -> AppliedEffect(name = name, category = category, strength = 0.25f, radius = 0.20f)
        "Vortex Blur", "Chromatic Vortex Blur" -> AppliedEffect(name = name, category = category, strength = 0.20f, radius = 0.40f, aberration = 0.25f)
        "Warp Blur" -> AppliedEffect(name = name, category = category, intensity = 0.50f, scale = 2.0f, evolution = 1.0f)
        "Motion Blur" -> AppliedEffect(name = name, category = category, tune = 1.5f)
        "Brightness / Contrast", "Brightness /\nContrast" -> AppliedEffect(name = "Brightness / Contrast", category = category, brightness = 0.0f, contrast = 0.0f)
        "Exposure / Gamma" -> AppliedEffect(name = name, category = category, exposure = 0.0f, gamma = 1.0f, offset = 0.0f)
        "Saturation / Vibrance" -> AppliedEffect(name = name, category = category, saturation = 0.0f, vibrance = 1.0f)
        "Color Temperature" -> AppliedEffect(name = name, category = category, temperature = 6.5f, strength = 1.0f)
        "Highlights and Shadows" -> AppliedEffect(name = name, category = category, highlights = 1.0f, shadows = 1.0f)
        "Threshold" -> AppliedEffect(name = name, category = category, threshold = 0.4f, feather = 0.0f)
        "Hue Shift" -> AppliedEffect(name = name, category = category, angle = 90f)
        "Colorize" -> AppliedEffect(name = name, category = category, strength = 0.8f)
        "Invert" -> AppliedEffect(name = name, category = category, strength = 1.0f)
        "Edge Glow", "Glow" -> AppliedEffect(name = name, category = category, radius = 0.5f, strength = 0.8f)
        "Gradient Overlay" -> AppliedEffect(name = name, category = category, angle = 45f, scale = 1.0f)
        "Halftone Dots" -> AppliedEffect(name = name, category = category, radius = 0.2f, angle = 45f)
        "Long Shadow" -> AppliedEffect(name = name, category = category, radius = 0.3f, angle = 45f, feather = 0.1f)
        "Turbulent Displace" -> AppliedEffect(name = name, category = category, intensity = 0.25f, scale = 1.0f, evolution = 0.0f)
        "Vignette" -> AppliedEffect(name = name, category = category, scale = 0.95f, feather = 0.5f, strength = 0.8f)
        "Flip" -> AppliedEffect(name = name, category = category, angle = 0f, axis = 0f, crop = 1.0f, zDist = 0f)
        "Fractal Warp" -> AppliedEffect(name = name, category = category, gain = 0.452f, lacunarity = 4.012f, amplitude = -0.5f, speed = 1.0f, frequency = 1.352f, luma = 1.172f)
        "Kaleidoscope" -> AppliedEffect(name = name, category = category, reflections = 6, angle = 0f)
        "Outline" -> AppliedEffect(name = name, category = category, radius = 5.0f)
        "Pinch/Bulge" -> AppliedEffect(name = name, category = category, strength = -0.9f, radius = 0.3f)
        "Pixelate" -> AppliedEffect(name = name, category = category, size = 20f, stretch = 0f, angle = 0f, feather = 0f, threshold = 0f, saturation = 0f)
        "Posterize" -> AppliedEffect(name = name, category = category, steps = 10, offset = 0f)
        "Radial Wipe" -> AppliedEffect(name = name, category = category, progress = 45f, angle = 0f, feather = 0f)
        "Shadow" -> AppliedEffect(name = name, category = category, radius = 5.0f, offsetX = 5.0f, offsetY = 5.0f)
        "Swirl" -> AppliedEffect(name = name, category = category, strength = 0.1f, radius = 0.3f)
        else -> AppliedEffect(name = name, category = category, strength = 0.2f)
    }
}
