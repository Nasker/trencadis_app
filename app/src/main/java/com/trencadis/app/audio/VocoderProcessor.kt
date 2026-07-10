package com.trencadis.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vocoder processor for Trencadis 2.0
 * Integrates with existing PD vocoder patch
 */
class VocoderProcessor(private val pdAudioEngine: com.trencadis.app.audio.PdAudioEngine) {
    
    private val _vocoderState = MutableStateFlow(VocoderState())
    val vocoderState = _vocoderState.asStateFlow()
    
    init {
        // Initialize vocoder in PD patch
        initializeVocoder()
    }
    
    /**
     * Enable or disable vocoder processing
     */
    fun enableVocoder(enabled: Boolean) {
        _vocoderState.value = _vocoderState.value.copy(enabled = enabled)
        
        // Send enable/disable message to PD
        pdAudioEngine.sendFloat("vocoder_enable", if (enabled) 1f else 0f)
        
        if (enabled) {
            // Start microphone input if needed
            startMicrophoneInput()
        } else {
            // Stop microphone input
            stopMicrophoneInput()
        }
    }
    
    /**
     * Set carrier level (synthesized audio from camera)
     */
    fun setCarrierLevel(level: Float) {
        _vocoderState.value = _vocoderState.value.copy(carrierLevel = level.coerceIn(0f, 1f))
        pdAudioEngine.sendFloat("vocoder_carrier_level", level.coerceIn(0f, 1f))
    }
    
    /**
     * Set modulator level (microphone input)
     */
    fun setModulatorLevel(level: Float) {
        _vocoderState.value = _vocoderState.value.copy(modulatorLevel = level.coerceIn(0f, 1f))
        pdAudioEngine.sendFloat("vocoder_modulator_level", level.coerceIn(0f, 1f))
    }
    
    /**
     * Set individual band gain for 8-band vocoder
     */
    fun setBandGain(bandIndex: Int, gain: Float) {
        if (bandIndex in 0..7) {
            val newBandGains = _vocoderState.value.bandGains.clone()
            newBandGains[bandIndex] = gain.coerceIn(0f, 1f)
            _vocoderState.value = _vocoderState.value.copy(bandGains = newBandGains)
            
            // Send to PD vocoder band
            pdAudioEngine.sendFloat("vocoder_band${bandIndex}_gain", gain.coerceIn(0f, 1f))
        }
    }
    
    /**
     * Set output mix (wet/dry)
     */
    fun setOutputMix(wetLevel: Float) {
        _vocoderState.value = _vocoderState.value.copy(outputMix = wetLevel.coerceIn(0f, 1f))
        pdAudioEngine.sendFloat("vocoder_output_mix", wetLevel.coerceIn(0f, 1f))
    }
    
    /**
     * Set microphone input gain
     */
    fun setInputGain(gain: Float) {
        _vocoderState.value = _vocoderState.value.copy(inputGain = gain.coerceIn(0f, 2f))
        pdAudioEngine.sendFloat("vocoder_input_gain", gain.coerceIn(0f, 2f))
    }
    
    /**
     * Set carrier source type
     */
    fun setCarrierSource(source: CarrierSource) {
        _vocoderState.value = _vocoderState.value.copy(carrierSource = source)
        
        val sourceValue = when (source) {
            CarrierSource.SYNTHESIZER -> 0f
            CarrierSource.EXTERNAL -> 1f
            CarrierSource.NOISE -> 2f
        }
        
        pdAudioEngine.sendFloat("vocoder_carrier_source", sourceValue)
    }
    
    /**
     * Reset all vocoder parameters to defaults
     */
    fun resetToDefaults() {
        _vocoderState.value = VocoderState()
        
        // Send all default values to PD
        pdAudioEngine.sendFloat("vocoder_enable", 0f)
        pdAudioEngine.sendFloat("vocoder_carrier_level", 0.7f)
        pdAudioEngine.sendFloat("vocoder_modulator_level", 0.5f)
        pdAudioEngine.sendFloat("vocoder_output_mix", 0.5f)
        pdAudioEngine.sendFloat("vocoder_input_gain", 0.8f)
        pdAudioEngine.sendFloat("vocoder_carrier_source", 0f)
        
        // Reset all band gains
        for (i in 0..7) {
            pdAudioEngine.sendFloat("vocoder_band${i}_gain", 0.5f)
        }
    }
    
    /**
     * Initialize vocoder in PD patch
     */
    private fun initializeVocoder() {
        // Send initialization messages to PD
        pdAudioEngine.sendBang("vocoder_init")
        resetToDefaults()
    }
    
    /**
     * Start microphone input for vocoder
     */
    private fun startMicrophoneInput() {
        // TODO: Implement microphone input initialization
        // This would involve Android AudioRecord for microphone capture
        // and routing the audio to the PD vocoder modulator input
    }
    
    /**
     * Stop microphone input
     */
    private fun stopMicrophoneInput() {
        // TODO: Implement microphone input cleanup
    }
    
    /**
     * Process microphone audio buffer (called from audio thread)
     */
    fun processMicrophoneInput(audioBuffer: FloatArray) {
        if (_vocoderState.value.enabled) {
            // Send microphone audio to PD vocoder modulator
            // This would typically be done via PD's audio input system
            // For now, we'll assume the PD patch handles this
        }
    }
    
    /**
     * Get current vocoder parameters as a map for preset saving
     */
    fun getParameters(): Map<String, Float> {
        val state = _vocoderState.value
        return mapOf(
            "vocoder_enabled" to if (state.enabled) 1f else 0f,
            "vocoder_carrier_level" to state.carrierLevel,
            "vocoder_modulator_level" to state.modulatorLevel,
            "vocoder_output_mix" to state.outputMix,
            "vocoder_input_gain" to state.inputGain,
            "vocoder_carrier_source" to when (state.carrierSource) {
                CarrierSource.SYNTHESIZER -> 0f
                CarrierSource.EXTERNAL -> 1f
                CarrierSource.NOISE -> 2f
            }
        ).plus(state.bandGains.mapIndexed { index, gain ->
            "vocoder_band${index}_gain" to gain
        })
    }
    
    /**
     * Load vocoder parameters from map
     */
    fun loadParameters(parameters: Map<String, Float>) {
        parameters["vocoder_enabled"]?.let { 
            enableVocoder(it > 0.5f) 
        }
        parameters["vocoder_carrier_level"]?.let { 
            setCarrierLevel(it) 
        }
        parameters["vocoder_modulator_level"]?.let { 
            setModulatorLevel(it) 
        }
        parameters["vocoder_output_mix"]?.let { 
            setOutputMix(it) 
        }
        parameters["vocoder_input_gain"]?.let { 
            setInputGain(it) 
        }
        parameters["vocoder_carrier_source"]?.let { 
            val source = when (it.toInt()) {
                0 -> CarrierSource.SYNTHESIZER
                1 -> CarrierSource.EXTERNAL
                2 -> CarrierSource.NOISE
                else -> CarrierSource.SYNTHESIZER
            }
            setCarrierSource(source)
        }
        
        // Load band gains
        for (i in 0..7) {
            parameters["vocoder_band${i}_gain"]?.let { 
                setBandGain(i, it) 
            }
        }
    }
}
