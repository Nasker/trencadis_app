# 🎹 Trencadis 2.0: Complete Feature Implementation Plan

## 📋 Executive Summary

**Vision:** Transform Trencadis from a real-time audiovisual synthesizer into a comprehensive creative platform that combines media capture, advanced audio processing, professional MIDI capabilities, and collaborative performance features.

**Core Pillars:**
1. **Media Capture & Playback** - Raw still/video capture with reinterpretation
2. **MIDI Polyphony & Multitimbrality** - Multi-voice control and external synth integration
3. **MIDI Input Arpeggiation** - Chord detection and harmonic coherence with external instruments
4. **Streamlined Sharing** - Frictionless preset and media exchange
5. **Audio Engine Enhancements** - *(Backlog)* Vocoder integration and advanced synthesis

> **Priority update:** The built-in vocoder is moving to the backlog. Power users can already route Trencadís MIDI/audio into their DAW/synth and apply vocoding there, so MIDI improvements (polyphony, multitimbrality, arpeggiation, CC mapping, clock improvements) take priority over an internal vocoder for now.

---

## 🏗️ Phase 1: Foundation Architecture (Weeks 1-2)

### 1.1 Core System Architecture
**New Components:**
```kotlin
// Media Management
class RawMediaCaptureManager
class MediaLibraryManager  
class VideoFrameExtractor
class StaticImagePlayer

// Audio Enhancements
class VocoderProcessor
class PolyphonyManager
class MidiChannelManager

// MIDI Input System
class MidiInputReceiver
class ChordDetector
class ArpeggiatorEngine
class HarmonyManager

// UI Framework
class MediaCapturePanel
class AudioControlPanel
class PolyphonyCursorManager
class MidiInputPanel
```

**State Management Expansion:**
```kotlin
data class TrencadisStateV2(
    // Existing state...
    val mediaMode: MediaMode = MediaMode.LIVE_CAMERA,
    val capturedMedia: MediaLibrary? = null,
    val vocoderState: VocoderState = VocoderState(),
    val polyphonyState: PolyphonyState = PolyphonyState(),
    val midiChannelState: MidiChannelState = MidiChannelState(),
    val midiInputState: MidiInputState = MidiInputState()
)

enum class MediaMode {
    LIVE_CAMERA, STATIC_IMAGE, VIDEO_PLAYBACK, VIDEO_RECORDING
}

data class MidiInputState(
    val enabled: Boolean = false,
    val harmonyMode: HarmonyMode = HarmonyMode.INTERNAL_SCALES,
    val detectedChord: Chord? = null,
    val arpeggioPattern: ArpeggioPattern = ArpeggioPattern.UP,
    val activeNotes: Set<Int> = emptySet()
)

enum class HarmonyMode {
    INTERNAL_SCALES,     // Use built-in scales
    EXTERNAL_CHORD,      // Follow incoming MIDI chords
    HYBRID               // Blend both systems
}
```

### 1.2 Storage Architecture
**Directory Structure:**
```
/app/files/
├── media/
│   ├── stills/
│   ├── videos/
│   ├── thumbnails/
│   └── exports/
├── presets/
│   ├── user/
│   └── shared/
├── midi/
│   ├── mappings/
│   └── profiles/
└── cache/
    ├── video_frames/
    └── temp_files/
```

---

## 🎥 Phase 2: Media Capture System (Weeks 3-4)

### 2.1 Raw Media Capture
**Still Image Capture:**
```kotlin
class RawMediaCaptureManager {
    suspend fun captureStillFrame(): Bitmap
    private fun processCameraFrame(): Bitmap
    private fun saveToMediaLibrary(bitmap: Bitmap): MediaFile
}
```

**Video Capture:**
```kotlin
class VideoCaptureManager {
    suspend fun startVideoCapture(): VideoRecordingSession
    suspend fun stopRecording(): MediaFile
    private fun encodeRawCameraFeed(): MP4File
}
```

**Gallery Integration:**
```kotlin
class MediaLibraryManager {
    suspend fun importFromGallery(): List<MediaFile>
    suspend fun generateThumbnail(mediaFile: MediaFile): Bitmap
    fun getMediaLibrary(): Flow<List<MediaFile>>
}
```

