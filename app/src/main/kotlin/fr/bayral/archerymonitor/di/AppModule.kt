package fr.bayral.archerymonitor.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.renderer.VisualCache
import fr.bayral.archerymonitor.core.renderer.VideoExporter
import javax.inject.Singleton

/**
 * Hilt module for injecting App layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVisualCache(): VisualCache = VisualCache(300)

    @Provides
    @Singleton
    fun provideVideoExporter(): VideoExporter = VideoExporter()
}
