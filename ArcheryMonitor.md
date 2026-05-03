# 🎯 Master Prompt : Archery Monitor

## 📌 Context & Goal
Build a high-performance Android application for archers, providing **real-time biomechanical coaching**.
**Core Feature:** Delayed video feedback (0-30s) with a synchronized, intelligent AI skeleton (MediaPipe) overlaid on the delayed footage, featuring real-time biomechanical analysis.

---

## 🎮 6. Remote Control Integration
To enhance the archer's workflow, the application supports Bluetooth camera remotes (like AB Shutter 3) by intercepting standard Volume Key events:

- **Volume UP:** Toggle Recording (`onKeyUp`) / Decrease Delay (`LongPress`).
- **Volume DOWN:** Toggle AI Analysis (`onKeyUp`) / Increase Delay (`LongPress`).

**Implementation detail:** 
- Key events are explicitly consumed (returns `true`) to prevent system-level volume bars or interference.
- Hardware key handling uses `onKeyDown` (for long-press adjustment) and `onKeyUp` (to distinguish from standard toggles).

## 🛠 1. Core Architecture Principles
- **Circular Buffer:** `mmap` circular buffer for zero-copy 30s video storage.
- **Unified Clock:** `SystemClock.elapsedRealtimeNanos` for AI-Video timestamp synchronization.
- **Battery Efficiency:** AI analysis is gated: Active ONLY when recording/buffering + AI toggle ON.
- **Modular Analysis:** Analysis modules are pluggable; the user can select an analysis mode (or "None") from a localized list.

---

## 🏗 2. Software Modules
- **:core:** Common interfaces (`ISyncEngine`, `IPostureModule`), `IBufferManager`, `MatrixUtils`.
- **:feature-camera:** `CameraXProvider` (capture), `H264Encoder`, `H264Decoder`.
- **:feature-ai:** `MediaPipePoseAnalyzer` (data provider) + `PostureAnalysisEngine` (biomechanical logic engine).
- **:app:** UI, `MainViewModel`, and Localization.

---

## 🚀 3. Key Technical Stabilizations
- **Undistorted Display:** `FILL_CENTER` layout via `requiredSize()` to avoid stretching.
- **Synchronized Overlay:** `SyncEngineImpl` matches video PTS with AI poses using a `TreeMap` with a 1s jitter tolerance.
- **Ghost Removal:** `SyncEngine` history is purged on AI toggle or session restart.
- **Device Support:** Manual YUV stride handling for correct AI detection on Pixel devices.

---

## 🏹 4. Phase 5: Biomechanical Analysis (The MVP Coach)
The application evaluates posture based on theoretical references. Feedback is trinary (Green/Yellow/Red).

### A. General Posture Module (Lateral/Front View)
* **Shoulder Alignment:** Angle between `L_SHOULDER` and `R_SHOULDER` relative to the horizon.
    * **Green:** < 5° | **Yellow:** 5-12° | **Red:** > 12°.
* **Body Lean (Stability):** Angle between the spine (Neck to Mid-Hips) and the gravity vertical.
    * **Green:** < 3° | **Yellow:** 3-8° | **Red:** > 8°.

### B. Alignment & Draw Module (Lateral View)
* **Planar Alignment:** Angle of the Draw Arm (Wrist-Elbow) relative to the Arrow line.
    * **Green:** 0° to 10° (upwards) | **Yellow:** > 15° or Negative | **Red:** Excessive misalignment.
* **Hold Stability:** Variance of `WRIST` position during the last 2 seconds of the aiming phase.

### C. Release Module (Dynamic)
* **Follow-Through Trajectory:** Comparison of `ELBOW` position at $T_{release}$ and $T_{release} + 500ms$.
    * **Green:** Positive backward movement | **Red:** "Dead" release or forward collapse.

---

## 📦 5. Core Implementation Requirements
1. **Geometric Precision:** Use 2D vector math (atan2/dot products) for all angle calculations.
2. **Dynamic Coloring:** Skeleton segment coloring (Green/Yellow/Red) applied per frame.
3. **Calibration Mode:** UI overlay guide to help the user position the tripod at the correct distance/angle.
4. **Data Persistence:** Log raw Landmark coordinates during sessions for offline refinement.
5. **Localization:** String resources for EN, FR, IT (including "None" for analysis).
6. **Efficiency:** Analysis runs on background threads; heavy calculations are gated by AI toggle.