### 2.2 Media Playback Engines
**Static Image Player:**
```kotlin
class StaticImagePlayer {
    fun loadStaticImage(bitmap: Bitmap): PixelGrid
    fun enableAudioSynthesis(enabled: Boolean)
    fun updatePixelSelection(mode: PixelSelectionMode)
}
```

**Video Frame Extractor:**
```kotlin
class VideoFrameExtractor {
    suspend fun extractFrame(timestamp: Long): Bitmap
    fun getFrameCount(): Int
    fun getFrameRate(): Float
    suspend fun seekToFrame(frameIndex: Int)
}
```

---

## 🎛️ Phase 3: Audio Engine Enhancements (Weeks 5-6)

### 3.1 Vocoder Integration
**PD Integration:**
```kotlin
class VocoderProcessor {
    fun enableVocoder(enabled: Boolean)
    fun setCarrierLevel(level: Float)
    fun setModulatorLevel(level: Float)
    fun setBandGain(bandIndex: Int, gain: Float)
    fun setOutputMix(wetLevel: Float)
    
    private fun processMicrophoneInput(): AudioBuffer
    private fun routeCarrierSignal(): AudioBuffer
    private fun processVocoderPatch(): AudioBuffer
}
```

**State Management:**
```kotlin
data class VocoderState(
    val enabled: Boolean = false,
    val carrierLevel: Float = 0.7f,
    val modulatorLevel: Float = 0.5f,
    val bandGains: FloatArray = FloatArray(8) { 0.5f },
    val outputMix: Float = 0.5f,
    val inputGain: Float = 0.8f
)
```

### 3.2 Enhanced Audio Routing
**Audio Signal Flow:**
```
Camera → PixelGrid → Synthesis Engine → Vocoder Carrier → Output
Microphone → Vocoder Modulator ↗
```

---

## 🎹 Phase 4: MIDI Polyphony & Multitimbrality (Weeks 7-8)

### 4.1 Polyphony System
**Multi-Cursor Management:**
```kotlin
class PolyphonyManager {
    fun setVoiceCount(count: Int)
    fun updateCursorPositions(centerPixel: PixelData)
    fun triggerVoice(voiceId: Int, pixel: PixelData)
    fun setCursorSpacing(spacing: Float)
    
    private fun calculateCursorOffsets(): List<Offset>
    private fun distributeVoicesAcrossChannels(): Map<Int, List<Voice>>
}
```

**Visual Representation:**
```kotlin
data class CursorVoice(
    val id: Int,
    val channel: Int,
    val offset: Offset,
    val color: Color,
    val active: Boolean,
    val lastTriggeredPixel: PixelData?
)
```

### 4.2 MIDI Multitimbrality
**Channel Management:**
```kotlin
class MidiChannelManager {
    fun assignChannelToVoice(voiceId: Int, channel: Int)
    fun enableMultitimbralMode(enabled: Boolean)
    fun setChannelMapping(mapping: Map<Int, Int>)
    fun sendChannelSpecificCC(channel: Int, cc: Int, value: Int)
}
```

**External Synth Integration:**
```kotlin
data class ExternalSynthProfile(
    val name: String,
    val midiChannel: Int,
    val preferredVoiceCount: Int,
    val ccMappings: Map<Int, Int>
)
```

---

## 🎵 Phase 5: MIDI Input Arpeggiation System (Weeks 9-10)

### 5.1 MIDI Input Processing
**MIDI Input Receiver:**
```kotlin
class MidiInputReceiver {
    fun startMidiInput()
    fun stopMidiInput()
    private fun handleMidiNoteOn(note: Int, velocity: Int)
    private fun handleMidiNoteOff(note: Int)
    private fun handleMidiCC(cc: Int, value: Int)
}
```

