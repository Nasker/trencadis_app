# Trencadís — Development Plan

## Vision

Trencadís is a **single-device instrument** — one phone, one performer.
When used standalone it generates music from the camera autonomously.
When connected to a DAW or computer it becomes a **fancy expressive MIDI controller**:
the DAW drives tempo and harmony, Trencadís responds with camera-reactive notes and visuals.

Two connection modes planned — **USB MIDI first**, OSC later:

| Mode | Transport | Status |
|------|-----------|--------|
| **Wired** | USB MIDI | ✅ Implemented |
| **Wireless (BLE)** | Bluetooth LE MIDI | ✅ Implemented |
| **Wireless (OSC)** | WiFi + OSC (UDP) | Deferred, interfaces ready |

All modes make Trencadís a **slave** to the DAW. Visuals remain purely local.

---

## Completed Work

### Phase 1–2: Core App ✅

- Camera feed → pixel grid analysis at ~30fps
- Pure Data audio synthesis engine (17 patches)
- 5 oscillators, resonant filter, FM, chorus, delay, reverb
- 12 musical scales, 12 keys, 7 octaves
- 4 pixel selection modes (Sequence, Brightest, Center, Pointer)
- Cubist canvas rendering with 6 shape types
- 10 acid visual patterns with modulation controls
- Cubist blob mode (connected components + convex hull)
- Preset system (JSON save/load, 6 bundled presets, share via FileProvider)
- 7 edge-triggered panels with gesture handling
- Material 3 theme, edge-to-edge immersive UI

### Phase 3A: USB MIDI ✅

**Goal:** Phone → USB cable → DAW. DAW sends MIDI Clock, phone sends Note On/Off.

**Implemented:**
- `sync/ClockSource.kt` — transport-agnostic clock interface
- `sync/NoteDestination.kt` — transport-agnostic note output interface
- `midi/MidiClockSource.kt` — USB MIDI device detection, MIDI Clock parsing (0xF8/0xFA/0xFB/0xFC), BPM derivation from tick intervals, beat flow
- `midi/MidiNoteDestination.kt` — sends Note On (0x90) / Note Off (0x80) on configured channel
- `midi/NoteRouter.kt` — fan-out to multiple NoteDestinations with monophonic note management
- `midi/PdNoteDestination.kt` — wraps PdAudioEngine as a NoteDestination
- `midi/MidiBus.kt` — singleton event bus for MIDI I/O ports
- `midi/TrencadisMidiDeviceService.kt` — virtual MIDI device service (DAW sees app as MIDI device)
- `midi/MidiState.kt` — MIDI state (enabled, outputMode, channel, deviceName, clock lock, BLE)
- ViewModel wiring: clock flows → tempo override, sequence BANG → noteRouter
- 3 routing modes: INTERNAL (Pd only), MIDI_OUT (MIDI only), BOTH
- 16-channel selection
- MIDI clock indicator dot on Synth icon (green = locked, yellow = enabled, off = disconnected)
- MIDI controls in Synth panel (enable toggle, output mode, channel picker)
- `res/xml/midi_device_info.xml` — virtual device port declaration
- Manifest: `android.software.midi` feature, `TrencadisMidiDeviceService` registration

### Phase 3A.5: BLE MIDI Peripheral ✅

**Goal:** Phone advertises as a BLE MIDI peripheral — DAW/other device connects wirelessly.

**Implemented:**
- `midi/BleMidiPeripheral.kt` — full BLE MIDI GATT server:
  - Advertises MIDI service UUID (`03B80E5A-EDE8-4B33-A751-6CE34EC4C700`)
  - GATT server with MIDI characteristic (`7772E5DB-3868-4112-A1A9-F2669D106BF3`)
  - BLE MIDI packet encoder/decoder (header + timestamp + MIDI bytes framing)
  - Notification support via CCCD descriptor
  - Connection state tracking with callback
  - Adapter name pinning ("Trencadis") for advertising packet fit
- `midi/BleNoteDestination.kt` — sends Note On/Off via BLE GATT notifications
- ViewModel: `setBleEnabled()` starts/stops advertising, adds/removes BLE destination from router
- UI: BLE toggle in Synth panel with connection status indicator (● connected, ◌ advertising, off)
- BLE runtime permissions handling (Android 12+: `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`)
- Manifest: `android.hardware.bluetooth_le` feature, Bluetooth permissions

---

## Transport Abstraction Layer ✅

Two interfaces that both USB MIDI, BLE MIDI, and future OSC implement.
The ViewModel only ever talks to these — it never knows which transport is active.

