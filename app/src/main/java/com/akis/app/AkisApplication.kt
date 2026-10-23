package com.akis.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.akis.app.data.ImageStore

/** Gives memory back when Android asks: the picture cache is the only large thing the app holds besides its data. */
class AkisApplication : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) ImageStore.releaseMemory()
    }

    @Deprecated("Replaced by onTrimMemory levels, but still called on older versions", ReplaceWith("onTrimMemory"))
    override fun onLowMemory() {
        super.onLowMemory()
        ImageStore.releaseMemory()
    }
}