### 5.2 Chord Detection Engine
**Chord Analysis:**
```kotlin
class ChordDetector {
    fun analyzeChord(notes: Set<Int>): Chord?
    fun getChordQuality(chord: Chord): ChordQuality
    fun getChordRoot(chord: Chord): Int
    fun isInKey(chord: Chord, key: Int): Boolean
    
    private fun detectIntervals(notes: Set<Int>): List<Interval>
    private fun identifyChordType(intervals: List<Interval>): ChordType
}

data class Chord(
    val root: Int,
    val type: ChordType,
    val notes: Set<Int>,
    val inversion: Int = 0
)

enum class ChordType {
    MAJOR, MINOR, DIMINISHED, AUGMENTED,
    MAJOR_SEVENTH, MINOR_SEVENTH, DOMINANT_SEVENTH,
    SUS2, SUS4, POWER_CHORD
}
```

### 5.3 Arpeggiator Engine
**Arpeggiation Patterns:**
```kotlin
class ArpeggiatorEngine {
    fun setPattern(pattern: ArpeggioPattern)
    fun setTempo(bpm: Float)
    fun setGateLength(ratio: Float)
    fun setOctaveRange(range: Int)
    fun startArpeggio(chord: Chord)
    fun stopArpeggio()
    
    private fun generateArpeggioSequence(chord: Chord): List<Int>
    private fun scheduleNextNote()
}

enum class ArpeggioPattern {
    UP, DOWN, UP_DOWN, DOWN_UP,
    RANDOM, PATTERN_1, PATTERN_2, PATTERN_3
}
```

### 5.4 Harmony Management
**Mode Switching:**
```kotlin
class HarmonyManager {
    fun setHarmonyMode(mode: HarmonyMode)
    fun updateFromMidiChord(chord: Chord)
    fun blendWithInternalScale(chord: Chord, scale: Scale): Scale
    fun getCurrentScale(): Scale
    
    private fun createScaleFromChord(chord: Chord): Scale
    private fun harmonizeWithExternalKey(chord: Chord): Scale
}
```

---

## 🎨 Phase 6: User Interface Overhaul (Weeks 11-12)

### 6.1 New Panel System
**Expanded Panel Layout:**
```
Left Edge:          Right Edge:
├── 📷 Modes        ├── ∿ Synth
├── 🌀 Acid         ├── 🎛️ Vocoder  
├── 📱 Media        ├── 🎹 Polyphony
├── 🎵 MIDI Input   ├── 💾 Presets
└── ⏺️ Capture      └── 🎼 Channels

Top/Bottom:
├── 𝄞 Scales        ├── ♪ Keys
├── 🎬 Video Controls└── 📊 MIDI Monitor
```

### 6.2 MIDI Input UI
**Chord Detection Display:**
```kotlin
@Composable
fun MidiInputPanel(
    midiInputState: MidiInputState,
    onToggleInput: () -> Unit,
    onHarmonyModeChange: (HarmonyMode) -> Unit,
    onArpeggioPatternChange: (ArpeggioPattern) -> Unit
)
```

**Visual Chord Indicator:**
```kotlin
@Composable
fun ChordIndicator(
    chord: Chord?,
    harmonyMode: HarmonyMode,
    activeNotes: Set<Int>
)
```

**Arpeggio Controls:**
```kotlin
@Composable
fun ArpeggioControls(
    pattern: ArpeggioPattern,
    tempo: Float,
    gateLength: Float,
    onPatternChange: (ArpeggioPattern) -> Unit,
    onTempoChange: (Float) -> Unit,
    onGateChange: (Float) -> Unit
)
```

### 6.3 Enhanced Media Capture UI
**Capture Controls:**
```kotlin
@Composable
fun MediaCapturePanel(
    mediaMode: MediaMode,
    onCaptureStill: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenGallery: () -> Unit
)
```

**Media Browser:**
```kotlin
@Composable
fun MediaLibraryBrowser(
    mediaFiles: List<MediaFile>,
    onLoadMedia: (MediaFile) -> Unit,
    onDeleteMedia: (MediaFile) -> Unit
)
```

---

## 🔄 Phase 7: Video Playback & Navigation (Weeks 13-14)

### 7.1 Video Playback Engine
**Frame-by-Frame Navigation:**
```kotlin
class VideoPlaybackController {
    suspend fun playVideo(mediaFile: VideoFile)
    suspend fun pausePlayback()
    suspend fun seekToTimestamp(timestamp: Long)
    suspend fun setLoopPoints(start: Long, end: Long)
    fun getCurrentFrame(): Bitmap
}
```

