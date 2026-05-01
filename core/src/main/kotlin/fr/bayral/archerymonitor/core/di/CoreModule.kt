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

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun bindBufferManager(impl: CircularBufferManager): IBufferManager

    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: SyncEngineImpl): ISyncEngine
}
