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

### 🌀 **Acid Visual Patterns**
- **12 psychedelic patterns** — Ripple, Spiral, Plasma, Checkerboard, Diamond Wave, Interference, Vortex, Cellular, Fractal Noise, Wave Interference, Moiré, Kaleidoscope
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

### 📱 **Intuitive Touch UI**
- **Edge-triggered panels** — Drag from edges or tap icons
- **Double-tap to dismiss** — Quick panel closing
- **Front/back camera** — Switch with one tap
- **Hide UI** — Double-tap for immersive mode

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
| 📷 | Left (1/4) | **Modes** — Pixel selection & camera |
| 🌀 | Left (3/4) | **Acid** — Visual pattern effects |
| 𝄞 | Top | **Scales** — Musical scale selection |
| ♪ | Bottom | **Keys** — Key, octave, figure, tempo |
| ∿ | Right (1/4) | **Synth** — Oscillators, filter, effects |
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

---

## 🏗️ Architecture

```
trencadis_app/
├── app/src/main/java/com/example/trencadisapp/
│   ├── MainActivity.kt              # Entry point
│   ├── TrencadisViewModel.kt        # State management & business logic
│   ├── audio/
│   │   ├── PdAudioEngine.kt         # Pure Data integration
│   │   └── MusicConstants.kt        # Scales, frequencies, mappings
│   ├── camera/
│   │   ├── CameraPixelAnalyzer.kt   # Real-time pixel extraction
│   │   ├── PixelData.kt             # Pixel color/position data
│   │   └── PixelGrid.kt             # Grid management
│   ├── preset/
│   │   └── PresetManager.kt         # JSON preset save/load
│   └── ui/
│       ├── TrencadisScreen.kt       # Main composable screen
│       ├── CubistCanvas.kt          # Custom canvas rendering
│       ├── AcidPattern.kt           # Visual pattern generators
│       └── components/
│           ├── ControlPanels.kt     # Modes, Scales, Keys, Synth panels
│           ├── AcidPanel.kt         # Acid effect controls
│           └── PresetPanel.kt       # Preset management UI
└── app/src/main/assets/patch/
    └── *.pd                          # Pure Data synthesis patches
```

### Tech Stack
- **Kotlin** + **Jetpack Compose** — Modern Android UI
- **CameraX** — Efficient real-time camera analysis
- **pd-for-android** — Pure Data audio synthesis
- **StateFlow** — Reactive state management
- **Material 3** — Design system components

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

---

<p align="center">
  <b>🎨 Transform reality into music 🎵</b>
  <br><br>
  <i>Made with ❤️ and broken tiles</i>
</p>
