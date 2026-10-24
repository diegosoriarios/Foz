package com.example.foz.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.security.MessageDigest

/**
 * Disk cache for app icons so the launcher can render icons instantly on cold start
 * (including right after the launcher itself is updated), instead of re-decoding
 * every icon from the other apps' APKs.
 *
 * Keys are derived from package/component, the app's last update time and an optional
 * variant tag (icon pack + custom icon), so an updated app or a changed icon
 * automatically misses the cache and gets re-extracted.
 */
class IconCacheManager(context: Context) {
    private val appContext = context.applicationContext

    private val cacheDir: File
        get() = File(appContext.filesDir, CACHE_DIR_NAME).apply { mkdirs() }

    fun cacheKey(packageName: String, className: String, lastUpdateTime: Long, variant: String?): String {
        val raw = "$packageName/$className|$lastUpdateTime|${variant.orEmpty()}"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        val hash = digest.joinToString("") { byte -> "%02x".format(byte) }
        val safePackage = packageName.replace(Regex("[^a-zA-Z0-9._]"), "_")
        return "${safePackage}_$hash"
    }

    fun get(key: String): Drawable? {
        return try {
            val file = File(cacheDir, "$key.png")
            if (!file.exists()) return null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                file.delete()
                null
            } else {
                BitmapDrawable(appContext.resources, bitmap)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun put(key: String, drawable: Drawable) {
        try {
            val bitmap = drawableToBitmap(drawable) ?: return
            val tmp = File(cacheDir, "$key.tmp")
            tmp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!tmp.renameTo(File(cacheDir, "$key.png"))) {
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun cleanUp(validKeys: Set<String>) {
        try {
            val valid = validKeys.map { "$it.png" }.toSet()
            cacheDir.listFiles()?.forEach { file ->
                when {
                    file.name.endsWith(".tmp") -> file.delete()
                    file.isFile && file.name !in valid -> file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
            val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
            val largestSide = maxOf(intrinsicWidth, intrinsicHeight)
            val scale = if (largestSide > MAX_ICON_SIZE) largestSide.toFloat() / MAX_ICON_SIZE else 1f
            val width = (intrinsicWidth / scale).toInt().coerceAtLeast(1)
            val height = (intrinsicHeight / scale).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val CACHE_DIR_NAME = "icon_cache"
        private const val MAX_ICON_SIZE = 192

        fun variantKey(iconPackPackageName: String?, customIconDrawable: String?): String {
            return "${iconPackPackageName.orEmpty()}|${customIconDrawable.orEmpty()}"
        }
    }
}
