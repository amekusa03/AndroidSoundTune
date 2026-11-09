# SoundTune

![SoundTune](doc/ed57005e16c2455fab4dd6fbe21a6927.png)

An advanced Android acoustic analysis tool designed for car audio DSP and 2-channel home stereo tuning.  
Measures full-spectrum frequency response curves (20Hz–20kHz) with multi-curve overlay comparison and evaluates residual room/cabin decay time using a pulse duration comparison method that completely cancels out Bluetooth wireless latency.

---

## Background

When tuning a car DSP or home audio system, sound quality often varies dramatically depending on the acoustic space. Bass-heavy music frequently sounds boomy due to vehicle cabin standing waves or door resonance, while vocal frequencies may suffer cancellation from glass reflections or crossover phase issues.  

Tuning by ear alone without an objective reference leads to recurring trial-and-error mistakes. **SoundTune** was built as a dedicated acoustic measurement tool that runs directly on Android—even over wireless Bluetooth—allowing audio enthusiasts to measure, compare, and optimize their listening environments.

> 📖 [Read the development background story (Blog Post in Japanese)](https://amekusa03.com/essays/2026-07-09-soundtune)

---

## Key Features

| Tab | Feature | Description |
|:---|:---|:---|
| 📈 **Sweep** | **Log-Sweep Frequency Response & Multi-Curve Overlay** | 4-second logarithmic sine sweep (20Hz–20kHz) with sensitive auto-sync. Saves measured curves with custom names and overlays multiple curves on a single graph (e.g., Home Stereo vs Car Audio, Before EQ vs After EQ). |
| ⏱️ **Decay** | **Pulse Duration & Tail Resonance Measurement** | Emits a precise 500ms reference pulse and measures acoustic duration and tail lingering time (+XX ms). Completely cancels out Bluetooth transmission latency (~300ms) and volume variance. |

---

## Detailed Features

### 1. 📈 Sweep Frequency Response & Curve Overlay Comparison

- **Logarithmic Sine Sweep**: Plays a 4-second log sweep spanning 20 Hz to 20,000 Hz.
- **Wireless Bluetooth Auto-Sync**:
  - Emits a synchronized 1.5 kHz lead-in chirp before the sweep.
  - The analyzer tracks ambient noise and uses relative energy jumps to synchronize the acoustic timeline, rendering Bluetooth transmission latency (200–400ms) completely harmless.
  - High-sensitivity detection reliably captures sweeps even at quiet, neighbor-friendly playback volumes.
- **Channel Routing**: Supports `L`, `L+R` (Center/Stereo), and `R` channel output for testing individual speakers or stereo balance.
- **1/3 Octave Standard Bands**: Extracts sound pressure levels (dBFS) across 29 ISO standard 1/3-octave center frequencies (25 Hz to 20 kHz).
- **Named Curve Storage & Overlay View**:
  - Save measured curves with custom labels (e.g., *Home Reference*, *Car_Before_EQ*, *Car_After_EQ*, *Front_L*).
  - Overlay multiple curves in distinct palette colors on an audio-standard logarithmic frequency graph.
  - Toggle curve visibility with checkboxes to directly compare your car's acoustics against your home stereo or verify the effect of DSP equalizer adjustments.

### 2. ⏱️ Residual Tail & Decay Time Measurement (Bluetooth-Immune)

- **The Challenge**: Vehicle cabins often suffer from prolonged door panel or trunk resonance ("boomy" bass). However, measuring absolute sound decay over wireless Bluetooth is skewed by transmission delay (~300ms).
- **The Solution (Pulse Duration & Tail Comparison)**:
  - Emits an exact **500 ms reference pulse**.
  - Measures the actual received acoustic duration ($T_{\text{received}}$) using symmetric half-power (-6dB) threshold points on both rising and falling edges.
  - Transmission latency cancels out mathematically:
    $$\text{Tail (Lingering Time)} = T_{\text{received}} - 500\text{ ms}$$
  - Independent of playback volume, accurately calculating tail resonance (+XX ms), $T_{20}$ decay rate, and providing tailored acoustic advice (*Tight / Normal / Boomy*).

---

## Practical Tuning Workflow

1. **Benchmark at Home**:
   - Place your phone at your home listening sweet spot.
   - Run a Sweep measurement and save it as **`Home_Reference`**.
2. **Measure Your Car**:
   - Connect via Bluetooth in your car, place your phone at the driver's headrest position.
   - Run a Sweep measurement and save it as **`Car_Before_EQ`**.
3. **Compare & Adjust**:
   - Enable both curves on the graph to instantly spot differences (e.g., cabin standing wave peak at 63Hz, mid-range dip at 3kHz).
   - Switch to the **Decay** tab to test low frequencies (40Hz–125Hz) and see if specific bands have excessive tail resonance (>100ms) that require deadening materials or parametric EQ cuts.

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Platform | Android (minSdk: 23 / compileSdk: 37 / targetSdk: 37) |
| Language | Kotlin |
| Build System | Gradle 9.6 + Android Gradle Plugin 9.4 |
| UI Architecture | Single Activity + ViewPager2 + TabLayout + View Binding + Material 3 |
| Audio Output | `AudioTrack` (PCM 16-bit stereo at 44.1 kHz, static & streaming modes) |
| Audio Input | `AudioRecord` (`UNPROCESSED` / `VOICE_RECOGNITION` raw microphone input) |
| Signal Processing | Goertzel Algorithm, Dynamic Noise Floor Tracking, Logarithmic Sweep Synthesis, Half-power Edge Detection |

---

## File Structure

```
app/src/main/java/com/kusa/soundtune/
├── MainActivity.kt               # Edge-to-edge container & Toolbar setup
├── FirstFragment.kt              # Host Fragment managing 2-tab ViewPager2
├── AudioSharedViewModel.kt       # Shared ViewModel hosting engines and state
├── AudioEngine.kt                # Stereo PCM generation and pulse control
├── SweepAcousticUtils.kt         # Log sweep synthesis, chirp sync detection & 1/3-octave extraction
├── FrequencyGraphView.kt         # Custom View: Logarithmic frequency graph & multi-curve overlay
├── SweepMeasurementEngine.kt    # Engine executing sweep playback & recording
├── LevelMeterFragment.kt         # Tab 1: Sweep measurement, curve naming & overlay list UI
├── AcousticUtils.kt              # RMS, dBFS, Goertzel filter & pulse duration calculations
├── DecayMeasurementEngine.kt    # Engine executing pulse duration comparison
└── DecayMeasureFragment.kt       # Tab 2: Decay & residual tail resonance measurement UI
```

---

## Setup & Running

1. Open this project in **Android Studio** (Ladybug / Koala or newer recommended).
2. Run **Gradle Sync** ("Sync Project with Gradle Files").
3. Connect your Android device (Android 8.0+ / API 26+) or launch an emulator.
4. Ensure **Microphone Permission** (`RECORD_AUDIO`) is granted when prompted.
5. Position your device at your ear listening position and start tuning!
