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
 * Manages strict per-site session isolation for AI Hub.
 *
 * All WebView, ProfileStore, and CookieManager calls are strictly confined to Dispatchers.Main
 * so calling from background coroutines never throws a WebView thread violation crash.
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

        // Fallback Cookie Vault Isolation
        val previous = activeFallbackSite
        if (previous != null && previous.profileKey != site.profileKey) {
            val prevCookieHeader = withContext(Dispatchers.Main.immediate) {
                val cm = CookieManager.getInstance()
                val origin = extractOrigin(previous.url) ?: previous.url
                val header = cm.getCookie(origin) ?: ""
                cm.removeAllCookies(null)
                cm.flush()
                header
            }
            settingsDataStore.saveProfileCookieVault(previous.profileKey, prevCookieHeader)
        }

        val savedCookies = settingsDataStore.loadProfileCookieVault(site.profileKey)
        withContext(Dispatchers.Main.immediate) {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            if (!savedCookies.isNullOrBlank()) {
                val origin = extractOrigin(site.url) ?: site.url
                savedCookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { pair ->
                    cm.setCookie(origin, pair)
                }
                cm.flush()
            }
        }
        activeFallbackSite = site
    }

    /**
     * Flushes cookies to persistent disk storage so logins survive app restarts.
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
                    val origin = extractOrigin(site.url) ?: site.url
                    fallbackCookiesToSave = cm.getCookie(origin) ?: ""
                    cm.flush()
                }
            } catch (_: Throwable) {
            }
        }
        if (fallbackCookiesToSave != null) {
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
