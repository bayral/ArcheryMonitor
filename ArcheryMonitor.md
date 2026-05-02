# 🎯 Master Prompt : Archery Monitor

## 📌 Context & Goal
Build a high-performance Android application (Target SDK 35) for archers.
**Core Feature:** A delayed video feedback loop (0-30s) allowing the archer to shoot, then review their posture with a synchronized AI skeleton (MediaPipe) overlaid on the delayed footage.

---

## 🛠 1. Core Architecture Principles
- **Circular Buffer:** Uses H.264 packets stored in a memory-mapped file (`mmap`) for efficient 30s buffering without OOM.
- **Unified Clock:** Every frame and pose result is timestamped using `SystemClock.elapsedRealtimeNanos()` to ensure accurate replay anchoring.
- **Battery Efficiency:** AI analysis is only triggered when recording is active and the AI toggle is ON.
- **Device Compatibility:** Explicit YUV stride handling ensures correct AI detection on Google Pixel and similar hardware.

---

## 🏗 2. Software Modules
- **:core:** Common interfaces, `ISyncEngine`, `IBufferManager`, and `MatrixUtils`.
- **:feature-camera:** `CameraXProvider` (capture), `H264Encoder`, and `H264Decoder` (replay).
- **:feature-ai:** `MediaPipePoseAnalyzer` using Pose Landmarker (GPU/CPU).
- **:app:** Jetpack Compose UI, `MainViewModel`, and App entry point.

---

## 🚀 3. Key Technical Stabilizations
- **Undistorted Display:** Replay uses `Modifier.requiredSize()` with manual scale calculation to achieve `FILL_CENTER` without squashing the video on ultra-wide screens (e.g. Pixel 7).
- **Synchronized Overlay:** `SyncEngineImpl` matches video PTS with AI poses using a `TreeMap` with a 1s jitter tolerance.
- **State Machine:**
    - `IDLE`: Live preview only.
    - `BUFFERING`: Filling the delay buffer (visual progress indicator).
    - `RECORDING`: Delayed replay with synchronized AI skeleton.
- **Resumption Logic:** Automatically returns to `BUFFERING` upon app resume if a recording was active, as the volatile buffer is cleared.

---

## 🚨 4. UI & Localization
- **High Contrast:** Neon skeleton overlay for outdoor visibility.
- **Localization:** Full support for English, French, and Italian.
- **Safety Padding:** Overlay text positioned (80.dp top) to avoid camera cutouts/notches.

---

## 📦 5. Core Implementation Requirements
1. **No Bitmap Allocation:** Strictly avoid creating bitmaps in the primary analysis/encoding loop.
2. **Matrix Mapping:** Use `Matrix.mapPoints` for skeleton rendering.
3. **Lifecycle Aware:** Always release Camera and Codec resources in `ON_PAUSE`.
