## Vision & Architecture Principles
- **Project:** Archery Vision Pro (Android) - Delayed feedback with Pose Estimation.
- **Performance:** strictly "Zero-Copy" logic. Circular buffer using `mmap`.
- **Battery Optimization:** Gated AI analysis (active only during recording + if toggled ON).
- **Coordinate System:** Strict use of `android.graphics.Matrix` (Normalized -> Screen Pixels).
- **Time Sync:** Mandatory use of `SystemClock.elapsedRealtimeNanos` for AI-Video anchoring.

## Technical Stack
- **IA:** MediaPipe Pose Landmarker (GPU with CPU fallback).
- **UI:** Jetpack Compose (2026) using `requiredSize` for `FILL_CENTER`.
- **Logic:** Idiomatic Kotlin (Coroutines/Flow).

## Coaching & Analysis Goals (Phase 5)
- **Biometrics:** Shoulder horizontal alignment, spine verticality (90°), and hold stability.
- **Visual Feedback:** Contextual skeleton coloring and real-time posture metrics.
- **Precision:** Use vector-based math for all biomechanical diagnostics.

## Critical Stabilizations (Lessons Learned)
- **Pixel/Stride Support:** Manual YUV stride handling is mandatory for Pixel devices.
- **Layout Deformation:** Forced `FILL_CENTER` layout to prevent squashing on ultra-wide screens.
- **Temporal Sync:** 1s jitter threshold + history purge on toggle to avoid AI ghosting.
- **Lifecycle Management:** Explicit teardown on `ON_PAUSE` is required for Surface stability.

## Critical Constraints
- **Remote:** Handle `KEYCODE_VOLUME_UP` as the trigger.
- **Localization:** Full support for English, French, and Italian.
