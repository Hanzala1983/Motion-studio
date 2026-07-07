package com.example.ui.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppliedEffect

@Composable
fun AppliedEffectCard(
    effect: AppliedEffect,
    onUpdate: (AppliedEffect) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF262C3A))
    ) {
        // Card Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(effect.copy(isExpanded = !effect.isExpanded)) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (effect.isExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
                contentDescription = "Expand",
                tint = Color(0xFF90A4AE),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = effect.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            if (effect.isExpanded) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable { onRemove() }
                )
            } else {
                Icon(
                    imageVector = if (effect.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle visibility",
                    tint = if (effect.isVisible) Color.White else Color(0xFF606D7B),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onUpdate(effect.copy(isVisible = !effect.isVisible)) }
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Reorder",
                    tint = Color(0xFF606D7B),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Expanded Parameter Sliders
        if (effect.isExpanded && effect.isVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1E28))
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (effect.name) {
                    "Directional Blur" -> {
                        EffectParameterSlider("Strength", effect.strength, 0f..1f, "%.2f") {
                            onUpdate(effect.copy(strength = it))
                        }
                        EffectParameterSlider("Angle", effect.angle, 0f..360f, "%.1f°") {
                            onUpdate(effect.copy(angle = it))
                        }
                    }
                    "Box Blur", "Precise Box Blur", "Fast Box Blur", "Inner Blur" -> {
                        EffectParameterSlider("Strength", effect.strength, 0f..1f, "%.2f") {
                            onUpdate(effect.copy(strength = it))
                        }
                        EffectParameterSlider("Iterations", effect.iterations.toFloat(), 1f..6f, "%.0f") {
                            onUpdate(effect.copy(iterations = it.toInt()))
                        }
                    }
                    "Gaussian Blur", "Box Blur 3", "Mask Blur" -> {
                        EffectParameterSlider("Strength", effect.strength, 0f..2f, "%.2f") {
                            onUpdate(effect.copy(strength = it))
                        }
                    }
                    "Zoom Blur", "Chromatic Zoom Blur" -> {
                        EffectParameterSlider("Strength", effect.strength, 0f..2f, "%.2f") {
                            onUpdate(effect.copy(strength = it))
                        }
                        EffectParameterSlider("Center X", effect.centerX, -500f..500f, "%.0f px") {
                            onUpdate(effect.copy(centerX = it))
                        }
                        EffectParameterSlider("Center Y", effect.centerY, -500f..500f, "%.0f px") {
                            onUpdate(effect.copy(centerY = it))
                        }
                    }
                    "Spin Blur" -> {
                        EffectParameterSlider("Angle", effect.angle, 0f..60f, "%.1f°") {
                            onUpdate(effect.copy(angle = it))
                        }
                        EffectParameterSlider("Center X", effect.centerX, -500f..500f, "%.0f px") {
                            onUpdate(effect.copy(centerX = it))
                        }
                        EffectParameterSlider("Center Y", effect.centerY, -500f..500f, "%.0f px") {
                            onUpdate(effect.copy(centerY = it))
                        }
                    }
                    "Lens Blur" -> {
                        EffectParameterSlider("Strength", effect.strength, 0f..1f, "%.2f") {
                            onUpdate(effect.copy(strength = it))
                        }
                        EffectParameterSlider("Radius", effect.radius, 0f..1f, "%.2f") {
                            onUpdate(effect.copy(radius = it))
                        }
                    }
                    "Vortex Blur", "Chromatic Vortex Blur" -> {
                        EffectParameterSlider("Strength", effect.strength, -0.5f..0.5f, "%.3f") {
                            onUpdate(effect.copy(strength = it))
                        }
                        EffectParameterSlider("Radius", effect.radius, 0f..0.8f, "%.2f") {
                            onUpdate(effect.copy(radius = it))
                        }
                        if (effect.name.contains("Chromatic")) {
                            EffectParameterSlider("Aberration", effect.aberration, -1f..1f, "%.2f") {
                                onUpdate(effect.copy(aberration = it))
                            }
                        }
                    }
                    "Warp Blur" -> {
                        EffectParameterSlider("Intensity", effect.intensity, 0f..2.5f, "%.2f") {
                            onUpdate(effect.copy(intensity = it))
                        }
                        EffectParameterSlider("Scale", effect.scale, 0.01f..50f, "%.1f") {
                            onUpdate(effect.copy(scale = it))
                        }
                        EffectParameterSlider("Evolution", effect.evolution, -50f..50f, "%.1f") {
                            onUpdate(effect.copy(evolution = it))
                        }
                    }
                    "Motion Blur" -> {
                        EffectParameterSlider("Tune", effect.tune, 0f..4f, "%.1f") {
                            onUpdate(effect.copy(tune = it))
                        }
                    }
                    "Brightness / Contrast" -> {
                        EffectParameterSlider("Brightness", effect.brightness, -1f..1f, formatAsPercent = true) {
                            onUpdate(effect.copy(brightness = it))
                        }
                        EffectParameterSlider("Contrast", effect.contrast, -1f..1f, formatAsPercent = true) {
                            onUpdate(effect.copy(contrast = it))
                        }
                    }
                    "Exposure / Gamma" -> {
                        EffectParameterSlider("Exposure", effect.exposure, -2f..2f, "%.2f") {
                            onUpdate(effect.copy(exposure = it))
                        }
                        EffectParameterSlider("Gamma", effect.gamma, 0.01f..9.99f, "%.2f") {
                            onUpdate(effect.copy(gamma = it))
                        }
                        EffectParameterSlider("Offset", effect.offset, -0.9f..0.9f, "%.2f") {
                            onUpdate(effect.copy(offset = it))
                        }
                    }
                    "Saturation / Vibrance" -> {
                        EffectParameterSlider("Saturation", effect.saturation, -1f..1f, formatAsPercent = true) {
                            onUpdate(effect.copy(saturation = it))
                        }
                        EffectParameterSlider("Vibrance", effect.vibrance, 1f..2f, "%.2f") {
                            onUpdate(effect.copy(vibrance = it))
                        }
                    }
                    "Color Temperature" -> {
                        EffectParameterSlider("Temperature", effect.temperature, 1f..40f, "%.2f") {
                            onUpdate(effect.copy(temperature = it))
                        }
                        EffectParameterSlider("Strength", effect.strength, 0f..1f, formatAsPercent = true) {
                            onUpdate(effect.copy(strength = it))
                        }
                    }
                    "Highlights and Shadows" -> {
                        EffectParameterSlider("Highlights", effect.highlights, 0f..2f, formatAsPercent = true) {
                            onUpdate(effect.copy(highlights = it))
                        }
                        EffectParameterSlider("Shadows", effect.shadows, 0f..2f, formatAsPercent = true) {
                            onUpdate(effect.copy(shadows = it))
                        }
                    }
                    "Threshold" -> {
                        EffectParameterSlider("Threshold", effect.threshold, 0f..1f, "%.2f") {
                            onUpdate(effect.copy(threshold = it))
                        }
                        EffectParameterSlider("Feather", effect.feather, 0f..1f, "%.2f") {
                            onUpdate(effect.copy(feather = it))
                        }
                    }
                    "Hue Shift" -> {
                        EffectParameterSlider("Hue", effect.angle, 0f..360f, "%.1f°") {
                            onUpdate(effect.copy(angle = it))
                        }
                    }
                    
                    "Flip" -> {
                        EffectParameterSlider("Angle", effect.angle, -360f..360f, "%.1f°") { onUpdate(effect.copy(angle = it)) }
                        EffectParameterSlider("Axis", effect.axis, 0f..360f, "%.1f°") { onUpdate(effect.copy(axis = it)) }
                        EffectParameterSlider("Crop", effect.crop, 0.01f..2f, "%.3f") { onUpdate(effect.copy(crop = it)) }
                        EffectParameterSlider("Distance", effect.zDist, 0f..10f, "%.3f") { onUpdate(effect.copy(zDist = it)) }
                    }
                    "Fractal Warp" -> {
                        EffectParameterSlider("Gain", effect.gain, 0.01f..0.99f, "%.3f") { onUpdate(effect.copy(gain = it)) }
                        EffectParameterSlider("Lacunarity", effect.lacunarity, 0.1f..10f, "%.3f") { onUpdate(effect.copy(lacunarity = it)) }
                        EffectParameterSlider("Amplitude", effect.amplitude, -2f..2f, "%.1f") { onUpdate(effect.copy(amplitude = it)) }
                        EffectParameterSlider("Speed", effect.speed, 0.25f..10f, "%.2f") { onUpdate(effect.copy(speed = it)) }
                        EffectParameterSlider("Zoom", effect.frequency, 0.5f..2f, "%.3f") { onUpdate(effect.copy(frequency = it)) }
                        EffectParameterSlider("Luma", effect.luma, 0.5f..2f, "%.3f") { onUpdate(effect.copy(luma = it)) }
                    }
                    "Kaleidoscope" -> {
                        EffectParameterSlider("Reflections", effect.reflections.toFloat(), 1f..24f, "%.0f") { onUpdate(effect.copy(reflections = it.toInt())) }
                        EffectParameterSlider("Angle", effect.angle, 0f..360f, "%.1f°") { onUpdate(effect.copy(angle = it)) }
                    }
                    "Outline" -> {
                        EffectParameterSlider("Radius", effect.radius, 0.5f..200f, "%.1f") { onUpdate(effect.copy(radius = it)) }
                    }
                    "Pinch/Bulge" -> {
                        EffectParameterSlider("Strength", effect.strength, -1f..1f, "%.2f") { onUpdate(effect.copy(strength = it)) }
                        EffectParameterSlider("Radius", effect.radius, 0f..2.5f, "%.3f") { onUpdate(effect.copy(radius = it)) }
                    }
                    "Pixelate" -> {
                        EffectParameterSlider("Size", effect.size, 1f..500f, "%.0f") { onUpdate(effect.copy(size = it)) }
                        EffectParameterSlider("Stretch", effect.stretch, -2000f..2000f, "%.1f") { onUpdate(effect.copy(stretch = it)) }
                        EffectParameterSlider("Angle", effect.angle, 0f..360f, "%.1f°") { onUpdate(effect.copy(angle = it)) }
                    }
                    "Posterize" -> {
                        EffectParameterSlider("Steps", effect.steps.toFloat(), 2f..255f, "%.0f") { onUpdate(effect.copy(steps = it.toInt())) }
                        EffectParameterSlider("Offset", effect.offset, -1f..1f, "%.3f") { onUpdate(effect.copy(offset = it)) }
                    }
                    "Radial Wipe" -> {
                        EffectParameterSlider("Progress", effect.progress, 0f..360f, "%.1f°") { onUpdate(effect.copy(progress = it)) }
                        EffectParameterSlider("Angle", effect.angle, -3600f..3600f, "%.1f°") { onUpdate(effect.copy(angle = it)) }
                        EffectParameterSlider("Feather", effect.feather, 0f..100f, "%.1f") { onUpdate(effect.copy(feather = it)) }
                    }
                    "Shadow" -> {
                        EffectParameterSlider("Radius", effect.radius, 0.5f..200f, "%.1f") { onUpdate(effect.copy(radius = it)) }
                        EffectParameterSlider("X Offset", effect.offsetX, -50f..50f, "%.1f") { onUpdate(effect.copy(offsetX = it)) }
                        EffectParameterSlider("Y Offset", effect.offsetY, -50f..50f, "%.1f") { onUpdate(effect.copy(offsetY = it)) }
                    }
                    "Swirl" -> {
                        EffectParameterSlider("Strength", effect.strength, -0.5f..0.5f, "%.2f") { onUpdate(effect.copy(strength = it)) }
                        EffectParameterSlider("Radius", effect.radius, 0f..0.8f, "%.2f") { onUpdate(effect.copy(radius = it)) }
                    }
                    else -> {
                        EffectParameterSlider("Strength", effect.strength, 0f..2f, "%.2f") {
                            onUpdate(effect.copy(strength = it))
                        }
                    }
                }
            }
        }
    }
}
