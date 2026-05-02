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
- **:feature-ai:** `MediaPipePoseAnalyzer` and the upcoming `PostureAnalyzer`.
- **:app:** Jetpack Compose UI, `MainViewModel`, and Localization.

---

## 🚀 3. Key Technical Stabilizations
- **Undistorted Display:** Replay uses `Modifier.requiredSize()` with manual scale calculation to achieve `FILL_CENTER` without squashing the video.
- **Synchronized Overlay:** `SyncEngineImpl` matches video PTS with AI poses using a `TreeMap` with a 1s jitter tolerance.
- **Purge Logic:** AI history is cleared on every toggle to eliminate "ghost skeletons".
- **Resumption:** Automatic return to `BUFFERING` state on app resume during active recording.

---

## 🏹 4. Phase 5: Biomechanical Analysis (The Coach)
The primary objective is to analyze the archer's technique using the AI landmarks:
- **Shoulder Alignment:** Calculate the angle between left/right shoulders (ideal: horizontal).
- **Verticality:** Measure the angle of the main body axis (spine) relative to the ground (ideal: 90°).
- **Head Stability:** Track head movement during the "hold" phase.
- **Visual Coaching:** Dynamic skeleton coloring (e.g., Red shoulders if tilted, Green if aligned).
- **Hold Detection:** Identify the 2-3 second static phase before the shot to trigger specific stability metrics.

---

## 📦 5. Core Implementation Requirements
1. **Geometric Precision:** Use 2D vector math and trigonometry for all angle calculations.
2. **Matrix Mapping:** Use `Matrix.mapPoints` for all skeleton rendering.
3. **Performance:** Posture analysis calculations must be efficient enough to run alongside video decoding.
4. **Lifecycle Aware:** Always release Camera and Codec resources in `ON_PAUSE`.
