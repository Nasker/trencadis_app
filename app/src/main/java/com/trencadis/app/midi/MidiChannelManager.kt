package com.trencadis.app.midi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * MIDI channel manager for Trencadis 2.0
 * Handles multitimbral operation with multiple MIDI channels
 */
class MidiChannelManager {
    
    private val _channelState = MutableStateFlow(MidiChannelState())
    val channelState = _channelState.asStateFlow()
    
    // Channel mapping (voice ID -> MIDI channel)
    private var voiceToChannelMap: Map<Int, Int> = emptyMap()
    
    // Channel activity tracking
    private var channelActivity: Map<Int, ChannelActivity> = emptyMap()
    
    init {
        initializeChannels()
    }
    
    /**
     * Enable or disable multitimbral mode
     */
    fun enableMultitimbralMode(enabled: Boolean) {
        _channelState.value = _channelState.value.copy(enabled = enabled)
        
        if (enabled) {
            setupMultitimbralMapping()
        } else {
            setupMonophonicMapping()
        }
    }
    
    /**
     * Assign a MIDI channel to a specific voice
     */
    fun assignChannelToVoice(voiceId: Int, channel: Int) {
        val validChannel = channel.coerceIn(0, 15) // MIDI channels 0-15
        
        // Update mapping
        val newMapping = voiceToChannelMap.toMutableMap()
        newMapping[voiceId] = validChannel
        voiceToChannelMap = newMapping
        
        // Update state
        val newChannelMappings = _channelState.value.channelMappings.toMutableMap()
        newChannelMappings[voiceId] = validChannel
        _channelState.value = _channelState.value.copy(
            channelMappings = newChannelMappings
        )
        
        // Update channel activity
        updateChannelActivity(validChannel, true)
    }
    
    /**
     * Get MIDI channel for a voice
     */
    fun getChannelForVoice(voiceId: Int): Int {
        return voiceToChannelMap[voiceId] ?: 0
    }
    
    /**
     * Get all voices assigned to a specific channel
     */
    fun getVoicesForChannel(channel: Int): List<Int> {
        return voiceToChannelMap.filter { it.value == channel }.keys.toList()
    }
    
    /**
     * Send MIDI note on for a specific voice
     */
    fun sendNoteOn(voiceId: Int, note: Int, velocity: Int = 127) {
        val channel = getChannelForVoice(voiceId)
        sendMidiNoteOn(channel, note, velocity)
        
        // Update channel activity
        updateChannelActivity(channel, true, note, velocity)
    }
    
    /**
     * Send MIDI note off for a specific voice
     */
    fun sendNoteOff(voiceId: Int, note: Int, velocity: Int = 0) {
        val channel = getChannelForVoice(voiceId)
        sendMidiNoteOff(channel, note, velocity)
        
        // Update channel activity
        updateChannelActivity(channel, false, note, velocity)
    }
    
    /**
     * Send MIDI CC for a specific voice
     */
    fun sendCC(voiceId: Int, cc: Int, value: Int) {
        val channel = getChannelForVoice(voiceId)
        sendMidiCC(channel, cc, value)
    }
    
    /**
     * Send MIDI CC for all voices on a channel
     */
    fun sendCCToChannel(channel: Int, cc: Int, value: Int) {
        sendMidiCC(channel, cc, value)
    }
    
    /**
     * Send MIDI pitch bend for a specific voice
     */
    fun sendPitchBend(voiceId: Int, value: Int) {
        val channel = getChannelForVoice(voiceId)
        sendMidiPitchBend(channel, value)
    }
    
    /**
     * Send MIDI program change for a specific voice
     */
    fun sendProgramChange(voiceId: Int, program: Int) {
        val channel = getChannelForVoice(voiceId)
        sendMidiProgramChange(channel, program)
    }
    
    /**
     * Set channel mapping mode
     */
    fun setChannelMode(mode: ChannelMode) {
        when (mode) {
            ChannelMode.MONOPHONIC -> setupMonophonicMapping()
            ChannelMode.MULTITIMBRAL -> setupMultitimbralMapping()
            ChannelMode.CHORD_MODE -> setupChordModeMapping()
        }
    }
    
