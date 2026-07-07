package com.example.ui.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppliedEffect

@Composable
fun EffectBrowserScreen(
    onClose: () -> Unit,
    onSelectEffect: (String, String) -> Unit = { _, _ -> }
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    val allEffects = listOf(
        "Box Blur" to "Blur",
        "Precise Box Blur" to "Blur",
        "Box Blur 3" to "Blur",
        "Fast Box Blur" to "Blur",
        "Gaussian Blur" to "Blur",
        "Directional Blur" to "Blur",
        "Motion Blur" to "Blur",
        "Zoom Blur" to "Blur",
        "Spin Blur" to "Blur",
        "Lens Blur" to "Blur",
        "Chromatic Zoom Blur" to "Blur",
        "Chromatic Vortex Blur" to "Blur",
        "Vortex Blur" to "Distortion/Warp",
        "Warp Blur" to "Distortion/Warp",
        "Inner Blur" to "Blur",
        "Mask Blur" to "Blur",
        "Tiles" to "Distortion/Warp",
        "Wave Warp" to "Distortion/Warp",
        "Spherize" to "Distortion/Warp",
        "Brightness / Contrast" to "Color & Light",
        "Color Balance" to "Color & Light",
        "Color Temperature" to "Color & Light",
        "Colorize" to "Color & Light",
        "Exposure / Gamma" to "Color & Light",
        "Highlights and Shadows" to "Color & Light",
        "Hue Shift" to "Color & Light",
        "Invert" to "Color & Light",
        "Saturation / Vibrance" to "Color & Light",
        "Threshold" to "Color & Light",
        "Edge Glow" to "Drawing & Edge",
        "Glow" to "Drawing & Edge",
        "Gradient Overlay" to "Color & Light",
        "Halftone Dots" to "Drawing & Edge",
        "Long Shadow" to "Color & Light",
        "Turbulent Displace" to "Distortion/Warp",
        "Vignette" to "Matte/Mask/Key",

        "Flip" to "3D",
        "Fractal Warp" to "Distortion/Warp",
        "Kaleidoscope" to "Distortion/Warp",
        "Outline" to "Drawing & Edge",
        "Pinch/Bulge" to "Distortion/Warp",
        "Pixelate" to "Distortion/Warp",
        "Posterize" to "Color & Light",
        "Radial Wipe" to "Matte/Mask/Key",
        "Shadow" to "Color & Light",
        "Swirl" to "Distortion/Warp",

    )

    if (selectedCategory != null) {
        EffectCategoryScreen(
            title = selectedCategory!!,
            onBack = { selectedCategory = null },
            onSelectEffect = { effectName -> onSelectEffect(effectName, selectedCategory!!) }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF181A24))) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF262934))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSearching) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Search effects...", color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    )
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Cancel Search",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                isSearching = false
                                searchQuery = ""
                            }
                    )
                } else {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onClose)
                    )
                    Text(
                        text = "Effect Browser",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { isSearching = true }
                    )
                }
            }

            if (isSearching && searchQuery.isNotEmpty()) {
                val filtered = allEffects.filter { it.first.contains(searchQuery, ignoreCase = true) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered) { (name, category) ->
                        val color = when(category) {
                            "Blur" -> Color(0xFF364852)
                            "Distortion/Warp" -> Color(0xFF263C4E)
                            "Color & Light" -> Color(0xFF1E88E5)
                            else -> Color(0xFF3E364A)
                        }
                        EffectItemBox(name, color, onClick = { onSelectEffect(name, category) })
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    // Featured / Recent Effects Area
                    Column(modifier = Modifier.weight(1.5f).fillMaxWidth()) {
                        Text(
                            "Featured / Recent",
                            color = Color(0xFF90A4AE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                        )
                        val featuredEffects = listOf(
                            "Directional Blur" to Color(0xFF364852),
                            "Gaussian Blur" to Color(0xFF364852),
                            "Motion Blur" to Color(0xFF364852),
                            "Chromatic Zoom Blur" to Color(0xFF364852),
                            "Edge Glow" to Color(0xFF5A442A),
                            "Glow" to Color(0xFF5A442A),
                            "Gradient Overlay" to Color(0xFF3E364A),
                            "Halftone Dots" to Color(0xFF5A442A),
                            "Long Shadow" to Color(0xFF3E364A),
                            "Turbulent Displace" to Color(0xFF263C4E),
                            "Vignette" to Color(0xFF3A2E35),
                "Radial Wipe" to Color(0xFF3A2E35)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(featuredEffects) { (name, color) ->
                                EffectItemBox(name, color, onClick = { onSelectEffect(name, "Blur") })
                            }
                        }
                    }
    
                    // Categories Area
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            CategoryItem("Color & Light", Color(0xFF3E364A), Modifier.weight(1f), onClick = { selectedCategory = "Color & Light" })
                            CategoryItem("Drawing & Edge", Color(0xFF5A442A), Modifier.weight(1f), onClick = { selectedCategory = "Drawing & Edge" })
                        }
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            CategoryItem("Blur", Color(0xFF364852), Modifier.weight(1f), onClick = { selectedCategory = "Blur" })
                            CategoryItem("Distortion/Warp", Color(0xFF263C4E), Modifier.weight(1f), onClick = { selectedCategory = "Distortion/Warp" })
                        }
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            CategoryItem("Procedural", Color(0xFF4F2A4A), Modifier.weight(1f), onClick = { selectedCategory = "Procedural" })
                            CategoryItem("3D", Color(0xFF2A2A35), Modifier.weight(1f), onClick = { selectedCategory = "3D" })
                        }
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            CategoryItem("Move/Transform", Color(0xFF262C3A), Modifier.weight(1f), onClick = { selectedCategory = "Move/Transform" })
                            CategoryItem("Repeat", Color(0xFF3A1A4A), Modifier.weight(1f), onClick = { selectedCategory = "Repeat" })
                        }
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            CategoryItem("Matte/Mask/Key", Color(0xFF3A2E35), Modifier.weight(1f), onClick = { selectedCategory = "Matte/Mask/Key" })
                            CategoryItem("Opacity/Visibility", Color(0xFF2A3A45), Modifier.weight(1f), onClick = { selectedCategory = "Opacity/Visibility" })
                        }
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            CategoryItem("Text", Color(0xFF20202A), Modifier.weight(1f), onClick = { selectedCategory = "Text" })
                            CategoryItem("Other", Color(0xFF1B1D25), Modifier.weight(1f), onClick = { selectedCategory = "Other" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EffectCategoryScreen(
    title: String,
    onBack: () -> Unit,
    onSelectEffect: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2D3C))) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF262934))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack)
            )
            Spacer(modifier = Modifier.width(32.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).offset(x = (-28).dp)
            )
        }
        
        val effectsList = when (title) {
            "Blur" -> listOf(
                "Box Blur" to Color(0xFF364852),
                "Precise Box Blur" to Color(0xFF364852),
                "Box Blur 3" to Color(0xFF364852),
                "Fast Box Blur" to Color(0xFF364852),
                "Gaussian Blur" to Color(0xFF364852),
                "Directional Blur" to Color(0xFF364852),
                "Motion Blur" to Color(0xFF364852),
                "Zoom Blur" to Color(0xFF364852),
                "Spin Blur" to Color(0xFF364852),
                "Lens Blur" to Color(0xFF364852),
                "Chromatic Zoom Blur" to Color(0xFF364852),
                "Chromatic Vortex Blur" to Color(0xFF364852),
                "Vortex Blur" to Color(0xFF364852),
                "Warp Blur" to Color(0xFF364852),
                "Inner Blur" to Color(0xFF364852),
                "Mask Blur" to Color(0xFF364852)
            )
            "Distortion/Warp" -> listOf(
                "Vortex Blur" to Color(0xFF263C4E),
                "Warp Blur" to Color(0xFF263C4E),
                "Chromatic Vortex Blur" to Color(0xFF263C4E),
                "Tiles" to Color(0xFF263C4E),
                "Wave Warp" to Color(0xFF263C4E),
                "Spherize" to Color(0xFF263C4E),
                "Turbulent Displace" to Color(0xFF263C4E),
                "Fractal Warp" to Color(0xFF263C4E),
                "Kaleidoscope" to Color(0xFF263C4E),
                "Pinch/Bulge" to Color(0xFF263C4E),
                "Pixelate" to Color(0xFF263C4E),
                "Swirl" to Color(0xFF263C4E)
            )
            "Color & Light" -> listOf(
                "Brightness / Contrast" to Color(0xFF1E88E5),
                "Color Balance" to Color(0xFFE53935),
                "Color Temperature" to Color(0xFF43A047),
                "Colorize" to Color(0xFFD81B60),
                "Exposure / Gamma" to Color(0xFF263C4E),
                "Highlights and Shadows" to Color(0xFF1E88E5),
                "Hue Shift" to Color(0xFFAED581),
                "Invert" to Color(0xFF1E88E5),
                "Saturation / Vibrance" to Color(0xFF263C4E),
                "Threshold" to Color(0xFF1E88E5),
                "Gradient Overlay" to Color(0xFF1E88E5),
                "Long Shadow" to Color(0xFF1E88E5),
                "Posterize" to Color(0xFF1E88E5),
                "Shadow" to Color(0xFF1E88E5)
            )
            "Drawing & Edge" -> listOf(
                "Edge Glow" to Color(0xFF5A442A),
                "Glow" to Color(0xFF5A442A),
                "Halftone Dots" to Color(0xFF5A442A),
                "Outline" to Color(0xFF5A442A)
            )
            "Matte/Mask/Key" -> listOf(
                "Vignette" to Color(0xFF3A2E35),
                "Radial Wipe" to Color(0xFF3A2E35)
            )
            "3D" -> listOf(
                "Flip" to Color(0xFF2A2A35)
            )
            else -> emptyList()
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(effectsList) { (name, color) ->
                EffectItemBox(name, color, onClick = { onSelectEffect(name) })
            }
        }
    }
}

@Composable
fun CategoryItem(title: String, bgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun EffectItemBox(name: String, placeholderColor: Color, isLocked: Boolean = false, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderColor),
            contentAlignment = Alignment.Center
        ) {
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .size(20.dp)
                        .background(Color(0xFF00BFA5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            } else {
                Text(
                    text = name.take(2).uppercase(),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}
