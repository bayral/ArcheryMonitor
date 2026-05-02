## Vision & Architecture Principles
- **Project:** Archery Vision Pro (Android) - Delayed feedback with Pose Estimation.
- **Performance:** strictly "Zero-Copy" logic where possible. Circular buffer using `mmap` for high-performance H.264 packet storage.
- **Battery Optimization:** Gated AI analysis (only active during recording + if toggled ON).
- **Coordinate System:** Strict use of `android.graphics.Matrix` for all coordinate mapping (Normalized -> Screen Pixels).
- **Time Sync:** Mandatory use of `SystemClock.elapsedRealtimeNanos` for unified AI-Video anchoring.

## Technical Stack
- **Languages:** Idiomatic Kotlin (Coroutines/Flow).
- **IA:** MediaPipe Pose Landmarker (GPU with CPU fallback).
- **UI:** Jetpack Compose (2026) using `SurfaceView` and `requiredSize` for undistorted `FILL_CENTER`.
- **Capture:** CameraX with dual-stream ImageAnalysis/Preview.

## Critical Stabilizations (Lessons Learned)
- **Pixel/Stride Support:** Must manually handle YUV plane row strides in image conversions to avoid skewed AI input on Pixel devices.
- **Layout Deformation:** Use `requiredSize` and manual scale calculation to replicate `FILL_CENTER` without stretching on ultra-wide screens.
- **Temporal Sync:** Use a 1s jitter threshold in `SyncEngine` and purge history on AI toggle to eliminate "ghost skeletons".
- **Lifecycle:** Explicitly unbind CameraX and stop Decoders on `ON_PAUSE` to prevent "BufferQueue abandoned" errors.
- **State Machine:** Handle `IDLE`, `BUFFERING`, and `RECORDING` transitions with visual user feedback (progress circle).

## Critical Constraints
- **Remote:** Handle `KEYCODE_VOLUME_UP` as the trigger (Bluetooth remote compatibility).
- **Localization:** Support for English, French, and Italian.
- **Sync:** Pose results MUST match delayed frame PTS via `SyncEngine` TreeMap lookup.
