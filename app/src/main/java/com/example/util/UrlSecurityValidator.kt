package com.example.util

import androidx.core.net.toUri
import com.example.data.local.SiteEntity
import java.util.Locale

sealed class ExternalLinkMatch {
    data class MatchedSite(val site: SiteEntity, val fullUrl: String) : ExternalLinkMatch()
    data class UnmatchedValidUrl(val host: String, val fullUrl: String) : ExternalLinkMatch()
    data class BlockedInvalidUrl(val reason: String) : ExternalLinkMatch()
}

object UrlSecurityValidator {

    private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "vbscript", "intent")

    fun normalizeAndValidateInputUrl(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = if (!trimmed.contains("://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return if (isSafeWebUrl(withScheme)) withScheme else null
    }

    fun isSafeWebUrl(url: String): Boolean {
        return try {
            val uri = url.trim().toUri()
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
            if (scheme in BLOCKED_SCHEMES) return false
            if (scheme != "https" && scheme != "http") return false
            val host = uri.host?.trim()?.lowercase(Locale.US) ?: return false
            if (host.isEmpty() || !host.contains(".") || host.contains(" ")) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun extractNormalizedHost(url: String): String? {
        return try {
            val uri = url.trim().toUri()
            uri.host?.lowercase(Locale.US)?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun extractFirstUrlFromSharedText(text: String): String? {
        val regex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)?.value ?: return null
        return if (isSafeWebUrl(match)) match else null
    }

    fun resolveExternalLink(incomingUrl: String, sites: List<SiteEntity>): ExternalLinkMatch {
        val cleaned = incomingUrl.trim()
        if (!isSafeWebUrl(cleaned)) {
            return ExternalLinkMatch.BlockedInvalidUrl("Unsafe or malformed URL")
        }

        val incomingHost = extractNormalizedHost(cleaned)
            ?: return ExternalLinkMatch.BlockedInvalidUrl("Missing host")

        val exactMatch = sites.firstOrNull { site ->
            if (!site.isEnabled) return@firstOrNull false
            val siteHost = extractNormalizedHost(site.url) ?: return@firstOrNull false
            incomingHost == siteHost
        }
        if (exactMatch != null) {
            return ExternalLinkMatch.MatchedSite(exactMatch, cleaned)
        }

        // A configured parent domain may own its subdomains, but a parent
        // domain must never be captured by a site configured only for a subdomain.
        val domainMatch = sites.firstOrNull { site ->
            if (!site.isEnabled) return@firstOrNull false
            val siteHost = extractNormalizedHost(site.url) ?: return@firstOrNull false
            incomingHost.endsWith(".$siteHost")
        }
        if (domainMatch != null) {
            return ExternalLinkMatch.MatchedSite(domainMatch, cleaned)
        }

        return ExternalLinkMatch.UnmatchedValidUrl(host = incomingHost, fullUrl = cleaned)
    }

    /**
     * Disabled: Google/Apple login must load inside the shared WebView session.
     * Intercepting and opening Custom Tabs or a different URL broke navigation
     * (address bar changed without loading the page).
     * Always return false so the WebView navigates to the full OAuth URL itself.
     */
    fun isFederatedOAuthChallenge(url: String, currentSiteUrl: String): Boolean = false
}
