package fr.bayral.archerymonitor.feature_ai.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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
abstract class AIModule {

    @Binds
    @Singleton
    abstract fun bindPoseAnalyzer(analyzer: MediaPipePoseAnalyzer): IPoseAnalyzer

    companion object {
        @Provides
        @IntoSet
        fun provideGeneralPostureModule(): IPostureModule = GeneralPostureModule()
    }
}
