package com.trencadis.app

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trencadis.app.audio.MusicConstants
import com.trencadis.app.audio.PdAudioEngine
import com.trencadis.app.audio.EnhancedVocoderProcessor
import com.trencadis.app.camera.PixelData
import com.trencadis.app.camera.PixelGrid
import com.trencadis.app.camera.PixelSelectionMode
import com.trencadis.app.camera.EnhancedCameraPixelAnalyzer
import com.trencadis.app.ui.AcidModulation
import com.trencadis.app.ui.BlobModulation
import com.trencadis.app.ui.PolyphonyCursorManager
import com.trencadis.app.ui.FeatureLevelManager
import com.trencadis.app.preset.Preset
import com.trencadis.app.preset.PresetManager
import com.trencadis.app.preset.SimplifiedPresetSharingManager
import com.trencadis.app.media.RawMediaCaptureManager
import com.trencadis.app.media.MediaLibraryManager
import com.trencadis.app.media.StaticImagePlayer
import com.trencadis.app.media.VideoFrameExtractor
import com.trencadis.app.midi.BleMidiPeripheral
import com.trencadis.app.midi.BleNoteDestination
import com.trencadis.app.midi.MidiBus
import com.trencadis.app.midi.MidiClockSource
import com.trencadis.app.midi.MidiNoteDestination
import com.trencadis.app.midi.MidiOutputMode
import com.trencadis.app.midi.MidiState
import com.trencadis.app.midi.NoteRouter
import com.trencadis.app.midi.PdNoteDestination
import com.trencadis.app.midi.PolyphonyManager
import com.trencadis.app.midi.MidiChannelManager
import com.trencadis.app.midi.MidiInputReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Enhanced TrencadisViewModel for Trencadis 2.0
 * Integrates all new features while maintaining backward compatibility
 */
class EnhancedTrencadisViewModel(application: Application) : AndroidViewModel(application) {
    
    // Core existing components
    private val pdAudioEngine = PdAudioEngine(application)
    private val presetManager = PresetManager(application)
    
    // New Trencadis 2.0 components
    private val featureLevelManager = FeatureLevelManager()
    private val mediaCaptureManager = RawMediaCaptureManager(application)
    private val mediaLibraryManager = MediaLibraryManager(application)
    private val staticImagePlayer = StaticImagePlayer()
    private val videoFrameExtractor = VideoFrameExtractor()
    private val vocoderProcessor = EnhancedVocoderProcessor(pdAudioEngine, viewModelScope)
    private val polyphonyManager = PolyphonyManager()
    private val polyphonyCursorManager = PolyphonyCursorManager(polyphonyManager)
    private val midiChannelManager = MidiChannelManager()
    private val midiInputReceiver = MidiInputReceiver()
    private val presetSharingManager = SimplifiedPresetSharingManager(application)
    
    // Enhanced camera analyzer with media capture
    private var enhancedCameraAnalyzer: EnhancedCameraPixelAnalyzer? = null
    
    // UI State (V2 with backward compatibility)
    private val _uiState = MutableStateFlow<TrencadisStateV2>(TrencadisStateV2())
    val uiState: StateFlow<TrencadisStateV2> = _uiState.asStateFlow()
    
    // Legacy state for compatibility
    val legacyState: StateFlow<TrencadisState> = _uiState.map { it.toV1() }
    
    init {
        initializeAudio()
        initializeMedia()
        initializeMIDI()
        loadDefaultPreset()
    }
    
    /**
     * Initialize audio engine and vocoder
     */
    private fun initializeAudio() {
        pdAudioEngine.initialize { pdMessage ->
            when (pdMessage) {
                "BANG" -> incrementSequenceIndex()
                // Handle other PD messages
            }
        }
        
        _uiState.update { it.copy(isAudioInitialized = true) }
    }
    
    /**
     * Initialize media capture and playback
     */
    private fun initializeMedia() {
        viewModelScope.launch {
            mediaLibraryManager.loadLibrary()
            _uiState.update { it.copy(
                mediaCaptureState = mediaCaptureManager.captureState.value
            ) }
        }
    }
    
    /**
     * Initialize MIDI components
     */
    private fun initializeMIDI() {
        // Initialize existing MIDI components
        val noteDestinations = listOf(
            PdNoteDestination(pdAudioEngine),
            BleNoteDestination()
        )
        val noteRouter = NoteRouter(noteDestinations)
        
        val midiState = MidiState(
            isEnabled = false,
            clockSource = MidiClockSource.INTERNAL,
            outputMode = MidiOutputMode.NOTE,
            blePeripheral = BleMidiPeripheral(application)
        )
        
        _uiState.update { it.copy(midiState = midiState) }
    }
    
    /**
     * Load default preset
     */
    private fun loadDefaultPreset() {
        viewModelScope.launch {
            val presets = presetManager.loadPresets()
            _uiState.update { it.copy(presetNames = presets.map { it.name }) }
        }
    }
    
