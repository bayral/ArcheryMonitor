package fr.bayral.archerymonitor.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.buffer.CircularBufferManager
import fr.bayral.archerymonitor.core.interfaces.IBufferManager
import fr.bayral.archerymonitor.core.interfaces.ISyncEngine
import fr.bayral.archerymonitor.core.sync.SyncEngineImpl
import javax.inject.Singleton

/**
 * Hilt module for injecting Core layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideBufferManager(manager: CircularBufferManager): IBufferManager = manager

    @Provides
    @Singleton
    fun provideSyncEngine(engine: SyncEngineImpl): ISyncEngine = engine
    }

