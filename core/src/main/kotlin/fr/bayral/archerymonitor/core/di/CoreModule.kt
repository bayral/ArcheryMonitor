package fr.bayral.archerymonitor.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.bayral.archerymonitor.core.buffer.CircularBufferManager
import fr.bayral.archerymonitor.core.interfaces.IBufferManager
import fr.bayral.archerymonitor.core.interfaces.ISyncEngine
import fr.bayral.archerymonitor.core.sync.SyncEngineImpl
import javax.inject.Singleton

/**
 * Hilt module for providing core business logic implementations.
 * These bindings are used by Hilt for dependency injection and are marked as unused
 * by the IDE because they are only accessed during code generation.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun bindBufferManager(impl: CircularBufferManager): IBufferManager

    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: SyncEngineImpl): ISyncEngine
}
