# SoundTune

![SoundTune](doc/ed57005e16c2455fab4dd6fbe21a6927.png)

An Android application that generates reference audio tones for car DSP and equalizer tuning.  
Generates sine waves of arbitrary frequencies across up to 5 simultaneous tones in real time, with automatic audio output device detection.

---

## Background

When tuning a car DSP system, audio quality varies significantly depending on the music track—bass-heavy genres like jazz often sound boomy.  
While a common rule of thumb is to cut frequencies below 80–200 Hz, repeating adjustments without a proper "reference setup" often leads to the same mistakes.  
To address this, I built a custom Android tool that generates precise sine waves across any frequency from low to high.

> 📖 [Read the development background story (Blog Post in Japanese)](https://amekusa03.com/essays/2026-07-09-soundtune)

---

## Features

| Feature | Description |
|---------|-------------|
| 🎵 **Sine Wave Generation** | Real-time generation and playback of audio at any frequency (Hz) |
| 🎶 **5-Tone Simultaneous Playback** | Play different frequencies simultaneously using 5 independent tone slots |
| 🔊 **Output Device Detection** | Real-time detection and display of audio output destination (Built-in Speaker, Wired, Bluetooth, etc.) |

### Sine Wave Generation / 5-Tone Simultaneous Playback

- Real-time audio streaming using `AudioTrack` with PCM 16-bit mono at a 44.1 kHz sampling rate
- Input frequencies into 5 slots (Tone 1–5) with independent Play/Stop controls
- Each slot runs on an independent `AudioTrack` instance and background thread

### Output Device Detection

- Automatically detects current audio destination (built-in speaker, wired headphones, Bluetooth speaker, etc.)
- Real-time updates using `AudioManager`

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Platform | Android (minSdk: 26 / targetSdk: 37) |
| Language | Kotlin |
| Build System | Gradle (Kotlin DSL) |
| UI | AppCompat + Navigation Component + View Binding + Material Design |
| Audio | `AudioTrack` / `AudioManager` |

---

## File Structure

```
app/src/main/java/com/kusa/soundtune/
├── AudioEngine.kt    # Audio generation logic controlling AudioTrack across 5 slots
├── MainActivity.kt   # Main activity (edge-to-edge layout, navigation host)
└── FirstFragment.kt  # Main UI (5-slot playback/stop control, device detection)
```

---

## Setup

1. Open this project in **Android Studio** (Koala or newer recommended).
2. Run **Gradle Sync** ("Sync Project with Gradle Files").
3. Build and deploy to a physical device or emulator running **Android 8.0 (API 26) or higher**.
