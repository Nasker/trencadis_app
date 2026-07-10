package com.trencadis.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Feature level manager for progressive disclosure in Trencadis 2.0
 * Ensures basic usage remains uncluttered while providing access to advanced features
 */
class FeatureLevelManager {
    
    private val _featureLevel = MutableStateFlow(FeatureLevel.BASIC)
    val featureLevel = _featureLevel.asStateFlow()
    
    private val _featureVisibility = MutableStateFlow(FeatureVisibility())
    val featureVisibility = _featureVisibility.asStateFlow()
    
    init {
        updateFeatureVisibility()
    }
    
    /**
     * Set the current feature level
     */
    fun setFeatureLevel(level: FeatureLevel) {
        _featureLevel.value = level
        updateFeatureVisibility()
    }
    
    /**
     * Toggle between basic and advanced mode
     */
    fun toggleAdvancedMode() {
        val newLevel = if (_featureLevel.value == FeatureLevel.BASIC) {
            FeatureLevel.ADVANCED
        } else {
            FeatureLevel.BASIC
        }
        setFeatureLevel(newLevel)
    }
    
    /**
     * Check if a feature is visible at current level
     */
    fun isFeatureVisible(feature: UIFeature): Boolean {
        return when (feature) {
            // Basic features - always visible
            UIFeature.LIVE_CAMERA,
            UIFeature.BASIC_SYNTH,
            UIFeature.SCALES_KEYS,
            UIFeature.MODES_PANEL,
            UIFeature.ACID_PANEL,
            UIFeature.PRESET_PANEL -> true
            
            // Media capture - visible in basic mode (simple version)
            UIFeature.MEDIA_CAPTURE_BASIC -> _featureLevel.value != FeatureLevel.MINIMAL
            UIFeature.MEDIA_LIBRARY -> _featureLevel.value == FeatureLevel.ADVANCED
            
            // Advanced audio features
            UIFeature.VOCODER,
            UIFeature.POLYPHONY,
            UIFeature.MIDI_INPUT,
            UIFeature.MIDI_CHANNELS -> _featureLevel.value == FeatureLevel.ADVANCED
            
            // Advanced media features
            UIFeature.VIDEO_PLAYBACK,
            UIFeature.VIDEO_CONTROLS,
            UIFeature.MEDIA_MANAGEMENT -> _featureLevel.value == FeatureLevel.ADVANCED
            
            // Expert features
            UIFeature.EXTERNAL_SYNTH,
            UIFeature.MIDI_LEARN,
            UIFeature.ADVANCED_SHARING -> _featureLevel.value == FeatureLevel.EXPERT
        }
    }
    
    /**
     * Get simplified panel configuration for current level
     */
    fun getPanelConfiguration(): PanelConfiguration {
        return when (_featureLevel.value) {
            FeatureLevel.MINIMAL -> PanelConfiguration(
                showModesPanel = true,
                showScalesPanel = true,
                showKeysPanel = true,
                showSynthPanel = true,
                showPresetPanel = true,
                showMediaPanel = false,
                showVocoderPanel = false,
                showPolyphonyPanel = false,
                showMidiInputPanel = false,
                showChannelPanel = false
            )
            
            FeatureLevel.BASIC -> PanelConfiguration(
                showModesPanel = true,
                showScalesPanel = true,
                showKeysPanel = true,
                showSynthPanel = true,
                showPresetPanel = true,
                showMediaPanel = true,  // Simple media capture
                showVocoderPanel = false,
                showPolyphonyPanel = false,
                showMidiInputPanel = false,
                showChannelPanel = false
            )
            
            FeatureLevel.ADVANCED -> PanelConfiguration(
                showModesPanel = true,
                showScalesPanel = true,
                showKeysPanel = true,
                showSynthPanel = true,
                showPresetPanel = true,
                showMediaPanel = true,
                showVocoderPanel = true,
                showPolyphonyPanel = true,
                showMidiInputPanel = true,
                showChannelPanel = true
            )
            
            FeatureLevel.EXPERT -> PanelConfiguration(
                showModesPanel = true,
                showScalesPanel = true,
                showKeysPanel = true,
                showSynthPanel = true,
                showPresetPanel = true,
                showMediaPanel = true,
                showVocoderPanel = true,
                showPolyphonyPanel = true,
                showMidiInputPanel = true,
                showChannelPanel = true
            )
        }
    }
    
    /**
     * Get simplified controls for current level
     */
    fun getSimplifiedControls(): SimplifiedControls {
        return when (_featureLevel.value) {
            FeatureLevel.MINIMAL -> SimplifiedControls(
                mediaCaptureMode = MediaCaptureMode.NONE,
                vocoderControls = VocoderControls.NONE,
                polyphonyMode = PolyphonyMode.NONE,
                midiMode = MidiMode.NONE
            )
            
            FeatureLevel.BASIC -> SimplifiedControls(
                mediaCaptureMode = MediaCaptureMode.STILL_ONLY,
                vocoderControls = VocoderControls.NONE,
                polyphonyMode = PolyphonyMode.NONE,
                midiMode = MidiMode.BASIC_OUTPUT
            )
            
            FeatureLevel.ADVANCED -> SimplifiedControls(
                mediaCaptureMode = MediaCaptureMode.FULL,
                vocoderControls = VocoderControls.BASIC,
                polyphonyMode = PolyphonyMode.MULTI_CURSOR,
                midiMode = MidiMode.FULL
            )
            
            FeatureLevel.EXPERT -> SimplifiedControls(
                mediaCaptureMode = MediaCaptureMode.FULL,
                vocoderControls = VocoderControls.ADVANCED,
                polyphonyMode = PolyphonyMode.EXPERT,
                midiMode = MidiMode.EXPERT
            )
        }
    }
    
