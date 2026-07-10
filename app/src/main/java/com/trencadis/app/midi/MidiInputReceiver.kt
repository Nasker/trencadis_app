package com.trencadis.app.midi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MIDI input receiver for Trencadis 2.0
 * Handles incoming MIDI messages and chord detection
 */
class MidiInputReceiver {
    
    private val _midiInputState = MutableStateFlow(MidiInputState())
    val midiInputState = _midiInputState.asStateFlow()
    
    private val chordDetector = ChordDetector()
    private val arpeggiatorEngine = ArpeggiatorEngine()
    
    // MIDI device management
    private var isInputEnabled = false
    
    /**
     * Start MIDI input processing
     */
    fun startMidiInput(deviceName: String? = null) {
        isInputEnabled = true
        _midiInputState.value = _midiInputState.value.copy(
            enabled = true,
            inputDevice = deviceName
        )
        
        // TODO: Initialize actual MIDI device connection
        // This would use Android MIDI API to open MIDI input device
    }
    
    /**
     * Stop MIDI input processing
     */
    fun stopMidiInput() {
        isInputEnabled = false
        _midiInputState.value = _midiInputState.value.copy(
            enabled = false,
            activeNotes = emptySet(),
            detectedChord = null
        )
        
        arpeggiatorEngine.stopArpeggio()
        
        // TODO: Close MIDI device connection
    }
    
    /**
     * Handle MIDI Note On message
     */
    fun handleMidiNoteOn(note: Int, velocity: Int, channel: Int = 0) {
        if (!isInputEnabled) return
        
        val currentActiveNotes = _midiInputState.value.activeNotes.toMutableSet()
        currentActiveNotes.add(note)
        
        _midiInputState.value = _midiInputState.value.copy(
            activeNotes = currentActiveNotes
        )
        
        // Detect chord from active notes
        val detectedChord = chordDetector.analyzeChord(currentActiveNotes)
        _midiInputState.value = _midiInputState.value.copy(
            detectedChord = detectedChord
        )
        
        // Handle based on harmony mode
        when (_midiInputState.value.harmonyMode) {
            HarmonyMode.EXTERNAL_CHORD -> {
                if (detectedChord != null) {
                    arpeggiatorEngine.startArpeggio(detectedChord)
                }
            }
            HarmonyMode.HYBRID -> {
                // Blend with internal scale
                if (detectedChord != null) {
                    arpeggiatorEngine.startArpeggio(detectedChord)
                }
            }
            HarmonyMode.INTERNAL_SCALES -> {
                // Use internal scale, but still track MIDI for reference
            }
        }
    }
    
    /**
     * Handle MIDI Note Off message
     */
    fun handleMidiNoteOff(note: Int, channel: Int = 0) {
        if (!isInputEnabled) return
        
        val currentActiveNotes = _midiInputState.value.activeNotes.toMutableSet()
        currentActiveNotes.remove(note)
        
        _midiInputState.value = _midiInputState.value.copy(
            activeNotes = currentActiveNotes
        )
        
        // Re-analyze chord with remaining notes
        val detectedChord = if (currentActiveNotes.isNotEmpty()) {
            chordDetector.analyzeChord(currentActiveNotes)
        } else {
            // No notes left, stop arpeggio
            arpeggiatorEngine.stopArpeggio()
            null
        }
        
        _midiInputState.value = _midiInputState.value.copy(
            detectedChord = detectedChord
        )
    }
    
    /**
     * Handle MIDI Control Change message
     */
    fun handleMidiCC(cc: Int, value: Int, channel: Int = 0) {
        if (!isInputEnabled) return
        
        // Handle MIDI CC for various parameters
        when (cc) {
            // Modulation wheel
            1 -> {
                // Could control filter cutoff or LFO amount
            }
            // Expression pedal
            11 -> {
                // Could control volume or expression
            }
            // Sustain pedal
            64 -> {
                if (value >= 64) {
                    arpeggiatorEngine.setHoldNotes(true)
                } else {
                    arpeggiatorEngine.setHoldNotes(false)
                }
            }
        }
    }
    
    /**
     * Set harmony mode
     */
    fun setHarmonyMode(mode: HarmonyMode) {
        _midiInputState.value = _midiInputState.value.copy(harmonyMode = mode)
        
        // Restart arpeggio if needed
        val currentChord = _midiInputState.value.detectedChord
        if (mode != HarmonyMode.INTERNAL_SCALES && currentChord != null) {
            arpeggiatorEngine.startArpeggio(currentChord)
        } else {
            arpeggiatorEngine.stopArpeggio()
        }
    }
    
    /**
     * Set arpeggio pattern
     */
    fun setArpeggioPattern(pattern: ArpeggioPattern) {
        _midiInputState.value = _midiInputState.value.copy(
            arpeggioPattern = pattern,
            arpeggioState = _midiInputState.value.arpeggioState.copy(pattern = pattern)
        )
        arpeggiatorEngine.setPattern(pattern)
    }
    
