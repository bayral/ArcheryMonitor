package fr.bayral.archerymonitor.feature_ai.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.interfaces.IPoseAnalyzer
import fr.bayral.archerymonitor.feature_ai.MediaPipePoseAnalyzer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {
    @Binds
    @Singleton
    abstract fun bindPoseAnalyzer(impl: MediaPipePoseAnalyzer): IPoseAnalyzer
}
