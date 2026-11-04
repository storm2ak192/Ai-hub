package com.example.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
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
            val iconBitmap = buildAppIcon(context)
            val appLabel = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
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

                val label = appLabel.ifBlank { "AI Hub" }
                val shortcut = ShortcutInfoCompat.Builder(context, "site_" + site.id)
                    .setShortLabel(label.take(25))
                    .setLongLabel(label)
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
     * Render only the foreground layer of the app's adaptive launcher icon.
     * The transparent bitmap deliberately omits the adaptive icon background.
     */
    private fun buildAppIcon(context: Context): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val appDrawable = context.packageManager.getApplicationIcon(context.applicationInfo)
        val drawable = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            appDrawable is AdaptiveIconDrawable
        ) {
            appDrawable.foreground ?: appDrawable
        } else {
            appDrawable
        }
        val inset = (size * 0.08f).toInt()
        drawable.setBounds(inset, inset, size - inset, size - inset)
        drawable.draw(canvas)
        return bitmap
    }

    private fun isArabic(context: Context): Boolean {
        val locale = context.resources.configuration.locales[0]
        return locale.language == "ar"
    }
}