**Playback Controls:**
```kotlin
data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val currentTime: Long = 0L,
    val duration: Long = 0L,
    val loopStart: Long = 0L,
    val loopEnd: Long = 0L,
    val playbackSpeed: Float = 1.0f
)
```

### 7.2 Loop Management
**Loop Point System:**
```kotlin
class LoopManager {
    fun setLoopPoints(start: Long, end: Long)
    fun enableLoop(enabled: Boolean)
    fun createLoopFromSelection(selection: TimeRange): VideoLoop
    fun exportLoop(loop: VideoLoop): MediaFile
}
```

---

## 📤 Phase 8: Sharing & Export System (Weeks 15-16)

### 8.1 Simplified Preset Sharing
**Direct File Sharing:**
```kotlin
class PresetSharingManager {
    suspend fun sharePreset(preset: TrencadisPreset): ShareIntent
    suspend fun importSharedPreset(uri: Uri): TrencadisPreset
    suspend fun createPresetPackage(preset: TrencadisPreset, mediaFile: MediaFile): SharePackage
}
```

**Share Intent Builder:**
```kotlin
fun createShareIntent(preset: TrencadisPreset): Intent {
    return Intent().apply {
        action = Intent.ACTION_SEND
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, presetFileUri)
        putExtra(Intent.EXTRA_TEXT, "Trencadis preset: ${preset.name}")
        putExtra(Intent.EXTRA_SUBJECT, "Check out this Trencadis preset!")
    }
}
```

### 8.2 Media Export Options
**Export Formats:**
- Still images: PNG/JPEG with embedded preset data
- Video loops: MP4 with synchronized audio
- Preset packages: JSON + media bundle

---

## 🧪 Phase 9: Performance Optimization (Weeks 17-18)

### 9.1 Memory Management
**Video Frame Caching:**
```kotlin
class VideoFrameCache {
    private val frameCache = LruCache<String, Bitmap>(maxSize = 50)
    suspend fun getFrame(key: String): Bitmap
    private fun preloadFrames(aroundTimestamp: Long)
}
```

**Polyphony Optimization:**
```kotlin
class PolyphonyOptimizer {
    fun optimizeVoiceAllocation(voices: List<CursorVoice>): OptimizedLayout
    fun minimizePixelLookups(cursors: List<Offset>): LookupStrategy
    fun batchMidiEvents(events: List<MidiEvent>)
}
```

### 9.2 MIDI Input Optimization
**Low-Latency Processing:**
```kotlin
class MidiInputOptimizer {
    fun minimizeInputLatency()
    fun prioritizeNoteEvents()
    fun batchChordAnalysis()
    fun optimizeArpeggioTiming()
}
```

---

## 🎯 Phase 10: Advanced Features & Polish (Weeks 19-20)

### 10.1 Professional MIDI Features
**Advanced MIDI Integration:**
- MIDI learn mode for parameter mapping
- External transport sync (start/stop/continue)
- MIDI clock synchronization improvements
- MIDI controller mapping for arpeggiator

**Advanced Harmony Features:**
- Chord inversion detection
- Scale/chord hybrid modes
- Modulation target assignment
- External key following

### 10.2 User Experience Enhancements
**Workflow Improvements:**
- One-touch capture-to-preset workflow
- Quick switch between harmony modes
- Gesture-based cursor positioning
- Context-aware panel suggestions

**Visual Feedback:**
- Real-time chord display
- MIDI activity indicators
- Arpeggio pattern visualization
- Performance monitoring overlay

---

## 📚 Phase 11: Documentation & Community (Weeks 21-22)

### 11.1 In-App Documentation
**Interactive Tutorials:**
- First-time user walkthrough
- MIDI input setup guide
- Feature discovery system
- Context-sensitive help

**Advanced Guides:**
- Vocoder techniques tutorial
- Polyphony workflow guide
- External synth integration manual
- MIDI collaboration best practices

### 11.2 Community Features
**Preset Discovery:**
- Featured presets gallery
- User-submitted preset showcase
- Technique sharing platform
- MIDI collaboration examples

---

## 🚀 Implementation Timeline

### **Month 1: Foundation**
- Architecture design and core components
- Basic media capture implementation
- Initial UI framework
- MIDI input foundation

