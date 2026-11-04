package com.example.util

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object FaviconResolver {

    /**
     * Builds a reliable default favicon URL immediately from any valid site URL
     * so cards show the website's real icon right away.
     */
    fun buildInstantFaviconUrl(siteUrl: String): String? {
        val host = UrlSecurityValidator.extractNormalizedHost(siteUrl) ?: return null
        return "https://www.google.com/s2/favicons?domain_url=https://$host&sz=128"
    }

    /**
     * Fetches the website's HTML <head> on a background IO thread to discover its original
     * high-resolution <link rel="apple-touch-icon"> or <link rel="icon"> tag, falling back
     * to /apple-touch-icon.png, /favicon.ico, or Google S2 high-res favicon service.
     */
    suspend fun discoverOriginalSiteIconUrl(siteUrl: String): String? = withContext(Dispatchers.IO) {
        val normalizedUrl = UrlSecurityValidator.normalizeAndValidateInputUrl(siteUrl)
            ?: return@withContext null
        val parsedUri = normalizedUrl.toUri()
        val scheme = parsedUri.scheme ?: "https"
        val host = parsedUri.host ?: return@withContext null
        val origin = "$scheme://$host"

        // 1. Try parsing <link rel="apple-touch-icon" | "icon"> from the website's HTML head
        val htmlDiscoveredIcon = fetchIconFromHtmlHead(normalizedUrl, origin)
        if (!htmlDiscoveredIcon.isNullOrBlank()) {
            return@withContext htmlDiscoveredIcon
        }

        // 2. Check standard /apple-touch-icon.png if reachable with image content-type
        val appleTouchCandidate = "$origin/apple-touch-icon.png"
        if (isReachableImageUrl(appleTouchCandidate)) {
            return@withContext appleTouchCandidate
        }

        // 3. Reliable high-res Google S2 Favicon fallback for the exact domain
        return@withContext buildInstantFaviconUrl(normalizedUrl)
    }

    /**
     * Reads a site's public HTML metadata and returns a concise description for a new site.
     * Uses meta description first, then Open Graph/Twitter description, page title, and host.
     */
    suspend fun discoverSiteDescription(siteUrl: String): String? = withContext(Dispatchers.IO) {
        val normalizedUrl = UrlSecurityValidator.normalizeAndValidateInputUrl(siteUrl)
            ?: return@withContext null
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            if (connection.responseCode !in 200..299) return@withContext null

            val buffer = CharArray(49152)
            val reader = connection.inputStream.bufferedReader(Charsets.UTF_8)
            val charsRead = reader.read(buffer)
            if (charsRead <= 0) return@withContext null
            val html = String(buffer, 0, charsRead)

            val metaTags = Regex("""<meta\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val contentRegex = Regex("""\bcontent\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val nameRegex = Regex("""\b(?:name|property)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val preferredKeys = listOf("description", "og:description", "twitter:description")
            val descriptions = metaTags.findAll(html).mapNotNull { match ->
                val tag = match.value
                val key = nameRegex.find(tag)?.groupValues?.getOrNull(1)?.lowercase(Locale.US)
                val value = contentRegex.find(tag)?.groupValues?.getOrNull(1)
                if (key in preferredKeys && !value.isNullOrBlank()) key to value else null
            }.toList()
            val rawDescription = preferredKeys.firstNotNullOfOrNull { key ->
                descriptions.firstOrNull { it.first == key }?.second
            }
            val title = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)
            val host = URL(connection.url.toString()).host
            val selected = rawDescription ?: title ?: host
            selected
                ?.replace(Regex("""<[^>]+>"""), " ")
                ?.replace("&amp;", "&")
                ?.replace("&quot;", "\"")
                ?.replace("&#39;", "'")
                ?.replace("&lt;", "<")
                ?.replace("&gt;", ">")
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.take(180)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchIconFromHtmlHead(pageUrl: String, origin: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }

            val code = connection.responseCode
            if (code !in 200..299) return null

            val finalOrigin = runCatching {
                val u = connection.url
                "${u.protocol}://${u.host}"
            }.getOrDefault(origin)

            // Read only up to first 48 KB (enough for <head>) to stay fast & lightweight
            val buffer = CharArray(49152)
            val reader = connection.inputStream.bufferedReader(Charsets.UTF_8)
            val charsRead = reader.read(buffer)
            if (charsRead <= 0) return null
            val htmlChunk = String(buffer, 0, charsRead)

            extractBestIconHref(htmlChunk, finalOrigin, connection.url.toString())
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun extractBestIconHref(html: String, origin: String, baseUrl: String): String? {
        val linkRegex = Regex("""<link\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val relRegex = Regex("""\brel\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val hrefRegex = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val sizesRegex = Regex("""\bsizes\s*=\s*["'](\d+)x\d+["']""", RegexOption.IGNORE_CASE)

        data class IconCandidate(val url: String, val priority: Int)
        val candidates = mutableListOf<IconCandidate>()

        for (match in linkRegex.findAll(html)) {
            val tag = match.value
            val rel = relRegex.find(tag)?.groupValues?.getOrNull(1)?.lowercase(Locale.US) ?: continue
            if (!rel.contains("icon")) continue

            val rawHref = hrefRegex.find(tag)?.groupValues?.getOrNull(1)?.trim() ?: continue
            if (rawHref.isEmpty() || rawHref.startsWith("data:")) continue

            val resolved = resolveRelativeIconUrl(rawHref, origin, baseUrl) ?: continue
            val sizeScore = sizesRegex.find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

            val basePriority = when {
                rel.contains("apple-touch-icon") -> 300
                rel == "icon" || rel == "shortcut icon" -> 200
                else -> 100
            }
            val formatBonus = when {
                resolved.endsWith(".png", ignoreCase = true) || resolved.contains(".png?") -> 40
                resolved.endsWith(".webp", ignoreCase = true) -> 35
                resolved.endsWith(".ico", ignoreCase = true) -> 15
                resolved.endsWith(".svg", ignoreCase = true) -> -50 // Coil base doesn't decode SVG withoutcoil-svg
                else -> 10
            }
            candidates.add(IconCandidate(resolved, basePriority + sizeScore + formatBonus))
        }

        return candidates
            .filter { !it.url.endsWith(".svg", ignoreCase = true) }
            .maxByOrNull { it.priority }
            ?.url
    }

    private fun resolveRelativeIconUrl(href: String, origin: String, baseUrl: String): String? {
        return try {
            when {
                href.startsWith("https://", ignoreCase = true) ||
                    href.startsWith("http://", ignoreCase = true) -> href
                href.startsWith("//") -> "https:$href"
                href.startsWith("/") -> "$origin$href"
                else -> URL(URL(baseUrl), href).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isReachableImageUrl(candidateUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(candidateUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 2500
                readTimeout = 2500
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            val contentType = conn.contentType?.lowercase(Locale.US) ?: ""
            code in 200..299 && contentType.startsWith("image/") && !contentType.contains("svg")
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
