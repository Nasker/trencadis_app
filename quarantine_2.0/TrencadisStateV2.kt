package com.trencadis.app

import com.trencadis.app.camera.PixelData
import com.trencadis.app.camera.PixelGrid
import com.trencadis.app.camera.PixelSelectionMode
import com.trencadis.app.ui.AcidModulation
import com.trencadis.app.ui.BlobModulation
import com.trencadis.app.midi.MidiState
import com.trencadis.app.media.MediaCaptureState
import com.trencadis.app.audio.VocoderState
import com.trencadis.app.midi.PolyphonyState
import com.trencadis.app.midi.MidiChannelState
import com.trencadis.app.midi.MidiInputState

/**
 * Extended TrencadisState for Trencadis 2.0 with all new features
 * This extends the original state while maintaining backward compatibility
 */
data class TrencadisStateV2(
    // Original state fields (maintained for compatibility)
    val pixelGrid: PixelGrid? = null,
    val selectedPixel: PixelData? = null,
    val selectionMode: PixelSelectionMode = PixelSelectionMode.SEQUENCE,
    val sequenceIndex: Int = 0,
    val blockSize: Int = 120,
    val customGridResolution: Int? = null,
    val touchX: Float = 0f,
    val touchY: Float = 0f,
    val canvasWidth: Float = 1f,
    val canvasHeight: Float = 1f,
    val isTouching: Boolean = false,
    val showModesPanel: Boolean = false,
    val showScalesPanel: Boolean = false,
    val showKeysPanel: Boolean = false,
    val showSynthPanel: Boolean = false,
    val isAudioInitialized: Boolean = false,
    val useFrontCamera: Boolean = false,
    val acidModulation: AcidModulation = AcidModulation(),
    val acidPatternIndex: Int = 5,
    val showPalettePanel: Boolean = false,
    val showPresetPanel: Boolean = false,
    val presetNames: List<String> = emptyList(),
    val screenAspectRatio: Float = 9f / 16f,
    val useBlobMode: Boolean = false,
    val blobModulation: BlobModulation = BlobModulation(),
    val midiState: MidiState = MidiState(),
    val envelopeTrail: List<Float> = emptyList(),
    
    // New Trencadis 2.0 features
    val mediaCaptureState: MediaCaptureState = MediaCaptureState(),
    val vocoderState: VocoderState = VocoderState(),
    val polyphonyState: PolyphonyState = PolyphonyState(),
    val midiChannelState: MidiChannelState = MidiChannelState(),
    val midiInputState: MidiInputState = MidiInputState(),
    
    // New UI panels for 2.0 features
    val showMediaPanel: Boolean = false,
    val showVocoderPanel: Boolean = false,
    val showPolyphonyPanel: Boolean = false,
    val showMidiInputPanel: Boolean = false,
    val showChannelPanel: Boolean = false
)

/**
 * Helper function to convert original TrencadisState to V2
 */
fun TrencadisState.toV2(): TrencadisStateV2 {
    return TrencadisStateV2(
        pixelGrid = this.pixelGrid,
        selectedPixel = this.selectedPixel,
        selectionMode = this.selectionMode,
        sequenceIndex = this.sequenceIndex,
        blockSize = this.blockSize,
        customGridResolution = this.customGridResolution,
        touchX = this.touchX,
        touchY = this.touchY,
        canvasWidth = this.canvasWidth,
        canvasHeight = this.canvasHeight,
        isTouching = this.isTouching,
        showModesPanel = this.showModesPanel,
        showScalesPanel = this.showScalesPanel,
        showKeysPanel = this.showKeysPanel,
        showSynthPanel = this.showSynthPanel,
        isAudioInitialized = this.isAudioInitialized,
        useFrontCamera = this.useFrontCamera,
        acidModulation = this.acidModulation,
        acidPatternIndex = this.acidPatternIndex,
        showPalettePanel = this.showPalettePanel,
        showPresetPanel = this.showPresetPanel,
        presetNames = this.presetNames,
        screenAspectRatio = this.screenAspectRatio,
        useBlobMode = this.useBlobMode,
        blobModulation = this.blobModulation,
        midiState = this.midiState,
        envelopeTrail = this.envelopeTrail
    )
}

/**
 * Helper function to convert V2 state back to original (for compatibility)
 */
fun TrencadisStateV2.toV1(): TrencadisState {
    return TrencadisState(
        pixelGrid = this.pixelGrid,
        selectedPixel = this.selectedPixel,
        selectionMode = this.selectionMode,
        sequenceIndex = this.sequenceIndex,
        blockSize = this.blockSize,
        customGridResolution = this.customGridResolution,
        touchX = this.touchX,
        touchY = this.touchY,
        canvasWidth = this.canvasWidth,
        canvasHeight = this.canvasHeight,
        isTouching = this.isTouching,
        showModesPanel = this.showModesPanel,
        showScalesPanel = this.showScalesPanel,
        showKeysPanel = this.showKeysPanel,
        showSynthPanel = this.showSynthPanel,
        isAudioInitialized = this.isAudioInitialized,
        useFrontCamera = this.useFrontCamera,
        acidModulation = this.acidModulation,
        acidPatternIndex = this.acidPatternIndex,
        showPalettePanel = this.showPalettePanel,
        showPresetPanel = this.showPresetPanel,
        presetNames = this.presetNames,
        screenAspectRatio = this.screenAspectRatio,
        useBlobMode = this.useBlobMode,
        blobModulation = this.blobModulation,
        midiState = this.midiState,
        envelopeTrail = this.envelopeTrail
    )
}
