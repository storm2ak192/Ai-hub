package com.example.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.MainActivity
import com.example.data.local.SiteEntity
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PinnedSiteShortcutManager {
    const val ACTION_OPEN_PINNED_SITE = "com.aistudio.aihub.nxqvtr.action.OPEN_PINNED_SITE"
    const val EXTRA_SITE_ID = "com.aistudio.aihub.nxqvtr.extra.SITE_ID"

    fun pin(context: Context, site: SiteEntity) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(
                context,
                if (isArabic(context)) "مشغل الشاشة الرئيسية لا يدعم إضافة الاختصارات." else "Your launcher does not support pinned shortcuts.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val iconBitmap = buildShortcutIcon(context, site)
            withContext(Dispatchers.Main) {
                val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_PINNED_SITE
                    putExtra(EXTRA_SITE_ID, site.id)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }

                val shortcut = ShortcutInfoCompat.Builder(context, "site_" + site.id)
                    .setShortLabel(site.name.take(25))
                    .setLongLabel(site.name)
                    .setIcon(IconCompat.createWithBitmap(iconBitmap))
                    .setIntent(shortcutIntent)
                    .build()

                val accepted = runCatching {
                    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                }.getOrDefault(false)

                val message = if (accepted) {
                    if (isArabic(context)) "تم إرسال طلب إضافة الاختصار إلى الشاشة الرئيسية." else "Shortcut request sent to your home screen."
                } else {
                    if (isArabic(context)) "تعذرت إضافة الاختصار. تحقق من دعم مشغل الشاشة الرئيسية." else "Could not add the shortcut. Check launcher support."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Use the exact same iconUrl source as SiteIconBadge/Coil in the app.
     * Coil also shares its disk and memory cache with the visible site cards.
     */
    private suspend fun buildShortcutIcon(context: Context, site: SiteEntity): Bitmap {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val accent = site.accentColorHex.toInt()

        paint.color = accent
        canvas.drawRoundRect(RectF(0f, 0f, 192f, 192f), 42f, 42f, paint)

        val icon = loadCoilIcon(context, site.iconUrl)
        if (icon != null) {
            val maxSize = 122f
            val scale = minOf(maxSize / icon.width, maxSize / icon.height)
            val drawWidth = icon.width * scale
            val drawHeight = icon.height * scale
            val left = (192f - drawWidth) / 2f
            val top = (192f - drawHeight) / 2f
            val destination = RectF(left, top, left + drawWidth, top + drawHeight)
            paint.isFilterBitmap = true
            canvas.drawBitmap(icon, null, destination, paint)
            if (icon !== bitmap && !icon.isRecycled) icon.recycle()
        } else {
            // Same accent color and site identity as the app's fallback icon treatment.
            paint.color = android.graphics.Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = 104f
            val initial = site.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val baseline = 96f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(initial, 96f, baseline, paint)
        }

        return bitmap
    }

    private suspend fun loadCoilIcon(context: Context, iconUrl: String?): Bitmap? {
        if (iconUrl.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(iconUrl)
                .size(128)
                .allowHardware(false)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                result.drawable.toBitmap(
                    width = 128,
                    height = 128,
                    config = Bitmap.Config.ARGB_8888
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isArabic(context: Context): Boolean {
        val locale = context.resources.configuration.locales[0]
        return locale.language == "ar"
    }
}
