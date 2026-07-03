# 🎨 Trencadís

**A real-time audiovisual synthesizer for Android** that transforms your camera feed into generative music and cubist art.

> *Trencadís* — the mosaic technique of broken tile shards used by Gaudí — inspires this app's visual aesthetic, fragmenting reality into colorful geometric shapes that dance to algorithmically generated melodies.

---

## ✨ Features

### 🎵 **Real-Time Sound Synthesis**
- **Pure Data audio engine** — Professional-grade synthesis using libpd
- **12 musical scales** — Ionian, Dorian, Phrygian, Lydian, Mixolydian, Aeolian, Locrian, Harmonic Minor, Spanish Gipsy, Hawaiian, Blues, Japanese
- **Full chromatic keys** — C through B with sharp notes
- **7 octave range** — From deep bass to crystalline highs
- **Tap tempo** — Set your own BPM by tapping

### 🎹 **Powerful Synthesizer**
- **5 oscillators** — Sub, Sine, Saw, Square, Noise (mixable)
- **Resonant filter** — Cutoff, resonance, envelope amount
- **Amp envelope** — Attack & release shaping
- **Effects** — FM synthesis, chorus, delay with feedback, reverb

### 🎨 **Cubist Blob Mode**
- **Color-region detection** — Groups similar-colored pixels into mosaic tiles using connected-component analysis
- **Convex hull polygons** — Each color region rendered as a simplified angular polygon
- **Grout gaps** — Shapes contract from their boundaries, creating authentic trencadís-style mortar lines
- **Temporal smoothing** — Colors blend across frames for fluid, organic shape evolution
- **Full parameter control** — Hue buckets, min/max blob size, opacity, outline, tile overlay, draw order

### 🌀 **Acid Visual Patterns**
- **10 animated patterns** — Grid, Tan-H, Tan-V, Wave, Tan-D, Tan-X, Grad1, Grad2, Grad3, Acid
- **Modulation controls** — Hue, size, rotation, alpha, animation speed
- **Multi-shape mode** — Rectangles, circles, triangles, diamonds, hexagons, stars

### 📷 **4 Pixel Selection Modes**
| Mode | Description |
|------|-------------|
| **Sequence** | Cycles through pixels in order — creates melodic patterns |
| **Brightest** | Always plays the brightest pixel — follows light sources |
| **Center** | Locks to center pixel — stable drone tones |
| **Pointer** | Touch to play — becomes a visual theremin |

### 💾 **Preset System**
- **Save unlimited presets** with custom names
- **Instant recall** of all synth, music, and visual settings
- **JSON format** — Easy to backup and share
- **Delete with confirmation** — No accidental losses
- **6 bundled presets** — Acid Trip, Ambient Pad, Gipsy Dream, Face Melting, Ringing Trip, Typical Seq

### 📱 **Intuitive Touch UI**
- **Edge-triggered panels** — Drag from edges or tap icons
- **Double-tap to dismiss** — Quick panel closing
- **Front/back camera** — Switch with one tap
- **Hide UI** — Double-tap for immersive mode

### 🎹 **MIDI Connectivity**
- **USB MIDI out** — Send notes to a DAW via USB cable
- **BLE MIDI peripheral** — Advertise as a Bluetooth MIDI device, wireless note output
- **MIDI Clock sync** — Receive MIDI Clock (0xF8) from DAW, auto-derive BPM, lock tempo
- **3 routing modes** — Internal Pd only, MIDI out only, or both simultaneously
- **16-channel selection** — Choose which MIDI channel to send on
- **Clock indicator** — Colored dot on the Synth icon: green = clock locked, yellow = MIDI enabled, off = disconnected

---

## 🎬 Screenshots

<p align="center">
  <i>Screenshots coming soon</i>
</p>

---

## 📲 Installation

### Requirements
- Android 7.0 (API 24) or higher
- Camera permission
- Audio output (speakers or headphones recommended)
- Bluetooth permissions (Android 12+) — only for BLE MIDI feature

### From Source
```bash
# Clone the repository
git clone https://github.com/yourusername/trencadis_app.git
cd trencadis_app

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### Release Build
```bash
# Set up signing (create keystore first)
export KEYSTORE_PASSWORD=your_password
export KEY_PASSWORD=your_key_password