    /**
     * Create external synth profile
     */
    fun createExternalSynthProfile(
        name: String,
        midiChannel: Int,
        preferredVoiceCount: Int,
        ccMappings: Map<Int, Int> = emptyMap()
    ): ExternalSynthProfile {
        val profile = ExternalSynthProfile(
            name = name,
            midiChannel = midiChannel,
            preferredVoiceCount = preferredVoiceCount,
            ccMappings = ccMappings,
            isActive = false
        )
        
        val updatedProfiles = _channelState.value.externalSynthProfiles + profile
        _channelState.value = _channelState.value.copy(
            externalSynthProfiles = updatedProfiles
        )
        
        return profile
    }
    
    /**
     * Activate external synth profile
     */
    fun activateExternalSynthProfile(profileId: String) {
        val updatedProfiles = _channelState.value.externalSynthProfiles.map { profile ->
            if (profile.name == profileId) {
                profile.copy(isActive = true)
            } else {
                profile.copy(isActive = false)
            }
        }
        
        _channelState.value = _channelState.value.copy(
            externalSynthProfiles = updatedProfiles
        )
        
        // Setup channel mapping based on active profile
        val activeProfile = updatedProfiles.find { it.isActive }
        activeProfile?.let { profile ->
            setupProfileMapping(profile)
        }
    }
    
    /**
     * Enable MIDI learn mode for parameter mapping
     */
    fun enableLearnMode(enabled: Boolean) {
        _channelState.value = _channelState.value.copy(learnMode = enabled)
    }
    
    /**
     * Map MIDI CC to parameter
     */
    fun mapCCToParameter(cc: Int, parameterId: Int) {
        // TODO: Implement CC to parameter mapping
        // This would be used in MIDI learn mode
    }
    
    /**
     * Get channel activity for all channels
     */
    fun getChannelActivity(): Map<Int, ChannelActivity> {
        return channelActivity
    }
    
    /**
     * Get active channels
     */
    fun getActiveChannels(): Set<Int> {
        return _channelState.value.activeChannels
    }
    
    /**
     * Clear all channel mappings
     */
    fun clearChannelMappings() {
        voiceToChannelMap = emptyMap()
        _channelState.value = _channelState.value.copy(
            channelMappings = emptyMap(),
            activeChannels = emptySet()
        )
        
        // Clear channel activity
        channelActivity = emptyMap()
    }
    
    /**
     * Initialize default channel setup
     */
    private fun initializeChannels() {
        setupMonophonicMapping()
        
        // Initialize channel activity tracking
        channelActivity = (0..15).associateWith { channel ->
            ChannelActivity(
                channel = channel,
                isActive = false,
                lastNote = -1,
                lastVelocity = 0,
                lastActivityTime = 0L
            )
        }
    }
    
    /**
     * Setup monophonic mapping (all voices on channel 0)
     */
    private fun setupMonophonicMapping() {
        clearChannelMappings()
        
        // Map all voices to channel 0
        val mapping = (0..7).associateWith { 0 }
        voiceToChannelMap = mapping
        
        _channelState.value = _channelState.value.copy(
            channelMappings = mapping,
            activeChannels = setOf(0)
        )
    }
    
    /**
     * Setup multitimbral mapping (each voice on different channel)
     */
    private fun setupMultitimbralMapping() {
        clearChannelMappings()
        
        // Map voices 0-7 to channels 0-7
        val mapping = (0..7).associateWith { it }
        voiceToChannelMap = mapping
        
        _channelState.value = _channelState.value.copy(
            channelMappings = mapping,
            activeChannels = (0..7).toSet()
        )
    }
    
    /**
     * Setup chord mode mapping (all voices on same channel)
     */
    private fun setupChordModeMapping() {
        clearChannelMappings()
        
        // Map all voices to channel 0 for chord mode
        val mapping = (0..7).associateWith { 0 }
        voiceToChannelMap = mapping
        
        _channelState.value = _channelState.value.copy(
            channelMappings = mapping,
            activeChannels = setOf(0)
        )
    }
    
    /**
     * Setup mapping based on external synth profile
     */
    private fun setupProfileMapping(profile: ExternalSynthProfile) {
        clearChannelMappings()
        
        // Map voices to profile's preferred channel
        val mapping = (0 until profile.preferredVoiceCount).associateWith { profile.midiChannel }
        voiceToChannelMap = mapping
        
        _channelState.value = _channelState.value.copy(
            channelMappings = mapping,
            activeChannels = setOf(profile.midiChannel)
        )
    }
    
