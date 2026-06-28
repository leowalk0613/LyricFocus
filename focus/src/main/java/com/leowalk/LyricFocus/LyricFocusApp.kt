package com.leowalk.LyricFocus

import android.app.Application
import com.google.android.material.color.DynamicColors

class LyricFocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
