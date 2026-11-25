package dev.miles.fliplink

import android.app.Application
import android.content.ComponentCallbacks2

class FlipLinkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::container.isInitialized) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            container.appIconCache.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::container.isInitialized) {
            container.appIconCache.clear()
        }
    }
}
