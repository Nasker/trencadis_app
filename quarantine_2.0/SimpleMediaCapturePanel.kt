package com.trencadis.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trencadis.app.media.MediaMode
import com.trencadis.app.ui.FeatureLevelManager
import com.trencadis.app.ui.FeatureLevel

/**
 * Simple media capture panel for basic usage
 * Provides essential capture controls without advanced features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMediaCapturePanel(
    mediaMode: MediaMode,
    isRecording: Boolean,
    hasCapturedMedia: Boolean,
    featureLevel: FeatureLevelManager,
    onCaptureStill: () -> Unit,
    onToggleRecording: () -> Unit,
    onViewCaptured: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBasicMode = featureLevel.featureLevel.value == FeatureLevel.BASIC
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Media Capture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (isBasicMode) {
                    // Simple mode indicator
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Simple",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Current mode indicator
            MediaModeIndicator(
                currentMode = mediaMode,
                isRecording = isRecording
            )
            
            // Capture controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Still capture button
                CaptureButton(
                    onClick = onCaptureStill,
                    icon = Icons.Default.PhotoCamera,
                    label = "Capture",
                    enabled = !isRecording,
                    modifier = Modifier.weight(1f)
                )
                
                // Video recording button (only in basic mode)
                if (isBasicMode) {
                    CaptureButton(
                        onClick = onToggleRecording,
                        icon = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                        label = if (isRecording) "Stop" else "Record",
                        enabled = true,
                        isRecording = isRecording,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // View captured media button
            if (hasCapturedMedia) {
                OutlinedButton(
                    onClick = onViewCaptured,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Captured")
                }
            }
            
            // Simple mode tip
            if (isBasicMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "💡 Tip: Switch to Advanced mode for video playback and library features",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Media mode indicator
 */
@Composable
private fun MediaModeIndicator(
    currentMode: MediaMode,
    isRecording: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mode icon
        val (icon, iconColor) = when (currentMode) {
            MediaMode.LIVE_CAMERA -> Icons.Default.Camera to Color.Green
            MediaMode.STATIC_IMAGE -> Icons.Default.Image to Color.Blue
            MediaMode.VIDEO_PLAYBACK -> Icons.Default.PlayArrow to Color.Orange
            MediaMode.VIDEO_RECORDING -> Icons.Default.Videocam to Color.Red
        }
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        
        // Mode text
        Text(
            text = when (currentMode) {
                MediaMode.LIVE_CAMERA -> "Live Camera"
                MediaMode.STATIC_IMAGE -> "Still Image"
                MediaMode.VIDEO_PLAYBACK -> "Video Playback"
                MediaMode.VIDEO_RECORDING -> "Recording..."
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        // Recording indicator
        if (isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Red, RoundedCornerShape(4.dp))
                )
                Text(
                    text = "REC",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Reusable capture button
 */
@Composable
private fun CaptureButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonColors = if (isRecording) {
        ButtonDefaults.buttonColors(
            containerColor = Color.Red,
            contentColor = Color.White
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = buttonColors,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * Minimal media capture controls for minimal mode
 */
@Composable
fun MinimalMediaCaptureControls(
    onCaptureStill: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Only show still capture in minimal mode
        IconButton(
            onClick = onCaptureStill,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Capture",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Advanced mode toggle button
 */
@Composable
fun AdvancedModeToggle(
    isAdvancedMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onToggle,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isAdvancedMode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isAdvancedMode) "Simple Mode" else "Advanced Mode")
    }
}
