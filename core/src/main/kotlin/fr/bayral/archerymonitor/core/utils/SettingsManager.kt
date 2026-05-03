package fr.bayral.archerymonitor.core.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Persists application settings using [SharedPreferences].
 *
 * This manager stores user preferences such as the replay delay and camera choice
 * to ensure they persist between app sessions.
 *
 * @param context The application context to retrieve SharedPreferences.
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    /** Internal reference to SharedPreferences. */
    private val prefs: SharedPreferences = context.getSharedPreferences("archery_settings", Context.MODE_PRIVATE)

    companion object {
        /** Key for the replay delay setting. */
        private const val KEY_RECORDING_DELAY = "recording_delay"
        /** Key for the front/back camera choice. */
        private const val KEY_USE_FRONT_CAMERA = "use_front_camera"
        /** Default delay in seconds. */
        private const val DEFAULT_DELAY = 6f
    }

    /**
     * User-configured replay delay in seconds.
     */
    var recordingDelay: Float
        get() = prefs.getFloat(KEY_RECORDING_DELAY, DEFAULT_DELAY)
        set(value) = prefs.edit { putFloat(KEY_RECORDING_DELAY, value)}

    /**
     * True if the user prefers the front-facing (selfie) camera.
     */
    var useFrontCamera: Boolean
        get() = prefs.getBoolean(KEY_USE_FRONT_CAMERA, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_FRONT_CAMERA, value) }
}
