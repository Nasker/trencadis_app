package com.trencadis.app

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trencadis.app.audio.MusicConstants
import com.trencadis.app.audio.PdAudioEngine
import com.trencadis.app.camera.PixelData
import com.trencadis.app.camera.PixelGrid
import com.trencadis.app.camera.PixelSelectionMode
import com.trencadis.app.media.MediaCaptureState
import com.trencadis.app.media.RawMediaCaptureManager
import com.trencadis.app.ui.AcidModulation
import com.trencadis.app.ui.AcidPattern
import com.trencadis.app.ui.BlobModulation
import com.trencadis.app.preset.Preset
import com.trencadis.app.preset.PresetManager
import com.trencadis.app.midi.BleMidiPeripheral
import com.trencadis.app.midi.BleNoteDestination
import com.trencadis.app.midi.MidiBus
import com.trencadis.app.midi.MidiClockSource
import com.trencadis.app.midi.MidiNoteDestination
import com.trencadis.app.midi.MidiOutputMode
import com.trencadis.app.midi.MidiState
import com.trencadis.app.midi.NoteRouter
import com.trencadis.app.midi.PdNoteDestination
import com.trencadis.app.midi.SyncSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
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
    val feedback: Float = 0.4f,
    val gateLength: Float = 1f  // 1 = full/legato, 0 = staccato
)

data class MusicState(
    val scaleIndex: Int = 8,  // Gipsy scale (like original)
    val keyIndex: Int = 0,    // C
    val octaveIndex: Int = 2, // x3
    val figureIndex: Int = 2, // Negra
    val chordTypeIndex: Int = 0, // Major
    val useChordMapping: Boolean = false, // true when chord was selected last, false when scale was selected last
    val tempo: Float = 120f,  // BPM
    val periodTempo: Float = 500f  // ms between notes
)

data class TrencadisState(
    val pixelGrid: PixelGrid? = null,
    val selectedPixel: PixelData? = null,
    val selectionMode: PixelSelectionMode = PixelSelectionMode.SEQUENCE,
    val sequenceIndex: Int = 0,
    val blockSize: Int = 120,
    // User override for grid resolution; null = follow per-mode defaults
    val customGridResolution: Int? = null,
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
    val midiState: MidiState = MidiState(),
    // Recent amp-envelope samples from Pd (~30Hz, newest first). The canvas
    // samples this delayed by grid distance to ripple outward from the note.
    val envelopeTrail: List<Float> = emptyList(),
    val mediaCaptureState: MediaCaptureState = MediaCaptureState(),
    val isPlaying: Boolean = true
)

class TrencadisViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Grid density along the longer screen axis. Below ~20 cells the mosaic loses
        // meaning; above ~160 per-frame analysis cost grows quadratically.
        const val MIN_GRID_RESOLUTION = 20
        const val MAX_GRID_RESOLUTION = 160

        // Envelope samples kept for the visual ripple (~0.8s at 33ms/sample)
        const val ENVELOPE_TRAIL_SIZE = 24

        // Sequence-mode tap/swipe thresholds (in pixels / ms)
        const val SEQUENCE_TAP_DISTANCE = 24f
        const val SEQUENCE_TAP_TIMEOUT = 250L
        const val SEQUENCE_SWIPE_DISTANCE = 48f
    }

    private val _state = MutableStateFlow(TrencadisState())
    val state: StateFlow<TrencadisState> = _state.asStateFlow()
    
    private val pdEngine = PdAudioEngine(application)
    private val presetManager = PresetManager(application)

    // Raw still/video capture of the live camera feed, independent of audio/MIDI.
    val mediaCaptureManager = RawMediaCaptureManager(application)

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

    // Gesture tracking for sequence-mode tap/swipe control
    private var seqTouchStartX = 0f
    private var seqTouchStartY = 0f
    private var seqTouchStartTime = 0L
    private var seqSwipeFired = false

    // External MIDI clock phase (24 ticks per quarter note). Written from the
    // MIDI delivery thread; reset only by MIDI Start (0xFA) so tick 0 stays
    // anchored to the DAW's transport grid.
    @Volatile private var externalTickCount = 0L

    // --- MIDI clock recovery & step scheduler ---
    // Android delivers 0xF8 ticks jittered and batched (USB driver batching,
    // binder hops, BLE connection intervals of 30-50 ms). Firing steps on raw
    // tick arrival is fine at 1/4 (500 ms steps) but audibly wrecks the groove
    // at 1/8 and 1/16, where the step interval is comparable to the batch size.
    // Instead, a delay-locked loop smooths tick phase/period and each step is
    // scheduled at its predicted grid time on a dedicated handler thread.
    private val clockLock = Any()
    private val stepSchedulerThread =
        HandlerThread("midi-step-scheduler", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            .apply { start() }
    private val stepScheduler = Handler(stepSchedulerThread.looper)
    private val fireStep = Runnable { fireScheduledStep() }
    private val tickWindow = ArrayDeque<Pair<Long, Long>>() // (tick index, uptimeMs)
    private var tickPeriodMs = 60_000.0 / 120.0 / 24.0      // smoothed ms per tick
    private var expectedTickTimeMs = 0.0                    // DLL-smoothed arrival phase
    private var scheduledBoundary = -1.0                    // absolute tick pos of pending step
    private var lastFiredBoundary = -1.0                    // absolute tick pos of last step

    /**
     * Recomputes the effective clock lock whenever external clock availability
     * or the user's sync-source choice changes. The lock only engages when the
     * user selected EXTERNAL *and* ticks are actually arriving. While locked
     * the Pd metro is silenced and steps are driven from MIDI ticks; when the
     * lock drops the internal metro takes over again (except in pointer mode).
     * Only the metro stops — onSEQ stays on so the bang-triggered envelope
     * keeps sounding the internal synth on external ticks.
     */
    private fun updateClockLock(
        available: Boolean = _state.value.midiState.externalClockAvailable,
        source: SyncSource = _state.value.midiState.syncSource
    ) {
        val wasLocked = _state.value.midiState.isClockLocked
        val locked = available && source == SyncSource.EXTERNAL
        _state.update {
            it.copy(midiState = it.midiState.copy(
                externalClockAvailable = available,
                syncSource = source,
                isClockLocked = locked
            ))
        }
        // Note: the tick counter is deliberately NOT reset here — it keeps
        // counting through lock changes so the grid anchor from the DAW's
        // Start message survives and re-locking lands back on the beat.
        if (!locked && wasLocked) {
            synchronized(clockLock) { cancelScheduledStepLocked() }
            // Release the note the last externally-clocked step left sounding;
            // the internal metro will retrigger from here.
            noteRouter.allNotesOff(_state.value.midiState.channel)
        }
        applySequencerState()
    }

    fun setSyncSource(source: SyncSource) {
        updateClockLock(source = source)
    }

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

        // Handle the BANG synchronously on the delivery thread. Bouncing it
        // through a main-thread coroutine queued every sequenced MIDI note
        // behind Compose/render work, adding up to a frame of jitter per note.
        pdEngine.setOnBangReceived {
            incrementSequenceIndex()
        }
        pdEngine.setOnEnvelopeReceived { level ->
            pushEnvelopeSample(level.coerceIn(0f, 1f))
        }
        // Copy bundled presets on first launch
        presetManager.copyBundledPresetsIfNeeded()
        refreshPresetList()

        viewModelScope.launch {
            mediaCaptureManager.captureState.collect { captureState ->
                _state.update { it.copy(mediaCaptureState = captureState) }
            }
        }

        // Start collecting MIDI clock (no-op until device connects)
        midiClockSource.onStart = { onExternalTransportStart() }
        midiClockSource.onContinue = { onExternalTransportContinue() }
        midiClockSource.onStop = { onExternalTransportStop() }
        midiClockSource.onTick = { onExternalClockTick() }
        midiClockSource.connect()
        viewModelScope.launch {
            midiClockSource.isConnected.collect { available ->
                updateClockLock(available = available)
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
                applySequencerState()
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

        // Calculate frequency from hue, mapping to the selected scale degrees or chord grades
        val freq = MusicConstants.calculateFrequency(
            hue = pixel.hue,
            scaleIndex = musicState.scaleIndex,
            keyIndex = musicState.keyIndex,
            octaveIndex = musicState.octaveIndex,
            chordTypeIndex = musicState.chordTypeIndex,
            useChordMapping = musicState.useChordMapping
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
    
    // ~0.8s of envelope history at 33ms per sample
    private val envelopeTrailBuf = ArrayDeque<Float>(ENVELOPE_TRAIL_SIZE)

    private fun pushEnvelopeSample(level: Float) {
        synchronized(envelopeTrailBuf) {
            // Once silent and the whole trail has drained to zero, stop pushing
            // state updates so an idle app doesn't recompose at 30Hz forever.
            if (level <= 0.001f &&
                envelopeTrailBuf.size >= ENVELOPE_TRAIL_SIZE &&
                envelopeTrailBuf.all { it <= 0.001f }
            ) return

            envelopeTrailBuf.addFirst(level)
            while (envelopeTrailBuf.size > ENVELOPE_TRAIL_SIZE) envelopeTrailBuf.removeLast()
            val snapshot = envelopeTrailBuf.toList()
            _state.update { it.copy(envelopeTrail = snapshot) }
        }
    }

    private fun resetExternalClockPhase() {
        synchronized(clockLock) {
            externalTickCount = 0L
            expectedTickTimeMs = 0.0
            tickWindow.clear()
            lastFiredBoundary = -1.0
            cancelScheduledStepLocked()
        }
    }

    /** MIDI Start (0xFA): hard phase reset and start. */
    private fun onExternalTransportStart() {
        if (_state.value.midiState.syncSource != SyncSource.EXTERNAL) return
        _state.update {
            it.copy(isPlaying = true, midiState = it.midiState.copy(isClockLocked = true))
        }
        applySequencerState()
        resetExternalClockPhase()
    }

    /** MIDI Continue (0xFB): resume on the current grid. */
    private fun onExternalTransportContinue() {
        if (_state.value.midiState.syncSource != SyncSource.EXTERNAL) return
        _state.update {
            it.copy(isPlaying = true, midiState = it.midiState.copy(isClockLocked = true))
        }
        applySequencerState()
    }

    /** MIDI Stop (0xFC) or clock watchdog timeout: stop transport. */
    private fun onExternalTransportStop() {
        if (_state.value.midiState.syncSource != SyncSource.EXTERNAL) return
        synchronized(clockLock) { cancelScheduledStepLocked() }
        noteRouter.allNotesOff(_state.value.midiState.channel)
        _state.update {
            it.copy(isPlaying = false, midiState = it.midiState.copy(isClockLocked = false))
        }
        applySequencerState()
    }

    private fun cancelScheduledStepLocked() {
        scheduledBoundary = -1.0
        stepScheduler.removeCallbacks(fireStep)
    }

    /**
     * Called synchronously on the MIDI thread for every 0xF8 clock tick (24 ppqn).
     *
     * Step boundaries live on an ABSOLUTE tick grid anchored at MIDI Start
     * (boundary = n * ticksPerStep; quarter = 24 ticks, eighth = 12, ...), so
     * every figure shares the same grid and changing figure mid-play stays on
     * the DAW's beat. The counter advances even while stopped, unlocked or in
     * pointer mode so the anchor survives and resuming rejoins the beat.
     *
     * Ticks feed a delay-locked loop (smoothed period over a 25-tick window +
     * gently corrected phase), and the next boundary's BANG is *scheduled* at
     * its predicted wall-clock time rather than fired on raw tick arrival —
     * batched/jittered tick delivery no longer lands steps off the grid.
     * The BANG echoes back through the app\'s "BANG" subscription, so sequence
     * stepping and MIDI note-out follow the exact same path as the internal metro.
     *
     * Firing is gated on syncSource == EXTERNAL and isPlaying. isClockLocked is
     * also set immediately on the MIDI thread so the internal metro is silenced
     * before it can fire an extra step, and so the first tick can start a DAW
     * that was already playing (no Start/Continue message) or one that just resumed.
     */
    private fun onExternalClockTick() {
        val nowMs = SystemClock.uptimeMillis()
        var state = _state.value

        // Lock to the first external tick to avoid a race with the isConnected
        // StateFlow (which runs on the main thread and may arrive too late).
        if (state.midiState.syncSource == SyncSource.EXTERNAL &&
            (!state.isPlaying || !state.midiState.isClockLocked) &&
            state.selectionMode != PixelSelectionMode.POINTER
        ) {
            _state.update {
                it.copy(
                    isPlaying = true,
                    midiState = it.midiState.copy(isClockLocked = true)
                )
            }
            applySequencerState()
            state = _state.value
        }

        synchronized(clockLock) {
            val tick = externalTickCount++

            // --- clock recovery ---
            tickWindow.addLast(tick to nowMs)
            if (tickWindow.size > 25) tickWindow.removeFirst()
            val (firstTick, firstTime) = tickWindow.first()
            if (tick > firstTick && nowMs > firstTime) {
                tickPeriodMs = (nowMs - firstTime).toDouble() / (tick - firstTick)
            }
            if (expectedTickTimeMs == 0.0) {
                expectedTickTimeMs = nowMs.toDouble()
            } else {
                expectedTickTimeMs += tickPeriodMs
                val err = nowMs - expectedTickTimeMs
                if (abs(err) > 4 * tickPeriodMs) {
                    // Tempo jump or long stall: hard resync instead of chasing.
                    expectedTickTimeMs = nowMs.toDouble()
                    tickWindow.clear()
                    tickWindow.addLast(tick to nowMs)
                } else {
                    // Gentle pull toward measured arrivals filters delivery jitter.
                    expectedTickTimeMs += 0.15 * err
                }
            }

            if (state.midiState.syncSource != SyncSource.EXTERNAL ||
                !state.isPlaying ||
                state.selectionMode == PixelSelectionMode.POINTER
            ) {
                cancelScheduledStepLocked()
                return
            }

            // --- schedule the earliest unfired boundary on the shared grid ---
            val ticksPerStep = 96.0 / 2.0.pow(state.musicState.figureIndex)
            var boundary = (floor(lastFiredBoundary / ticksPerStep).toLong() + 1) * ticksPerStep
            // Don't replay boundaries missed during stalls or gate-offs — jump
            // to the grid position at the current tick.
            val currentGridPos = floor(tick / ticksPerStep) * ticksPerStep
            if (boundary < currentGridPos) boundary = currentGridPos
            if (boundary <= lastFiredBoundary) boundary += ticksPerStep

            val predictedMs = expectedTickTimeMs + (boundary - tick) * tickPeriodMs
            scheduledBoundary = boundary
            stepScheduler.removeCallbacks(fireStep)
            stepScheduler.postAtTime(fireStep, predictedMs.toLong().coerceAtLeast(nowMs))
        }
    }

    private fun fireScheduledStep() {
        synchronized(clockLock) {
            if (scheduledBoundary < 0) return
            lastFiredBoundary = scheduledBoundary
            scheduledBoundary = -1.0
        }
        val state = _state.value
        if (state.midiState.syncSource != SyncSource.EXTERNAL ||
            !state.isPlaying ||
            state.selectionMode == PixelSelectionMode.POINTER
        ) return
        pdEngine.triggerBang()
    }

    /**
     * Sends a note-on for the given pixel and lets [NoteRouter] schedule the
     * matching note-off after [gateLength] of the current step duration.
     * Gate = 1.0 means no scheduled off (legato: the next note-on releases the
     * previous one); smaller values shorten the note for detached/staccato
     * articulation. Pointer mode keeps the note held while the finger is down.
     */
    private fun playNote(pixel: PixelData, stepPeriodMs: Double) {
        val state = _state.value
        if (!state.midiState.enabled ||
            state.midiState.outputMode == MidiOutputMode.INTERNAL
        ) return

        val music = state.musicState
        val freq = MusicConstants.calculateFrequency(
            pixel.hue,
            music.scaleIndex,
            music.keyIndex,
            music.octaveIndex,
            music.chordTypeIndex,
            music.useChordMapping
        )
        val pitch = freqToMidiPitch(freq)
        val velocity = (pixel.brightness * 127).toInt().coerceIn(1, 127)
        val channel = state.midiState.channel

        val durationMs = if (state.selectionMode == PixelSelectionMode.POINTER) {
            -1L
        } else {
            val gate = state.synthState.gateLength.coerceIn(0f, 1f)
            if (gate < 0.99f && stepPeriodMs > 0) {
                (stepPeriodMs * gate).toLong().coerceAtLeast(4L)
            } else {
                -1L
            }
        }

        noteRouter.noteOn(pitch, velocity, channel, durationMs)
    }

    private fun currentStepPeriodMs(): Double {
        val music = _state.value.musicState
        return music.periodTempo.toDouble() / 2.0.pow((music.figureIndex - 2).toDouble())
    }

    private fun freqToMidiPitch(freq: Float): Int =
        Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2.0)).toInt().coerceIn(0, 127)

    private fun incrementSequenceIndex() {
        _state.update { state ->
            val maxIndex = state.pixelGrid?.pixels?.size ?: 1
            state.copy(sequenceIndex = (state.sequenceIndex + 1) % maxIndex)
        }
        val state = _state.value
        state.selectedPixel?.let { pixel ->
            playNote(pixel, currentStepPeriodMs())
        }
    }
    
    fun setSelectionMode(mode: PixelSelectionMode) {
        // The sequencer stops stepping in pointer mode, so whatever note the
        // last step left sounding would never get its note-off.
        if (mode == PixelSelectionMode.POINTER) {
            noteRouter.allNotesOff(_state.value.midiState.channel)
        }
        _state.update { it.copy(selectionMode = mode) }

        val newBlockSize = _state.value.customGridResolution ?: defaultBlockSizeFor(mode)
        _state.update { it.copy(blockSize = newBlockSize) }

        // Update sequencer state — the internal metro stays off while an
        // external MIDI clock is driving the steps.
        applySequencerState()
    }

    /**
     * onSEQ selects the bang-triggered envelope path in the patch and must stay
     * on whenever steps are bang-driven — by the internal metro or by external
     * MIDI ticks. metroSEQ only gates the internal metro, which yields to the
     * external clock while locked. The transport play/stop button gates the
     * whole sequencer, including externally clocked steps.
     */
    private fun applySequencerState() {
        val state = _state.value
        val bangDriven = state.isPlaying && state.selectionMode != PixelSelectionMode.POINTER
        pdEngine.setSequencerOn(bangDriven)
        pdEngine.setMetroOn(bangDriven && !state.midiState.isClockLocked)
    }

    private fun setTransportPlaying(playing: Boolean) {
        if (!playing) {
            synchronized(clockLock) { cancelScheduledStepLocked() }
            noteRouter.allNotesOff(_state.value.midiState.channel)
        }
        // The external tick counter keeps running while stopped, so resuming
        // under external clock rejoins the DAW's grid — no phase reset needed.
        _state.update { it.copy(isPlaying = playing) }
        applySequencerState()
    }

    fun setPlaying(playing: Boolean) {
        val state = _state.value
        // While an external clock is actually driving the sequencer, the DAW
        // transport Start/Stop/Continue messages (or a watchdog timeout) rule.
        if (state.midiState.syncSource == SyncSource.EXTERNAL &&
            state.midiState.isClockLocked
        ) return

        setTransportPlaying(playing)
    }

    private fun defaultBlockSizeFor(mode: PixelSelectionMode): Int = when (mode) {
        PixelSelectionMode.SEQUENCE -> 60
        PixelSelectionMode.BRIGHTEST -> 50
        PixelSelectionMode.CENTER -> 60
        PixelSelectionMode.POINTER -> 50
    }

    fun setGridResolution(resolution: Int) {
        val coerced = resolution.coerceIn(MIN_GRID_RESOLUTION, MAX_GRID_RESOLUTION)
        _state.update { it.copy(customGridResolution = coerced, blockSize = coerced) }
    }

    fun resetGridResolution() {
        _state.update {
            it.copy(
                customGridResolution = null,
                blockSize = defaultBlockSizeFor(it.selectionMode)
            )
        }
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

        when (_state.value.selectionMode) {
            PixelSelectionMode.POINTER -> {
                pdEngine.setNoteOn(isTouching)

                // Finger lifted: release the sounding MIDI note. Pd has its own
                // NoteOn gate, but external destinations only ever saw note-ons.
                if (!isTouching && prevState.isTouching) {
                    noteRouter.allNotesOff(_state.value.midiState.channel)
                }

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
                            // Publish the touched pixel synchronously: the BANG
                            // echo fires the MIDI note from selectedPixel, which
                            // the camera pipeline hasn't refreshed yet on the
                            // first touch (it was still null, so no note fired
                            // until the finger dragged to the next pixel).
                            val ipBefore = lastIp
                            val jpBefore = lastJp
                            newPixel?.let { pixel ->
                                _state.update { it.copy(selectedPixel = pixel) }
                                sendPixelToAudio(pixel)
                            }
                            // sendPixelToAudio already bangs when the position
                            // changed; only bang here if it didn't (e.g. re-touch
                            // of the same pixel), so each step is a single bang
                            // and a single MIDI note instead of two.
                            if (lastIp == ipBefore && lastJp == jpBefore) {
                                pdEngine.triggerBang()
                            }
                        }
                    } else {
                        pdEngine.triggerBang()
                    }
                }
            }
            PixelSelectionMode.SEQUENCE -> {
                handleSequenceTouch(prevState, x, y, isTouching, canvasWidth, canvasHeight)
            }
            else -> { /* Center / brightest are not interactive through the canvas */ }
        }
    }

    private fun handleSequenceTouch(
        prevState: TrencadisState,
        x: Float,
        y: Float,
        isTouching: Boolean,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        if (!prevState.isTouching && isTouching) {
            // Touch-down: remember where the gesture started and when.
            seqTouchStartX = x
            seqTouchStartY = y
            seqTouchStartTime = System.currentTimeMillis()
            seqSwipeFired = false
            return
        }

        if (prevState.isTouching && !isTouching) {
            // Touch-up: decide whether it was a tap or a swipe.
            val dx = x - seqTouchStartX
            val dy = y - seqTouchStartY
            val distance = kotlin.math.hypot(dx, dy)
            val duration = System.currentTimeMillis() - seqTouchStartTime
            val grid = _state.value.pixelGrid ?: return
            val width = if (canvasWidth > 0f) canvasWidth else _state.value.canvasWidth
            val height = if (canvasHeight > 0f) canvasHeight else _state.value.canvasHeight

            when {
                distance < SEQUENCE_TAP_DISTANCE && duration < SEQUENCE_TAP_TIMEOUT -> {
                    // Tap: jump the cursor to the pixel under the finger.
                    grid.getAtPosition(seqTouchStartX, seqTouchStartY, width, height)
                        ?.let { setSequenceCursor(grid, it) }
                }
                distance >= SEQUENCE_SWIPE_DISTANCE && !seqSwipeFired -> {
                    // Swipe: move the cursor one cell in the dominant direction.
                    val (deltaCol, deltaRow) = dominantGridDelta(dx, dy)
                    moveSequenceCursor(grid, deltaCol, deltaRow)
                }
            }
        }
    }

    private fun setSequenceCursor(grid: PixelGrid, pixel: PixelData) {
        val index = grid.pixels.indexOf(pixel)
        if (index >= 0) {
            _state.update { it.copy(sequenceIndex = index, selectedPixel = pixel) }
            playPixel(pixel)
        }
    }

    private fun moveSequenceCursor(grid: PixelGrid, deltaCol: Int, deltaRow: Int) {
        val currentPixel = _state.value.selectedPixel
            ?: grid.getAtPosition(seqTouchStartX, seqTouchStartY, _state.value.canvasWidth, _state.value.canvasHeight)
            ?: return
        val target = grid.getPixelAt(currentPixel.gridX + deltaCol, currentPixel.gridY + deltaRow)
            ?: return
        val index = grid.pixels.indexOf(target)
        if (index >= 0) {
            _state.update { it.copy(sequenceIndex = index, selectedPixel = target) }
            playPixel(target)
        }
    }

    private fun playPixel(pixel: PixelData) {
        sendPixelToAudio(pixel)
        pdEngine.triggerBang()

        // The BANG echo already routes the pixel through incrementSequenceIndex,
        // which sends the external MIDI note with the correct gate length.
        // Re-sending it here caused a duplicate note-on.
    }

    private fun dominantGridDelta(dx: Float, dy: Float): Pair<Int, Int> {
        return if (dx * dx > dy * dy) {
            Pair(if (dx > 0) 1 else -1, 0)
        } else {
            Pair(0, if (dy > 0) 1 else -1)
        }
    }

    // Music state setters
    fun setScale(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(scaleIndex = index, useChordMapping = false)) }
    }
    
    fun setKey(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(keyIndex = index)) }
    }
    
    fun setOctave(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(octaveIndex = index)) }
    }
    
    fun setFigure(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(figureIndex = index)) }
        applyMusicState(_state.value.musicState)
    }

    fun setChordType(index: Int) {
        _state.update { it.copy(musicState = it.musicState.copy(chordTypeIndex = index.coerceIn(0, MusicConstants.CHORD_TYPE_SHORT_NAMES.lastIndex), useChordMapping = true)) }
    }
    
    fun setTempo(bpm: Float) {
        val period = (60000f / bpm)
        val music = _state.value.musicState.copy(tempo = bpm, periodTempo = period)
        _state.update { it.copy(musicState = music) }
        applyMusicState(music)
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
            blobModulation = currentState.blobModulation,
            customGridResolution = currentState.customGridResolution,
            midiEnabled = currentState.midiState.enabled,
            midiOutputMode = currentState.midiState.outputMode,
            midiChannel = currentState.midiState.channel,
            bleEnabled = currentState.midiState.bleEnabled
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
                blobModulation = preset.blobModulation,
                customGridResolution = preset.customGridResolution,
                blockSize = preset.customGridResolution ?: defaultBlockSizeFor(preset.selectionMode),
                midiState = it.midiState.copy(
                    enabled = preset.midiEnabled,
                    outputMode = preset.midiOutputMode,
                    channel = preset.midiChannel,
                    bleEnabled = preset.bleEnabled
                )
            )
        }
        // Apply loaded state to audio engine
        applySynthState(preset.synthState)
        applyMusicState(preset.musicState)
        // Apply selection mode to sequencer
        setSelectionMode(preset.selectionMode)
        // Apply MIDI routing
        setMidiEnabled(preset.midiEnabled)
        if (preset.midiEnabled) {
            setMidiOutputMode(preset.midiOutputMode)
            setMidiChannel(preset.midiChannel)
            if (preset.bleEnabled) setBleEnabled(true)
        }
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
            // Release before removing — a destination that has already been
            // removed never receives its note-off and the note hangs.
            noteRouter.allNotesOff(_state.value.midiState.channel)
            noteRouter.remove(midiNoteDestination)
        }
    }

    fun setMidiOutputMode(mode: MidiOutputMode) {
        _state.update { it.copy(midiState = it.midiState.copy(outputMode = mode)) }
        pdNoteDestination.setActive(mode != MidiOutputMode.MIDI_OUT)
        when (mode) {
            MidiOutputMode.INTERNAL -> {
                noteRouter.allNotesOff(_state.value.midiState.channel)
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
            noteRouter.allNotesOff(_state.value.midiState.channel)
            noteRouter.remove(bleNoteDestination)
            blePeripheral.stopAdvertising()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stepSchedulerThread.quitSafely()
        releaseAudio()
        noteRouter.release()
    }
}
