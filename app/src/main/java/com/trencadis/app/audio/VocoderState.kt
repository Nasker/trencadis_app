package com.trencadis.app.audio

/**
 * Vocoder processing state for Trencadis 2.0
 */
data class VocoderState(
    val enabled: Boolean = false,
    val carrierLevel: Float = 0.7f,        // Synthesized audio from camera
    val modulatorLevel: Float = 0.5f,      // Microphone input
    val bandGains: FloatArray = FloatArray(8) { 0.5f }, // 8-band vocoder
    val outputMix: Float = 0.5f,            // Wet/Dry mix (0 = dry, 1 = wet)
    val inputGain: Float = 0.8f,            // Microphone input gain
    val carrierSource: CarrierSource = CarrierSource.SYNTHESIZER
)

enum class CarrierSource {
    SYNTHESIZER,    // Camera-driven synthesis
    EXTERNAL,       // External audio input
    NOISE           // Noise generator
}
