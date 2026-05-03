package fr.bayral.archerymonitor.feature_ai.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.interfaces.IPoseAnalyzer
import fr.bayral.archerymonitor.core.interfaces.IPostureModule
import fr.bayral.archerymonitor.feature_ai.GeneralPostureModule
import fr.bayral.archerymonitor.feature_ai.MediaPipePoseAnalyzer
import javax.inject.Singleton

/**
 * Hilt module for injecting AI feature dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun providePoseAnalyzer(analyzer: MediaPipePoseAnalyzer): IPoseAnalyzer = analyzer

    @Provides
    @Singleton
    fun providePostureModules(): List<IPostureModule> = listOf(GeneralPostureModule())
}
