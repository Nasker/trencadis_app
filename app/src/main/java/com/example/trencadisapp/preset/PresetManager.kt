package com.example.trencadisapp.preset

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.trencadisapp.SynthState
import com.example.trencadisapp.MusicState
import com.example.trencadisapp.camera.PixelSelectionMode
import com.example.trencadisapp.ui.AcidModulation
import com.example.trencadisapp.ui.BlobModulation
import org.json.JSONObject
import java.io.File
import kotlin.math.round

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
    val useFrontCamera: Boolean,
    val useBlobMode: Boolean = false,
    val blobModulation: BlobModulation = BlobModulation()
) {
    private fun Float.round2(): Double = (round(this * 100) / 100).toDouble()
    
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
                put("cutoff", synthState.cutoff.round2())
                put("resonance", synthState.resonance.round2())
                put("envelope", synthState.envelope.round2())
                put("attack", synthState.attack.round2())
                put("release", synthState.release.round2())
                put("distortion", synthState.distortion.round2())
                put("fm", synthState.fm.round2())
                put("fmAmount", synthState.fmAmount.round2())
                put("chorusFreq", synthState.chorusFreq.round2())
                put("chorusMod", synthState.chorusMod.round2())
                put("delayFigure", synthState.delayFigure.round2())
                put("feedback", synthState.feedback.round2())
            })
            
            // Music state
            put("music", JSONObject().apply {
                put("scaleIndex", musicState.scaleIndex)
                put("keyIndex", musicState.keyIndex)
                put("octaveIndex", musicState.octaveIndex)
                put("figureIndex", musicState.figureIndex)
                put("tempo", musicState.tempo.round2())
            })
            
            // Acid modulation
            put("acid", JSONObject().apply {
                put("enabled", acidModulation.enabled)
                put("multiShape", acidModulation.multiShape)
                put("hueAmount", acidModulation.hueAmount.round2())
                put("sizeAmount", acidModulation.sizeAmount.round2())
                put("rotationAmount", acidModulation.rotationAmount.round2())
                put("alphaAmount", acidModulation.alphaAmount.round2())
                put("animationSpeed", acidModulation.animationSpeed.round2())
            })
            
            put("acidPatternIndex", acidPatternIndex)
            put("selectionMode", selectionMode.name)
            put("useFrontCamera", useFrontCamera)
            put("useBlobMode", useBlobMode)
            put("blob", JSONObject().apply {
                put("hueBuckets", blobModulation.hueBuckets)
                put("minBlobSize", blobModulation.minBlobSize)
                put("maxBlobs", blobModulation.maxBlobs)
                put("blobAlpha", blobModulation.blobAlpha.round2())
                put("outlineWidth", blobModulation.outlineWidth.round2())
                put("outlineAlpha", blobModulation.outlineAlpha.round2())
                put("tileOverlayAlpha", blobModulation.tileOverlayAlpha.round2())
                put("blobsOnTop", blobModulation.blobsOnTop)
            })
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): Preset {
            val synth = json.getJSONObject("synth")
            val music = json.getJSONObject("music")
            val acid = json.getJSONObject("acid")
            val blob = json.optJSONObject("blob")
            
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
                    feedback = synth.optDouble("feedback", 0.4).toFloat().coerceAtMost(0.49f)
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
                useFrontCamera = json.optBoolean("useFrontCamera", false),
                useBlobMode = json.optBoolean("useBlobMode", false),
                blobModulation = if (blob != null) BlobModulation(
                    hueBuckets = blob.optInt("hueBuckets", 8),
                    minBlobSize = blob.optInt("minBlobSize", 2),
                    maxBlobs = blob.optInt("maxBlobs", 300),
                    blobAlpha = blob.optDouble("blobAlpha", 1.0).toFloat(),
                    outlineWidth = blob.optDouble("outlineWidth", 3.0).toFloat(),
                    outlineAlpha = blob.optDouble("outlineAlpha", 0.6).toFloat(),
                    tileOverlayAlpha = blob.optDouble("tileOverlayAlpha", 0.15).toFloat(),
                    blobsOnTop = blob.optBoolean("blobsOnTop", true)
                ) else BlobModulation()
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
    
    /**
     * Get the JSON content of a preset for sharing
     */
    fun getPresetJson(name: String): String? {
        return try {
            val fileName = sanitizeFileName(name) + ".json"
            val file = File(presetsDir, fileName)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Create a share intent for a preset
     */
    fun createShareIntent(name: String): Intent? {
        return try {
            val fileName = sanitizeFileName(name) + ".json"
            val file = File(presetsDir, fileName)
            if (!file.exists()) return null
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Trencadís Preset: $name")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Copy bundled presets from assets to internal storage on first launch
     */
    fun copyBundledPresetsIfNeeded() {
        try {
            val assetManager = context.assets
            val bundledPresets = assetManager.list("presets") ?: emptyArray()
            
            for (presetFile in bundledPresets) {
                if (presetFile.endsWith(".json")) {
                    val targetFile = File(presetsDir, presetFile)
                    // Only copy if doesn't exist (don't overwrite user modifications)
                    if (!targetFile.exists()) {
                        assetManager.open("presets/$presetFile").use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
