package com.trencadis.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trencadis.app.TrencadisStateV2
import com.trencadis.app.EnhancedTrencadisViewModel
import com.trencadis.app.media.MediaMode
import com.trencadis.app.ui.components.*
import com.trencadis.app.ui.FeatureLevel

/**
 * Enhanced TrencadisScreen with progressive disclosure for Trencadis 2.0
 * Maintains clean basic experience while providing access to advanced features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTrencadisScreen(
    viewModel: EnhancedTrencadisViewModel,
    modifier: Modifier = Modifier
) {
    // Feature level management
    val featureLevelManager = remember { FeatureLevelManager() }
    val currentLevel by featureLevelManager.featureLevel.collectAsState()
    val featureVisibility by featureLevelManager.featureVisibility.collectAsState()
    
    // UI state
    var showFeatureDialog by remember { mutableStateOf(false) }
    var showDiscoveryTooltip by remember { mutableStateOf(false) }
    var discoveredFeature by remember { mutableStateOf<UIFeature?>(null) }
    
    // Get current state
    val state by viewModel.uiState.collectAsState()
    
    // Panel configuration based on feature level
    val panelConfig = featureLevelManager.getPanelConfiguration()
    
    // Show discovery tooltip for new features
    LaunchedEffect(currentLevel) {
        if (currentLevel == FeatureLevel.BASIC) {
            // Show tooltip for media capture when first entering basic mode
            showDiscoveryTooltip = true
            discoveredFeature = UIFeature.MEDIA_CAPTURE_BASIC
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Main canvas (always visible)
        CubistCanvas(
            pixelGrid = state.pixelGrid,
            selectedPixel = state.selectedPixel,
            blockSize = state.blockSize,
            useBlobMode = state.useBlobMode,
            blobModulation = state.blobModulation,
            acidModulation = state.acidModulation,
            envelopeTrail = state.envelopeTrail,
            polyphonyCursors = if (featureVisibility.showPolyphony) {
                // Get polyphony cursors if feature is visible
                emptyList() // TODO: Get from polyphony manager
            } else emptyList(),
            modifier = Modifier.fillMaxSize()
        )
        
        // Edge hints (adapted based on feature level)
        EdgeHintsSystem(
            panelConfig = panelConfig,
            featureVisibility = featureVisibility,
            onPanelToggle = { panel -> /* Handle panel toggle */ },
            onFeatureToggle = { showFeatureDialog = true },
            modifier = Modifier.fillMaxSize()
        )
        
        // Sliding panels (progressive disclosure)
        SlidingPanelsSystem(
            state = state,
            panelConfig = panelConfig,
            featureVisibility = featureVisibility,
            featureLevelManager = featureLevelManager,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
        
        // Status bar with feature level indicator
        StatusBar(
            currentLevel = currentLevel,
            mediaMode = state.mediaCaptureState.mode,
            isRecording = state.mediaCaptureState.isRecording,
            onFeatureToggle = { showFeatureDialog = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Feature discovery tooltip
        discoveredFeature?.let { feature ->
            FeatureDiscoveryTooltip(
                feature = feature,
                isVisible = showDiscoveryTooltip,
                onDismiss = { 
                    showDiscoveryTooltip = false
                    discoveredFeature = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
    
    // Feature level dialog
    if (showFeatureDialog) {
        AdvancedFeatureToggleDialog(
            currentLevel = currentLevel,
            onDismiss = { showFeatureDialog = false },
            onLevelChange = { newLevel ->
                featureLevelManager.setFeatureLevel(newLevel)
                // Show discovery tooltip for newly unlocked features
                when (newLevel) {
                    FeatureLevel.ADVANCED -> {
                        showDiscoveryTooltip = true
                        discoveredFeature = UIFeature.VOCODER
                    }
                    FeatureLevel.EXPERT -> {
                        showDiscoveryTooltip = true
                        discoveredFeature = UIFeature.EXTERNAL_SYNTH
                    }
                    else -> { /* No tooltip for lower levels */ }
                }
            }
        )
    }
}

/**
 * Edge hints system adapted for feature levels
 */
@Composable
private fun EdgeHintsSystem(
    panelConfig: PanelConfiguration,
    featureVisibility: FeatureVisibility,
    onPanelToggle: (String) -> Unit,
    onFeatureToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Left edge hints
        if (panelConfig.showModesPanel) {
            EdgeHint(
                icon = Icons.Default.Camera,
                position = EdgePosition.LEFT_TOP,
                onClick = { onPanelToggle("modes") }
            )
        }
        
        if (panelConfig.showMediaPanel) {
            EdgeHint(
                icon = Icons.Default.PhotoLibrary,
                position = EdgePosition.LEFT_BOTTOM,
                onClick = { onPanelToggle("media") }
            )
        }
        
        // Top edge hints
        if (panelConfig.showScalesPanel) {
            EdgeHint(
                icon = Icons.Default.Piano,
                position = EdgePosition.TOP_LEFT,
                onClick = { onPanelToggle("scales") }
            )
        }
        
        // Bottom edge hints
        if (panelConfig.showKeysPanel) {
            EdgeHint(
                icon = Icons.Default.MusicNote,
                position = EdgePosition.BOTTOM_LEFT,
                onClick = { onPanelToggle("keys") }
            )
        }
        
        // Right edge hints
        if (panelConfig.showSynthPanel) {
            EdgeHint(
                icon = Icons.Default.GraphicEq,
                position = EdgePosition.RIGHT_TOP,
                onClick = { onPanelToggle("synth") }
            )
        }
        
        if (panelConfig.showPresetPanel) {
            EdgeHint(
                icon = Icons.Default.Bookmark,
                position = EdgePosition.RIGHT_BOTTOM,
                onClick = { onPanelToggle("presets") }
            )
        }
        
        // Advanced feature hints (only in advanced/expert modes)
        if (featureVisibility.showVocoder && panelConfig.showVocoderPanel) {
            EdgeHint(
                icon = Icons.Default.Mic,
                position = EdgePosition.RIGHT_CENTER,
                onClick = { onPanelToggle("vocoder") }
            )
        }
        
        if (featureVisibility.showPolyphony && panelConfig.showPolyphonyPanel) {
            EdgeHint(
                icon = Icons.Default.Grain,
                position = EdgePosition.LEFT_CENTER,
                onClick = { onPanelToggle("polyphony") }
            )
        }
        
        if (featureVisibility.showMidiInput && panelConfig.showMidiInputPanel) {
            EdgeHint(
                icon = Icons.Default.Keyboard,
                position = EdgePosition.TOP_CENTER,
                onClick = { onPanelToggle("midiInput") }
            )
        }
        
        // Feature toggle button (always visible)
        EdgeHint(
            icon = Icons.Default.Tune,
            position = EdgePosition.TOP_RIGHT,
            onClick = onFeatureToggle
        )
    }
}

/**
 * Sliding panels system with progressive disclosure
 */
@Composable
private fun SlidingPanelsSystem(
    state: TrencadisStateV2,
    panelConfig: PanelConfiguration,
    featureVisibility: FeatureVisibility,
    featureLevelManager: FeatureLevelManager,
    viewModel: EnhancedTrencadisViewModel,
    modifier: Modifier = Modifier
) {
    // Existing panels (always available in basic mode)
    if (panelConfig.showModesPanel) {
        SlidingPanel(
            isVisible = state.showModesPanel,
            position = PanelPosition.LEFT,
            content = { ModesPanel(state, viewModel) }
        )
    }
    
    if (panelConfig.showScalesPanel) {
        SlidingPanel(
            isVisible = state.showScalesPanel,
            position = PanelPosition.TOP,
            content = { ScalesPanel(state, viewModel) }
        )
    }
    
    if (panelConfig.showKeysPanel) {
        SlidingPanel(
            isVisible = state.showKeysPanel,
            position = PanelPosition.BOTTOM,
            content = { KeysPanel(state, viewModel) }
        )
    }
    
    if (panelConfig.showSynthPanel) {
        SlidingPanel(
            isVisible = state.showSynthPanel,
            position = PanelPosition.RIGHT,
            content = { SynthPanel(state, viewModel) }
        )
    }
    
    if (panelConfig.showPresetPanel) {
        SlidingPanel(
            isVisible = state.showPresetPanel,
            position = PanelPosition.RIGHT,
            content = { PresetPanel(state, viewModel) }
        )
    }
    
    // New Trencadis 2.0 panels (progressive disclosure)
    if (panelConfig.showMediaPanel && featureVisibility.showMediaCapture) {
        SlidingPanel(
            isVisible = state.showMediaPanel,
            position = PanelPosition.LEFT,
            content = { 
                if (featureLevelManager.featureLevel.value == FeatureLevel.BASIC) {
                    SimpleMediaCapturePanel(
                        mediaMode = state.mediaCaptureState.mode,
                        isRecording = state.mediaCaptureState.isRecording,
                        hasCapturedMedia = state.mediaCaptureState.library.stills.isNotEmpty() || 
                                       state.mediaCaptureState.library.videos.isNotEmpty(),
                        featureLevelManager = featureLevelManager,
                        onCaptureStill = { /* Handle capture */ },
                        onToggleRecording = { /* Handle recording */ },
                        onViewCaptured = { /* Handle view */ }
                    )
                } else {
                    // Advanced media panel
                    AdvancedMediaPanel(state, viewModel)
                }
            }
        )
    }
    
    if (panelConfig.showVocoderPanel && featureVisibility.showVocoder) {
        SlidingPanel(
            isVisible = state.showVocoderPanel,
            position = PanelPosition.RIGHT,
            content = { VocoderPanel(state, viewModel) }
        )
    }
    
    if (panelConfig.showPolyphonyPanel && featureVisibility.showPolyphony) {
        SlidingPanel(
            isVisible = state.showPolyphonyPanel,
            position = PanelPosition.LEFT,
            content = { PolyphonyPanel(state, viewModel) }
        )
    }
    
    if (panelConfig.showMidiInputPanel && featureVisibility.showMidiInput) {
        SlidingPanel(
            isVisible = state.showMidiInputPanel,
            position = PanelPosition.TOP,
            content = { MidiInputPanel(state, viewModel) }
        )
    }
    
    if (panelConfig.showChannelPanel && featureVisibility.showMidiChannels) {
        SlidingPanel(
            isVisible = state.showChannelPanel,
            position = PanelPosition.BOTTOM,
            content = { MidiChannelPanel(state, viewModel) }
        )
    }
}

/**
 * Status bar with feature level and media indicators
 */
@Composable
private fun StatusBar(
    currentLevel: FeatureLevel,
    mediaMode: MediaMode,
    isRecording: Boolean,
    onFeatureToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media mode indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (mediaMode) {
                    MediaMode.LIVE_CAMERA -> {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = null,
                            tint = Color.Green
                        )
                        Text("Live", style = MaterialTheme.typography.labelSmall)
                    }
                    MediaMode.STATIC_IMAGE -> {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.Blue
                        )
                        Text("Still", style = MaterialTheme.typography.labelSmall)
                    }
                    MediaMode.VIDEO_PLAYBACK -> {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Orange
                        )
                        Text("Video", style = MaterialTheme.typography.labelSmall)
                    }
                    MediaMode.VIDEO_RECORDING -> {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.Red
                        )
                        if (isRecording) {
                            Text("REC", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                        }
                    }
                }
            }
            
            // Feature level indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = when (currentLevel) {
                        FeatureLevel.MINIMAL -> "Minimal"
                        FeatureLevel.BASIC -> "Basic"
                        FeatureLevel.ADVANCED -> "Advanced"
                        FeatureLevel.EXPERT -> "Expert"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                IconButton(
                    onClick = onFeatureToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Feature settings",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Helper enums and data classes
enum class EdgePosition {
    LEFT_TOP, LEFT_CENTER, LEFT_BOTTOM,
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    RIGHT_TOP, RIGHT_CENTER, RIGHT_BOTTOM,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

enum class PanelPosition {
    LEFT, TOP, RIGHT, BOTTOM
}

// Placeholder components (would be implemented separately)
@Composable
private fun EdgeHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    position: EdgePosition,
    onClick: () -> Unit
) {
    // Implementation would position the hint at the specified edge
}

@Composable
private fun SlidingPanel(
    isVisible: Boolean,
    position: PanelPosition,
    content: @Composable () -> Unit
) {
    // Implementation would show sliding panel from specified position
}

@Composable
private fun ModesPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Existing modes panel implementation
}

@Composable
private fun ScalesPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Existing scales panel implementation
}

@Composable
private fun KeysPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Existing keys panel implementation
}

@Composable
private fun SynthPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Existing synth panel implementation
}

@Composable
private fun PresetPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Existing preset panel implementation
}

@Composable
private fun AdvancedMediaPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Advanced media panel implementation
}

@Composable
private fun VocoderPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Vocoder panel implementation
}

@Composable
private fun PolyphonyPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // Polyphony panel implementation
}

@Composable
private fun MidiInputPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // MIDI input panel implementation
}

@Composable
private fun MidiChannelPanel(state: TrencadisStateV2, viewModel: EnhancedTrencadisViewModel) {
    // MIDI channel panel implementation
}
