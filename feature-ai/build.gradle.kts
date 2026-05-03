plugins {
    id("com.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "fr.bayral.archerymonitor.feature_ai"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.camera.core.ExperimentalGetImage",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=androidx.camera.camera2.interop.ExperimentalCamera2Interop"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":resources"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
