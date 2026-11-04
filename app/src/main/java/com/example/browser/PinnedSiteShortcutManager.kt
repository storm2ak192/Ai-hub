package com.example.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.data.local.SiteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
            val iconBitmap = loadSiteIcon(site.iconUrl) ?: createFallbackIcon(site)
            withContext(Dispatchers.Main) {
                val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_PINNED_SITE
                    putExtra(EXTRA_SITE_ID, site.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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

    private fun loadSiteIcon(iconUrl: String?): Bitmap? {
        if (iconUrl.isNullOrBlank() || !iconUrl.startsWith("https://")) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(iconUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AI-Hub-Android")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    total += count
                    if (total > 2 * 1024 * 1024) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun createFallbackIcon(site: SiteEntity): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = site.accentColorHex.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 38f, 38f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 100f
        val initial = site.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val baseline = size / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(initial, size / 2f, baseline, paint)
        return bitmap
    }

    private fun isArabic(context: Context): Boolean {
        val locale = context.resources.configuration.locales[0]
        return locale.language == "ar"
    }
}
