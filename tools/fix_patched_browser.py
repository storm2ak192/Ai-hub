#!/usr/bin/env python3
"""Post-process patched BrowserScreen.kt for compile safety + OAuth + speed."""
from pathlib import Path

p = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
t = p.read_text(encoding="utf-8")

t = t.replace("view?.loadUrl(u)", "mainWv.loadUrl(u)")

cleaned = []
for line in t.splitlines(keepends=True):
    stripped = line.strip()
    if stripped.startswith("private val ") and "Mozilla/5.0" in stripped and "DESKTOP_USER_AGENT" not in stripped:
        print("drop bad line:", stripped[:80])
        continue
    cleaned.append(line)
t = "".join(cleaned)

# OAuth redirect + auth host allowlist
old_google_check = '''                                    val targetHost = UrlSecurityValidator.extractNormalizedHost(targetStr)
                                    val isGoogleLogin = targetHost == "accounts.google.com" ||
                                        (targetHost != null && targetHost.endsWith("google.com") &&
                                            (targetStr.contains("oauth", true) ||
                                                targetStr.contains("ServiceLogin", true) ||
                                                targetStr.contains("signin", true)))
                                    if (isGoogleLogin) {
                                        // Let this WebView navigate — do not switch site / blank screen
                                        return false
                                    }'''

new_google_check = '''                                    // Always allow HTTP redirects (OAuth completion steps)
                                    if (request.isRedirect) {
                                        return false
                                    }
                                    val targetHost = UrlSecurityValidator.extractNormalizedHost(targetStr) ?: ""
                                    val lowerUrl = targetStr.lowercase()
                                    val isGoogleLogin = targetHost == "accounts.google.com" ||
                                        (targetHost.endsWith("google.com") &&
                                            ("oauth" in lowerUrl || "servicelogin" in lowerUrl || "signin" in lowerUrl))
                                    val isAuthCallback =
                                        targetHost.startsWith("auth.") ||
                                        targetHost.endsWith("grokusercontent.com") ||
                                        targetHost.endsWith("googleusercontent.com") ||
                                        "set-cookie" in lowerUrl ||
                                        "/oauth" in lowerUrl ||
                                        "/callback" in lowerUrl ||
                                        "/sso" in lowerUrl ||
                                        "openid" in lowerUrl
                                    if (isGoogleLogin || isAuthCallback) {
                                        return false
                                    }'''

if old_google_check in t:
    t = t.replace(old_google_check, new_google_check, 1)
    print("OK: OAuth redirect + auth host allowlist")
elif "isAuthCallback" in t:
    print("OK: auth allowlist already")
else:
    print("WARN: google login check block not found")

# Extra WebView speed settings after LAYER_TYPE_HARDWARE block if present
if "offscreenPreRaster" not in t and "LAYER_TYPE_HARDWARE" in t:
    old_speed = (
        "mainWv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)\n"
        "                        mainWv.settings.domStorageEnabled = true\n"
        "                        mainWv.settings.loadsImagesAutomatically = true\n"
        "                        mainWv.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT"
    )
    new_speed = (
        "mainWv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)\n"
        "                        mainWv.settings.domStorageEnabled = true\n"
        "                        mainWv.settings.databaseEnabled = true\n"
        "                        mainWv.settings.loadsImagesAutomatically = true\n"
        "                        mainWv.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT\n"
        "                        mainWv.settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)\n"
        "                        if (android.os.Build.VERSION.SDK_INT >= 23) {\n"
        "                            mainWv.settings.offscreenPreRaster = true\n"
        "                        }\n"
        "                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)\n"
        "                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(mainWv, true)"
    )
    if old_speed in t:
        t = t.replace(old_speed, new_speed, 1)
        print("OK: extra WebView speed")
    else:
        print("WARN: speed block pattern missing")

p.write_text(t, encoding="utf-8")
print("braces", t.count("{"), t.count("}"))
print("has undefined const", "CHROME_MOBILE_USER_AGENT" in t)
print("has auth allow", "isAuthCallback" in t)
