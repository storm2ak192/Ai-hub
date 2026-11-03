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
 * Session manager for AI Hub.
 *
 * Isolation is DISABLED so Google (and other) logins are shared across all sites.
 * All WebViews use the same cookie jar / shared profile so an account logged in
 * on one site is available on the others.
 */
class IsolatedSessionManager(
    private val settingsDataStore: SettingsDataStore
) {
    private val mutex = Mutex()

    /** Shared profile key used by every site (no isolation). */
    private val SHARED_PROFILE = "aihub_shared_session"

    /**
     * Attaches the WebView to the shared session profile and enables cookies
     * (including third-party cookies required for Google OAuth).
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

                    if (!savedCookies.isNullOrBlank()) {
                        val origins = listOfNotNull(
                            extractOrigin(site.url),
                            "https://accounts.google.com",
                            "https://google.com",
                            "https://www.google.com"
                        ).distinct()
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
                }
            } catch (_: Throwable) {
                try {
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(webView, true)
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Flushes cookies so logins survive app restarts and stay shared across sites.
     */
    suspend fun persistActiveSession(site: SiteEntity) = mutex.withLock {
        var cookiesToSave: String? = null

        withContext(Dispatchers.Main.immediate) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    val profile = ProfileStore.getInstance().getProfile(SHARED_PROFILE)
                    profile?.cookieManager?.flush()
                } else {
                    val cm = CookieManager.getInstance()
                    val origins = listOf(
                        "https://accounts.google.com",
                        "https://google.com",
                        "https://www.google.com"
                    ) + listOfNotNull(extractOrigin(site.url))
                    cookiesToSave = origins.distinct().mapNotNull { origin ->
                        cm.getCookie(origin)?.takeIf { it.isNotBlank() }
                    }.joinToString("; ")
                    cm.flush()
                }
            } catch (_: Throwable) {
            }
        }

        if (!cookiesToSave.isNullOrBlank()) {
            settingsDataStore.saveProfileCookieVault(SHARED_PROFILE, cookiesToSave!!)
        }
    }

    /**
     * Clears cache/history for the current WebView only.
     * Does NOT wipe shared Google cookies so other sites stay logged in.
     */
    suspend fun clearSiteDataOnly(site: SiteEntity, activeWebView: WebView? = null) = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            try {
                val origin = extractOrigin(site.url)
                if (origin != null) {
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
