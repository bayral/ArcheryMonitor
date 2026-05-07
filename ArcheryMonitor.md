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
    - **Downscaling:** Input images are resized to **480px** width before AI inference.
    - **Optimized Conversion:** Manual YUV to ARGB conversion (avoiding JPEG overhead).
    - **GPU-to-CPU Fallback:** Automatic switch to CPU delegate if GPU acceleration fails during runtime.
- **Modular Analysis:** Analysis modules are pluggable; the user can select an analysis mode (or "None") from a localized list.

---

## 🏗 2. Software Modules
- **:core:** Common interfaces (`ISyncEngine`, `IPostureModule`), `IBufferManager`, `MatrixUtils`, `PoseUtils`.
- **:feature-camera:** `CameraXProvider` (capture), `H264Encoder`, `H264Decoder`.
- **:feature-ai:** `MediaPipePoseAnalyzer` (data provider) + `GeneralPostureModule` (advanced biomechanical logic).
- **:app:** UI, `MainViewModel` (Badge system), and Localization.

---

## 🚀 3. Key Technical Stabilizations
- **Undistorted Display:** `FILL_CENTER` layout via `requiredSize()` to avoid stretching.
- **Synchronized Overlay:** `SyncEngineImpl` matches video PTS with AI poses using a `TreeMap`.
- **Ghost Removal:** `SyncEngine` history is purged on AI toggle or session restart.
- **Device Support:** Manual YUV stride handling for correct AI detection on Pixel devices.

---

## 🏹 4. Phase 5: Biomechanical Analysis & Gamification
The application evaluates posture based on theoretical references. Feedback is trinary (Green/Yellow/Red).

### A. General Posture Module (Advanced)
* **Shoulder Alignment:** Angle between `L_SHOULDER` and `R_SHOULDER` relative to the horizon.
* **Arm Alignment:** Angle between shoulder and elbow for both Bow and Draw arms.
* **Vertical Axis:** Alignment between Mid-Shoulders and Mid-Hips.
* **Orientation Aware:** Dynamically adjusts indices based on user settings and orientation (Facing vs Back to camera).

### B. Gamification: Trophy Room (Badges)
A progression system encourages consistent form:
- **🎯 Perfect Shot (1s):** Maintain > 95% score for 1 continuous second.
- **🏅 Solid Form (5s):** Maintain > 85% score for 5 continuous seconds.
- **🗿 Statue (15s):** Maintain > 80% score for 15 continuous seconds.
*Badges are notified via a top-screen banner and persisted in Settings.*

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
