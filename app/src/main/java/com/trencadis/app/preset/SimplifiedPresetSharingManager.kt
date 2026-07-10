package com.trencadis.app.preset

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream

/**
 * Simplified preset sharing manager for Trencadis 2.0
 * Handles easy preset sharing without authentication or complex infrastructure
 */
class SimplifiedPresetSharingManager(private val context: Context) {
    
    private val _sharingState = MutableStateFlow(PresetSharingState())
    val sharingState = _sharingState.asStateFlow()
    
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
    }
    
    // Directory for shared presets
    private val sharedPresetsDir = File(context.filesDir, "shared_presets")
    
    init {
        sharedPresetsDir.mkdirs()
    }
    
    /**
     * Share a preset as JSON file
     */
    suspend fun sharePreset(preset: TrencadisPreset): Result<ShareIntent> {
        return try {
            _sharingState.value = _sharingState.value.copy(isSharing = true)
            
            // Convert preset to shareable format
            val shareablePreset = createShareablePreset(preset)
            
            // Save to temporary file
            val tempFile = createTempPresetFile(shareablePreset)
            
            // Create share intent
            val shareIntent = createShareIntent(tempFile, shareablePreset)
            
            _sharingState.value = _sharingState.value.copy(
                isSharing = false,
                lastSharedPreset = shareablePreset
            )
            
            Result.success(shareIntent)
            
        } catch (e: Exception) {
            _sharingState.value = _sharingState.value.copy(
                isSharing = false,
                error = "Failed to share preset: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Share preset with associated media file
     */
    suspend fun sharePresetWithMedia(
        preset: TrencadisPreset, 
        mediaFile: com.trencadis.app.media.MediaFile
    ): Result<ShareIntent> {
        return try {
            _sharingState.value = _sharingState.value.copy(isSharing = true)
            
            // Create preset package
            val packageFile = createPresetPackage(preset, mediaFile)
            
            // Create share intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(packageFile))
                putExtra(Intent.EXTRA_TEXT, "Trencadis preset package: ${preset.name}")
                putExtra(Intent.EXTRA_SUBJECT, "Check out this Trencadis preset with media!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            _sharingState.value = _sharingState.value.copy(
                isSharing = false,
                lastSharedPreset = createShareablePreset(preset)
            )
            
            Result.success(ShareIntent(shareIntent, packageFile))
            
        } catch (e: Exception) {
            _sharingState.value = _sharingState.value.copy(
                isSharing = false,
                error = "Failed to share preset package: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Import preset from shared file
     */
    suspend fun importSharedPreset(uri: Uri): Result<TrencadisPreset> {
        return try {
            _sharingState.value = _sharingState.value.copy(isImporting = true)
            
            // Read preset file
            val presetContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { reader -> reader.readText() }
            } ?: throw Exception("Could not read preset file")
            
            // Parse preset
            val shareablePreset = json.decodeFromString<ShareablePreset>(presetContent)
            
            // Convert to TrencadisPreset
            val trencadisPreset = convertToTrencadisPreset(shareablePreset)
            
            // Save to local presets
            saveImportedPreset(trencadisPreset)
            
            _sharingState.value = _sharingState.value.copy(
                isImporting = false,
                lastImportedPreset = trencadisPreset
            )
            
            Result.success(trencadisPreset)
            
        } catch (e: Exception) {
            _sharingState.value = _sharingState.value.copy(
                isImporting = false,
                error = "Failed to import preset: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Import preset package with media
     */
    suspend fun importPresetPackage(uri: Uri): Result<PresetPackage> {
        return try {
            _sharingState.value = _sharingState.value.copy(isImporting = true)
            
            // TODO: Implement ZIP extraction
            // This would extract the preset JSON and media file from the ZIP
            
            _sharingState.value = _sharingState.value.copy(isImporting = false)
            Result.failure(NotImplementedError("Package import not yet implemented"))
            
        } catch (e: Exception) {
            _sharingState.value = _sharingState.value.copy(
                isImporting = false,
                error = "Failed to import preset package: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Get sharing history
     */
    fun getSharingHistory(): List<ShareablePreset> {
        return sharedPresetsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<ShareablePreset>(file.readText())
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.sharedAt }
            ?: emptyList()
    }
    
    /**
     * Clear sharing history
     */
    fun clearSharingHistory() {
        sharedPresetsDir.listFiles()?.forEach { it.delete() }
        _sharingState.value = _sharingState.value.copy(
            lastSharedPreset = null,
            lastImportedPreset = null
        )
    }
    
    /**
     * Create shareable preset format
     */
    private fun createShareablePreset(preset: TrencadisPreset): ShareablePreset {
        return ShareablePreset(
            id = preset.id,
            name = preset.name,
            description = preset.description ?: "",
            version = "2.0",
            sharedAt = System.currentTimeMillis(),
            author = "Trencadis User",
            tags = generateTagsForPreset(preset),
            parameters = preset.parameters,
            trencadisV2Features = extractV2Features(preset)
        )
    }
    
    /**
     * Create temporary preset file
     */
    private fun createTempPresetFile(shareablePreset: ShareablePreset): File {
        val fileName = "trencadis_preset_${shareablePreset.name.replace(" ", "_")}_${shareablePreset.sharedAt}.json"
        val tempFile = File(context.cacheDir, fileName)
        
        val presetJson = json.encodeToString(shareablePreset)
        tempFile.writeText(presetJson)
        
        return tempFile
    }
    
    /**
     * Create share intent
     */
    private fun createShareIntent(file: File, preset: ShareablePreset): ShareIntent {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
            putExtra(Intent.EXTRA_TEXT, "Trencadis preset: ${preset.name}")
            putExtra(Intent.EXTRA_SUBJECT, "Check out this Trencadis preset!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        return ShareIntent(intent, file)
    }
    
    /**
     * Create preset package with media
     */
    private fun createPresetPackage(
        preset: TrencadisPreset, 
        mediaFile: com.trencadis.app.media.MediaFile
    ): File {
        // TODO: Implement ZIP creation
        // This would create a ZIP file containing the preset JSON and media file
        val fileName = "trencadis_package_${preset.name.replace(" ", "_")}_${System.currentTimeMillis()}.zip"
        return File(context.cacheDir, fileName)
    }
    
    /**
     * Convert shareable preset back to TrencadisPreset
     */
    private fun convertToTrencadisPreset(shareablePreset: ShareablePreset): TrencadisPreset {
        return TrencadisPreset(
            id = shareablePreset.id,
            name = shareablePreset.name,
            description = shareablePreset.description,
            parameters = shareablePreset.parameters,
            createdAt = shareablePreset.sharedAt
        )
    }
    
    /**
     * Save imported preset to local storage
     */
    private fun saveImportedPreset(preset: TrencadisPreset) {
        // TODO: Use existing PresetManager to save the preset
        // This would integrate with the existing preset system
    }
    
    /**
     * Generate tags for preset based on parameters
     */
    private fun generateTagsForPreset(preset: TrencadisPreset): List<String> {
        val tags = mutableListOf<String>()
        
        // Analyze parameters to generate relevant tags
        preset.parameters.forEach { (key, value) ->
            val content = value.jsonPrimitive.content
            when (key) {
                "vocoder_enabled" -> if (content.toFloatOrNull() ?: 0f > 0.5f) tags.add("vocoder")
                "polyphony_enabled" -> if (content.toBoolean()) tags.add("polyphony")
                "midi_input_enabled" -> if (content.toBoolean()) tags.add("midi")
                "acid_modulation_enabled" -> if (content.toBoolean()) tags.add("acid")
                "use_blob_mode" -> if (content.toBoolean()) tags.add("blobs")
                // Add more tag generation logic
            }
        }
        
        return tags.distinct()
    }
    
    /**
     * Extract V2 features from preset
     */
    private fun extractV2Features(preset: TrencadisPreset): TrencadisV2Features? {
        // TODO: Extract V2-specific features from preset parameters
        return null
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _sharingState.value = _sharingState.value.copy(error = null)
    }
}

/**
 * Preset sharing state
 */
data class PresetSharingState(
    val isSharing: Boolean = false,
    val isImporting: Boolean = false,
    val lastSharedPreset: ShareablePreset? = null,
    val lastImportedPreset: TrencadisPreset? = null,
    val error: String? = null
)

/**
 * Shareable preset format
 */
@Serializable
data class ShareablePreset(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val sharedAt: Long,
    val author: String,
    val tags: List<String>,
    val parameters: Map<String, JsonElement>,
    val trencadisV2Features: TrencadisV2Features? = null
)

/**
 * Trencadis 2.0 specific features
 */
@Serializable
data class TrencadisV2Features(
    val mediaCaptureSettings: Map<String, JsonElement>? = null,
    val vocoderSettings: Map<String, JsonElement>? = null,
    val polyphonySettings: Map<String, JsonElement>? = null,
    val midiInputSettings: Map<String, JsonElement>? = null
)

/**
 * Trencadis preset data class
 */
data class TrencadisPreset(
    val id: String,
    val name: String,
    val description: String? = null,
    val parameters: Map<String, JsonElement> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Share intent wrapper
 */
data class ShareIntent(
    val intent: Intent,
    val file: File
)

/**
 * Preset package result
 */
data class PresetPackage(
    val preset: TrencadisPreset,
    val mediaFile: com.trencadis.app.media.MediaFile?
)
