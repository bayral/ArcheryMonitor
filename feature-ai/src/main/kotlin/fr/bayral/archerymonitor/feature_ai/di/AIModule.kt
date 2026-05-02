package fr.bayral.archerymonitor.feature_ai.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.interfaces.IPoseAnalyzer
import fr.bayral.archerymonitor.feature_ai.MediaPipePoseAnalyzer
import javax.inject.Singleton

/**
 * Hilt module for providing AI/Pose estimation implementations.
 * These bindings are used by Hilt for dependency injection and are marked as unused
 * by the IDE because they are only accessed during code generation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {
    @Binds
    @Singleton
    @Suppress("unused")
    abstract fun bindPoseAnalyzer(impl: MediaPipePoseAnalyzer): IPoseAnalyzer
}
