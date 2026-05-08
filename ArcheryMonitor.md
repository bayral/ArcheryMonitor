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

---

## 🛡️ 0. CRITICAL INVARIANTS (DO NOT MODIFY)
**To preserve the Video Replay/Sync stability, these rules are ABSOLUTE:**
- **H264Decoder Loop:** The decoding loop MUST use a `while(dequeueOutputBuffer >= 0)` pattern to drain **all** available buffers. Replacing it with an `if` causes buffer saturation and breaks the replay.
- **Non-Blocking Logic:** Never introduce `runBlocking` or manual `delay()` inside the Codec `stop()` or `release()` methods. This causes UI freezes and synchronization loss.
- **Clock Reference:** Always use `SystemClock.elapsedRealtimeNanos() / 1000` as the unique time source for both Encoder and AI. Any other source (like `System.currentTimeMillis()`) will cause the skeleton to "drift" from the video.

## 🛠 1. Core Architecture Principles
- **Circular Buffer:** `mmap` circular buffer for zero-copy 30s video storage.
- **Unified Clock:** `SystemClock.elapsedRealtimeNanos` for AI-Video timestamp synchronization.
- **Efficiency & Robustness:** 
    - **Frame Skipping:** AI analysis processes 1 frame out of 2.
    - **Resolution:** Input images are resized to **640px** width for optimal landmark detection.
    - **Optimized Conversion:** Manual YUV to ARGB conversion.
    - **Tilt Compensation:** Automatic rotation matrix applied to landmarks based on accelerometer (Roll).
    - **Auto-Brightness:** Forced max brightness via light sensor for outdoor visibility.
- **Modular Analysis:** Analysis modules use centralized `AnalysisColors` (GREEN/RED/YELLOW) and a shared `AnalysisResult` data class.

---

## 🏗 2. Software Modules
- **:core:** Common interfaces, `OrientationMonitor`, `MatrixUtils`, `PoseUtils`.
- **:feature-camera:** Hardware-accelerated H.264 Encoder/Decoder.
- **:feature-ai:** MediaPipe provider + `GeneralPostureModule` (Gaussian scoring).
- **:app:** UI (Immersive Mode), Shot Counter, and Badge system.

---

## 🏹 4. Biomechanical Analysis & Scoring
The evaluation system uses a **Gaussian Curve** model for smooth, high-precision scoring.

### A. General Posture Module (Advanced)
* **Weights:** Shoulders (30%), Arms (50%), Vertical Axis (20%).
* **Stability Filter:** Progressive penalty during movement; score climbs to 100% only when the archer is static (Aiming Phase).
* **Smoothing:** Final score uses an **Exponential Moving Average (EMA)** to filter frame-to-frame jitters.
* **Release Detection (Recoil):** 
    - Triggered by `drawArmElbow` velocity (threshold: 0.05).
    - **Safety:** Archer must be stable and bow must be drawn (`minDrawDistance`) to fire a shot event.
    - **Dynamism Bonus:** Up to **+10% score** based on recoil speed (follow-through).
    - **Freeze:** Display and Badge validation are frozen for **3 seconds** after release.

### B. Gamification: Trophy Room (Badges)
A progression system encourages consistent form:
- **🎯 Perfect Shot (1s):** Maintain > 95% score for 1 continuous second.
- **🏅 Solid Form (5s):** Maintain > 85% score for 5 continuous seconds.
- **🗿 Statue (15s):** Maintain > 80% score for 15 continuous seconds.
*Badges use the frozen release score for validation.*

---

## 📦 5. Core Implementation Requirements
1. **Geometric Precision:** Use 2D vector math (atan2/dot products) for all angle calculations.
2. **Dynamic Coloring:** Skeleton segment coloring (Green/Yellow/Red) applied per frame.
3. **Calibration Mode:** UI overlay guide to help the user position the tripod at the correct distance/angle.
4. **Data Persistence:** Log raw Landmark coordinates during sessions for offline refinement.
5. **Localization:** String resources for EN, FR, IT (including "None" for analysis).
6. **Efficiency:** Analysis runs on background threads; heavy calculations are gated by AI toggle.
