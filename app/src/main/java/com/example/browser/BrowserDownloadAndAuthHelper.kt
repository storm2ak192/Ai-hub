package com.example.browser

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.example.data.local.DownloadEntity
import com.example.data.local.SiteEntity
import java.io.File
import java.net.URLDecoder

object BrowserDownloadAndAuthHelper {

    fun enqueueDownload(
        context: Context,
        site: SiteEntity,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ): DownloadEntity? {
        if (url.startsWith("data:", ignoreCase = true)) {
            return saveDataUriDownload(context, site, url, mimeType)
        }

        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            return null
        }

        return try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(url.toUri()).apply {
                setTitle(fileName)
                setDescription("${site.name} • AI Hub")
                setMimeType(mimeType.ifBlank { "*/*" })
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                addRequestHeader("User-Agent", userAgent)

                val cookies = getCookiesForSite(site, url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }

                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            DownloadEntity(
                systemDownloadId = downloadId,
                siteId = site.id,
                siteName = site.name,
                fileName = fileName,
                url = url,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                contentLength = contentLength,
                status = "DOWNLOADING"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveDataUriDownload(
        context: Context,
        site: SiteEntity,
        dataUrl: String,
        fallbackMimeType: String
    ): DownloadEntity? {
        return try {
            val commaIndex = dataUrl.indexOf(',')
            if (commaIndex == -1) return null

            val header = dataUrl.substring(5, commaIndex)
            val payload = dataUrl.substring(commaIndex + 1)
            val isBase64 = header.contains(";base64", ignoreCase = true)
            val detectedMime = header.substringBefore(';').ifBlank {
                fallbackMimeType.ifBlank { "application/octet-stream" }
            }

            val bytes: ByteArray = if (isBase64) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
            }

            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(detectedMime) ?: "bin"
            val fileName = "aihub_${site.name.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.$ext"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, detectedMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val itemUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) {
                    context.contentResolver.openOutputStream(itemUri)?.use { out ->
                        out.write(bytes)
                    }
                }
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                File(dir, fileName).writeBytes(bytes)
            }

            DownloadEntity(
                systemDownloadId = -1L,
                siteId = site.id,
                siteName = site.name,
                fileName = fileName,
                url = "Downloads/$fileName",
                mimeType = detectedMime,
                contentLength = bytes.size.toLong(),
                status = "COMPLETED"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getCookiesForSite(site: SiteEntity, url: String): String? {
        return try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().getProfile(site.profileKey)?.cookieManager?.getCookie(url)
            } else {
                CookieManager.getInstance().getCookie(url)
            }
        } catch (_: Throwable) {
            CookieManager.getInstance().getCookie(url)
        }
    }

    fun openSystemDownloadsView(context: Context) {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Opens [url] in a Chrome Custom Tab (in-app browser sheet).
     * Falls back to a normal external browser intent if Custom Tabs is unavailable.
     */
    fun openCustomTab(context: Context, url: String) {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return
        }
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
            customTabsIntent.launchUrl(context, url.toUri())
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
            }
        }
    }
}
