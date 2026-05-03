# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/lib/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# MediaPipe conservation
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Project Interfaces and AI logic
-keep class fr.bayral.archerymonitor.core.interfaces.** { *; }
-keep class fr.bayral.archerymonitor.feature_ai.** { *; }
-keep class fr.bayral.archerymonitor.ui.** { *; }

# Hilt/Dagger
-keep class * extends java.lang.annotation.Annotation
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
    @javax.inject.Inject *;
}