    /**
     * Update feature visibility based on current level
     */
    private fun updateFeatureVisibility() {
        val currentLevel = _featureLevel.value
        _featureVisibility.value = FeatureVisibility(
            showMediaCapture = currentLevel != FeatureLevel.MINIMAL,
            showMediaLibrary = currentLevel == FeatureLevel.ADVANCED || currentLevel == FeatureLevel.EXPERT,
            showVocoder = currentLevel == FeatureLevel.ADVANCED || currentLevel == FeatureLevel.EXPERT,
            showPolyphony = currentLevel == FeatureLevel.ADVANCED || currentLevel == FeatureLevel.EXPERT,
            showMidiInput = currentLevel == FeatureLevel.ADVANCED || currentLevel == FeatureLevel.EXPERT,
            showMidiChannels = currentLevel == FeatureLevel.ADVANCED || currentLevel == FeatureLevel.EXPERT,
            showVideoPlayback = currentLevel == FeatureLevel.ADVANCED || currentLevel == FeatureLevel.EXPERT,
            showAdvancedSharing = currentLevel == FeatureLevel.EXPERT,
            showExternalSynth = currentLevel == FeatureLevel.EXPERT,
            showMidiLearn = currentLevel == FeatureLevel.EXPERT
        )
    }
    
    /**
     * Get feature level description
     */
    fun getLevelDescription(): String {
        return when (_featureLevel.value) {
            FeatureLevel.MINIMAL -> "Minimal - Essential controls only"
            FeatureLevel.BASIC -> "Basic - Core features with simple media capture"
            FeatureLevel.ADVANCED -> "Advanced - Full creative toolkit"
            FeatureLevel.EXPERT -> "Expert - Professional features and MIDI control"
        }
    }
    
    /**
     * Check if user should see onboarding for advanced features
     */
    fun shouldShowAdvancedOnboarding(): Boolean {
        return _featureLevel.value == FeatureLevel.ADVANCED || _featureLevel.value == FeatureLevel.EXPERT
    }
    
    /**
     * Get recommended feature level based on user behavior
     */
    fun getRecommendedLevel(): FeatureLevel {
        // TODO: Analyze user behavior to recommend appropriate level
        // For now, return BASIC as default
        return FeatureLevel.BASIC
    }
}

/**
 * Feature levels for progressive disclosure
 */
enum class FeatureLevel {
    MINIMAL,    // Only essential controls
    BASIC,      // Core features + simple media capture
    ADVANCED,   // Full feature set
    EXPERT      // Professional features
}

/**
 * UI features that can be shown/hidden
 */
enum class UIFeature {
    // Basic features
    LIVE_CAMERA,
    BASIC_SYNTH,
    SCALES_KEYS,
    MODES_PANEL,
    ACID_PANEL,
    PRESET_PANEL,
    
    // Media features
    MEDIA_CAPTURE_BASIC,
    MEDIA_LIBRARY,
    VIDEO_PLAYBACK,
    VIDEO_CONTROLS,
    MEDIA_MANAGEMENT,
    
    // Advanced audio
    VOCODER,
    POLYPHONY,
    MIDI_INPUT,
    MIDI_CHANNELS,
    
    // Expert features
    EXTERNAL_SYNTH,
    MIDI_LEARN,
    ADVANCED_SHARING
}

/**
 * Feature visibility state
 */
data class FeatureVisibility(
    val showMediaCapture: Boolean = false,
    val showMediaLibrary: Boolean = false,
    val showVocoder: Boolean = false,
    val showPolyphony: Boolean = false,
    val showMidiInput: Boolean = false,
    val showMidiChannels: Boolean = false,
    val showVideoPlayback: Boolean = false,
    val showAdvancedSharing: Boolean = false,
    val showExternalSynth: Boolean = false,
    val showMidiLearn: Boolean = false
)

/**
 * Panel configuration for current feature level
 */
data class PanelConfiguration(
    val showModesPanel: Boolean,
    val showScalesPanel: Boolean,
    val showKeysPanel: Boolean,
    val showSynthPanel: Boolean,
    val showPresetPanel: Boolean,
    val showMediaPanel: Boolean,
    val showVocoderPanel: Boolean,
    val showPolyphonyPanel: Boolean,
    val showMidiInputPanel: Boolean,
    val showChannelPanel: Boolean
)

/**
 * Simplified controls for current feature level
 */
data class SimplifiedControls(
    val mediaCaptureMode: MediaCaptureMode,
    val vocoderControls: VocoderControls,
    val polyphonyMode: PolyphonyMode,
    val midiMode: MidiMode
)

/**
 * Media capture modes
 */
enum class MediaCaptureMode {
    NONE,           // No media capture
    STILL_ONLY,     // Still image capture only
    FULL            // Full video and still capture
}

/**
 * Vocoder control levels
 */
enum class VocoderControls {
    NONE,           // No vocoder controls
    BASIC,          // Basic on/off and mix
    ADVANCED        // Full band control and parameters
}

/**
 * Polyphony modes
 */
enum class PolyphonyMode {
    NONE,           // No polyphony
    MULTI_CURSOR,   // Multiple cursors
    EXPERT          // Full polyphony with channel mapping
}

/**
 * MIDI modes
 */
enum class MidiMode {
    NONE,           // No MIDI
    BASIC_OUTPUT,   // Basic MIDI output
    FULL,           // Full MIDI input/output
    EXPERT          // Expert MIDI with learn and mapping
}
