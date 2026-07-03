# Trencadís — Architecture

## Overview

Trencadís is a real-time audiovisual synthesizer for Android. The camera feed is analyzed pixel-by-pixel, mapped to musical notes via a Pure Data synthesis engine, and rendered as cubist/mosaic generative art. When connected to a DAW, the app becomes a MIDI controller — the DAW drives tempo via MIDI Clock, and Trencadís sends camera-reactive notes back.

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│  CameraX    │────▶│  Pixel Analyzer  │────▶│  ViewModel  │
│  (YUV feed) │     │  (grid + blobs)  │     │  (state)    │
└─────────────┘     └──────────────────┘     └──────┬──────┘
                                                     │
                    ┌────────────────┐               │
                    │  CubistCanvas  │◀──────────────┤  (visual state)
                    │  (Compose UI)  │               │
                    └────────────────┘               │
                                                     │
                    ┌────────────────┐               │
                    │  PdAudioEngine │◀──────────────┤  (audio params)
                    │  (Pure Data)   │               │
                    └────────────────┘               │
                                                     │
                    ┌────────────────┐               │
                    │  NoteRouter    │◀──────────────┤  (note events)
                    │  ├ PdDest      │               │
                    │  ├ MidiDest    │               │
                    │  └ BleDest     │               │
                    └────────────────┘               │
                                                     │
                    ┌────────────────┐               │
                    │  MidiClockSrc  │──────────────▶┤  (BPM/beat)
                    │  (USB + BLE)   │               │
                    └────────────────┘               │
                                                     │
                    ┌────────────────┐               │
                    │  PresetManager │◀──────────────┤  (save/load)
                    │  (JSON files)  │               │
                    └────────────────┘               │
