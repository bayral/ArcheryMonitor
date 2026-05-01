package fr.bayral.archerymonitor.feature_camera.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.interfaces.ICameraProvider
import fr.bayral.archerymonitor.feature_camera.CameraXProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {
    @Binds
    @Singleton
    abstract fun bindCameraProvider(impl: CameraXProvider): ICameraProvider
}