    /**
     * Set up enhanced camera analyzer
     */
    fun setupEnhancedCameraAnalyzer(
        blockSize: Int = 20,
        mirrorHorizontally: Boolean = false,
        screenAspectRatio: Float = 9f / 16f,
        blobModulation: BlobModulation? = null,
        onPixelGridReady: (PixelGrid) -> Unit
    ) {
        enhancedCameraAnalyzer = EnhancedCameraPixelAnalyzer(
            blockSize = blockSize,
            mirrorHorizontally = mirrorHorizontally,
            screenAspectRatio = screenAspectRatio,
            blobModulation = blobModulation,
            onPixelGridReady = onPixelGridReady,
            mediaCaptureManager = mediaCaptureManager,
            coroutineScope = viewModelScope
        )
    }
    
    /**
     * Get enhanced camera analyzer
     */
    fun getEnhancedCameraAnalyzer(): EnhancedCameraPixelAnalyzer? {
        return enhancedCameraAnalyzer
    }
    
    /**
     * Handle pixel grid updates from camera
     */
    fun onPixelGridReady(pixelGrid: PixelGrid) {
        _uiState.update { it.copy(pixelGrid = pixelGrid) }
        
        // Update polyphony cursors if enabled
        if (polyphonyManager.polyphonyState.value.enabled) {
            val selectedPixel = selectPixel(pixelGrid)
            selectedPixel?.let { pixel ->
                polyphonyManager.updateCursorPositions(pixel)
                polyphonyCursorManager.updateCursorPositions(
                    pixel, 
                    _uiState.value.canvasWidth, 
                    _uiState.value.canvasHeight
                )
            }
        }
    }
    
    /**
     * Select pixel based on current mode
     */
    private fun selectPixel(pixelGrid: PixelGrid): PixelData? {
        val state = _uiState.value
        return when (state.selectionMode) {
            PixelSelectionMode.SEQUENCE -> {
                val pixels = pixelGrid.pixels.sortedBy { it.gridY * pixelGrid.cols + it.gridX }
                val index = state.sequenceIndex % pixels.size
                pixels[index]
            }
            PixelSelectionMode.BRIGHTEST -> {
                pixelGrid.pixels.maxByOrNull { it.brightness }
            }
            PixelSelectionMode.CENTER -> {
                val centerX = pixelGrid.cols / 2
                val centerY = pixelGrid.rows / 2
                pixelGrid.pixels.find { it.gridX == centerX && it.gridY == centerY }
            }
            PixelSelectionMode.POINTER -> {
                state.selectedPixel
            }
        }
    }
    
    /**
     * Increment sequence index
     */
    private fun incrementSequenceIndex() {
        _uiState.update { 
            val nextIndex = (it.sequenceIndex + 1) % (it.pixelGrid?.pixels?.size ?: 1)
            it.copy(sequenceIndex = nextIndex) 
        }
    }
    
    /**
     * Update canvas dimensions
     */
    fun updateCanvasDimensions(width: Float, height: Float) {
        _uiState.update { it.copy(canvasWidth = width, canvasHeight = height) }
    }
    
    /**
     * Handle touch input
     */
    fun onTouch(x: Float, y: Float) {
        _uiState.update { it.copy(touchX = x, touchY = y, isTouching = true) }
        
        // Handle polyphony cursor interaction in manual mode
        if (polyphonyManager.polyphonyState.value.enabled) {
            polyphonyCursorManager.handleTouch(x, y, _uiState.value.canvasWidth, _uiState.value.canvasHeight)
        }
    }
    
    /**
     * Handle touch release
     */
    fun onTouchRelease() {
        _uiState.update { it.copy(isTouching = false) }
    }
    
    /**
     * Toggle panel visibility
     */
    fun togglePanel(panel: String) {
        _uiState.update { currentState ->
            when (panel) {
                "modes" -> currentState.copy(showModesPanel = !currentState.showModesPanel)
                "scales" -> currentState.copy(showScalesPanel = !currentState.showScalesPanel)
                "keys" -> currentState.copy(showKeysPanel = !currentState.showKeysPanel)
                "synth" -> currentState.copy(showSynthPanel = !currentState.showSynthPanel)
                "presets" -> currentState.copy(showPresetPanel = !currentState.showPresetPanel)
                "media" -> currentState.copy(showMediaPanel = !currentState.showMediaPanel)
                "vocoder" -> currentState.copy(showVocoderPanel = !currentState.showVocoderPanel)
                "polyphony" -> currentState.copy(showPolyphonyPanel = !currentState.showPolyphonyPanel)
                "midiInput" -> currentState.copy(showMidiInputPanel = !currentState.showMidiInputPanel)
                "channel" -> currentState.copy(showChannelPanel = !currentState.showChannelPanel)
                else -> currentState
            }
        }
    }
    
