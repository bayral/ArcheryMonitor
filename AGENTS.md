## Vision & Architecture Principles
- **Project:** Archery Vision Pro (Android) - Delayed feedback with Pose Estimation.
- **Performance:** Strictly "Zero-Copy" using HardwareBuffers/mmap for the 30s circular buffer. No Bitmap allocation in the main loop.
- **Battery:** Dual-stream processing (1080p for buffer, 480p for AI).
- **Coordinate System:** Use `android.graphics.Matrix` for all coordinate mapping (Normalised -> Screen Pixels). Do not use manual ratio calculations.

## Technical Stack
- **Languages:** Kotlin (Coroutines/Flow).
- **IA:** MediaPipe Pose Landmarker (GPU Delegate).
- **UI:** Jetpack Compose (2026).
- **Capture:** CameraX (Target SDK 35).

## Critical Constraints
- **Remote:** Handle `KEYCODE_VOLUME_UP` as the trigger (Mi Band 6 compatibility).
- **Sync:** Pose data must be timestamp-synced with the delayed video frame PTS.
- **Thermal:** Implement frame skipping for AI if SoC temperature rises.