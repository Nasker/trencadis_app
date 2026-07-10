package com.trencadis.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trencadis.app.ui.FeatureLevel
import com.trencadis.app.ui.UIFeature

/**
 * Advanced feature toggle dialog for progressive discovery
 * Allows users to enable/disable features and understand their purpose
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFeatureToggleDialog(
    currentLevel: FeatureLevel,
    onDismiss: () -> Unit,
    onLevelChange: (FeatureLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLevel by remember { mutableStateOf(currentLevel) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Feature Level",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Level description
                Text(
                    text = getLevelDescription(selectedLevel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Level selector
                FeatureLevelSelector(
                    currentLevel = selectedLevel,
                    onLevelSelected = { selectedLevel = it }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Feature preview
                Text(
                    text = "Features at this level:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                FeaturePreviewList(selectedLevel)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            onLevelChange(selectedLevel)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedLevel != currentLevel
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * Feature level selector
 */
@Composable
private fun FeatureLevelSelector(
    currentLevel: FeatureLevel,
    onLevelSelected: (FeatureLevel) -> Unit
) {
    val levels = listOf(
        FeatureLevel.MINIMAL,
        FeatureLevel.BASIC,
        FeatureLevel.ADVANCED,
        FeatureLevel.EXPERT
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        levels.forEach { level ->
            val isSelected = level == currentLevel
            val levelInfo = getLevelInfo(level)
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLevelSelected(level) }
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = levelInfo.icon,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = levelInfo.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Feature preview list
 */
@Composable
private fun FeaturePreviewList(level: FeatureLevel) {
    val features = getFeaturesForLevel(level)
    
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(features) { feature ->
            FeaturePreviewItem(feature)
        }
    }
}

/**
 * Individual feature preview item
 */
@Composable
private fun FeaturePreviewItem(feature: FeatureInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = feature.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (feature.isNew) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.error
            ) {
                Text(
                    text = "NEW",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

/**
 * Quick feature toggle button for status bar
 */
@Composable
fun QuickFeatureToggleButton(
    currentLevel: FeatureLevel,
    onShowDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onShowDialog,
        modifier = modifier
    ) {
        BadgedBox(
            badge = {
                if (currentLevel == FeatureLevel.BASIC) {
                    Badge {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = "New features available",
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Feature settings"
            )
        }
    }
}

/**
 * Progressive feature discovery tooltip
 */
@Composable
fun FeatureDiscoveryTooltip(
    feature: UIFeature,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val featureInfo = getUIFeatureInfo(feature)
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = featureInfo.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Text(
                        text = "New Feature!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Text(
                    text = featureInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// Data classes and helper functions

data class FeatureInfo(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isNew: Boolean = false
)

data class LevelInfo(
    val name: String,
    val icon: ImageVector
)

private fun getLevelInfo(level: FeatureLevel): LevelInfo {
    return when (level) {
        FeatureLevel.MINIMAL -> LevelInfo("Minimal", Icons.Default.RadioButtonUnchecked)
        FeatureLevel.BASIC -> LevelInfo("Basic", Icons.Default.RadioButtonChecked)
        FeatureLevel.ADVANCED -> LevelInfo("Advanced", Icons.Default.Settings)
        FeatureLevel.EXPERT -> LevelInfo("Expert", Icons.Default.Psychology)
    }
}

private fun getLevelDescription(level: FeatureLevel): String {
    return when (level) {
        FeatureLevel.MINIMAL -> "Essential controls only. Perfect for beginners or focused creative sessions."
        FeatureLevel.BASIC -> "Core features with simple media capture. Great for everyday use."
        FeatureLevel.ADVANCED -> "Full creative toolkit including vocoder, polyphony, and MIDI features."
        FeatureLevel.EXPERT -> "Professional features with external synth control and advanced MIDI mapping."
    }
}

private fun getFeaturesForLevel(level: FeatureLevel): List<FeatureInfo> {
    return when (level) {
        FeatureLevel.MINIMAL -> listOf(
            FeatureInfo("Live Camera", "Real-time camera input for synthesis", Icons.Default.Camera),
            FeatureInfo("Basic Synthesis", "Oscillators, filter, and effects", Icons.Default.GraphicEq),
            FeatureInfo("Scales & Keys", "Musical scale and key selection", Icons.Default.Piano),
            FeatureInfo("Presets", "Save and recall your settings", Icons.Default.Bookmark)
        )
        
        FeatureLevel.BASIC -> listOf(
            FeatureInfo("Live Camera", "Real-time camera input for synthesis", Icons.Default.Camera),
            FeatureInfo("Still Capture", "Capture photos to play as sound", Icons.Default.PhotoCamera, isNew = true),
            FeatureInfo("Basic Synthesis", "Oscillators, filter, and effects", Icons.Default.GraphicEq),
            FeatureInfo("Scales & Keys", "Musical scale and key selection", Icons.Default.Piano),
            FeatureInfo("Acid Patterns", "Animated visual patterns", Icons.Default.Pattern),
            FeatureInfo("Presets", "Save and recall your settings", Icons.Default.Bookmark),
            FeatureInfo("Simple Sharing", "Share presets with friends", Icons.Default.Share, isNew = true)
        )
        
        FeatureLevel.ADVANCED -> listOf(
            FeatureInfo("Live Camera", "Real-time camera input for synthesis", Icons.Default.Camera),
            FeatureInfo("Media Library", "Browse and manage captured media", Icons.Default.PhotoLibrary, isNew = true),
            FeatureInfo("Video Playback", "Play videos as sound sources", Icons.Default.PlayArrow, isNew = true),
            FeatureInfo("Vocoder", "Transform your voice with visual synthesis", Icons.Default.Mic, isNew = true),
            FeatureInfo("Polyphony", "Multiple cursors for rich harmonies", Icons.Default.Grain, isNew = true),
            FeatureInfo("MIDI Input", "Follow external chords and arpeggios", Icons.Default.MusicNote, isNew = true),
            FeatureInfo("Full Synthesis", "Complete audio engine control", Icons.Default.GraphicEq),
            FeatureInfo("Advanced Sharing", "Share presets with media", Icons.Default.Share)
        )
        
        FeatureLevel.EXPERT -> listOf(
            FeatureInfo("Complete Media System", "Full capture, library, and playback", Icons.Default.PhotoLibrary),
            FeatureInfo("Advanced Vocoder", "8-band control and carrier options", Icons.Default.Mic),
            FeatureInfo("Expert Polyphony", "Channel mapping and external control", Icons.Default.Grain),
            FeatureInfo("MIDI Mastery", "External synths, learn mode, and mapping", Icons.Default.MusicNote),
            FeatureInfo("External Synths", "Control your entire MIDI rig", Icons.Default.Synthesizer, isNew = true),
            FeatureInfo("MIDI Learn", "Map any controller to any parameter", Icons.Default.School, isNew = true),
            FeatureInfo("Professional Sharing", "Complete preset packages", Icons.Default.Share),
            FeatureInfo("Expert Presets", "Professional preset management", Icons.Default.Bookmark)
        )
    }
}

private fun getUIFeatureInfo(feature: UIFeature): FeatureInfo {
    return when (feature) {
        UIFeature.MEDIA_CAPTURE_BASIC -> FeatureInfo(
            "Media Capture", "Capture photos and videos to play as sound", Icons.Default.PhotoCamera
        )
        UIFeature.VOCODER -> FeatureInfo(
            "Vocoder", "Transform your voice with visual synthesis", Icons.Default.Mic
        )
        UIFeature.POLYPHONY -> FeatureInfo(
            "Polyphony", "Multiple cursors for rich harmonies", Icons.Default.Grain
        )
        UIFeature.MIDI_INPUT -> FeatureInfo(
            "MIDI Input", "Follow external chords and arpeggios", Icons.Default.MusicNote
        )
        else -> FeatureInfo(
            "New Feature", "Discover what's new in Trencadis 2.0", Icons.Default.NewReleases
        )
    }
}
