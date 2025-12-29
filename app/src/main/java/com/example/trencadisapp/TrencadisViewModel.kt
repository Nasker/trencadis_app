package com.example.trencadisapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trencadisapp.audio.MusicConstants
import com.example.trencadisapp.audio.PdAudioEngine
import com.example.trencadisapp.camera.PixelData
import com.example.trencadisapp.camera.PixelGrid
import com.example.trencadisapp.camera.PixelSelectionMode
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
    val feedback: Float = 0.5f
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
    val blockSize: Int = 20,
    val synthState: SynthState = SynthState(),
    val musicState: MusicState = MusicState(),
    val touchX: Float = 0f,
    val touchY: Float = 0f,
    val isTouching: Boolean = false,
    val showModesPanel: Boolean = false,
    val showScalesPanel: Boolean = false,
    val showKeysPanel: Boolean = false,
    val showSynthPanel: Boolean = false,
    val isAudioInitialized: Boolean = false
)

class TrencadisViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _state = MutableStateFlow(TrencadisState())
    val state: StateFlow<TrencadisState> = _state.asStateFlow()
    
    private val pdEngine = PdAudioEngine(application)
    
    private var lastIp = 0f
    private var lastJp = 0f
    
    init {
        pdEngine.setOnBangReceived {
            viewModelScope.launch {
                incrementSequenceIndex()
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
                    // Calculate grid position from touch coordinates
                    val col = (state.touchX / state.blockSize).toInt().coerceIn(0, grid.cols - 1)
                    val row = (state.touchY / state.blockSize).toInt().coerceIn(0, grid.rows - 1)
                    grid.getPixelAt(col, row)
                } else {
                    grid.getCenter()
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
        pdEngine.setGain(pixel.brightness * 0.5f)
        
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
    
    private fun incrementSequenceIndex() {
        _state.update { state ->
            val maxIndex = state.pixelGrid?.pixels?.size ?: 1
            state.copy(sequenceIndex = (state.sequenceIndex + 1) % maxIndex)
        }
    }
    
    fun setSelectionMode(mode: PixelSelectionMode) {
        _state.update { it.copy(selectionMode = mode) }
        
        val newBlockSize = when (mode) {
            PixelSelectionMode.SEQUENCE -> 20
            PixelSelectionMode.BRIGHTEST -> 10
            PixelSelectionMode.CENTER -> 20
            PixelSelectionMode.POINTER -> 10
        }
        _state.update { it.copy(blockSize = newBlockSize) }
        
        // Update sequencer state
        val sequencerOn = mode != PixelSelectionMode.POINTER
        pdEngine.setSequencerOn(sequencerOn)
    }
    
    fun setTouch(x: Float, y: Float, isTouching: Boolean) {
        _state.update { it.copy(touchX = x, touchY = y, isTouching = isTouching) }
        
        if (_state.value.selectionMode == PixelSelectionMode.POINTER) {
            pdEngine.setNoteOn(isTouching)
            if (isTouching) {
                pdEngine.triggerBang()
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
    
    override fun onCleared() {
        super.onCleared()
        releaseAudio()
    }
}
