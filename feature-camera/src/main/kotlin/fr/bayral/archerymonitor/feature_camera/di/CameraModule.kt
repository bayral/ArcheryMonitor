package fr.bayral.archerymonitor.feature_camera.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.interfaces.ICameraProvider
import fr.bayral.archerymonitor.feature_camera.CameraXProvider
import javax.inject.Singleton

/**
 * Hilt module for injecting Camera feature dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    @Singleton
    fun provideCameraProvider(provider: CameraXProvider): ICameraProvider = provider
}
