package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

fun Modifier.checkerboard(
    gridSize: Dp = 8.dp,
    color1: Color = Color(0xFFE2E2E2),
    color2: Color = Color(0xFFC0C0C0)
) = drawBehind {
    val sizePx = gridSize.toPx()
    val columns = (size.width / sizePx).toInt() + 1
    val rows = (size.height / sizePx).toInt() + 1
    for (r in 0 until rows) {
        for (c in 0 until columns) {
            val color = if ((r + c) % 2 == 0) color1 else color2
            drawRect(
                color = color,
                topLeft = Offset(c * sizePx, r * sizePx),
                size = Size(sizePx, sizePx)
            )
        }
    }
}

fun getColorFromName(name: String): Color {
    return when (name) {
        "Black" -> Color.Black
        "White" -> Color.White
        "Light Grey" -> Color(0xFFE2E2E2)
        "Green" -> Color(0xFF107C41)
        "Blue" -> Color(0xFF1F4E79)
        "Transparent" -> Color.Transparent
        else -> Color(0xFFE2E2E2)
    }
}

@Composable
fun ProjectSettingsMenu(
    selectedRatio: String,
    onRatioSelected: (String) -> Unit,
    selectedResolution: String,
    onResolutionSelected: (String) -> Unit,
    selectedFrameRate: String,
    onFrameRateSelected: (String) -> Unit,
    selectedBackground: String,
    onBackgroundSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    var openDropdown by remember { mutableStateOf<String?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClose)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color(0xFF2C3042))
                .clickable { /* prevent closing when clicking inside */ }
        ) {
            // Close icon
            IconButton(
                onClick = onClose,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            
            // Aspect ratio selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AspectRatioBox("16:9", isSelected = selectedRatio == "16:9", onClick = { onRatioSelected("16:9") })
                AspectRatioBox("9:16", isSelected = selectedRatio == "9:16", isTall = true, onClick = { onRatioSelected("9:16") })
                AspectRatioBox("4:5", isSelected = selectedRatio == "4:5", onClick = { onRatioSelected("4:5") })
                AspectRatioBox("1:1", isSelected = selectedRatio == "1:1", onClick = { onRatioSelected("1:1") })
                AspectRatioBox("4:3", isSelected = selectedRatio == "4:3", onClick = { onRatioSelected("4:3") })
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Resolution Selection Row
            val resolutionOptions = listOf("1440p (QHD)", "1080p (FHD)", "720p (HD)", "540p (SD)", "360p", "270p", "180p")
            SettingDropdownRow(
                label = "Resolution",
                value = selectedResolution,
                options = resolutionOptions,
                isOpen = openDropdown == "Resolution",
                onToggle = { openDropdown = if (openDropdown == "Resolution") null else "Resolution" },
                onSelect = {
                    onResolutionSelected(it)
                    openDropdown = null
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Frame Rate Selection Row
            val frameRateOptions = listOf("12 fps", "15 fps", "18 fps", "20 fps", "24 fps", "25 fps", "30 fps", "48 fps", "50 fps", "60 fps")
            SettingDropdownRow(
                label = "Frame Rate",
                value = selectedFrameRate,
                options = frameRateOptions,
                isOpen = openDropdown == "Frame Rate",
                onToggle = { openDropdown = if (openDropdown == "Frame Rate") null else "Frame Rate" },
                onSelect = {
                    onFrameRateSelected(it)
                    openDropdown = null
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Background Selection Row
            val backgroundOptions = listOf("Black", "White", "Light Grey", "Green", "Blue", "Transparent")
            SettingDropdownRow(
                label = "Background",
                value = selectedBackground,
                options = backgroundOptions,
                isOpen = openDropdown == "Background",
                onToggle = { openDropdown = if (openDropdown == "Background") null else "Background" },
                onSelect = {
                    onBackgroundSelected(it)
                    openDropdown = null
                },
                isColor = true
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                "Total Edit Time: 01:34",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
fun AspectRatioBox(ratio: String, isSelected: Boolean = false, isTall: Boolean = false, onClick: () -> Unit) {
    val bgColor = if (isSelected) Color(0xFF16B996) else Color.White
    val textColor = if (isSelected) Color.White else Color.Black
    val height = if (isTall) 60.dp else 40.dp
    
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(height)
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(ratio, color = textColor, fontSize = 12.sp)
    }
}

@Composable
fun SettingDropdownRow(
    label: String,
    value: String,
    options: List<String>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    isColor: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        
        Box(
            modifier = Modifier
                .weight(1.2f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isColor) {
                        if (value == "Transparent") {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .checkerboard(gridSize = 4.dp)
                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(getColorFromName(value))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(value, color = Color.Black, fontSize = 16.sp)
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dropdown", tint = Color.Gray)
            }
            
            if (isOpen) {
                Popup(
                    alignment = Alignment.BottomStart,
                    onDismissRequest = onToggle,
                    properties = PopupProperties(focusable = true)
                ) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .heightIn(max = 240.dp)
                            .background(Color(0xFF202330), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                        ) {
                            options.forEach { option ->
                                val isSelected = option == value
                                val itemBgColor = if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(itemBgColor)
                                        .clickable {
                                            onSelect(option)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isColor) {
                                        if (option == "Transparent") {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .checkerboard(gridSize = 4.dp)
                                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(getColorFromName(option))
                                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    
                                    Text(
                                        text = option,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
