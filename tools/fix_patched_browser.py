#!/usr/bin/env python3
"""Post-process patched BrowserScreen.kt for compile safety + OAuth completion."""
from pathlib import Path

p = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
t = p.read_text(encoding="utf-8")

t = t.replace("view?.loadUrl(u)", "mainWv.loadUrl(u)")

CHROME_UA = (
    '"Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"'
)
t = t.replace("CHROME_MOBILE_USER_AGENT", CHROME_UA)

cleaned = []
for line in t.splitlines(keepends=True):
    stripped = line.strip()
    if stripped.startswith("private val ") and "Mozilla/5.0" in stripped and "DESKTOP_USER_AGENT" not in stripped:
        print("drop bad line:", stripped[:80])
        continue
    cleaned.append(line)
t = "".join(cleaned)

# Strengthen solution-2: allow redirects + auth callback hosts (e.g. auth.grokusercontent.com)
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
                                    // Google login pages stay in the same WebView
                                    val isGoogleLogin = targetHost == "accounts.google.com" ||
                                        (targetHost.endsWith("google.com") &&
                                            ("oauth" in lowerUrl || "servicelogin" in lowerUrl || "signin" in lowerUrl))
                                    // Auth callback / SSO intermediate hosts (e.g. auth.grokusercontent.com/set-cookie)
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
else:
    print("WARN: google login check block not found")

p.write_text(t, encoding="utf-8")
print("braces", t.count("{"), t.count("}"))
print("has undefined const", "CHROME_MOBILE_USER_AGENT" in t)
print("has auth allow", "isAuthCallback" in t)
