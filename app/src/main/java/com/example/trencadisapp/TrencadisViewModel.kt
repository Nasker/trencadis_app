package com.example.trencadisapp

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trencadisapp.audio.MusicConstants
import com.example.trencadisapp.audio.PdAudioEngine
import com.example.trencadisapp.camera.PixelData
import com.example.trencadisapp.camera.PixelGrid
import com.example.trencadisapp.camera.PixelSelectionMode
import com.example.trencadisapp.ui.AcidModulation
import com.example.trencadisapp.ui.AcidPattern
import com.example.trencadisapp.ui.BlobModulation
import com.example.trencadisapp.preset.Preset
import com.example.trencadisapp.preset.PresetManager
import com.example.trencadisapp.midi.BleMidiPeripheral
import com.example.trencadisapp.midi.BleNoteDestination
import com.example.trencadisapp.midi.MidiBus
import com.example.trencadisapp.midi.MidiClockSource
import com.example.trencadisapp.midi.MidiNoteDestination
import com.example.trencadisapp.midi.MidiOutputMode
import com.example.trencadisapp.midi.MidiState
import com.example.trencadisapp.midi.NoteRouter
import com.example.trencadisapp.midi.PdNoteDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

data class SynthState(
    val subOsc: Boolean = true,
    val sinOsc: Boolean = true,
    val sawOsc: Boolean = false,
    val sqrOsc: Boolean = false,
    val noiseOsc: Boolean = false,
    val cutoff: Float = 1f,
    val resonance: Float = 0f,
    val envelope: Float = 0f,
    val attack: Float = 0f,
    val release: Float = 0.2f,
    val distortion: Float = 0f,
    val fm: Float = 0f,
    val fmAmount: Float = 0f,
    val chorusFreq: Float = 0f,
    val chorusMod: Float = 0f,
    val delayFigure: Float = 1f,
    val feedback: Float = 0.4f
)

data class MusicState(
    val scaleIndex: Int = 8,  // Gipsy scale (like original)
    val keyIndex: Int = 0,    // C
    val octaveIndex: Int = 2, // x3
    val figureIndex: Int = 2, // Negra
    val tempo: Float = 120f,  // BPM
    val periodTempo: Float = 500f  // ms between notes
)

data class TrencadisState(
    val pixelGrid: PixelGrid? = null,
    val selectedPixel: PixelData? = null,
    val selectionMode: PixelSelectionMode = PixelSelectionMode.SEQUENCE,
    val sequenceIndex: Int = 0,
    val blockSize: Int = 120,
    val synthState: SynthState = SynthState(),
    val musicState: MusicState = MusicState(),
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
    val acidPatternIndex: Int = 5,  // Default to WAVE_INTERFERENCE (ACID) — last in reduced list
    val showPalettePanel: Boolean = false,
    val showPresetPanel: Boolean = false,
    val presetNames: List<String> = emptyList(),
    val screenAspectRatio: Float = 9f / 16f,  // width/height, updated once canvas is measured
    val useBlobMode: Boolean = false,
    val blobModulation: BlobModulation = BlobModulation(),
    val midiState: MidiState = MidiState()
)

class TrencadisViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _state = MutableStateFlow(TrencadisState())
    val state: StateFlow<TrencadisState> = _state.asStateFlow()
    
    private val pdEngine = PdAudioEngine(application)
    private val presetManager = PresetManager(application)

    private val pdNoteDestination = PdNoteDestination(pdEngine)
    private val midiNoteDestination = MidiNoteDestination()
    private val blePeripheral = BleMidiPeripheral(application).also { ble ->
        ble.onConnectionChanged = { connected ->
            _state.update { it.copy(midiState = it.midiState.copy(bleConnected = connected)) }
        }
    }
    private val bleNoteDestination = BleNoteDestination(blePeripheral)
    private val noteRouter = NoteRouter().apply { add(pdNoteDestination) }
    private val midiClockSource = MidiClockSource(application, viewModelScope)
    
    private var lastIp = 0f
    private var lastJp = 0f
    
    init {
        // Read true hardware screen dimensions to get an accurate aspect ratio.
        // Compose's onGloballyPositioned under-reports height (insets not included)
        // which makes the ratio appear wider than reality.
        val wm = application.getSystemService(WindowManager::class.java)
        val realRatio: Float = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().toFloat() / bounds.height().toFloat()
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels.toFloat() / dm.heightPixels.toFloat()
        }
        android.util.Log.d("TrencadisAR", "init hardware ratio=$realRatio (${if (realRatio < 1f) "portrait" else "landscape"})")
        _state.update { it.copy(screenAspectRatio = realRatio) }

        pdEngine.setOnBangReceived {
            viewModelScope.launch {
                incrementSequenceIndex()
            }
        }
        // Copy bundled presets on first launch
        presetManager.copyBundledPresetsIfNeeded()
        refreshPresetList()

        // Start collecting MIDI clock (no-op until device connects)
        midiClockSource.connect()
        viewModelScope.launch {
            midiClockSource.isConnected.collect { connected ->
                _state.update { it.copy(midiState = it.midiState.copy(isClockLocked = connected)) }
            }
        }
        viewModelScope.launch {
            midiClockSource.bpmFlow.collect { bpm ->
                if (_state.value.midiState.isClockLocked) setTempo(bpm)
                _state.update { it.copy(midiState = it.midiState.copy(externalBpm = bpm)) }
            }
        }
    }
    
    fun initializeAudio() {
        viewModelScope.launch {
            val success = pdEngine.initialize()
            _state.update { it.copy(isAudioInitialized = success) }
            if (success) {
                applySynthState(_state.value.synthState)
                applyMusicState(_state.value.musicState)
            }
        }
    }
    
    fun releaseAudio() {
        noteRouter.allNotesOff(_state.value.midiState.channel)
        midiClockSource.disconnect()
        blePeripheral.stopAdvertising()
        MidiBus.closeUsbNotePort()
        pdEngine.release()
        _state.update { it.copy(isAudioInitialized = false) }
    }
    
    fun updatePixelGrid(grid: PixelGrid) {
        _state.update { currentState ->
            val selectedPixel = selectPixel(grid, currentState)
            currentState.copy(
                pixelGrid = grid,
                selectedPixel = selectedPixel
            )
        }
        
        // Send audio parameters based on selected pixel
        _state.value.selectedPixel?.let { pixel ->
            sendPixelToAudio(pixel)
        }
    }
    
    private fun selectPixel(grid: PixelGrid, state: TrencadisState): PixelData? {
        return when (state.selectionMode) {
            PixelSelectionMode.SEQUENCE -> grid.getSequential(state.sequenceIndex)
            PixelSelectionMode.BRIGHTEST -> grid.findBrightest()
            PixelSelectionMode.CENTER -> grid.getCenter()
            PixelSelectionMode.POINTER -> {
                if (state.isTouching) {
                    // Calculate grid position from touch coordinates using canvas dimensions
                    grid.getAtPosition(state.touchX, state.touchY, state.canvasWidth, state.canvasHeight)
                } else {
                    null  // No pixel selected when not touching
                }
            }
        }
    }
    
    private fun sendPixelToAudio(pixel: PixelData) {
        val state = _state.value
        val musicState = state.musicState
        val synthState = state.synthState
        
        // Calculate frequency from hue
        val freq = MusicConstants.calculateFrequency(
            hue = pixel.hue,
            scaleIndex = musicState.scaleIndex,
            keyIndex = musicState.keyIndex,
            octaveIndex = musicState.octaveIndex
        )
        
        // Calculate spatial position for panning (-20 to 20 like original)
        val spaceSize = 40f
        val grid = state.pixelGrid ?: return
        val ip = (pixel.gridX.toFloat() / grid.cols) * spaceSize - spaceSize / 2
        val jp = (pixel.gridY.toFloat() / grid.rows) * spaceSize - spaceSize / 2
        
        // Calculate filter cutoff with envelope
        val cutoff = freq / 2 + 16000 * synthState.cutoff.pow(4)
        val envDiff = cutoff * 2f.pow(4 * synthState.envelope) - cutoff
        
        pdEngine.setX(ip)
        pdEngine.setY(jp + 0.1f)
        pdEngine.setFrequency(freq)
        val pdActive = _state.value.midiState.outputMode != MidiOutputMode.MIDI_OUT
        pdEngine.setGain(if (pdActive) pixel.brightness * 0.5f else 0f)
        
        pdEngine.setCutoff(cutoff)
        pdEngine.setResonance(1 + 100 * synthState.resonance.pow(3))
        pdEngine.setEnvelope(envDiff)
        pdEngine.setAttack(5 + synthState.attack * 500)
        pdEngine.setRelease(synthState.release * 5000)
        pdEngine.setDistortion(synthState.distortion)
        pdEngine.setFM(8000 * synthState.fm.pow(2))
        pdEngine.setAmountFM(synthState.fmAmount)
        pdEngine.setChorusFreq(10 * synthState.chorusFreq.pow(2))
        pdEngine.setChorusMod(100 * synthState.chorusMod.pow(3))
        pdEngine.setFeedback(2.5f * synthState.feedback)
        pdEngine.setReverbSend(synthState.feedback / 5)
        
        pdEngine.setDelayTime(musicState.periodTempo / 2f.pow(synthState.delayFigure.roundToInt().toFloat()))
        pdEngine.setSequencerPeriod(musicState.periodTempo / 2f.pow((musicState.figureIndex - 2).toFloat()))
        
        val rootFreq = MusicConstants.getRootFrequency(musicState.keyIndex)
        pdEngine.setBPDFreq(rootFreq * 32)
        
        // In pointer mode, trigger on position change
        if (state.selectionMode == PixelSelectionMode.POINTER && 
            state.isTouching && (jp != lastJp || ip != lastIp)) {
            pdEngine.triggerBang()
            lastJp = jp
            lastIp = ip
        }
    }
    
    private fun freqToMidiPitch(freq: Float): Int =
        (69 + 12 * Math.log(freq / 440.0) / Math.log(2.0)).toInt().coerceIn(0, 127)

    private fun incrementSequenceIndex() {
        _state.update { state ->
            val maxIndex = state.pixelGrid?.pixels?.size ?: 1
            state.copy(sequenceIndex = (state.sequenceIndex + 1) % maxIndex)
        }
        val state = _state.value
        if (state.midiState.enabled && state.midiState.outputMode != MidiOutputMode.INTERNAL) {
            state.selectedPixel?.let { pixel ->
                val music = state.musicState
                val freq = com.example.trencadisapp.audio.MusicConstants.calculateFrequency(
                    pixel.hue, music.scaleIndex, music.keyIndex, music.octaveIndex
                )
                val pitch = freqToMidiPitch(freq)
                val velocity = (pixel.brightness * 127).toInt().coerceIn(1, 127)
                noteRouter.noteOn(pitch, velocity, state.midiState.channel)
            }
        }
    }
    
    fun setSelectionMode(mode: PixelSelectionMode) {
        _state.update { it.copy(selectionMode = mode) }
        
        val newBlockSize = when (mode) {
            PixelSelectionMode.SEQUENCE -> 60
            PixelSelectionMode.BRIGHTEST -> 50
            PixelSelectionMode.CENTER -> 60
            PixelSelectionMode.POINTER -> 50
        }
        _state.update { it.copy(blockSize = newBlockSize) }
        
        // Update sequencer state
        val sequencerOn = mode != PixelSelectionMode.POINTER
        pdEngine.setSequencerOn(sequencerOn)
    }
    
    fun setTouch(x: Float, y: Float, isTouching: Boolean, canvasWidth: Float = 0f, canvasHeight: Float = 0f) {
        val prevState = _state.value
        _state.update { 
            it.copy(
                touchX = x, 
                touchY = y, 
                isTouching = isTouching,
                canvasWidth = if (canvasWidth > 0f) canvasWidth else it.canvasWidth,
                canvasHeight = if (canvasHeight > 0f) canvasHeight else it.canvasHeight
            ) 
        }
        
        if (_state.value.selectionMode == PixelSelectionMode.POINTER) {
            pdEngine.setNoteOn(isTouching)
            
            // Trigger on press or when position changes while touching
            if (isTouching) {
                val grid = _state.value.pixelGrid
                if (grid != null) {
                    val newState = _state.value
                    val prevPixel = if (prevState.isTouching) {
                        grid.getAtPosition(prevState.touchX, prevState.touchY, prevState.canvasWidth, prevState.canvasHeight)
                    } else null
                    val newPixel = grid.getAtPosition(x, y, newState.canvasWidth, newState.canvasHeight)
                    
                    // Trigger if just started touching OR pixel changed
                    if (!prevState.isTouching || (prevPixel?.gridX != newPixel?.gridX || prevPixel?.gridY != newPixel?.gridY)) {
                        newPixel?.let { sendPixelToAudio(it) }
                        pdEngine.triggerBang()
                    }
                } else {
                    pdEngine.triggerBang()
                }
            }
        }
    }
    
    // Music state setters
    fun setScale(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(scaleIndex = index)) }
    }
    
    fun setKey(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(keyIndex = index)) }
    }
    
    fun setOctave(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(octaveIndex = index)) }
    }
    
    fun setFigure(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(figureIndex = index)) }
    }
    
    fun setTempo(bpm: Float) {
        val period = (60000f / bpm)
        _state.update { 
            it.copy(musicState = it.musicState.copy(tempo = bpm, periodTempo = period)) 
        }
    }
    
    fun tapTempo(currentTimeMs: Long, previousTapTimeMs: Long): Float {
        val period = currentTimeMs - previousTapTimeMs
        return if (period in 200..2000) {
            val bpm = 60000f / period
            setTempo(bpm)
            bpm
        } else {
            _state.value.musicState.tempo
        }
    }
    
    // Synth state setters
    fun updateSynthState(update: (SynthState) -> SynthState) {
        _state.update { it.copy(synthState = update(it.synthState)) }
        applySynthState(_state.value.synthState)
    }
    
    private fun applySynthState(synth: SynthState) {
        pdEngine.setOscillatorSub(synth.subOsc)
        pdEngine.setOscillatorSin(synth.sinOsc)
        pdEngine.setOscillatorSaw(synth.sawOsc)
        pdEngine.setOscillatorSqr(synth.sqrOsc)
        pdEngine.setOscillatorNoise(synth.noiseOsc)
    }
    
    private fun applyMusicState(music: MusicState) {
        pdEngine.setSequencerPeriod(music.periodTempo / 2f.pow((music.figureIndex - 2).toFloat()))
    }
    
    // Panel visibility
    fun setModesPanel(show: Boolean) = _state.update { it.copy(showModesPanel = show) }
    fun setScalesPanel(show: Boolean) = _state.update { it.copy(showScalesPanel = show) }
    fun setKeysPanel(show: Boolean) = _state.update { it.copy(showKeysPanel = show) }
    fun setSynthPanel(show: Boolean) = _state.update { it.copy(showSynthPanel = show) }
    
    // Camera selection
    fun toggleCamera() = _state.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    fun toggleBlobMode() = _state.update { it.copy(useBlobMode = !it.useBlobMode) }

    fun updateScreenAspectRatio(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            _state.update { it.copy(screenAspectRatio = width / height) }
        }
    }
    
    // Acid pattern controls
    fun toggleAcid() = _state.update { 
        it.copy(acidModulation = it.acidModulation.copy(enabled = !it.acidModulation.enabled)) 
    }
    
    fun setAcidPattern(index: Int) = _state.update { it.copy(acidPatternIndex = index) }
    
    fun setAcidModulation(modulation: AcidModulation) {
        _state.update { it.copy(acidModulation = modulation) }
    }
    
    fun setPalettePanel(show: Boolean) = _state.update { it.copy(showPalettePanel = show) }
    
    // Preset panel
    fun setPresetPanel(show: Boolean) = _state.update { it.copy(showPresetPanel = show) }

    fun updateBlobModulation(update: (BlobModulation) -> BlobModulation) =
        _state.update { it.copy(blobModulation = update(it.blobModulation)) }
    
    private fun refreshPresetList() {
        _state.update { it.copy(presetNames = presetManager.getPresetNames()) }
    }
    
    fun savePreset(name: String) {
        val currentState = _state.value
        val preset = Preset(
            name = name,
            synthState = currentState.synthState,
            musicState = currentState.musicState,
            acidModulation = currentState.acidModulation,
            acidPatternIndex = currentState.acidPatternIndex,
            selectionMode = currentState.selectionMode,
            useFrontCamera = currentState.useFrontCamera,
            useBlobMode = currentState.useBlobMode,
            blobModulation = currentState.blobModulation
        )
        presetManager.savePreset(preset)
        refreshPresetList()
    }
    
    fun loadPreset(name: String) {
        val preset = presetManager.loadPreset(name) ?: return
        _state.update { 
            it.copy(
                synthState = preset.synthState,
                musicState = preset.musicState,
                acidModulation = preset.acidModulation,
                acidPatternIndex = preset.acidPatternIndex,
                selectionMode = preset.selectionMode,
                useFrontCamera = preset.useFrontCamera,
                useBlobMode = preset.useBlobMode,
                blobModulation = preset.blobModulation
            )
        }
        // Apply loaded state to audio engine
        applySynthState(preset.synthState)
        applyMusicState(preset.musicState)
        // Apply selection mode to sequencer
        setSelectionMode(preset.selectionMode)
    }
    
    fun deletePreset(name: String) {
        presetManager.deletePreset(name)
        refreshPresetList()
    }
    
    fun getShareIntent(name: String): Intent? {
        return presetManager.createShareIntent(name)
    }
    
    fun setMidiEnabled(enabled: Boolean) {
        _state.update { it.copy(midiState = it.midiState.copy(enabled = enabled)) }
        if (enabled) {
            noteRouter.add(midiNoteDestination)
        } else {
            noteRouter.remove(midiNoteDestination)
            noteRouter.allNotesOff(_state.value.midiState.channel)
        }
    }

    fun setMidiOutputMode(mode: MidiOutputMode) {
        _state.update { it.copy(midiState = it.midiState.copy(outputMode = mode)) }
        pdNoteDestination.setActive(mode != MidiOutputMode.MIDI_OUT)
        when (mode) {
            MidiOutputMode.INTERNAL -> {
                noteRouter.remove(midiNoteDestination)
                noteRouter.remove(bleNoteDestination)
            }
            MidiOutputMode.MIDI_OUT, MidiOutputMode.BOTH -> {
                noteRouter.add(midiNoteDestination)
                if (_state.value.midiState.bleEnabled) noteRouter.add(bleNoteDestination)
            }
        }
    }

    fun setMidiChannel(channel: Int) {
        noteRouter.allNotesOff(_state.value.midiState.channel)
        _state.update { it.copy(midiState = it.midiState.copy(channel = channel.coerceIn(1, 16))) }
    }

    fun setBleEnabled(enabled: Boolean) {
        _state.update { it.copy(midiState = it.midiState.copy(bleEnabled = enabled)) }
        if (enabled) {
            blePeripheral.startAdvertising()
            if (_state.value.midiState.outputMode != MidiOutputMode.INTERNAL) {
                noteRouter.add(bleNoteDestination)
            }
        } else {
            noteRouter.remove(bleNoteDestination)
            noteRouter.allNotesOff(_state.value.midiState.channel)
            blePeripheral.stopAdvertising()
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseAudio()
    }
}