```

---

## State Management

All app state flows through a single `TrencadisState` held in `TrencadisViewModel` as a `MutableStateFlow`, exposed as a read-only `StateFlow<TrencadisState>`.

### TrencadisState

| Field | Type | Description |
|-------|------|-------------|
| `pixelGrid` | `PixelGrid?` | Current camera pixel grid |
| `selectedPixel` | `PixelData?` | Active pixel (drives audio) |
| `selectionMode` | `PixelSelectionMode` | SEQUENCE / BRIGHTEST / CENTER / POINTER |
| `sequenceIndex` | `Int` | Current step in sequence mode |
| `blockSize` | `Int` | Pixel grid resolution |
| `synthState` | `SynthState` | Oscillators, filter, effects |
| `musicState` | `MusicState` | Scale, key, octave, figure, tempo |
| `acidModulation` | `AcidModulation` | Acid pattern parameters |
| `acidPatternIndex` | `Int` | Active acid pattern (0–9) |
| `blobModulation` | `BlobModulation` | Cubist blob parameters |
| `useBlobMode` | `Boolean` | Toggle blob vs. tile rendering |
| `midiState` | `MidiState` | MIDI enabled, routing mode, channel, BLE |
| Panel visibility | `Boolean` × 7 | Modes, Scales, Keys, Synth, Acid, Blob, Preset |
| `screenAspectRatio` | `Float` | Hardware aspect ratio for camera preview |
| `isAudioInitialized` | `Boolean` | Pd engine status |

### SynthState

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `subOsc` | `Boolean` | `true` | Sub-bass oscillator |
| `sinOsc` | `Boolean` | `true` | Sine oscillator |
| `sawOsc` | `Boolean` | `false` | Sawtooth oscillator |
| `sqrOsc` | `Boolean` | `false` | Square oscillator |
| `noiseOsc` | `Boolean` | `false` | Noise oscillator |
| `cutoff` | `Float` | `1f` | Filter cutoff (0–1, exponential) |
| `resonance` | `Float` | `0f` | Filter resonance (0–1, cubic) |
| `envelope` | `Float` | `0f` | Envelope amount (0–1) |
| `attack` | `Float` | `0f` | Attack time (0–1 → 5–505ms) |
| `release` | `Float` | `0.2f` | Release time (0–1 → 0–5000ms) |
| `distortion` | `Float` | `0f` | Distortion amount |
| `fm` | `Float` | `0f` | FM frequency (exponential) |
| `fmAmount` | `Float` | `0f` | FM modulation depth |
| `chorusFreq` | `Float` | `0f` | Chorus LFO frequency |
| `chorusMod` | `Float` | `0f` | Chorus modulation depth |
| `delayFigure` | `Float` | `1f` | Delay time multiplier (power of 2) |
| `feedback` | `Float` | `0.4f` | Delay feedback + reverb send |

### MusicState

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `scaleIndex` | `Int` | `8` (Gipsy) | 12 scales (Ionian → Japanese) |
| `keyIndex` | `Int` | `0` (C) | 12 keys (C–B, including sharps) |
| `octaveIndex` | `Int` | `2` | 7 octaves |
| `figureIndex` | `Int` | `2` (Negra) | Note duration figure |
| `tempo` | `Float` | `120` | BPM |
| `periodTempo` | `Float` | `500` | ms between notes |

### MidiState

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `enabled` | `Boolean` | `false` | MIDI output enabled |
| `outputMode` | `MidiOutputMode` | `INTERNAL` | INTERNAL / MIDI_OUT / BOTH |
| `channel` | `Int` | `1` | MIDI channel (1–16) |
| `deviceName` | `String` | `""` | Connected device name |
| `isClockLocked` | `Boolean` | `false` | Receiving MIDI Clock |
| `externalBpm` | `Float` | `0f` | BPM from external clock |
| `bleEnabled` | `Boolean` | `false` | BLE MIDI advertising on |
| `bleConnected` | `Boolean` | `false` | BLE central connected |

---

## Audio Pipeline

### PdAudioEngine

Wraps `libpd` (pd-for-android). On `initialize()`:
1. Determines sample rate and output channels via `AudioParameters`
2. Initializes `PdAudio` with 0 input channels
3. Registers a `PdReceiver` to listen for `BANG` messages (sequencer step callback)
4. Copies all 17 `.pd` patch files from `assets/patch/` to `filesDir/patch/`
5. Opens `STEPPEDPIX.pd` as the main patch
6. Starts audio processing

### Pd Messages (receiver names)

| Receiver | Type | Purpose |
|----------|------|---------|
| `Freq` | float | Oscillator frequency |
| `Gain` | float | Output volume |
| `X` | float | Stereo pan (−20 to +20) |
| `Y` | float | Spatial depth |
| `Cutoff` | float | Filter cutoff frequency |
| `Resonance` | float | Filter resonance |
| `Envelope` | float | Envelope modulation amount |
| `Attack` | float | Attack time (ms) |
| `Release` | float | Release time (ms) |
| `Dist` | float | Distortion |
| `FM` | float | FM carrier frequency |
| `amountFM` | float | FM modulation amount |
| `freqChor` | float | Chorus LFO frequency |
| `modChor` | float | Chorus modulation depth |
| `Tdelay` | float | Delay time (ms) |
| `Lfeedback` | float | Delay feedback |
| `Rsend` | float | Reverb send |
| `Sub` / `Sin` / `Saw` / `Sqr` / `Noi` | float (0/1) | Oscillator on/off |
| `onSEQ` | float (0/1) | Sequencer on/off |
| `periodSEQ` | float | Sequencer period (ms) |
| `BPDFreq` | float | Bandpass filter frequency |
| `NoteOn` | float (0/1) | Pointer mode note gate |
| `BANG` | bang | Trigger note (from Pd → ViewModel) |

### Pure Data Patches (17 files)

| Patch | Purpose |
|-------|---------|
| `STEPPEDPIX.pd` | Main patch — oscillator mixer, sequencer, spatialization |
| `c_adsr.pd` | ADSR envelope generator |
| `c_ead.pd` | Exponential attack-decay envelope |
| `c_xfade.pd` | Crossfade utility |
| `e_beequad.pd` | Biquad filter |
| `e_chorus.pd` | Chorus effect |
| `e_dubdel.pd` | Dub-style delay |
| `e_lop2.pd` | Lowpass filter |
| `e_vocoder.pd` | Vocoder effect |
| `tapedelay.pd` | Tape delay emulation |
| `tapedelaysimple.pd` | Simplified tape delay |
| `u_bandpass1.pd` | Bandpass filter |
| `u_dispatch.pd` | Signal dispatcher |
| `u_loader.pd` | Patch loader utility |
| `u_lowpassq.pd` | Resonant lowpass filter |
| `u_sssad.pd` | State save/restore |
| `x_bandpass.pd` | Crossover bandpass |

### Color → Sound Mapping

| Color Property | Sound Parameter | Mapping |
|----------------|-----------------|---------|
| **Hue** | Pitch | Hue → scale degree → frequency via `MusicConstants.calculateFrequency()` |
| **Saturation** | Filter brightness | Influences cutoff modulation |
| **Brightness** | Volume & shape size | `Gain = brightness × 0.5` |
| **Position X** | Stereo pan | `X = (gridX/cols) × 40 − 20` |
| **Position Y** | Spatial depth | `Y = (gridY/rows) × 40 − 20 + 0.1` |

### MusicConstants

- `DIATONIC_STEPS` — 12 scale interval tables (Ionian, Dorian, Phrygian, Lydian, Mixolydian, Aeolian, Locrian, Harmonic Minor, Spanish Gipsy, Hawaiian, Blues, Japanese)
- `calculateFrequency(hue, scaleIndex, keyIndex, octaveIndex)` — maps hue (0–360) to a scale degree, applies key + octave transposition, returns frequency in Hz
- `getRootFrequency(keyIndex)` — returns root frequency for a given key

---

## Camera Pipeline

### CameraPixelAnalyzer

- Uses CameraX `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`
- Converts YUV `ImageProxy` → `Bitmap` → downsampled pixel grid
- Grid dimensions: `blockSize` controls resolution (e.g. 60×107 for 120-blockSize on 9:16)
- Temporal smoothing: blends current frame colors with previous frame for fluid transitions
- Blob mode: delegates to `BlobDetector` for connected-component analysis
- Runs on a single-thread executor (~30fps)

### PixelData

Each pixel in the grid carries:
- `hue`, `saturation`, `brightness` (HSV)
- `red`, `green`, `blue` (RGB, temporally smoothed)
- `gridX`, `gridY` (position in grid)
- `color` (packed ARGB int)

### BlobDetector

- Groups pixels by hue buckets (`hueBuckets` parameter)
- Connected-component labeling on the bucketed grid
- Convex hull simplification for each blob → angular polygon vertices
- Filters by `minBlobSize` and caps at `maxBlobs`
- Returns list of blobs with: polygon vertices, average color, centroid

### Pixel Selection Modes

| Mode | Behavior | Sequencer |
|------|----------|-----------|
| **SEQUENCE** | Cycles `sequenceIndex` through all pixels | On (Pd BANG drives increment) |
| **BRIGHTEST** | Picks highest-brightness pixel each frame | On |
| **CENTER** | Always selects center pixel (drone) | On |
| **POINTER** | Maps touch X/Y to grid position | Off (triggered by touch) |

---

## Visual Rendering

### CubistCanvas

Compose `Canvas` that renders the pixel grid as geometric shapes. Two modes:

**Tile mode** (default):
- Each pixel → one shape (rect/circle/triangle/diamond/hex/star)
- Shape type determined by hue
- Size scaled by brightness
- Color = pixel RGB with acid modulation applied

**Blob mode** (`useBlobMode = true`):
- Blob polygons drawn with convex hull vertices
- Grout gaps: shapes contract from boundaries for mortar-line effect
- `tileOverlayAlpha` controls underlay visibility
- `blobsOnTop` controls draw order

### AcidPattern

10 procedural modulation patterns applied to shape rendering:

| Index | Name | Formula |
|-------|------|---------|
| 0 | GRID_MULTIPLY | `i×j` — diagonal waves |
| 1 | TAN_ROWS | `tan(j)` — horizontal distortion |
| 2 | TAN_COLS | `tan(i)` — vertical distortion |
| 3 | WAVE_DIAGONAL | `sin(i+j)+j` — diagonal sine waves |
| 4 | TAN_DIAGONAL_SHIFT | `tan((i+j)+2)` — shifted tangent |
| 5 | TAN_MULTIPLY | `tan(i×j)` — complex interference |
| 6 | SUBTRACT_IJ | `i−j` — linear gradient |
| 7 | SUBTRACT_JI | `j−i` — reverse gradient |
| 8 | ADD_IJ | `i+j` — diagonal gradient |
| 9 | WAVE_INTERFERENCE | `sin(tan(i+j)+tan(i−j)−1)` — complex (default) |

`AcidModulation` controls: `enabled`, `multiShape`, `hueAmount`, `sizeAmount`, `rotationAmount`, `alphaAmount`, `animationSpeed`.

### BlobModulation

| Parameter | Default | Description |
|-----------|---------|-------------|
| `hueBuckets` | 8 | Color grouping resolution |
| `minBlobSize` | 2 | Minimum pixels per blob |
| `maxBlobs` | 300 | Performance cap |
| `blobAlpha` | 1.0 | Polygon fill opacity |
| `outlineWidth` | 3f | Outline stroke width |
| `outlineAlpha` | 0.6f | Outline opacity |
| `tileOverlayAlpha` | 0.15f | Underlay tile visibility |
| `blobsOnTop` | true | Draw order (blobs vs. tiles) |

---

## MIDI / Sync Layer

### Transport Abstraction

Two interfaces in `sync/` decouple the ViewModel from transport specifics:

```kotlin
interface ClockSource {
    val isConnected: StateFlow<Boolean>
    val bpmFlow: Flow<Float>
    val beatFlow: Flow<Int>
    val keyFlow: Flow<Int>
    val scaleFlow: Flow<Int>
    fun connect()
    fun disconnect()
}

