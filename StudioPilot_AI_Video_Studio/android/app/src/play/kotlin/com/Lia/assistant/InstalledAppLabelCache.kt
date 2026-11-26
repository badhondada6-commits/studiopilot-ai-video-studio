package com.Lia.assistant

import android.content.Context

object InstalledAppLabelCache {
    fun prewarm(context: Context) = Unit

    fun labelFor(packageName: String): String? = null
}
