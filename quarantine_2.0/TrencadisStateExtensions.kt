package com.trencadis.app

import com.trencadis.app.camera.PixelGrid
import com.trencadis.app.camera.PixelSelectionMode
import com.trencadis.app.ui.AcidModulation
import com.trencadis.app.ui.BlobModulation

/**
 * Extension functions for TrencadisState conversion
 * Provides backward compatibility between V1 and V2 states
 */

/**
 * Convert V1 TrencadisState to V2 TrencadisStateV2
 */
fun TrencadisState.toV2(): TrencadisStateV2 {
    return TrencadisStateV2(
        // Original V1 state
        pixelGrid = this.pixelGrid,
        selectedPixel = this.selectedPixel,
        selectionMode = this.selectionMode,
        blockSize = this.blockSize,
        useFrontCamera = this.useFrontCamera,
        acidPatternIndex = this.acidPatternIndex,
        useBlobMode = this.useBlobMode,
        blobModulation = this.blobModulation,
        acidModulation = this.acidModulation,
        envelopeTrail = this.envelopeTrail,
        sequenceIndex = this.sequenceIndex,
        touchX = this.touchX,
        touchY = this.touchY,
        isTouching = this.isTouching,
        synthState = this.synthState,
        musicState = this.musicState,
        midiState = this.midiState,
        presetNames = this.presetNames,
        showModesPanel = this.showModesPanel,
        showScalesPanel = this.showScalesPanel,
        showKeysPanel = this.showKeysPanel,
        showSynthPanel = this.showSynthPanel,
        showPresetPanel = this.showPresetPanel,
        
        // New V2 features with default values
        mediaCaptureState = com.trencadis.app.media.MediaCaptureState(),
        vocoderState = com.trencadis.app.audio.VocoderState(),
        polyphonyState = com.trencadis.app.midi.PolyphonyState(),
        midiInputState = com.trencadis.app.midi.MidiInputState(),
        midiChannelState = com.trencadis.app.midi.MidiChannelState(),
        
        // New panel states
        showMediaPanel = false,
        showVocoderPanel = false,
        showPolyphonyPanel = false,
        showMidiInputPanel = false,
        showChannelPanel = false,
        
        // Canvas dimensions
        canvasWidth = 1080f,
        canvasHeight = 1920f,
        
        // Audio initialization
        isAudioInitialized = false
    )
}

/**
 * Convert V2 TrencadisStateV2 to V1 TrencadisState
 */
fun TrencadisStateV2.toV1(): TrencadisState {
    return TrencadisState(
        // Core V1 state
        pixelGrid = this.pixelGrid,
        selectedPixel = this.selectedPixel,
        selectionMode = this.selectionMode,
        blockSize = this.blockSize,
        useFrontCamera = this.useFrontCamera,
        acidPatternIndex = this.acidPatternIndex,
        useBlobMode = this.useBlobMode,
        blobModulation = this.blobModulation,
        acidModulation = this.acidModulation,
        envelopeTrail = this.envelopeTrail,
        sequenceIndex = this.sequenceIndex,
        touchX = this.touchX,
        touchY = this.touchY,
        isTouching = this.isTouching,
        synthState = this.synthState,
        musicState = this.musicState,
        midiState = this.midiState,
        presetNames = this.presetNames,
        showModesPanel = this.showModesPanel,
        showScalesPanel = this.showScalesPanel,
        showKeysPanel = this.showKeysPanel,
        showSynthPanel = this.showSynthPanel,
        showPresetPanel = this.showPresetPanel
    )
}

/**
 * Create a default TrencadisState for testing
 */
fun createDefaultTrencadisState(): TrencadisState {
    return TrencadisState(
        pixelGrid = PixelGrid(emptyList(), 0, 0),
        selectedPixel = null,
        selectionMode = PixelSelectionMode.SEQUENCE,
        blockSize = 20,
        useFrontCamera = false,
        acidPatternIndex = 9, // ACID pattern
        useBlobMode = false,
        blobModulation = BlobModulation(),
        acidModulation = AcidModulation(),
        envelopeTrail = FloatArray(64) { 0f },
        sequenceIndex = 0,
        touchX = 0f,
        touchY = 0f,
        isTouching = false,
        synthState = com.trencadis.app.SynthState(),
        musicState = com.trencadis.app.MusicState(),
        midiState = com.trencadis.app.midi.MidiState(
            isEnabled = false,
            clockSource = com.trencadis.app.midi.MidiClockSource.INTERNAL,
            outputMode = com.trencadis.app.midi.MidiOutputMode.NOTE,
            blePeripheral = null
        ),
        presetNames = emptyList(),
        showModesPanel = false,
        showScalesPanel = false,
        showKeysPanel = false,
        showSynthPanel = false,
        showPresetPanel = false
    )
}

/**
 * Create a default TrencadisStateV2 for testing
 */
fun createDefaultTrencadisStateV2(): TrencadisStateV2 {
    return createDefaultTrencadisState().toV2()
}
