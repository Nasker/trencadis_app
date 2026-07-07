package com.example.trencadisapp.audio

import android.content.Context
import android.util.Log
import org.puredata.android.io.AudioParameters
import org.puredata.android.io.PdAudio
import org.puredata.core.PdBase
import org.puredata.core.PdReceiver
import org.puredata.core.utils.IoUtils
import java.io.File
import java.io.IOException

class PdAudioEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "PdAudioEngine"
        private const val SAMPLE_RATE = 44100
    }
    
    private var patchHandle: Int = -1
    private var isInitialized = false
    private var onBangReceived: (() -> Unit)? = null
    private var onEnvelopeReceived: ((Float) -> Unit)? = null

    fun setOnBangReceived(callback: () -> Unit) {
        onBangReceived = callback
    }

    fun setOnEnvelopeReceived(callback: (Float) -> Unit) {
        onEnvelopeReceived = callback
    }
    
    fun initialize(): Boolean {
        try {
            // Initialize audio parameters
            val sampleRate = AudioParameters.suggestSampleRate()
            val inputChannels = 0  // No input needed
            val outputChannels = AudioParameters.suggestOutputChannels()
            
            // Initialize PdAudio
            PdAudio.initAudio(sampleRate, inputChannels, outputChannels, 8, true)
            
            // Set up receiver for bangs from PD
            PdBase.setReceiver(object : PdReceiver {
                override fun receiveBang(source: String) {
                    if (source == "BANG") {
                        onBangReceived?.invoke()
                    }
                }
                override fun receiveFloat(source: String, x: Float) {
                    if (source == "ENVF") {
                        onEnvelopeReceived?.invoke(x)
                    }
                }
                override fun receiveSymbol(source: String, symbol: String) {}
                override fun receiveList(source: String, args: Array<out Any>?) {}
                override fun receiveMessage(source: String, symbol: String, args: Array<out Any>?) {}
                override fun print(s: String) {
                    Log.d(TAG, "PD: $s")
                }
            })
            
            // Subscribe to BANG messages and the envelope-follower stream
            PdBase.subscribe("BANG")
            PdBase.subscribe("ENVF")
            
            // Copy and open the patch
            val patchDir = copyPatchesToFilesDir()
            val patchFile = File(patchDir, "STEPPEDPIX.pd")
            
            if (!patchFile.exists()) {
                Log.e(TAG, "Patch file not found: ${patchFile.absolutePath}")
                return false
            }
            
            patchHandle = PdBase.openPatch(patchFile)
            
            // Start audio
            PdAudio.startAudio(context)
            
            // Initialize default values
            sendFloat("onSEQ", 1f)
            sendFloat("periodSEQ", 500f)
            sendFloat("Sub", 1f)
            sendFloat("Sin", 1f)
            sendFloat("Saw", 0f)
            sendFloat("Sqr", 0f)
            sendFloat("Noi", 0f)
            sendFloat("Rsend", 0.2f)
            
            isInitialized = true
            Log.d(TAG, "PD Audio Engine initialized successfully")
            return true
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize PD Audio Engine", e)
            return false
        }
    }
    
    private fun copyPatchesToFilesDir(): File {
        val patchDir = File(context.filesDir, "patch")
        if (!patchDir.exists()) {
            patchDir.mkdirs()
        }
        
        // List of all patch files to copy
        val patchFiles = listOf(
            "STEPPEDPIX.pd",
            "c_adsr.pd",
            "c_ead.pd",
            "c_xfade.pd",
            "e_beequad.pd",
            "e_chorus.pd",
            "e_dubdel.pd",
            "e_lop2.pd",
            "e_vocoder.pd",
            "tapedelay.pd",
            "tapedelaysimple.pd",
            "u_bandpass1.pd",
            "u_dispatch.pd",
            "u_loader.pd",
            "u_lowpassq.pd",
            "u_sssad.pd",
            "x_bandpass.pd"
        )
        
        // Always overwrite: bundled patches evolve with the app, and a stale
        // copy in filesDir would silently shadow the updated asset.
        for (fileName in patchFiles) {
            val destFile = File(patchDir, fileName)
            try {
                context.assets.open("patch/$fileName").use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Could not copy patch file: $fileName", e)
            }
        }
        
        return patchDir
    }
    
    fun sendFloat(receiver: String, value: Float) {
        if (isInitialized) {
            PdBase.sendFloat(receiver, value)
        }
    }
    
    fun sendBang(receiver: String) {
        if (isInitialized) {
            PdBase.sendBang(receiver)
        }
    }
    
    // Synth parameter methods
    fun setFrequency(freq: Float) = sendFloat("Freq", freq)
    fun setGain(gain: Float) = sendFloat("Gain", gain)
    fun setX(x: Float) = sendFloat("X", x)
    fun setY(y: Float) = sendFloat("Y", y)
    
    fun setCutoff(cutoff: Float) = sendFloat("Cutoff", cutoff)
    fun setResonance(resonance: Float) = sendFloat("Resonance", resonance)
    fun setEnvelope(envelope: Float) = sendFloat("Envelope", envelope)
    fun setAttack(attack: Float) = sendFloat("Attack", attack)
    fun setRelease(release: Float) = sendFloat("Release", release)
    fun setDistortion(dist: Float) = sendFloat("Dist", dist)
    
    fun setFM(fm: Float) = sendFloat("FM", fm)
    fun setAmountFM(amount: Float) = sendFloat("amountFM", amount)
    fun setChorusFreq(freq: Float) = sendFloat("freqChor", freq)
    fun setChorusMod(mod: Float) = sendFloat("modChor", mod)
    fun setDelayTime(time: Float) = sendFloat("Tdelay", time)
    fun setFeedback(feedback: Float) = sendFloat("Lfeedback", feedback)
    fun setReverbSend(send: Float) = sendFloat("Rsend", send)
    
    fun setOscillatorSub(on: Boolean) = sendFloat("Sub", if (on) 1f else 0f)
    fun setOscillatorSin(on: Boolean) = sendFloat("Sin", if (on) 1f else 0f)
    fun setOscillatorSaw(on: Boolean) = sendFloat("Saw", if (on) 1f else 0f)
    fun setOscillatorSqr(on: Boolean) = sendFloat("Sqr", if (on) 1f else 0f)
    fun setOscillatorNoise(on: Boolean) = sendFloat("Noi", if (on) 1f else 0f)
    
    fun setSequencerOn(on: Boolean) = sendFloat("onSEQ", if (on) 1f else 0f)
    fun setMetroOn(on: Boolean) = sendFloat("metroSEQ", if (on) 1f else 0f)
    fun setSequencerPeriod(period: Float) = sendFloat("periodSEQ", period)
    fun setBPDFreq(freq: Float) = sendFloat("BPDFreq", freq)
    
    fun setNoteOn(on: Boolean) = sendFloat("NoteOn", if (on) 1f else 0f)
    fun triggerBang() = sendBang("BANG")
    
    fun release() {
        if (isInitialized) {
            PdAudio.stopAudio()
            if (patchHandle >= 0) {
                PdBase.closePatch(patchHandle)
            }
            PdAudio.release()
            isInitialized = false
        }
    }
}
