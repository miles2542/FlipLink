package dev.miles.fliplink.data.pm

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppIconCache(
    context: Context,
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val pm: PackageManager = context.applicationContext.packageManager
    private val cache = LruCache<String, ImageBitmap>(maxEntries)

    fun getCached(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun loadIcon(packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }

        val loaded = withContext(Dispatchers.IO) {
            try {
                val drawable = pm.getApplicationIcon(packageName)
                val width = drawable.intrinsicWidth.takeIf { it in 1..MAX_ICON_PX } ?: DEFAULT_ICON_PX
                val height = drawable.intrinsicHeight.takeIf { it in 1..MAX_ICON_PX } ?: DEFAULT_ICON_PX
                drawable.toBitmap(width = width, height = height).asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }

        if (loaded != null) {
            cache.put(packageName, loaded)
        }
        return loaded
    }

    fun clear() {
        cache.evictAll()
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 120
        const val DEFAULT_ICON_PX = 96
        const val MAX_ICON_PX = 144
    }
}
