package com.streamflixreborn.streamflix.utils

import android.content.Context
import android.webkit.WebView
import com.bumptech.glide.Glide

object CacheUtils {
    fun clearAppCache(context: Context) {
        try { context.cacheDir?.deleteRecursively() } catch (_: Exception) {}

        try {
            Glide.get(context).clearMemory()
            Thread {
                try { Glide.get(context).clearDiskCache() } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}

        try {
            WebView(context).apply {
                clearCache(true)
                destroy()
            }
        } catch (_: Exception) {}
    }
}


