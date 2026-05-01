# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/lib/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# Keep Hilt and Dagger classes
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.** { *; }

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }
