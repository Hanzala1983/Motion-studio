package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FeaturedVideo
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorFillMenu(
    currentColor: Color = Color(0xFF16B996),
    onColorChange: (Color) -> Unit = {},
    onBack: () -> Unit,
    onPaletteClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(1) }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF1B1D25))) {
        // Left Column
        Column(
            modifier = Modifier.width(48.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(24.dp).clickable(onClick = onBack))
            Icon(Icons.Default.Add, "Keyframe", tint = Color.White, modifier = Modifier.size(24.dp))
            Icon(Icons.AutoMirrored.Filled.ShowChart, "Curve", tint = Color.White, modifier = Modifier.size(24.dp))
            Icon(Icons.Default.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        // Center Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            // Top tabs
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorFillTab(0, selectedTab, Icons.Default.Block, onTabSelected = { selectedTab = it }, modifier = Modifier.weight(1f))
                ColorFillTab(1, selectedTab, Icons.Default.FormatColorFill, onTabSelected = { selectedTab = it }, modifier = Modifier.weight(1f))
                ColorFillTab(2, selectedTab, Icons.Default.Gradient, onTabSelected = { selectedTab = it }, modifier = Modifier.weight(1f))
                ColorFillTab(3, selectedTab, Icons.AutoMirrored.Filled.FeaturedVideo, onTabSelected = { selectedTab = it }, modifier = Modifier.weight(1f))
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Content
            Row(
                modifier = Modifier.fillMaxSize().background(Color(0xFF222634), RoundedCornerShape(4.dp))
            ) {
                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    // Color code
                    val r = (currentColor.red * 255).toInt()
                    val g = (currentColor.green * 255).toInt()
                    val b = (currentColor.blue * 255).toInt()
                    val a = (currentColor.alpha * 100).toInt()
                    val hexCode = String.format("#%02X%02X%02X (%d%%)", r, g, b, a)
                    Box(modifier = Modifier.fillMaxWidth().height(28.dp).background(currentColor, RoundedCornerShape(4.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text(hexCode, color = if (currentColor.red * 0.299 + currentColor.green * 0.587 + currentColor.blue * 0.114 > 0.5) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    
                    // Swatches
                    val colors1 = listOf(
                        Color(0xFFF44336), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF4CAF50), 
                        Color(0xFF00BCD4), Color(0xFF3F51B5), Color(0xFFE91E63)
                    )
                    val colors2 = listOf(
                        Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF616161), 
                        Color(0xFF000000), Color(0xFF81D4FA), Color(0xFFA5D6A7)
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        colors1.forEach { color ->
                            val isSelected = color == currentColor
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(end = 4.dp)
                                    .background(color, RoundedCornerShape(4.dp))
                                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable { onColorChange(color) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        colors2.forEach { color ->
                            val isSelected = color == currentColor
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(end = 4.dp)
                                    .background(color, RoundedCornerShape(4.dp))
                                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color.Gray else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable { onColorChange(color) }
                            )
                        }
                    }
                }
                
                // Content Right Toolbar
                Column(
                    modifier = Modifier.width(56.dp).fillMaxHeight().background(Color(0xFF262934), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Icon(Icons.Default.Colorize, "Eyedropper", tint = Color.Gray, modifier = Modifier.size(24.dp))
                    Icon(Icons.Default.CropDin, "Box", tint = Color.Gray, modifier = Modifier.size(24.dp))
                    Icon(Icons.Default.Palette, "Palette", tint = Color.Gray, modifier = Modifier.size(24.dp).clickable(onClick = onPaletteClick))
                }
            }
        }
    }
}

@Composable
fun ColorFillTab(index: Int, selectedIndex: Int, icon: ImageVector, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val isSelected = index == selectedIndex
    val bgColor = if (isSelected) Color(0xFF222634) else Color(0xFF383C50)
    val color = if (isSelected) Color(0xFF16B996) else Color.White
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable { onTabSelected(index) },
        contentAlignment = Alignment.Center
    ) {
        if (index == 2) {
             Box(modifier = Modifier.size(20.dp, 14.dp).background(Brush.horizontalGradient(listOf(Color.White, Color.Gray)), RoundedCornerShape(2.dp)))
        } else {
             Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}
