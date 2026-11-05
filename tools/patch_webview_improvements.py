#!/usr/bin/env python3
"""Apply low-memory WebView tuning and session-safe reload behavior after all source patches."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
text = path.read_text(encoding="utf-8")

# 1) Reload must persist the active profile cookies before asking WebView to reload.
# It must never clear cookies, cache, DOM storage, form data, or site data.
reload_marker = "    var appliedZoomPercent by remember(site.id) { mutableIntStateOf(site.zoomPercent) }"
reload_helper = reload_marker + """

    // Save the active profile before a manual reload so a slow device or WebView
    // process restart cannot race the cookie flush and appear to sign the user out.
    fun reloadPagePreservingSession() {
        val targetWebView = webViewRef ?: return
        hasLoadError = false
        scope.launch {
            try {
                sessionManager.persistActiveSession(currentSite, currentUrl)
            } catch (_: Throwable) {
                try { android.webkit.CookieManager.getInstance().flush() } catch (_: Throwable) {}
            }
            if (webViewRef === targetWebView) {
                targetWebView.reload()
            }
        }
    }
"""
if "fun reloadPagePreservingSession()" not in text:
    if reload_marker not in text:
        raise SystemExit("Could not locate zoom state for safe reload helper")
    text = text.replace(reload_marker, reload_helper, 1)

# Route both toolbar refresh and error-retry through the same session-safe operation.
text = text.replace("webViewRef?.reload()", "reloadPagePreservingSession()")
if text.count("reloadPagePreservingSession()") < 3:
    raise SystemExit("Expected safe reload helper plus both reload actions")

# Persist the actual page origin too, not only the site's configured start URL.
# The first occurrence is the main WebView callback, where finalUrl is defined.
# Leave the popup callback using its default site-origin persistence.
text = text.replace(
    "sessionManager.persistActiveSession(currentSite)",
    "sessionManager.persistActiveSession(currentSite, finalUrl)",
    1
)

# 2) Keep manual zoom as a per-site persisted preference and reapply it after each page load.
# This assertion protects the existing Room-backed zoom feature from accidental removal.
if "zoomPercent = currentSite.zoomPercent" not in text:
    raise SystemExit("Per-site zoom restore hook is missing")
if "onUpdateZoomPercent(site, (site.zoomPercent" not in text:
    raise SystemExit("Per-site zoom controls are missing")

# 3) Tune WebView for memory-constrained phones: keep normal caching and DOM storage,
# eagerly load images when requested, and avoid off-screen raster buffers that waste RAM.
settings_marker = "        cacheMode = WebSettings.LOAD_DEFAULT"
if "offscreenPreRaster = false" not in text:
    if settings_marker not in text:
        raise SystemExit("Could not locate secure WebView settings block")
    tuning = """
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        blockNetworkImage = false
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            // Pre-rasterizing off-screen pages consumes extra memory on low-RAM devices.
            offscreenPreRaster = false
        }"""
    text = text.replace(settings_marker, settings_marker + tuning, 1)

path.write_text(text, encoding="utf-8")
print("WebView improvements applied; braces:", text.count("{"), text.count("}"))