### **Month 2: Core Features**
- Media library and playback
- Vocoder integration
- Basic polyphony
- MIDI input chord detection

### **Month 3: Advanced Features**
- Full multitimbrality
- Arpeggiator implementation
- Video playback controls
- Enhanced UI panels

### **Month 4: Integration & Polish**
- Harmony management system
- Video playback controls
- Sharing system
- Performance optimization

### **Month 5: Launch Preparation**
- Beta testing and feedback
- Bug fixes and refinements
- Final integration testing
- Documentation completion

---

## 💰 Resource Requirements

### **Development Effort:**
- **Core Developer:** 5 months full-time
- **UI/UX Designer:** 2 months part-time
- **Audio Engineer:** 1 month consultation
- **MIDI Specialist:** 1 month consultation
- **QA Testing:** 1 month intensive

### **Technical Dependencies:**
- **Android APIs:** MediaProjection, MediaRecorder, Storage Access Framework, MIDI API
- **External Libraries:** FFmpeg for video processing (if needed)
- **Hardware Requirements:** Minimum 4GB RAM for video processing

---

## 🎯 Success Metrics

### **User Engagement:**
- Media capture adoption rate > 60%
- MIDI input usage > 40% of active users
- Preset sharing frequency > 3 per user per month
- Average session length increase by 40%

### **Technical Performance:**
- Video playback: 30fps minimum on mid-range devices
- Polyphony: 8 voices with < 20ms latency
- MIDI input latency: < 10ms
- Memory usage: < 500MB for typical sessions

### **Community Building:**
- User-generated preset library: 1000+ presets
- External synth integration adoption: 25% of power users
- MIDI collaboration tutorials: 80% completion rate
- Community-submitted chord progressions: 500+

---

## 🎵 Creative Workflows Enabled

### **MIDI Collaboration Scenarios:**
1. **Keyboard + Trencadis:** Play chords on keyboard → Trencadis arpeggiates visual melodies
2. **DAW Integration:** Send chord progressions from DAW → Visual accompaniment
3. **Live Performance:** Band plays changes → Trencadis follows harmony visually
4. **Studio Production:** Compose chord changes → Generate visual audio material

### **Advanced Production Techniques:**
1. **Hybrid Harmony:** Blend internal scales with external chords for unique tonalities
2. **Dynamic Arpeggios:** Change arpeggio patterns based on visual content
3. **Multitimbral Layers:** Different chord tones control different external synths
4. **Visual Scoring:** Compose music by creating visual patterns that follow harmonic structure

---

## 🔧 Technical Implementation Details

### **MIDI Input Processing Pipeline:**
```
MIDI Device → MidiInputReceiver → ChordDetector → HarmonyManager → Scale System → Audio Engine
```

### **Arpeggiator Integration:**
```kotlin
// Integration with existing pixel selection
class PixelSelectionManager {
    fun updateFromArpeggio(note: Int) {
        when (harmonyManager.harmonyMode) {
            HarmonyMode.EXTERNAL_CHORD -> {
                val scaleNote = harmonyManager.mapToScale(note)
                selectPixelForNote(scaleNote)
            }
            HarmonyMode.HYBRID -> {
                val blendedNote = harmonyManager.blendWithScale(note)
                selectPixelForNote(blendedNote)
            }
            else -> useInternalScale()
        }
    }
}
```

### **Performance Considerations:**
- **MIDI Processing:** Separate thread for low-latency MIDI handling
- **Chord Detection:** Optimized algorithms for real-time analysis
- **Arpeggiator:** Precise timing using audio callback synchronization
- **Memory Management:** Efficient pooling for MIDI event objects

---

This comprehensive plan transforms Trencadis into a professional-grade audiovisual creative platform with collaborative MIDI capabilities while maintaining its core philosophy of visual-to-audio synthesis. The MIDI input arpeggiation feature positions it as a unique tool for both solo performance and ensemble collaboration.

**Key Innovation:** The ability to follow external harmony while maintaining visual synthesis creates a new category of instrument that bridges the gap between traditional MIDI gear and innovative visual synthesis.

---

*Document created: 2026-07-10*
*Version: 1.0*
*Author: Trencadis Development Team*
