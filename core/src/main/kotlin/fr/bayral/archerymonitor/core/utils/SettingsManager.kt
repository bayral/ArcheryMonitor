package fr.bayral.archerymonitor.core.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("archery_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RECORDING_DELAY = "recording_delay"
        private const val KEY_USE_FRONT_CAMERA = "use_front_camera"
        private const val DEFAULT_DELAY = 6f
    }

    var recordingDelay: Float
        get() = prefs.getFloat(KEY_RECORDING_DELAY, DEFAULT_DELAY)
        set(value) = prefs.edit { putFloat(KEY_RECORDING_DELAY, value)}

    var useFrontCamera: Boolean
        get() = prefs.getBoolean(KEY_USE_FRONT_CAMERA, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_FRONT_CAMERA, value) }
}
