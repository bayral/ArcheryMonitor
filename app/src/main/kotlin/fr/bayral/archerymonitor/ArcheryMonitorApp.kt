package fr.bayral.archerymonitor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for Archery Monitor.
 * Uses Hilt for dependency injection across the modular feature sets.
 */
@HiltAndroidApp
class ArcheryMonitorApp : Application()