```kotlin
// sync/ClockSource.kt
interface ClockSource {
    val isConnected: StateFlow<Boolean>
    val bpmFlow: Flow<Float>      // emits whenever BPM changes
    val beatFlow: Flow<Int>       // emits on every beat pulse (for phase lock)
    val keyFlow: Flow<Int>        // 0=C … 11=B  (optional, may never emit)
    val scaleFlow: Flow<Int>      // scale index  (optional, may never emit)
    fun connect()
    fun disconnect()
}

// sync/NoteDestination.kt
interface NoteDestination {
    val label: String             // "Internal Pd", "USB MIDI", "BLE MIDI"
    fun noteOn(pitch: Int, velocity: Int, channel: Int = 1)
    fun noteOff(pitch: Int, channel: Int = 1)
    fun isAvailable(): Boolean
}
```

`TrencadisViewModel` holds:
- `midiClockSource: MidiClockSource` — active clock source
- `noteRouter: NoteRouter` — fan-out to one or more `NoteDestination` instances

Adding OSC later is: implement `ClockSource` + `NoteDestination`, register them — zero changes to ViewModel logic.

---

## Remaining Work

### Phase 3B — Wireless: OSC over WiFi *(deferred)*

**Goal:** Same slave behaviour as USB MIDI but over WiFi — useful for wireless
performance or when a cable is impractical, and when BLE MIDI is not sufficient
(e.g. multi-device broadcast, higher bandwidth control data).

Because the abstraction layer is already in place, implementation is:

1. Add [JavaOSC](https://github.com/hoijui/JavaOSC) dependency (~50 KB, pure Java)
2. **`network/OscClockSource.kt`** implements `ClockSource` — UDP listener on a
   configurable port, parses `/trencadis/tempo`, `/trencadis/beat`, `/trencadis/key`,
   `/trencadis/scale`
3. **`network/OscNoteDestination.kt`** implements `NoteDestination` (optional — sends
   OSC note events to another device if needed)
4. Register in ViewModel alongside `MidiClockSource` — ViewModel code unchanged
5. Small UI: enable toggle + port field + IP indicator in Keys/Modes panel

### OSC address map (DAW → phone)

| Address | Args | Description |
|---------|------|-------------|
| `/trencadis/tempo` | `float bpm` | BPM lock |
| `/trencadis/beat` | `int beat` | Beat pulse for phase correction |
| `/trencadis/key` | `int semitone` | Root key |
| `/trencadis/scale` | `int index` | Scale index |

The DAW side is a small Pd or Max patch that re-broadcasts its MIDI Clock as OSC.

---

### Phase 3C — Ableton Link *(stretch)*

Phase-accurate WiFi sync with Ableton Live and Link-enabled apps.
Implemented as another `ClockSource`. Supersedes OSC beat clock in studio context.

---

### Future Enhancements *(ideas, not committed)*

- **MIDI Clock send** — Trencadís as clock master (currently only receives)
- **Key/scale sync from DAW** — `keyFlow` / `scaleFlow` are defined but not yet wired to MIDI input parsing
- **Polyphonic note routing** — NoteRouter is currently monophonic (auto note-off on pitch change)
- **Preset MIDI state** — Save/restore MIDI routing mode in presets (currently excluded)
- **Visual MIDI feedback** — Show incoming notes on the canvas
- **Audio recording** — Capture performance to audio file
- **Video recording** — Capture canvas + audio to video file
- **Multi-blob audio** — Each blob region triggers its own note (currently single-pixel selection)

---

## Implementation History

| Step | Status |
|------|--------|
| 1. Define `ClockSource` and `NoteDestination` interfaces + `NoteRouter` | ✅ Done |
| 2. Wrap `PdAudioEngine` in `PdNoteDestination` | ✅ Done |
| 3. `MidiClockSource` — detect USB device, parse MIDI Clock, emit BPM + beat | ✅ Done |
| 4. `MidiNoteDestination` — send Note On/Off on configured channel | ✅ Done |
| 5. Wire `MidiClockSource` → ViewModel BPM/beat/key/scale flows | ✅ Done (BPM + beat; key/scale deferred) |
| 6. Wire `NoteRouter` into the existing note-trigger path | ✅ Done |
| 7. MIDI section UI in Synth panel + USB indicator in EdgeHints | ✅ Done |
| 8. `BleMidiPeripheral` — GATT server + advertising + BLE packet codec | ✅ Done |
| 9. `BleNoteDestination` — route notes via BLE notifications | ✅ Done |
| 10. BLE permissions handling + UI toggle in Synth panel | ✅ Done |
| 11. `TrencadisMidiDeviceService` — virtual MIDI device for DAW discovery | ✅ Done |
| 12. *(later)* `OscClockSource` + `OscNoteDestination` — plugs in without ViewModel changes | Deferred |
| 13. *(stretch)* Ableton Link `ClockSource` | Deferred |
