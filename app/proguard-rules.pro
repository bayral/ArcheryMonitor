# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/lib/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# Keep annotations and inject members
-keep class * extends java.lang.annotation.Annotation
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
    @javax.inject.Inject *;
}

# MediaPipe and CameraX usually provide their own rules in AAR.
# We only add dontwarn for known issues or keep specific entry points if needed.
-dontwarn com.google.mediapipe.**
-dontwarn androidx.camera.**
