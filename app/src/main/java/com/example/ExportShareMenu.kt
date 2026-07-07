package com.example

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ExportShareMenu(
    selectedResolution: String,
    onResolutionSelected: (String) -> Unit,
    selectedFrameRate: String,
    onFrameRateSelected: (String) -> Unit,
    selectedAspectRatio: String = "9:16",
    selectedBackground: String = "Light Grey",
    vectorPoints: List<androidx.compose.ui.geometry.Offset> = emptyList(),
    pointModes: List<Boolean> = emptyList(),
    layerColors: Map<String, Color> = emptyMap(),
    defaultLayerCount: Map<String, Int> = emptyMap(),
    addedMedia: List<android.net.Uri> = emptyList(),
    addedShapes: List<androidx.compose.ui.graphics.vector.ImageVector?> = emptyList(),
    deletedLayers: List<String> = emptyList(),
    layerStartTimes: Map<String, Float> = emptyMap(),
    layerEndTimes: Map<String, Float> = emptyMap(),
    layerTransforms: Map<String, LayerTransform> = emptyMap(),
    layerKeyframes: Map<String, List<LayerKeyframe>> = emptyMap(),
    previewWidthPx: Float = 1f,
    previewHeightPx: Float = 1f,
    timelineDurationSeconds: Float = 4.0f,
    onClose: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf("Video") }
    var alsoAsTemplate by remember { mutableStateOf(false) }
    var showResolutionDropdown by remember { mutableStateOf(false) }
    
    // Export States
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportSuccess by remember { mutableStateOf(false) }
    var savedVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var savedFilePath by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    
    // Render MP4 video or simulate image sequence export
    LaunchedEffect(isExporting) {
        if (isExporting) {
            exportProgress = 0f
            if (selectedFormat == "Video") {
                val (uri, path) = VideoRenderer.renderAndSaveVideo(
                    context = context,
                    resolution = selectedResolution,
                    aspectRatio = selectedAspectRatio,
                    frameRateStr = selectedFrameRate,
                    backgroundStr = selectedBackground,
                    vectorPoints = vectorPoints,
                    pointModes = pointModes,
                    layerColors = layerColors,
                    defaultLayerCount = defaultLayerCount,
                    addedMedia = addedMedia,
                    addedShapes = addedShapes,
                    deletedLayers = deletedLayers,
                    layerStartTimes = layerStartTimes,
                    layerEndTimes = layerEndTimes,
                    layerTransforms = layerTransforms,
                    layerKeyframes = layerKeyframes,
                    previewWidthPx = previewWidthPx,
                    previewHeightPx = previewHeightPx,
                    timelineDurationSeconds = timelineDurationSeconds,
                    onProgress = { progress ->
                        exportProgress = progress
                    }
                )
                savedVideoUri = uri
                savedFilePath = path
            } else {
                while (exportProgress < 1.0f) {
                    delay(80)
                    exportProgress += 0.05f
                    if (exportProgress > 1.0f) {
                        exportProgress = 1.0f
                    }
                }
            }
            delay(300)
            isExporting = false
            exportSuccess = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        // Main bottom sheet container
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFFF1F2F6)) // Off-white/light gray background from screenshot
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = "Export & Share",
                    color = Color(0xFF10182C),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                
                // Invisible spacer to balance the close icon on the left
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            // Divider
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
            
            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Info Section: Available Space & File Size Estimate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Available Space",
                            color = Color(0xFF5E6E82),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = "62.9GB",
                            color = Color(0xFF10182C),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "File size (estimate)",
                            color = Color(0xFF5E6E82),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = if (selectedFormat == "Video") {
                                when (selectedResolution) {
                                    "1440p (QHD)" -> "3.5MB"
                                    "1080p (FHD)" -> "2.0MB"
                                    "720p (HD)" -> "1.2MB"
                                    "540p (SD)" -> "0.8MB"
                                    else -> "0.5MB"
                                }
                            } else if (selectedFormat == "Current Frame as PNG") {
                                "0.4MB"
                            } else if (selectedFormat == "Image Sequence") {
                                "1.5MB"
                            } else if (selectedFormat == "GIF") {
                                "0.9MB"
                            } else {
                                "2.0MB"
                            },
                            color = Color(0xFF10182C),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Formats list container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. VIDEO
                    FormatItemCard(
                        title = "Video",
                        subtitle = "MP4 $selectedResolution $selectedFrameRate",
                        icon = Icons.Default.Movie,
                        isSelected = selectedFormat == "Video",
                        onClick = { selectedFormat = "Video" },
                        hasChevron = true
                    ) {
                        // Embedded Resolution Selector inside Selected Video card
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(Color(0xFFF1F2F6), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { showResolutionDropdown = !showResolutionDropdown }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedResolution,
                                    color = Color(0xFF10182C),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Resolution",
                                    tint = Color.Gray
                                )
                            }
                        }
                        
                        if (showResolutionDropdown) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                val resolutions = listOf("1440p (QHD)", "1080p (FHD)", "720p (HD)", "540p (SD)", "360p")
                                Column {
                                    resolutions.forEachIndexed { index, res ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onResolutionSelected(res)
                                                    showResolutionDropdown = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = res,
                                                color = if (res == selectedResolution) Color(0xFF16B996) else Color.DarkGray,
                                                fontWeight = if (res == selectedResolution) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                            if (res == selectedResolution) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF16B996),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (index < resolutions.size - 1) {
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // 2. UPLOAD TO CLOUD (LOCKED)
                    FormatItemCard(
                        title = "Upload to Cloud",
                        subtitle = "Keep your project safe!",
                        icon = Icons.Default.CloudUpload,
                        isSelected = selectedFormat == "Upload to Cloud",
                        onClick = { 
                            selectedFormat = "Upload to Cloud"
                            Toast.makeText(context, "Premium Cloud Sync unlocked in preview mode!", Toast.LENGTH_SHORT).show()
                        },
                        isLocked = true
                    )
                    
                    // 3. PROJECT PACKAGE (LOCKED)
                    FormatItemCard(
                        title = "Project Package",
                        subtitle = "with Original Media",
                        icon = Icons.Default.Inbox,
                        isSelected = selectedFormat == "Project Package",
                        onClick = { 
                            selectedFormat = "Project Package"
                            Toast.makeText(context, "Premium Project Package packaging enabled!", Toast.LENGTH_SHORT).show()
                        },
                        isLocked = true,
                        hasInfo = true,
                        onInfoClick = {
                            Toast.makeText(context, "Bundles original media, assets and project timeline nodes for easy transfer.", Toast.LENGTH_LONG).show()
                        }
                    )
                    
                    // 4. CURRENT FRAME AS PNG
                    FormatItemCard(
                        title = "Current Frame as PNG",
                        subtitle = null,
                        icon = Icons.Default.Image,
                        isSelected = selectedFormat == "Current Frame as PNG",
                        onClick = { selectedFormat = "Current Frame as PNG" }
                    )
                    
                    // 5. IMAGE SEQUENCE
                    FormatItemCard(
                        title = "Image Sequence",
                        subtitle = "PNG 360p 15fps",
                        icon = Icons.Default.PhotoLibrary,
                        isSelected = selectedFormat == "Image Sequence",
                        onClick = { selectedFormat = "Image Sequence" },
                        hasChevron = true
                    )
                    
                    // 6. GIF
                    FormatItemCard(
                        title = "GIF",
                        subtitle = "180x320 15fps",
                        icon = Icons.Default.Gif,
                        isSelected = selectedFormat == "GIF",
                        onClick = { selectedFormat = "GIF" },
                        hasChevron = true
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // "Also export as a Template" row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Also export as a Template",
                            color = Color(0xFF10182C),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    Toast.makeText(context, "Saves a reusable template project node with editable assets.", Toast.LENGTH_LONG).show()
                                }
                        )
                    }
                    
                    Switch(
                        checked = alsoAsTemplate,
                        onCheckedChange = { alsoAsTemplate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF16B996),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Bottom Button Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { isExporting = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10182C) // Dark Blue from screenshot
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Export",
                        color = Color(0xFF16B996), // Teal/green text color from screenshot
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // EXPORTING PROGRESS POPUP
        if (isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = false) { }, // prevent dismiss during export
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D29))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Exporting Project",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Preparing assets and compiling video frames...",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Styled dynamic percentage indicator
                        val progressPercent = (exportProgress * 100).toInt()
                        Text(
                            text = "$progressPercent%",
                            color = Color(0xFF16B996),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF16B996),
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Estimated remaining: ${((1.0f - exportProgress) * 5).toInt() + 1}s",
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        
        // SUCCESS DIALOG
        if (exportSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { exportSuccess = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D29))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF16B996).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF16B996),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = if (selectedFormat == "Video") "MP4 Saved to Device!" else "Export Successful!",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (selectedFormat == "Video") {
                                "Your MP4 video ($selectedResolution) is saved to your device Gallery (Movies/AlightMotion folder)!"
                            } else {
                                "Your $selectedFormat is saved successfully and is ready to share."
                            },
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (selectedFormat == "Video" && savedVideoUri != null) {
                            Button(
                                onClick = {
                                    try {
                                        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setDataAndType(savedVideoUri, "video/mp4")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(viewIntent, "Open MP4 Video"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Saved at: $savedFilePath", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3246)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(18.dp), tint = Color(0xFF16B996))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play / Open Video", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        
                        Button(
                            onClick = {
                                if (selectedFormat == "Video" && savedVideoUri != null) {
                                    try {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(android.content.Intent.EXTRA_STREAM, savedVideoUri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share MP4 Video"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Shared successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    exportSuccess = false
                                    Toast.makeText(context, "Shared successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16B996)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share Video", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { exportSuccess = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormatItemCard(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    isLocked: Boolean = false,
    hasChevron: Boolean = false,
    hasInfo: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Radio button circle on the left
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            width = if (isSelected) 6.dp else 2.dp,
                            color = if (isSelected) Color(0xFF16B996) else Color.Gray.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .background(
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Icon representing the format
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF10182C),
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Text Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color(0xFF10182C),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (isLocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            // Green circular badge with a lock icon inside
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xFF16B996), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Premium Lock",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                
                // Actions on the far right
                if (hasChevron) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Expand Options",
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (hasInfo) {
                    IconButton(
                        onClick = { onInfoClick?.invoke() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Information",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            if (isSelected && extraContent != null) {
                extraContent()
            }
        }
    }
}
