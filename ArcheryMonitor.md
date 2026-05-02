# 🎯 Master Prompt : Archery Monitor

## 📌 Context & Goal
Build a high-performance Android application (Target SDK 35) for archers.
**Core Feature:** A delayed video feedback loop (0-60s) allowing the archer to shoot, then walk back to the phone to review their posture with a real-time AI skeleton (Pose Estimation) overlaid on the delayed footage.

---

## 🛠 1. AGENTS.md / Core Instructions
*These principles must govern every line of code generated.*

- **Zero-Copy Architecture:** Avoid CPU-bound image copying. Use `HardwareBuffers` and `Surface` sharing between CameraX, MediaCodec, and MediaPipe.
- **Memory Management:** Strictly use a **Circular Buffer** on internal storage via `mmap` (Memory-Mapped Files) to handle up to 30s of 1080p video without OOM (Out of Memory) crashes.
- **Battery & Thermal:**
    - Dual-Stream: Capture 1080p for recording, downsample to 480p for AI inference.
    - Thermal Throttling: Implement frame-skipping for AI if `PowerManager` reports high thermal status.
- **Coordinate Precision:** Use `android.graphics.Matrix` for mapping MediaPipe normalized landmarks (0-1) to UI Canvas pixels. **Do not use manual ratio calculations.**

---

## 🏗 2. Software Architecture Plan

### Phase 1: Foundations (Clean Architecture)
- Setup **Hilt** for DI.
- Interfaces: `ICameraProvider`, `IBufferManager`, `IPoseAnalyzer`, `ISyncEngine`.
- Use **Kotlin Flow** for reactive state management (IDLE, RECORDING).

### Phase 2: Capture & Storage (The Producer)
- **CameraX Implementation:**
    - Stream A: 1080p -> MediaCodec (H.264) -> Circular Buffer (mmap).
    - Stream B: Low-res -> ImageAnalysis (MediaPipe).
- Features: Digital Zoom, AE/AF Lock, Mirror Flip (for front camera).

### Phase 3: AI Inference & Sync (The Processor)
- **MediaPipe Pose Landmarker:** 2D Landmarks with `Delegate.GPU`.
- **Timestamp Sync:** Use a `SyncEngine` to match the Pose result of frame $T$ with the delayed video frame $T$ being displayed.
- **Matrix Mapping:** Compute the transformation matrix once per frame including rotation, aspect ratio (ContentScale.Fit), and sensor orientation.

### Phase 4: UI & Remote Control (The Consumer)
- **Jetpack Compose UI:**
    - `SurfaceView` for raw video performance.
    - `Canvas` Overlay: Neon yellow skeleton with black outlines for high-contrast visibility in outdoor archery ranges.
- **Remote Trigger:** Intercept `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` to toggle recording (Mi Band 6 & Bluetooth remote compatibility).
- **UI Elements:** Delay slider (0-30s), Buffer status indicator, AI toggle.

---

## 🚨 3. Critical Constraints for the AI Generator
1. **Refuse** any code using `Bitmap.createBitmap` or `BitmapFactory` in the processing loop.
2. **Refuse** manual `Canvas.drawPoint` calculations without using `Matrix.mapPoints`.
3. **Handle Lifecycle:** Ensure all native resources (Camera, MediaCodec, mmap) are released in `onPause` or `onStop`.
4. **Keep Screen On:** Set `FLAG_KEEP_SCREEN_ON` during active recording/review.

---

## 📦 4. Expected Deliverables
1. Folder structure (Modular: `:core`, `:feature-camera`, `:feature-ai`).
2. `BufferManager` implementation using native memory mapping.
3. `SyncEngine` logic for correlating Timestamps between the video buffer and Pose results.
4. Compose `SkeletonOverlay` component using the transformation matrix.
