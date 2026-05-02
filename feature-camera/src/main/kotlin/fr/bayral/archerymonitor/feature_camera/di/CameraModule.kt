package fr.bayral.archerymonitor.feature_camera.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.interfaces.ICameraProvider
import fr.bayral.archerymonitor.feature_camera.CameraXProvider
import javax.inject.Singleton

/**
 * Hilt module for providing CameraX and encoding implementations.
 * These bindings are used by Hilt for dependency injection and are marked as unused
 * by the IDE because they are only accessed during code generation.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class CameraModule {
    @Binds
    @Singleton
    abstract fun bindCameraProvider(impl: CameraXProvider): ICameraProvider
}
