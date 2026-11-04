package com.example.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.data.local.SiteEntity
import com.example.util.FaviconResolver
import com.example.util.UrlSecurityValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
            // Resolve and download the website's own favicon before asking the launcher
            // to create the shortcut. Never use the AI Hub application icon here.
            val iconBitmap = loadWebsiteIcon(site)
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

                val originalSiteName = site.name.trim().ifBlank {
                    UrlSecurityValidator.extractNormalizedHost(site.url) ?: site.url
                }
                val shortcut = ShortcutInfoCompat.Builder(context, "site_" + site.id)
                    .setShortLabel(originalSiteName.take(25))
                    .setLongLabel(originalSiteName)
                    .setIcon(IconCompat.createWithBitmap(iconBitmap))
                    .setIntent(shortcutIntent)
                    .build()

                val accepted = runCatching {
                    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                }.getOrDefault(false)

                val message = if (accepted) {
                    if (isArabic(context)) "تم إرسال طلب إضافة اختصار الموقع إلى الشاشة الرئيسية." else "Website shortcut request sent to your home screen."
                } else {
                    if (isArabic(context)) "تعذرت إضافة الاختصار. تحقق من دعم مشغل الشاشة الرئيسية." else "Could not add the shortcut. Check launcher support."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Uses the icon URL saved for this site first, then discovers the website's
     * original HTML icon, and finally tries the domain favicon endpoint.
     * The resulting bitmap is a square transparent icon suitable for Android launchers.
     */
    private suspend fun loadWebsiteIcon(site: SiteEntity): Bitmap {
        val candidates = linkedSetOf<String>()

        // Prefer a user-supplied non-proxy icon, then the original icon declared by
        // the website. Google S2 proxy favicons are fallback-only, not first choice.
        site.iconUrl?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("google.com/s2/favicons", ignoreCase = true) }
            ?.let(candidates::add)

        runCatching {
            FaviconResolver.discoverOriginalSiteIconUrl(site.url)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let(candidates::add)

        site.iconUrl?.trim()
            ?.takeIf { it.isNotBlank() && it.contains("google.com/s2/favicons", ignoreCase = true) }
            ?.let(candidates::add)
        FaviconResolver.buildInstantFaviconUrl(site.url)?.let(candidates::add)

        for (iconUrl in candidates) {
            val downloaded = downloadIcon(iconUrl)
            if (downloaded != null) return fitLauncherIcon(downloaded)
        }

        return buildWebsiteFallbackIcon(site)
    }

    private fun downloadIcon(iconUrl: String): Bitmap? {
        val safeUrl = UrlSecurityValidator.normalizeAndValidateInputUrl(iconUrl) ?: return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(safeUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.8")
                setRequestProperty("User-Agent", "AI-Hub-Android")
            }

            val responseCode = connection.responseCode
            val finalProtocol = connection.url.protocol
            val contentType = connection.contentType?.lowercase().orEmpty()
            val contentLength = connection.contentLengthLong
            if (
                responseCode !in 200..299 ||
                !finalProtocol.equals("https", ignoreCase = true) ||
                (!contentType.startsWith("image/") && contentType.isNotBlank()) ||
                contentLength > MAX_ICON_BYTES
            ) {
                return null
            }

            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_ICON_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }
            BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun fitLauncherIcon(source: Bitmap): Bitmap {
        val size = 192
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val maxContentSize = 176f
        val scale = minOf(
            maxContentSize / source.width.coerceAtLeast(1),
            maxContentSize / source.height.coerceAtLeast(1)
        )
        val width = source.width * scale
        val height = source.height * scale
        val left = (size - width) / 2f
        val top = (size - height) / 2f
        val destination = RectF(left, top, left + width, top + height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, destination, paint)
        if (source !== output && !source.isRecycled) source.recycle()
        return output
    }

    /**
     * A neutral globe is used only if the site does not expose a downloadable icon.
     * It is deliberately not the AI Hub application icon.
     */
    private fun buildWebsiteFallbackIcon(site: SiteEntity): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = site.accentColorHex.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 78f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        val globe = RectF(59f, 59f, 133f, 133f)
        canvas.drawOval(globe, paint)
        canvas.drawOval(RectF(80f, 59f, 112f, 133f), paint)
        canvas.drawLine(60f, 96f, 132f, 96f, paint)
        canvas.drawArc(RectF(65f, 73f, 127f, 119f), 0f, 180f, false, paint)
        canvas.drawArc(RectF(65f, 73f, 127f, 119f), 180f, 180f, false, paint)
        return bitmap
    }

    private fun isArabic(context: Context): Boolean {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return locale.language == "ar"
    }

    private const val MAX_ICON_BYTES = 2 * 1024 * 1024
}
