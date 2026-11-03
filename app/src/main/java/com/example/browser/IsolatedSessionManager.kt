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
 * Manages per-site session isolation for AI Hub.
 *
 * Improvements:
 * - Login (including Google) stays inside the WebView profile
 * - Cookies are flushed aggressively so sessions survive app restarts
 * - Third-party cookies are accepted (needed for Google OAuth)
 */
class IsolatedSessionManager(
    private val settingsDataStore: SettingsDataStore
) {
    private val mutex = Mutex()
    private var activeFallbackSite: SiteEntity? = null

    /**
     * Attaches the WebView to the site's dedicated isolated profile BEFORE any URL is loaded.
     */
    suspend fun attachIsolatedProfile(webView: WebView, site: SiteEntity) = mutex.withLock {
        val attachedMultiProfile = withContext(Dispatchers.Main.immediate) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    WebViewCompat.setProfile(webView, site.profileKey)
                    val profile = ProfileStore.getInstance().getOrCreateProfile(site.profileKey)
                    profile.cookieManager.apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }
                    true
                } else {
                    false
                }
            } catch (_: Throwable) {
                false
            }
        }

        if (attachedMultiProfile) return@withLock

        // Fallback Cookie Vault Isolation (older WebView versions)
        val previous = activeFallbackSite
        if (previous != null && previous.profileKey != site.profileKey) {
            val prevCookieHeader = withContext(Dispatchers.Main.immediate) {
                val cm = CookieManager.getInstance()
                // Save cookies for the previous site from multiple possible origins
                val origins = listOfNotNull(
                    extractOrigin(previous.url),
                    "https://accounts.google.com",
                    "https://google.com",
                    "https://www.google.com"
                ).distinct()
                val allCookies = origins.mapNotNull { origin ->
                    cm.getCookie(origin)?.takeIf { it.isNotBlank() }
                }.joinToString("; ")
                cm.removeAllCookies(null)
                cm.flush()
                allCookies
            }
            if (prevCookieHeader.isNotBlank()) {
                settingsDataStore.saveProfileCookieVault(previous.profileKey, prevCookieHeader)
            }
        }

        val savedCookies = settingsDataStore.loadProfileCookieVault(site.profileKey)
        withContext(Dispatchers.Main.immediate) {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)

            if (!savedCookies.isNullOrBlank()) {
                // Restore cookies to the site origin + Google domains (for SSO)
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
        activeFallbackSite = site
    }

    /**
     * Flushes cookies to persistent disk storage so logins survive app restarts.
     * Also saves Google-related cookies so the account can be reused more reliably.
     */
    suspend fun persistActiveSession(site: SiteEntity) = mutex.withLock {
        var fallbackCookiesToSave: String? = null
        withContext(Dispatchers.Main.immediate) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    val profile = ProfileStore.getInstance().getProfile(site.profileKey)
                    profile?.cookieManager?.flush()
                } else {
                    val cm = CookieManager.getInstance()
                    val origins = listOfNotNull(
                        extractOrigin(site.url),
                        "https://accounts.google.com",
                        "https://google.com",
                        "https://www.google.com"
                    ).distinct()
                    val allCookies = origins.mapNotNull { origin ->
                        cm.getCookie(origin)?.takeIf { it.isNotBlank() }
                    }.joinToString("; ")
                    fallbackCookiesToSave = allCookies
                    cm.flush()
                }
            } catch (_: Throwable) {
            }
        }
        if (!fallbackCookiesToSave.isNullOrBlank()) {
            settingsDataStore.saveProfileCookieVault(site.profileKey, fallbackCookiesToSave!!)
        }
    }

    /**
     * Clears cookies, login session, DOM storage, and cache ONLY for the specified site
     * without affecting any other site in AI Hub.
     */
    suspend fun clearSiteDataOnly(site: SiteEntity, activeWebView: WebView? = null) = mutex.withLock {
        settingsDataStore.clearProfileCookieVault(site.profileKey)
        withContext(Dispatchers.Main.immediate) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    val store = ProfileStore.getInstance()
                    val profile = store.getProfile(site.profileKey)
                    if (profile != null) {
                        profile.cookieManager.removeAllCookies(null)
                        profile.cookieManager.flush()
                        profile.webStorage.deleteAllData()
                    }
                } else {
                    val origin = extractOrigin(site.url)
                    if (origin != null) {
                        WebStorage.getInstance().deleteOrigin(origin)
                    }
                    if (activeFallbackSite?.profileKey == site.profileKey) {
                        val cm = CookieManager.getInstance()
                        cm.removeAllCookies(null)
                        cm.flush()
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
