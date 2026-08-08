package com.aidesktop.os

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt graph root.
 * No background services with elevated permissions are started here —
 * this app performs no device-wide automation.
 */
@HiltAndroidApp
class AiDesktopApp : Application()
