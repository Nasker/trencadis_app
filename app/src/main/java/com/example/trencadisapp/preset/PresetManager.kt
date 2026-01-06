package com.example.trencadisapp.preset

import android.content.Context
import com.example.trencadisapp.SynthState
import com.example.trencadisapp.MusicState
import com.example.trencadisapp.camera.PixelSelectionMode
import com.example.trencadisapp.ui.AcidModulation
import org.json.JSONObject
import java.io.File

/**
 * Data class representing a complete preset with all saveable settings
 */
data class Preset(
    val name: String,
    val synthState: SynthState,
    val musicState: MusicState,
    val acidModulation: AcidModulation,
    val acidPatternIndex: Int,
    val selectionMode: PixelSelectionMode,
    val useFrontCamera: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("version", 1)
            
            // Synth state
            put("synth", JSONObject().apply {
                put("subOsc", synthState.subOsc)
                put("sinOsc", synthState.sinOsc)
                put("sawOsc", synthState.sawOsc)
                put("sqrOsc", synthState.sqrOsc)
                put("noiseOsc", synthState.noiseOsc)
                put("cutoff", synthState.cutoff.toDouble())
                put("resonance", synthState.resonance.toDouble())
                put("envelope", synthState.envelope.toDouble())
                put("attack", synthState.attack.toDouble())
                put("release", synthState.release.toDouble())
                put("distortion", synthState.distortion.toDouble())
                put("fm", synthState.fm.toDouble())
                put("fmAmount", synthState.fmAmount.toDouble())
                put("chorusFreq", synthState.chorusFreq.toDouble())
                put("chorusMod", synthState.chorusMod.toDouble())
                put("delayFigure", synthState.delayFigure.toDouble())
                put("feedback", synthState.feedback.toDouble())
            })
            
            // Music state
            put("music", JSONObject().apply {
                put("scaleIndex", musicState.scaleIndex)
                put("keyIndex", musicState.keyIndex)
                put("octaveIndex", musicState.octaveIndex)
                put("figureIndex", musicState.figureIndex)
                put("tempo", musicState.tempo.toDouble())
            })
            
            // Acid modulation
            put("acid", JSONObject().apply {
                put("enabled", acidModulation.enabled)
                put("multiShape", acidModulation.multiShape)
                put("hueAmount", acidModulation.hueAmount.toDouble())
                put("sizeAmount", acidModulation.sizeAmount.toDouble())
                put("rotationAmount", acidModulation.rotationAmount.toDouble())
                put("alphaAmount", acidModulation.alphaAmount.toDouble())
                put("animationSpeed", acidModulation.animationSpeed.toDouble())
            })
            
            put("acidPatternIndex", acidPatternIndex)
            put("selectionMode", selectionMode.name)
            put("useFrontCamera", useFrontCamera)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): Preset {
            val synth = json.getJSONObject("synth")
            val music = json.getJSONObject("music")
            val acid = json.getJSONObject("acid")
            
            return Preset(
                name = json.getString("name"),
                synthState = SynthState(
                    subOsc = synth.optBoolean("subOsc", true),
                    sinOsc = synth.optBoolean("sinOsc", true),
                    sawOsc = synth.optBoolean("sawOsc", false),
                    sqrOsc = synth.optBoolean("sqrOsc", false),
                    noiseOsc = synth.optBoolean("noiseOsc", false),
                    cutoff = synth.optDouble("cutoff", 1.0).toFloat(),
                    resonance = synth.optDouble("resonance", 0.0).toFloat(),
                    envelope = synth.optDouble("envelope", 0.0).toFloat(),
                    attack = synth.optDouble("attack", 0.0).toFloat(),
                    release = synth.optDouble("release", 0.2).toFloat(),
                    distortion = synth.optDouble("distortion", 0.0).toFloat(),
                    fm = synth.optDouble("fm", 0.0).toFloat(),
                    fmAmount = synth.optDouble("fmAmount", 0.0).toFloat(),
                    chorusFreq = synth.optDouble("chorusFreq", 0.0).toFloat(),
                    chorusMod = synth.optDouble("chorusMod", 0.0).toFloat(),
                    delayFigure = synth.optDouble("delayFigure", 1.0).toFloat(),
                    feedback = synth.optDouble("feedback", 0.5).toFloat()
                ),
                musicState = MusicState(
                    scaleIndex = music.optInt("scaleIndex", 8),
                    keyIndex = music.optInt("keyIndex", 0),
                    octaveIndex = music.optInt("octaveIndex", 2),
                    figureIndex = music.optInt("figureIndex", 2),
                    tempo = music.optDouble("tempo", 120.0).toFloat(),
                    periodTempo = (60000f / music.optDouble("tempo", 120.0).toFloat())
                ),
                acidModulation = AcidModulation(
                    enabled = acid.optBoolean("enabled", false),
                    multiShape = acid.optBoolean("multiShape", false),
                    hueAmount = acid.optDouble("hueAmount", 0.5).toFloat(),
                    sizeAmount = acid.optDouble("sizeAmount", 0.3).toFloat(),
                    rotationAmount = acid.optDouble("rotationAmount", 0.5).toFloat(),
                    alphaAmount = acid.optDouble("alphaAmount", 0.2).toFloat(),
                    animationSpeed = acid.optDouble("animationSpeed", 0.5).toFloat()
                ),
                acidPatternIndex = json.optInt("acidPatternIndex", 9),
                selectionMode = try {
                    PixelSelectionMode.valueOf(json.optString("selectionMode", "SEQUENCE"))
                } catch (e: Exception) {
                    PixelSelectionMode.SEQUENCE
                },
                useFrontCamera = json.optBoolean("useFrontCamera", false)
            )
        }
    }
}

/**
 * Manager class for saving and loading presets to/from JSON files
 */
class PresetManager(private val context: Context) {
    
    private val presetsDir: File
        get() = File(context.filesDir, "presets").also { 
            if (!it.exists()) it.mkdirs() 
        }
    
    /**
     * Save a preset to a JSON file
     */
    fun savePreset(preset: Preset): Boolean {
        return try {
            val fileName = sanitizeFileName(preset.name) + ".json"
            val file = File(presetsDir, fileName)
            file.writeText(preset.toJson().toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Load a preset from a JSON file by name
     */
    fun loadPreset(name: String): Preset? {
        return try {
            val fileName = sanitizeFileName(name) + ".json"
            val file = File(presetsDir, fileName)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                Preset.fromJson(json)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get list of all saved preset names
     */
    fun getPresetNames(): List<String> {
        return presetsDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val json = JSONObject(file.readText())
                    json.getString("name")
                } catch (e: Exception) {
                    null
                }
            }
            ?.sorted()
            ?: emptyList()
    }
    
    /**
     * Delete a preset by name
     */
    fun deletePreset(name: String): Boolean {
        return try {
            val fileName = sanitizeFileName(name) + ".json"
            val file = File(presetsDir, fileName)
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Check if a preset with the given name exists
     */
    fun presetExists(name: String): Boolean {
        val fileName = sanitizeFileName(name) + ".json"
        return File(presetsDir, fileName).exists()
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(50)
            .ifEmpty { "preset" }
    }
}
