# 🎯 Archery Monitor

Archery Monitor is a high-performance Android application designed for archers to improve their technique through **synchronized delayed video feedback**. It allows an archer to shoot an arrow, then walk back to their phone to review their posture with a real-time AI skeleton overlaid on the delayed footage.

## ✨ Key Features

*   **⏱️ Adjustable Delayed Replay:** Configure a delay (1s to 30s) to review your shots immediately after shooting.
*   **🎥 WYSIWYG Replay Mode:** Review your shots frame-by-frame with synchronized AI analysis. Use instant navigation and export your best clips as high-quality MP4 files directly to your "Movies" folder.
*   **🤖 Advanced AI Analysis:** Real-time biomechanical analysis of shoulders, arms, and body axis, with automatic **device tilt compensation**.
*   **📊 Gaussian Scoring & Feedback:** Smooth, non-linear scoring system that rewards perfection and filters micro-jitters with an **Exponential Moving Average (EMA)**.
*   **🏹 Release Detection:** Automatically detects shots based on recoil velocity, with a **dynamism bonus** for clean follow-throughs and a 3-second score freeze.
*   **🎯 Interactive Calibration:** Use the integrated archer silhouette guide (selectable for left/right-handed archers) to align your position perfectly with the camera.
*   **🏆 Achievement System:** Unlock badges (Perfect Shot, Solid Form, Statue) based on your shot quality and stability.
*   **📷 Immersive Experience:** Full-screen mode and **auto-brightness** adjustment for optimal outdoor visibility.
*   **🚀 High Performance:** Optimized AI pipeline with **frame skipping** and **640px resolution** analysis for the best balance of speed and precision.

## 🛠 Technical Architecture

The application is built using a modular Clean Architecture approach:

- **`:core`**: Central synchronization engine, memory-mapped buffer management, and `OrientationMonitor` for accelerometer-based tilt compensation.
- **`:feature-camera`**: Manages the CameraX pipeline, H.264 hardware encoding, and real-time playback decoding.
- **`:feature-ai`**: Handles AI inference using MediaPipe Pose Landmarker with optimized YUV conversion and Gaussian scoring modules.
- **`:app`**: Modern UI built with Jetpack Compose featuring an immersive mode and Shot Counter.

### 🔬 Engineering Challenges & Solutions

*   **Pixel-Perfect Sync:** We use a unified high-precision system clock across the AI and Video pipelines to ensure the skeleton "sticks" to the archer.
*   **Precision AI:** We process 1 AI frame out of 2 and use a **640px** resolution for landmark detection to maintain accuracy at distance.
*   **Hardware Integration:** Auto-brightness uses the device light sensor to force max visibility in direct sunlight.
*   **Zero-Lag Circular Buffer:** Video packets are stored in a memory-mapped file (`mmap`) to support up to 30 seconds of HD video without OOM.

## 📱 Remote Control (Bluetooth)

You can control Archery Monitor wirelessly using standard Bluetooth camera remotes (e.g., AB Shutter 3):

- **Volume UP:** 
    - **Single Press:** Toggle Recording ON/OFF.
    - **Long Press:** Decrease playback delay (min 1s).
- **Volume DOWN:** 
    - **Single Press:** Toggle AI Analysis ON/OFF.
    - **Long Press:** Increase playback delay.

The remote events are consumed by the app, so you won't see system volume bars or menus while using these features.

### Prerequisites
- Android device running **Android 11 (API 30)** or higher.
- Recommended: High-performance SoC for smooth AI inference (e.g., Pixel 6+, Galaxy S21+).

## 📥 Installation

### Option 1: APK Download (Recommended)
You can download the latest pre-compiled APK directly from our GitHub releases:
1. Navigate to the [Releases](https://github.com/bayral/ArcheryMonitor/releases) page.
2. Download the latest `.apk` file.
3. Install the APK on your device (you may need to allow "Install from unknown sources" in your Android settings).

### Option 2: Local Build
1. Clone the repository.
2. Open the project in **Android Studio (Ladybug or newer)**.
3. Build and run the `app` module on your device.

## 📜 License

This project is licensed under the MIT License - see the LICENSE file for details.
