package com.launcher.androidmediabar.data

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** `queryIntentActivities` across releases: the `ResolveInfoFlags` overload only exists from Android 13. */
internal fun PackageManager.queryActivitiesCompat(intent: Intent, flags: Int = 0): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, flags)
    }
