package fr.bayral.archerymonitor.core.utils

import fr.bayral.archerymonitor.core.interfaces.ArcherySettings
import fr.bayral.archerymonitor.core.interfaces.IPostureModule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory to manage available posture analysis modules based on settings.
 */
@Singleton
class PostureModuleFactory @Inject constructor(
    private val modules: @JvmSuppressWildcards Set<@JvmSuppressWildcards IPostureModule>
) {
    /** Returns only modules compatible with current archery settings. */
    fun getCompatibleModules(settings: ArcherySettings): List<IPostureModule> {
        return modules.filter { it.capabilities.supportedBowTypes.contains(settings.bowType) }
    }
}
