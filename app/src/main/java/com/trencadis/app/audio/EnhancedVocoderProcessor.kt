package com.trencadis.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

/**
 * Enhanced vocoder processor for Trencadis 2.0
 * Integrates with existing PD vocoder patch and adds microphone input
 */
class EnhancedVocoderProcessor(
    private val pdAudioEngine: com.trencadis.app.audio.PdAudioEngine,
    private val coroutineScope: CoroutineScope
) {
    
    private val _vocoderState = MutableStateFlow(VocoderState())
    val vocoderState = _vocoderState.asStateFlow()
    
    // Microphone input
    private var audioRecord: AudioRecord? = null
    private var microphoneJob: Job? = null
    private var isMicrophoneActive = false
    
    // Audio buffer for microphone input
    private val bufferSize = 1024
    private val audioBuffer = ShortArray(bufferSize)
    
    // Vocoder instance ID for PD communication
    private var vocoderInstanceId = 0
    
    init {
        initializeVocoder()
    }
    
    /**
     * Enable or disable vocoder processing
     */
    fun enableVocoder(enabled: Boolean) {
        _vocoderState.value = _vocoderState.value.copy(enabled = enabled)
        
        // Send enable/disable to PD
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_enable", if (enabled) 1f else 0f)
        
        if (enabled) {
            startMicrophoneInput()
        } else {
            stopMicrophoneInput()
        }
    }
    
    /**
     * Set carrier level (synthesized audio from camera)
     */
    fun setCarrierLevel(level: Float) {
        _vocoderState.value = _vocoderState.value.copy(carrierLevel = level.coerceIn(0f, 1f))
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_carrier_level", level.coerceIn(0f, 1f))
    }
    
    /**
     * Set modulator level (microphone input)
     */
    fun setModulatorLevel(level: Float) {
        _vocoderState.value = _vocoderState.value.copy(modulatorLevel = level.coerceIn(0f, 1f))
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_modulator_level", level.coerceIn(0f, 1f))
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
            pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_band${bandIndex}_gain", gain.coerceIn(0f, 1f))
        }
    }
    
    /**
     * Set output mix (wet/dry)
     */
    fun setOutputMix(wetLevel: Float) {
        _vocoderState.value = _vocoderState.value.copy(outputMix = wetLevel.coerceIn(0f, 1f))
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_output_mix", wetLevel.coerceIn(0f, 1f))
    }
    
    /**
     * Set microphone input gain
     */
    fun setInputGain(gain: Float) {
        _vocoderState.value = _vocoderState.value.copy(inputGain = gain.coerceIn(0f, 2f))
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_input_gain", gain.coerceIn(0f, 2f))
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
        
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_carrier_source", sourceValue)
    }
    
    /**
     * Set vocoder "squelch" parameter (noise gate threshold)
     */
    fun setSquelch(squelch: Float) {
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_squelch", squelch.coerceIn(0f, 100f))
    }
    
    /**
     * Reset all vocoder parameters to defaults
     */
    fun resetToDefaults() {
        _vocoderState.value = VocoderState()
        
        // Send all default values to PD
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_enable", 0f)
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_carrier_level", 0.7f)
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_modulator_level", 0.5f)
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_output_mix", 0.5f)
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_input_gain", 0.8f)
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_carrier_source", 0f)
        pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_squelch", 30f)
        
        // Reset all band gains
        for (i in 0..7) {
            pdAudioEngine.sendFloat("vocoder${vocoderInstanceId}_band${i}_gain", 0.5f)
        }
    }
    
    /**
     * Initialize vocoder in PD patch
     */
    private fun initializeVocoder() {
        vocoderInstanceId = (0..999).random()
        
        // Initialize the e_vocoder patch with unique ID
        pdAudioEngine.sendBang("vocoder${vocoderInstanceId}_init")
        
        // Set default parameters
        resetToDefaults()
    }
    
    /**
     * Start microphone input for vocoder
     */
    private fun startMicrophoneInput() {
        if (isMicrophoneActive) return
        
        try {
            // Configure audio record for microphone input
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )
            
            audioRecord?.startRecording()
            isMicrophoneActive = true
            
            // Start microphone processing coroutine
            microphoneJob = coroutineScope.launch(Dispatchers.IO) {
                processMicrophoneInput()
            }
            
        } catch (e: Exception) {
            // Handle microphone initialization error
            _vocoderState.value = _vocoderState.value.copy(
                enabled = false
            )
        }
    }
    
    /**
     * Stop microphone input
     */
    private fun stopMicrophoneInput() {
        isMicrophoneActive = false
        microphoneJob?.cancel()
        microphoneJob = null
        
        audioRecord?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder.release()
        }
        audioRecord = null
    }
    
    /**
     * Process microphone audio input continuously
     */
    private suspend fun processMicrophoneInput() {
        val recorder = audioRecord ?: return
        
        while (currentCoroutineContext().isActive && isMicrophoneActive) {
            try {
                // Read audio data from microphone
                val bytesRead = recorder.read(audioBuffer, 0, bufferSize)
                
                if (bytesRead > 0 && _vocoderState.value.enabled) {
                    // Convert from 16-bit PCM to float
                    val floatBuffer = convertShortToFloat(audioBuffer.copyOfRange(0, bytesRead))
                    
                    // Send microphone audio to PD vocoder modulator input
                    sendMicrophoneAudioToPD(floatBuffer)
                }
                
                // Small delay to prevent overwhelming the system
                delay(10)
                
            } catch (e: Exception) {
                // Handle audio processing error
                delay(100) // Wait before retrying
            }
        }
    }
    
    /**
     * Send microphone audio to PD vocoder modulator
     */
    private fun sendMicrophoneAudioToPD(audioBuffer: FloatArray) {
        // Send audio buffer to PD patch
        
        // Apply input gain
        val gain = _vocoderState.value.inputGain
        val processedBuffer = FloatArray(audioBuffer.size)
        for (i in audioBuffer.indices) {
            processedBuffer[i] = audioBuffer[i] * gain
        }
        
        // Send to PD
        pdAudioEngine.sendAudioBuffer("vocoder${vocoderInstanceId}_modulator", processedBuffer)
    }
    
    /**
     * Convert short array to float array
     */
    private fun convertShortToFloat(shortArray: ShortArray): FloatArray {
        val floatArray = FloatArray(shortArray.size)
        for (i in shortArray.indices) {
            floatArray[i] = shortArray[i].toFloat() / 32768f
        }
        return floatArray
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
        
        // Load squelch parameter
        parameters["vocoder_squelch"]?.let { 
            setSquelch(it) 
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopMicrophoneInput()
        resetToDefaults()
    }
}
