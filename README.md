# SoundTune

![SoundTune](doc/ed57005e16c2455fab4dd6fbe21a6927.png)

An advanced Android audio acoustic analysis and tone generation tool designed for car audio DSP and 2-channel home stereo tuning.  
Measures full-spectrum frequency response curves (20Hz–20kHz), residual room/cabin decay time (canceling Bluetooth latency), speaker time alignments, and generates arbitrary reference sine waves with independent L/R channel routing.

---

## Background

When tuning a car DSP or home audio system, sound quality often varies dramatically depending on the acoustic space. Bass-heavy music can sound boomy due to vehicle cabin standing waves or door resonance, while vocal frequencies may suffer cancellation from glass reflections or crossover phase issues.  

Tuning by ear alone without a solid acoustic reference frequently leads to recurring trial-and-error mistakes. **SoundTune** was built as a dedicated acoustic measurement and reference generator tool that runs directly on Android—even over wireless Bluetooth—allowing audio enthusiasts to measure, compare, and optimize their listening environments.

> 📖 [Read the development background story (Blog Post in Japanese)](https://amekusa03.com/essays/2026-07-09-soundtune)

---

## Key Features

| Tab | Feature | Description |
|:---|:---|:---|
| 📈 **Sweep** | **Log-Sweep Frequency Response & Curve Overlay** | 4-second logarithmic sine sweep (20Hz–20kHz) with sensitive auto-sync. Saves measured curves by name and overlays multiple curves (e.g. Home Stereo vs Car Audio, Before EQ vs After EQ). |
| ⏱️ **Decay** | **Pulse Duration & Tail Resonance Measurement** | Emits a precise 500ms tone pulse and measures the acoustic duration and tail lingering time (+XX ms). Completely cancels out Bluetooth transmission delays (~300ms) and volume variance. |

---

## Detailed Features

### 1. 📈 Sweep Frequency Response & Curve Overlay Comparison

- **Log-Sine Sweep**: Plays a 4-second logarithmic chirp spanning from 20 Hz to 20,000 Hz.
- **Bluetooth Latency Compensation**: Emits a 1.5 kHz lead-in sync chirp before the sweep. The analyzer automatically detects the chirp to synchronize the acoustic timeline, rendering Bluetooth transmission latency (200–400ms) completely harmless.
- **1/3 Octave Standard Bands**: Extracts sound pressure levels (dBFS) across 29 ISO standard 1/3-octave center frequencies.
- **Named Curve Storage & Overlay**:
  - Save measured curves with custom names (e.g., *Home Reference Stereo*, *Car_Before_EQ*, *Car_After_EQ*, *Door_L*).
  - Overlay multiple curves in distinct colors on a logarithmic frequency response graph.
  - Toggle visibility with checkboxes to visually pinpoint cabin resonance peaks (+dB) or phase-cancellation dips (-dB).

### 2. ⏱️ Residual Tail & Decay Time Measurement (Bluetooth-Immune)

- **The Problem**: Car cabin bass often suffers from lingering door/trunk resonance ("boomy" bass). However, measuring absolute decay over Bluetooth is skewed by transmission latency (~300ms).
- **The Solution (Pulse Duration Comparison)**:
  - Emits a precise **500 ms reference pulse**.
  - Measures the actual acoustic duration ($T_{\text{received}}$) at half-power (-6dB) threshold on both rising and falling edges.
  - Bluetooth delay cancels out automatically: $\text{Tail} = T_{\text{received}} - 500\text{ ms}$.
  - Independent of volume level, accurately reporting tail lingering time (+XX ms), $T_{20}$ decay, and acoustic tuning advice (Tight / Normal / Boomy).

### 3. 🎵 Multi-Tone Generator with Channel Routing

- Real-time audio synthesis using `AudioTrack` (PCM 16-bit stereo at 44.1 kHz).
- 5 independent tone slots with per-slot frequency input and channel selector:
  - `L`: Left channel only
  - `L+R`: Center / Stereo
  - `R`: Right channel only
- Instant frequency presets for car audio crossover and EQ points (`31.5Hz`, `50Hz`, `80Hz`, `160Hz`, `1kHz`, `4kHz`, `10kHz`).
- One-touch **Stop All** button.

### 4. 📐 5ch Time Alignment & Delay Compensation

- Supports standard car audio 5-channel layout:
  - **FL**: Front Left (L)
  - **FR**: Front Right (R)
  - **RL**: Rear Left (L)
  - **RR**: Rear Right (R)
  - **SW**: Subwoofer (L+R / 120Hz band)
- Computes relative arrival time differences and outputs recommended DSP delay values (`+X.XX ms` / `XX.X cm`) aligned to the farthest speaker.

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Platform | Android (minSdk: 23 / compileSdk: 37 / targetSdk: 37) |
| Language | Kotlin |
| Build System | Gradle 9.6 + Android Gradle Plugin 9.4 |
| UI Architecture | Single Activity + Navigation Component + ViewPager2 + TabLayout + View Binding + Material 3 |
| Audio I/O | `AudioTrack` (Low-latency PCM stereo) / `AudioRecord` (`UNPROCESSED` / `VOICE_RECOGNITION` raw input) |
| Signal Processing | Goertzel Algorithm, Sliding RMS Windows, Logarithmic Sweep Synthesis, Half-power Edge Detection |

---

## File Structure

```
app/src/main/java/com/kusa/soundtune/
├── MainActivity.kt               # Edge-to-edge container & Toolbar setup
├── FirstFragment.kt              # Host Fragment managing 4-tab ViewPager2
├── AudioSharedViewModel.kt       # Shared ViewModel hosting engines and state
├── AudioEngine.kt                # Real-time stereo sine wave synthesizer & pulse generator
├── GeneratorFragment.kt          # Tab 1: 5-tone generator UI with L/R routing & presets
├── SweepAcousticUtils.kt         # Log sweep synthesis, chirp sync detection & 1/3-octave extraction
├── FrequencyGraphView.kt         # Custom View: Logarithmic frequency response & curve overlay
├── SweepMeasurementEngine.kt    # Engine executing sweep playback & recording
├── LevelMeterFragment.kt         # Tab 2: Sweep frequency measurement & curve comparison UI
├── AcousticUtils.kt              # RMS, dBFS, Goertzel filter, pulse duration & delay calculations
├── DecayMeasurementEngine.kt    # Engine executing pulse duration comparison
├── DecayMeasureFragment.kt       # Tab 3: Decay & residual tail measurement UI
├── DelayMeasurementEngine.kt    # Engine measuring 5ch speaker acoustic arrival times
└── DelayMeasureFragment.kt       # Tab 4: 5ch time alignment measurement & DSP delay calculator
```

---

## Setup & Running

1. Open this project in **Android Studio** (Ladybug / Koala or newer recommended).
2. Run **Gradle Sync** ("Sync Project with Gradle Files").
3. Connect your Android device (Android 8.0+ / API 26+) or launch an emulator.
4. Ensure **Microphone Permission** (`RECORD_AUDIO`) is granted when prompted on first measurement.
5. Place your phone at your ear listening position (e.g. driver seat headrest) and start measuring!