    /**
     * Get feature level manager
     */
    fun getFeatureLevelManager(): FeatureLevelManager {
        return featureLevelManager
    }
    
    /**
     * Get media capture manager
     */
    fun getMediaCaptureManager(): RawMediaCaptureManager {
        return mediaCaptureManager
    }
    
    /**
     * Get vocoder processor
     */
    fun getVocoderProcessor(): EnhancedVocoderProcessor {
        return vocoderProcessor
    }
    
    /**
     * Get polyphony manager
     */
    fun getPolyphonyManager(): PolyphonyManager {
        return polyphonyManager
    }
    
    /**
     * Get polyphony cursor manager
     */
    fun getPolyphonyCursorManager(): PolyphonyCursorManager {
        return polyphonyCursorManager
    }
    
    /**
     * Get MIDI channel manager
     */
    fun getMidiChannelManager(): MidiChannelManager {
        return midiChannelManager
    }
    
    /**
     * Get MIDI input receiver
     */
    fun getMidiInputReceiver(): MidiInputReceiver {
        return midiInputReceiver
    }
    
    /**
     * Get preset sharing manager
     */
    fun getPresetSharingManager(): SimplifiedPresetSharingManager {
        return presetSharingManager
    }
    
    /**
     * Save current state as preset
     */
    fun savePreset(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val preset = Preset(
                id = name.lowercase().replace(" ", "_"),
                name = name,
                description = "Trencadis 2.0 preset",
                parameters = getAllParameters()
            )
            
            presetManager.savePreset(preset)
            loadPresets()
        }
    }
    
    /**
     * Load preset
     */
    fun loadPreset(presetName: String) {
        viewModelScope.launch {
            val preset = presetManager.loadPreset(presetName)
            preset?.let { loadPresetParameters(it.parameters) }
        }
    }
    
    /**
     * Load all presets
     */
    private fun loadPresets() {
        viewModelScope.launch {
            val presets = presetManager.loadPresets()
            _uiState.update { it.copy(presetNames = presets.map { it.name }) }
        }
    }
    
    /**
     * Get all parameters from all managers
     */
    private fun getAllParameters(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            // Add existing parameters
            putAll(getCurrentStateParameters())
            
            // Add V2 parameters
            putAll(vocoderProcessor.getParameters())
            putAll(polyphonyManager.getParameters())
            putAll(midiChannelManager.getParameters())
            putAll(midiInputReceiver.getParameters())
        }
    }
    
    /**
     * Load preset parameters to all managers
     */
    private fun loadPresetParameters(parameters: Map<String, Any>) {
        // Load V2 parameters
        vocoderProcessor.loadParameters(parameters.mapValues { it.value.toString().toFloat() })
        polyphonyManager.loadParameters(parameters)
        midiChannelManager.loadParameters(parameters)
        midiInputReceiver.loadParameters(parameters)
    }
    
    /**
     * Get current state parameters
     */
    private fun getCurrentStateParameters(): Map<String, Any> {
        val state = _uiState.value
        return mapOf(
            "selectionMode" to state.selectionMode.name,
            "blockSize" to state.blockSize,
            "useFrontCamera" to state.useFrontCamera,
            "acidPatternIndex" to state.acidPatternIndex,
            "useBlobMode" to state.useBlobMode,
            // Add synth parameters
            "subOsc" to state.synthState.subOsc,
            "sinOsc" to state.synthState.sinOsc,
            "sawOsc" to state.synthState.sawOsc,
            "sqrOsc" to state.synthState.sqrOsc,
            "noiseOsc" to state.synthState.noiseOsc,
            "cutoff" to state.synthState.cutoff,
            "resonance" to state.synthState.resonance,
            "envelope" to state.synthState.envelope,
            "attack" to state.synthState.attack,
            "release" to state.synthState.release,
            "distortion" to state.synthState.distortion,
            "fm" to state.synthState.fm,
            "fmAmount" to state.synthState.fmAmount,
            "chorusFreq" to state.synthState.chorusFreq,
            "chorusMod" to state.synthState.chorusMod,
            "delayFigure" to state.synthState.delayFigure,
            "feedback" to state.synthState.feedback,
            // Add music parameters
            "scaleIndex" to state.musicState.scaleIndex,
            "keyIndex" to state.musicState.keyIndex,
            "octaveIndex" to state.musicState.octaveIndex,
            "figureIndex" to state.musicState.figureIndex,
            "tempo" to state.musicState.tempo
        )
    }
    
    /**
     * Cleanup resources
     */
    override fun onCleared() {
        super.onCleared()
        vocoderProcessor.cleanup()
        pdAudioEngine.release()
        videoFrameExtractor.release()
        staticImagePlayer.unloadImage()
    }
}