    /**
     * Update channel activity
     */
    private fun updateChannelActivity(channel: Int, isActive: Boolean, note: Int = -1, velocity: Int = 0) {
        val currentTime = System.currentTimeMillis()
        val currentActivity = channelActivity[channel] ?: ChannelActivity(channel, false, -1, 0, 0L)
        
        val updatedActivity = currentActivity.copy(
            isActive = isActive,
            lastNote = if (note != -1) note else currentActivity.lastNote,
            lastVelocity = if (velocity != 0) velocity else currentActivity.lastVelocity,
            lastActivityTime = if (isActive) currentTime else currentActivity.lastActivityTime
        )
        
        channelActivity = channelActivity + (channel to updatedActivity)
        
        // Update active channels set
        val activeChannels = if (isActive) {
            _channelState.value.activeChannels + channel
        } else {
            _channelState.value.activeChannels - channel
        }
        
        _channelState.value = _channelState.value.copy(
            activeChannels = activeChannels
        )
    }
    
    /**
     * Send MIDI note on message
     */
    private fun sendMidiNoteOn(channel: Int, note: Int, velocity: Int) {
        // TODO: Implement actual MIDI message sending
        // This would use the existing MIDI output system in Trencadis
    }
    
    /**
     * Send MIDI note off message
     */
    private fun sendMidiNoteOff(channel: Int, note: Int, velocity: Int) {
        // TODO: Implement actual MIDI message sending
    }
    
    /**
     * Send MIDI CC message
     */
    private fun sendMidiCC(channel: Int, cc: Int, value: Int) {
        // TODO: Implement actual MIDI message sending
    }
    
    /**
     * Send MIDI pitch bend message
     */
    private fun sendMidiPitchBend(channel: Int, value: Int) {
        // TODO: Implement actual MIDI message sending
    }
    
    /**
     * Send MIDI program change message
     */
    private fun sendMidiProgramChange(channel: Int, program: Int) {
        // TODO: Implement actual MIDI message sending
    }
    
    /**
     * Get channel parameters for preset saving
     */
    fun getParameters(): Map<String, Any> {
        return mapOf(
            "multitimbral_enabled" to _channelState.value.enabled,
            "channel_mode" to _channelState.value.channelMappings.toString(),
            "active_channels" to _channelState.value.activeChannels,
            "voice_mappings" to voiceToChannelMap,
            "external_profiles" to _channelState.value.externalSynthProfiles.map { profile ->
                mapOf(
                    "name" to profile.name,
                    "channel" to profile.midiChannel,
                    "voice_count" to profile.preferredVoiceCount,
                    "cc_mappings" to profile.ccMappings,
                    "is_active" to profile.isActive
                )
            }
        )
    }
    
    /**
     * Load channel parameters from map
     */
    fun loadParameters(parameters: Map<String, Any>) {
        parameters["multitimbral_enabled"]?.let { 
            enableMultitimbralMode(it as Boolean) 
        }
        
        // Load external synth profiles
        (parameters["external_profiles"] as? List<Map<String, Any>>)?.let { profiles ->
            val loadedProfiles = profiles.map { profileData ->
                ExternalSynthProfile(
                    name = profileData["name"] as String,
                    midiChannel = profileData["channel"] as Int,
                    preferredVoiceCount = profileData["voice_count"] as Int,
                    ccMappings = profileData["cc_mappings"] as? Map<Int, Int> ?: emptyMap(),
                    isActive = profileData["is_active"] as Boolean
                )
            }
            
            _channelState.value = _channelState.value.copy(
                externalSynthProfiles = loadedProfiles
            )
            
            // Activate first active profile
            loadedProfiles.find { it.isActive }?.let { profile ->
                activateExternalSynthProfile(profile.name)
            }
        }
    }
}

/**
 * Channel activity tracking
 */
data class ChannelActivity(
    val channel: Int,
    val isActive: Boolean,
    val lastNote: Int,
    val lastVelocity: Int,
    val lastActivityTime: Long
)