# Build signed release APK
./gradlew assembleRelease
```

---

## 🎮 How to Use

### Quick Start
1. **Launch the app** — Grant camera permission when prompted
2. **Point your camera** at something colorful
3. **Listen** as colors become music!

### Panel Controls

| Icon | Location | Panel |
|------|----------|-------|
| 📷 | Left top | **Modes** — Pixel selection & camera |
| 🎨 | Left middle | **Blob** — Cubist mosaic parameters |
| 🌀 | Left bottom | **Acid** — Visual pattern effects |
| 𝄞 | Top | **Scales** — Musical scale selection |
| ♪ | Bottom | **Keys** — Key, octave, figure, tempo |
| ∿ | Right (1/4) | **Synth** — Oscillators, filter, effects, MIDI |
| 💾 | Right (3/4) | **Presets** — Save & load settings |

### Gestures
- **Tap icon** — Open panel
- **Drag from edge** — Slide panel in
- **Double-tap outside panel** — Close all panels
- **Double-tap (no panels)** — Toggle icon visibility

### Tips
- 🎨 **Colorful scenes** produce more varied melodies
- 🔦 **Moving lights** in Brightest mode create dynamic sequences
- 🎹 **Pointer mode** turns the screen into a playable instrument
- 🌀 **Acid patterns** sync beautifully with slower tempos
- 💾 **Save presets** before experimenting wildly!
- 🎹 **Connect a DAW** via USB MIDI for clock-synced performance
- 📶 **BLE MIDI** lets you go wireless — pair as a Bluetooth MIDI device

---

## 🏗️ Architecture

```
trencadis_app/
├── app/src/main/java/com/example/trencadisapp/
│   ├── MainActivity.kt              # Entry point
│   ├── TrencadisViewModel.kt        # State management, MIDI routing, audio + clock wiring
│   ├── audio/
│   │   ├── PdAudioEngine.kt         # Pure Data integration (libpd wrapper)
│   │   └── MusicConstants.kt        # Scales, frequencies, color→sound mappings
│   ├── camera/
│   │   ├── CameraPixelAnalyzer.kt   # YUV→Bitmap→PixelGrid ~30fps, temporal smoothing
│   │   ├── PixelData.kt             # PixelData & PixelGrid data classes
│   │   └── BlobDetector.kt          # Connected components + convex hull blob detection
│   ├── midi/                        # MIDI connectivity layer
│   │   ├── MidiState.kt             # MIDI state data class + MidiOutputMode enum
│   │   ├── MidiBus.kt               # Global event bus for MIDI I/O ports
│   │   ├── MidiClockSource.kt       # USB MIDI Clock receiver → BPM + beat flows
│   │   ├── MidiNoteDestination.kt   # USB MIDI Note On/Off sender
│   │   ├── BleMidiPeripheral.kt     # BLE MIDI GATT server + advertising
│   │   ├── BleNoteDestination.kt    # BLE MIDI Note On/Off sender
│   │   ├── NoteRouter.kt            # Fan-out router to multiple NoteDestinations
│   │   ├── PdNoteDestination.kt     # Wraps PdAudioEngine as a NoteDestination
│   │   └── TrencadisMidiDeviceService.kt  # Virtual MIDI device service (DAW sees app)
│   ├── sync/                        # Transport abstraction interfaces
│   │   ├── ClockSource.kt           # Interface: isConnected, bpmFlow, beatFlow, key/scale
│   │   └── NoteDestination.kt       # Interface: noteOn, noteOff, isAvailable
│   ├── preset/
│   │   └── PresetManager.kt         # JSON preset save/load, bundled preset copy
│   └── ui/
│       ├── TrencadisScreen.kt       # Main composable, 7 edge-triggered panels
│       ├── CubistCanvas.kt          # Canvas: tile shapes + blob polygons
│       ├── AcidPattern.kt           # 10 animated pattern generators
│       ├── BlobModulation.kt        # Blob tuning parameters data class
│       ├── theme/                   # Material 3 theme definitions
│       └── components/
│           ├── ControlPanels.kt     # Modes, Scales, Keys, Synth + MIDI controls
│           ├── BlobPanel.kt         # Cubist blob parameter controls
│           └── PresetPanel.kt       # Preset management UI
├── app/src/main/assets/
│   ├── patch/                       # Pure Data synthesis patches (17 .pd files)
│   └── presets/                     # 6 bundled JSON presets
└── app/src/main/res/xml/
    ├── midi_device_info.xml         # Virtual MIDI device port declaration
    ├── file_paths.xml               # FileProvider paths for preset sharing
    ├── backup_rules.xml             # Backup rules
    └── data_extraction_rules.xml    # Data extraction rules
```

### Tech Stack
- **Kotlin** + **Jetpack Compose** — Modern Android UI
- **CameraX** — Efficient real-time camera analysis
- **pd-for-android** — Pure Data audio synthesis
- **android.media.midi** — Built-in Android MIDI API (USB MIDI)
- **Bluetooth LE GATT** — BLE MIDI peripheral (advertising + GATT server)
- **StateFlow** — Reactive state management
- **Material 3** — Design system components
- **Accompanist Permissions** — Runtime permission handling

---

## 🎼 The Sound Engine

The audio synthesis is powered by **Pure Data (Pd)**, a visual programming language for audio. The patches implement:

- **Polyphonic oscillator mixing** with sub-bass
- **State-variable filter** with resonance and envelope modulation
- **Stereo spatialization** based on pixel position
- **Tempo-synced delay** with feedback
- **Algorithmic reverb** for depth

### Color → Sound Mapping
| Color Property | Sound Parameter |
|----------------|-----------------|
| **Hue** | Note pitch (mapped to scale) |
| **Saturation** | Filter brightness |
| **Brightness** | Volume & shape size |
| **Position X** | Stereo panning |
| **Position Y** | Spatial depth |

---

## 🤝 Contributing

Contributions are welcome! Feel free to:
- 🐛 Report bugs
- 💡 Suggest features
- 🔧 Submit pull requests

---

## 📄 License

This project is open source. See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- **Pure Data** by Miller Puckette
- **pd-for-android** by Peter Brinkmann & contributors
- **Antoni Gaudí** for the trencadís inspiration
- The **Jetpack Compose** team at Google
- The **BLE MIDI** spec by Apple, for wireless MIDI over Bluetooth LE

---

<p align="center">
  <b>🎨 Transform reality into music 🎵</b>
  <br><br>
  <i>Made with ❤️ and broken tiles</i>
</p>
