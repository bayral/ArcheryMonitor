package fr.bayral.archerymonitor.core.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import fr.bayral.archerymonitor.core.interfaces.BowType
import fr.bayral.archerymonitor.core.interfaces.Laterality

/**
 * Persists application settings using [SharedPreferences].
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("archery_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RECORDING_DELAY = "recording_delay"
        private const val KEY_USE_FRONT_CAMERA = "use_front_camera"
        private const val KEY_LATERALITY = "laterality"
        private const val KEY_BOW_TYPE = "bow_type"
        private const val KEY_SELECTED_MODULE = "selected_module"
        private const val DEFAULT_DELAY = 6f
    }

    var recordingDelay: Float
        get() = prefs.getFloat(KEY_RECORDING_DELAY, DEFAULT_DELAY)
        set(value) = prefs.edit { putFloat(KEY_RECORDING_DELAY, value)}

    var useFrontCamera: Boolean
        get() = prefs.getBoolean(KEY_USE_FRONT_CAMERA, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_FRONT_CAMERA, value) }

    var laterality: Laterality
        get() = Laterality.valueOf(prefs.getString(KEY_LATERALITY, Laterality.RIGHT_HANDED.name) ?: Laterality.RIGHT_HANDED.name)
        set(value) = prefs.edit { putString(KEY_LATERALITY, value.name) }

    var bowType: BowType
        get() = BowType.valueOf(prefs.getString(KEY_BOW_TYPE, BowType.RECURVE.name) ?: BowType.RECURVE.name)
        set(value) = prefs.edit { putString(KEY_BOW_TYPE, value.name) }

    var selectedModuleId: String?
        get() = prefs.getString(KEY_SELECTED_MODULE, null)
        set(value) = prefs.edit { putString(KEY_SELECTED_MODULE, value) }
}
