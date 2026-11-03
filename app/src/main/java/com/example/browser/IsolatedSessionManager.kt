package com.example.browser

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.data.local.SiteEntity
import com.example.data.preferences.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Shared session manager — isolation DISABLED.
 *
 * All sites share one cookie jar so a Google account logged in on any site
 * appears on the other sites as well. Cookies are flushed to disk so the
 * session survives app restarts.
 */
class IsolatedSessionManager(
    private val settingsDataStore: SettingsDataStore
) {
    private val mutex = Mutex()

    private val SHARED_PROFILE = "aihub_shared_session"

    private val GOOGLE_ORIGINS = listOf(
        "https://accounts.google.com",
        "https://google.com",
        "https://www.google.com",
        "https://myaccount.google.com"
    )

    /**
     * Attach every WebView to the same shared profile and accept third-party cookies.
     */
    suspend fun attachIsolatedProfile(webView: WebView, site: SiteEntity) = mutex.withLock {
        val savedCookies = settingsDataStore.loadProfileCookieVault(SHARED_PROFILE)

        withContext(Dispatchers.Main.immediate) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    WebViewCompat.setProfile(webView, SHARED_PROFILE)
                    val profile = ProfileStore.getInstance().getOrCreateProfile(SHARED_PROFILE)
                    profile.cookieManager.apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }
                } else {
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(webView, true)
                    restoreCookies(cm, site, savedCookies)
                }
            } catch (_: Throwable) {
                try {
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(webView, true)
                    restoreCookies(cm, site, savedCookies)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun restoreCookies(cm: CookieManager, site: SiteEntity, savedCookies: String?) {
        if (savedCookies.isNullOrBlank()) return
        val origins = (GOOGLE_ORIGINS + listOfNotNull(extractOrigin(site.url))).distinct()
        savedCookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { pair ->
            origins.forEach { origin ->
                try {
                    cm.setCookie(origin, pair)
                } catch (_: Exception) {
                }
            }
        }
        cm.flush()
    }

    /**
     * Flush shared cookies to disk (Google + current site) so login survives restart.
     */
    suspend fun persistActiveSession(site: SiteEntity) = mutex.withLock {
        var cookiesToSave: String? = null

        withContext(Dispatchers.Main.immediate) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getProfile(SHARED_PROFILE)?.cookieManager?.flush()
                }

                val cm = CookieManager.getInstance()
                val origins = (GOOGLE_ORIGINS + listOfNotNull(extractOrigin(site.url))).distinct()
                cookiesToSave = origins.mapNotNull { origin ->
                    cm.getCookie(origin)?.takeIf { it.isNotBlank() }
                }.joinToString("; ")
                cm.flush()
            } catch (_: Throwable) {
            }
        }

        if (!cookiesToSave.isNullOrBlank()) {
            settingsDataStore.saveProfileCookieVault(SHARED_PROFILE, cookiesToSave!!)
        }
    }

    /**
     * Clears only the current site's DOM storage/cache.
     * Shared Google cookies are kept so other sites stay logged in.
     */
    suspend fun clearSiteDataOnly(site: SiteEntity, activeWebView: WebView? = null) = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            try {
                extractOrigin(site.url)?.let { origin ->
                    try {
                        WebStorage.getInstance().deleteOrigin(origin)
                    } catch (_: Throwable) {
                    }
                }
                activeWebView?.clearCache(true)
                activeWebView?.clearHistory()
                activeWebView?.clearFormData()
            } catch (_: Throwable) {
            }
        }
    }

    private fun extractOrigin(url: String): String? {
        return try {
            val uri = url.toUri()
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            null
        }
    }
}
