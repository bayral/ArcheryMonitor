# 🎯 Archery Monitor

Archery Monitor is a high-performance Android application designed for archers to improve their technique through **synchronized delayed video feedback**. It allows an archer to shoot an arrow, then walk back to their phone to review their posture with a real-time AI skeleton overlaid on the delayed footage.

## ✨ Key Features

*   **⏱️ Adjustable Delayed Replay:** Configure a delay (1s to 30s) to review your shots immediately after shooting.
*   **🤖 Advanced AI Analysis:** Real-time biomechanical analysis of shoulders, arms, and body axis, dynamically adapted to your laterality.
*   **📊 Live Scoring & Feedback:** Immediate visual feedback with a percentage score and color-coded skeleton (Green/Yellow/Red).
*   **🏆 Achievement System:** Unlock badges (Perfect Shot, Solid Form, Statue) based on your posture stability.
*   **📷 Dual-Camera Support:** Toggle between front and back cameras with automatic orientation and mirror handling.
*   **🚀 High Performance:** Optimized AI pipeline with **frame skipping**, **image downscaling**, and **GPU-to-CPU fallback**.
*   **🌍 Multi-language Support:** Fully localized in English, French, and Italian.

## 🛠 Technical Architecture

The application is built using a modular Clean Architecture approach:

- **`:core`**: Contains the central synchronization engine, memory-mapped buffer management, and `PoseUtils` for orientation detection.
- **`:feature-camera`**: Manages the CameraX pipeline, H.264 hardware encoding, and real-time playback decoding.
- **`:feature-ai`**: Handles AI inference using MediaPipe Pose Landmarker with optimized YUV conversion.
- **`:app`**: Modern UI built with Jetpack Compose following the MVI (Model-View-Intent) pattern.

### 🔬 Engineering Challenges & Solutions

*   **Pixel-Perfect Sync:** We use a unified high-precision system clock across the AI and Video pipelines to ensure the skeleton "sticks" to the archer.
*   **CPU Optimization:** To maintain high FPS on all devices, we process 1 AI frame out of 2 and downscale input images to 480px.
*   **Robustness:** Automatic fallback to CPU delegate ensures the analysis never stops, even if the GPU driver encountered an error.
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

### Installation
1. Clone the repository.
2. Open the project in **Android Studio (Ladybug or newer)**.
3. Build and run the `app` module on your device.

## 📜 License

This project is licensed under the MIT License - see the LICENSE file for details.