interface NoteDestination {
    val label: String
    fun noteOn(pitch: Int, velocity: Int, channel: Int = 1)
    fun noteOff(pitch: Int, channel: Int = 1)
    fun isAvailable(): Boolean
}
```

Adding a new transport (e.g. OSC) means implementing these interfaces — zero ViewModel changes.

### NoteRouter

Fans out note events to all registered `NoteDestination` instances:
- `PdNoteDestination` — wraps `PdAudioEngine`, toggled by output mode
- `MidiNoteDestination` — sends to USB MIDI input port via `MidiBus`
- `BleNoteDestination` — sends via BLE MIDI GATT notifications

Auto-sends `noteOff` for previous pitch when pitch changes (monophonic behavior).

### MidiClockSource

- Registers `MidiManager.DeviceCallback` for USB MIDI plug/unplug
- Opens output port 0 of each USB device, connects a `MidiReceiver`
- Also collects from `MidiBus.midiInputEvents` (virtual device path)
- Parses MIDI Clock bytes:
  - `0xF8` (Clock tick) → accumulates 24 ticks per beat, derives BPM from tick intervals
  - `0xFA` (Start) → resets beat counter, sets connected
  - `0xFB` (Continue) → sets connected
  - `0xFC` (Stop) → sets disconnected
- Emits BPM on `bpmFlow`, beat count on `beatFlow`

### MidiBus (singleton)

Global event bus for MIDI I/O:
- `midiInputEvents: SharedFlow<Pair<ByteArray, Long>>` — incoming MIDI bytes from any source
- `usbNotePort: MidiInputPort?` — writable port for USB MIDI output
- `deviceOutputPort: MidiInputPort?` — writable port for virtual device output
- `notePort` — convenience getter: USB port takes priority, falls back to device port

### TrencadisMidiDeviceService

Virtual MIDI device registered in `AndroidManifest.xml`. Allows DAWs to see Trencadís as a MIDI device even without USB (e.g. over virtual MIDI routing). Forwards incoming bytes to `MidiBus`.

### BleMidiPeripheral

Implements the BLE MIDI peripheral spec:
- Advertises the MIDI service UUID (`03B80E5A-...`)
- Runs a GATT server with the MIDI characteristic (`7772E5DB-...`)
- Encodes/decodes BLE MIDI packets (header + timestamp + MIDI bytes)
- Notifies connected centrals when notes are sent
- Handles CCCD descriptor for notification enable/disable

### MIDI Routing Modes

| Mode | Pd | USB MIDI | BLE MIDI |
|------|----|----------|----------|
| `INTERNAL` | ✅ | ❌ | ❌ |
| `MIDI_OUT` | ❌ | ✅ | ✅ (if BLE enabled) |
| `BOTH` | ✅ | ✅ | ✅ (if BLE enabled) |

### ViewModel MIDI Wiring

- `midiClockSource.connect()` called on init — collects clock flows
- When `isClockLocked` → external BPM overrides local tempo
- `incrementSequenceIndex()` (triggered by Pd BANG) routes notes through `noteRouter`
- `freqToMidiPitch(freq)` converts Pd frequency to MIDI note number (69 + 12×log2(f/440))
- Velocity = `brightness × 127` (clamped 1–127)

---

## Preset System

### PresetManager

- Saves/loads presets as JSON files in `context.filesDir/presets/`
- `copyBundledPresetsIfNeeded()` — copies 6 bundled presets from `assets/presets/` on first launch (doesn't overwrite user modifications)
- `createShareIntent(name)` — uses `FileProvider` to share preset JSON via Android share sheet

### Preset Data

```kotlin
data class Preset(
    name: String,
    synthState: SynthState,
    musicState: MusicState,
    acidModulation: AcidModulation,
    acidPatternIndex: Int,
    selectionMode: PixelSelectionMode,
    useFrontCamera: Boolean,
    useBlobMode: Boolean,
    blobModulation: BlobModulation
)
```

Note: `midiState` is **not** saved in presets (MIDI connection is runtime state).

### Bundled Presets (6)

| Preset | Character |
|--------|-----------|
| Acid Trip | Heavy acid patterns, distortion |
| Ambient Pad | Slow, atmospheric, center drone |
| Gipsy Dream | Gipsy scale, warm filter |
| Face Melting | Extreme FM, fast tempo |
| Ringing Trip | Ringing delays, chorus |
| Typical Seq | Default sequence setup |

---

## UI Architecture

### TrencadisScreen

Main composable. Structure:
1. **Camera preview** — `CameraPreviewWithAnalysis` (hidden off-screen at x=−10000dp, only used for pixel analysis)
2. **CubistCanvas** — full-screen canvas rendering pixel grid / blobs with acid modulation
3. **Touch overlay** — handles drag (pointer mode), double-tap (panel close / icon toggle), edge drag (panel open)
4. **7 edge-triggered panels** — `AnimatedVisibility` with slide-in/out transitions
5. **EdgeHints** — circular icon buttons at screen edges, with MIDI clock indicator dot

### Panels

| Panel | Edge | Trigger | Content |
|-------|------|---------|---------|
| Modes | Left 1/4 | 📷 icon or left edge drag | Pixel selection mode, camera toggle, blob mode toggle |
| Blob | Left 1/2 | 🎨 icon | Hue buckets, blob size, alpha, outline, tile overlay |
| Acid | Left 3/4 | 🌀 icon | Pattern selection, modulation controls |
| Scales | Top | 𝄞 icon | 12 scale buttons |
| Keys | Bottom | ♪ icon | 12 keys, 7 octaves, 6 figures, tap tempo |
| Synth | Right 1/4 | ∿ icon | Oscillators, filter, effects, MIDI controls |
| Presets | Right 3/4 | 💾 icon | Preset list, save, load, delete, share |

### MIDI UI (in Synth panel)

- **Enable toggle** — turns MIDI output on/off
- **Output mode** — Pd / MIDI / Both
- **Channel selector** — 16 channels in a horizontal scroll row
- **BLE toggle** — start/stop BLE MIDI advertising, with connection status indicator (● connected, ◌ advertising, off)

### EdgeHints MIDI Indicator

A colored dot on the Synth (∿) icon:
- **Green** (`#00E5A0`) — MIDI Clock locked to external source
- **Yellow** (`#FFD040`) — MIDI enabled but no clock
- **Transparent** — MIDI disabled