    /**
     * Set arpeggio tempo
     */
    fun setArpeggioTempo(tempo: Float) {
        val validTempo = tempo.coerceIn(40f, 300f)
        _midiInputState.value = _midiInputState.value.copy(
            arpeggioState = _midiInputState.value.arpeggioState.copy(tempo = validTempo)
        )
        arpeggiatorEngine.setTempo(validTempo)
    }
    
    /**
     * Set arpeggio gate length
     */
    fun setArpeggioGateLength(gateLength: Float) {
        val validGate = gateLength.coerceIn(0.1f, 0.9f)
        _midiInputState.value = _midiInputState.value.copy(
            arpeggioState = _midiInputState.value.arpeggioState.copy(gateLength = validGate)
        )
        arpeggiatorEngine.setGateLength(validGate)
    }
    
    /**
     * Get current arpeggiator state
     */
    fun getArpeggiatorState(): ArpeggioState {
        return _midiInputState.value.arpeggioState
    }
    
    /**
     * Get MIDI input parameters for preset saving
     */
    fun getParameters(): Map<String, Any> {
        val state = _midiInputState.value
        return mapOf(
            "midi_input_enabled" to state.enabled,
            "harmony_mode" to state.harmonyMode.name,
            "arpeggio_pattern" to state.arpeggioPattern.name,
            "arpeggio_tempo" to state.arpeggioState.tempo,
            "arpeggio_gate_length" to state.arpeggioState.gateLength,
            "arpeggio_octave_range" to state.arpeggioState.octaveRange,
            "arpeggio_hold_notes" to state.arpeggioState.holdNotes
        )
    }
    
    /**
     * Load MIDI input parameters from map
     */
    fun loadParameters(parameters: Map<String, Any>) {
        parameters["midi_input_enabled"]?.let { 
            if (it as Boolean) {
                startMidiInput()
            } else {
                stopMidiInput()
            }
        }
        
        (parameters["harmony_mode"] as? String)?.let { modeName ->
            try {
                val mode = HarmonyMode.valueOf(modeName)
                setHarmonyMode(mode)
            } catch (e: IllegalArgumentException) {
                // Invalid mode, ignore
            }
        }
        
        (parameters["arpeggio_pattern"] as? String)?.let { patternName ->
            try {
                val pattern = ArpeggioPattern.valueOf(patternName)
                setArpeggioPattern(pattern)
            } catch (e: IllegalArgumentException) {
                // Invalid pattern, ignore
            }
        }
        
        parameters["arpeggio_tempo"]?.let { 
            setArpeggioTempo(it as Float) 
        }
        
        parameters["arpeggio_gate_length"]?.let { 
            setArpeggioGateLength(it as Float) 
        }
        
        parameters["arpeggio_octave_range"]?.let { 
            arpeggiatorEngine.setOctaveRange(it as Int) 
        }
        
        parameters["arpeggio_hold_notes"]?.let { 
            arpeggiatorEngine.setHoldNotes(it as Boolean) 
        }
    }
}

/**
 * Placeholder for chord detector implementation
 */
class ChordDetector {
    fun analyzeChord(notes: Set<Int>): Chord? {
        if (notes.isEmpty()) return null
        if (notes.size == 1) return null // Single note is not a chord
        
        // Sort notes for analysis
        val sortedNotes = notes.sorted()
        val root = sortedNotes.first()
        
        // Calculate intervals from root
        val intervals = sortedNotes.map { it - root }.toSet()
        
        // Determine chord type based on intervals
        val chordType = when (intervals) {
            setOf(0, 4, 7) -> ChordType.MAJOR
            setOf(0, 3, 7) -> ChordType.MINOR
            setOf(0, 4, 7, 11) -> ChordType.MAJOR_SEVENTH
            setOf(0, 3, 7, 10) -> ChordType.MINOR_SEVENTH
            setOf(0, 4, 7, 10) -> ChordType.DOMINANT_SEVENTH
            setOf(0, 5, 7) -> ChordType.SUS4
            setOf(0, 2, 7) -> ChordType.SUS2
            setOf(0, 7) -> ChordType.POWER_CHORD
            else -> ChordType.UNKNOWN
        }
        
        return Chord(
            root = root,
            type = chordType,
            notes = notes,
            inversion = 0 // TODO: Calculate inversion
        )
    }
}

/**
 * Placeholder for arpeggiator engine implementation
 */
class ArpeggiatorEngine {
    fun startArpeggio(chord: Chord) {
        // TODO: Implement arpeggio start
    }
    
    fun stopArpeggio() {
        // TODO: Implement arpeggio stop
    }
    
    fun setPattern(pattern: ArpeggioPattern) {
        // TODO: Implement pattern change
    }
    
    fun setTempo(tempo: Float) {
        // TODO: Implement tempo change
    }
    
    fun setGateLength(gateLength: Float) {
        // TODO: Implement gate length change
    }
    
    fun setOctaveRange(range: Int) {
        // TODO: Implement octave range change
    }
    
    fun setHoldNotes(hold: Boolean) {
        // TODO: Implement hold notes
    }
}