---

## Build Configuration

| Setting | Value |
|---------|-------|
| `compileSdk` | 36 |
| `minSdk` | 24 (Android 7.0) |
| `targetSdk` | 36 |
| `versionCode` | 1 |
| `versionName` | "1.0" |
| Java version | 11 |
| Kotlin JVM target | 11 |
| R8 minify | Yes (release) |
| Resource shrinking | Yes (release) |

### Manifest Features

- `android.hardware.camera` (required)
- `android.software.midi` (optional)
- `android.hardware.bluetooth_le` (optional)

### Manifest Permissions

- `CAMERA`
- `RECORD_AUDIO`
- `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdk 30)
- `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE` (Android 12+)

### Manifest Services

- `TrencadisMidiDeviceService` — exported virtual MIDI device with `BIND_MIDI_DEVICE_SERVICE` permission

### Key Dependencies

- **CameraX** — core, camera2, lifecycle, view
- **pd-for-android** (`org.puredata.android:pd-core`)
- **Jetpack Compose** — UI, Material 3, tooling
- **Lifecycle** — ViewModel Compose, runtime compose
- **Accompanist Permissions** — runtime permission handling

---

## Package Structure

```
com.example.trencadisapp
├── MainActivity.kt
├── TrencadisViewModel.kt          (SynthState, MusicState, TrencadisState)
├── audio/
│   ├── PdAudioEngine.kt
│   └── MusicConstants.kt
├── camera/
│   ├── CameraPixelAnalyzer.kt
│   ├── PixelData.kt               (PixelData, PixelGrid, PixelSelectionMode)
│   └── BlobDetector.kt
├── midi/
│   ├── MidiState.kt               (MidiState, MidiOutputMode)
│   ├── MidiBus.kt                 (singleton object)
│   ├── MidiClockSource.kt
│   ├── MidiNoteDestination.kt
│   ├── BleMidiPeripheral.kt
│   ├── BleNoteDestination.kt
│   ├── NoteRouter.kt
│   ├── PdNoteDestination.kt
│   └── TrencadisMidiDeviceService.kt
├── sync/
│   ├── ClockSource.kt             (interface)
│   └── NoteDestination.kt         (interface)
├── preset/
│   └── PresetManager.kt           (Preset data class + manager)
└── ui/
    ├── TrencadisScreen.kt
    ├── CubistCanvas.kt
    ├── AcidPattern.kt             (AcidPattern, AcidModulation)
    ├── BlobModulation.kt
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    └── components/
        ├── ControlPanels.kt       (ModesPanel, ScalesPanel, KeysPanel, SynthPanel, AcidPanel)
        ├── BlobPanel.kt
        └── PresetPanel.kt
```
