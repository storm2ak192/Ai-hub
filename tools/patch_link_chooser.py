#!/usr/bin/env python3
"""When a site opens a link in a new window, ask: secure popup vs Custom Tab."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/BrowserScreen.kt")
text = path.read_text(encoding="utf-8")

# ---------------------------------------------------------------------------
# 1) State for pending link choice
# ---------------------------------------------------------------------------
if "pendingLinkUrl" not in text:
    old_popup_state = "var popupWebView by remember { mutableStateOf<WebView?>(null) }"
    new_popup_state = (
        "var popupWebView by remember { mutableStateOf<WebView?>(null) }\n"
        "    var pendingLinkUrl by remember { mutableStateOf<String?>(null) }"
    )
    if old_popup_state in text:
        text = text.replace(old_popup_state, new_popup_state, 1)
        print("OK: pendingLinkUrl state")
    else:
        print("WARN: popupWebView state not found")

# ---------------------------------------------------------------------------
# 2) Replace onCreatePopup to capture URL then show chooser (not auto-open)
# ---------------------------------------------------------------------------
marker = "onCreatePopup = { resultMsg ->"
if marker not in text:
    print("ERROR: onCreatePopup not found")
    raise SystemExit(1)

start = text.find(marker)
end = text.find("onCustomVideoShow = {", start)
if start < 0 or end < 0:
    print("ERROR: could not bound onCreatePopup block")
    raise SystemExit(1)

indent = "                            "
new_create = (
    "onCreatePopup = { resultMsg ->\n"
    f"{indent}    if (!currentSite.allowPopups || resultMsg == null) return@createHubWebChromeClient false\n"
    f"{indent}    // Probe WebView: capture the target URL, then ask the user how to open it\n"
    f"{indent}    val probe = WebView(ctx)\n"
    f"{indent}    probe.settings.javaScriptEnabled = currentSite.javaScriptEnabled\n"
    f"{indent}    probe.webViewClient = object : WebViewClient() {{\n"
    f"{indent}        override fun shouldOverrideUrlLoading(\n"
    f"{indent}            v: WebView?,\n"
    f"{indent}            req: WebResourceRequest?\n"
    f"{indent}        ): Boolean {{\n"
    f"{indent}            val u = req?.url?.toString() ?: return true\n"
    f"{indent}            if (req.isForMainFrame && u.startsWith(\"https://\", ignoreCase = true) && UrlSecurityValidator.isSafeWebUrl(u)) {{\n"
    f"{indent}                pendingLinkUrl = u\n"
    f"{indent}                try {{ probe.stopLoading(); probe.destroy() }} catch (_: Exception) {{}}\n"
    f"{indent}                return true\n"
    f"{indent}            }}\n"
    f"{indent}            return false\n"
    f"{indent}        }}\n"
    f"{indent}        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {{\n"
    f"{indent}            if (!url.isNullOrBlank() && url.startsWith(\"https://\", ignoreCase = true) && UrlSecurityValidator.isSafeWebUrl(url) && pendingLinkUrl == null) {{\n"
    f"{indent}                pendingLinkUrl = url\n"
    f"{indent}                try {{ probe.stopLoading(); probe.destroy() }} catch (_: Exception) {{}}\n"
    f"{indent}            }}\n"
    f"{indent}        }}\n"
    f"{indent}    }}\n"
    f"{indent}    val transport = resultMsg.obj as? WebView.WebViewTransport\n"
    f"{indent}    transport?.webView = probe\n"
    f"{indent}    resultMsg.sendToTarget()\n"
    f"{indent}    true\n"
    f"{indent}}},\n"
    f"{indent}"
)

text = text[:start] + new_create + text[end:]
print("OK: onCreatePopup chooser probe")

popup_dialog_marker = "// Popup Window Dialog (Sharing the exact same isolated site profile)"
if popup_dialog_marker not in text:
    print("ERROR: popup dialog marker missing")
    raise SystemExit(1)

chooser_dialog = r'''
    // Link open chooser: secure in-app popup OR Chrome Custom Tab
    if (pendingLinkUrl != null) {
        val link = pendingLinkUrl!!
        AlertDialog(
            onDismissRequest = { pendingLinkUrl = null },
            title = { Text(strings.browserOpenLinkTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.browserOpenLinkDesc)
                    Text(
                        text = link,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            pendingLinkUrl = null
                            // Open inside secure in-app popup (same profile)
                            val childWv = WebView(context)
                            childWv.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scope.launch {
                                sessionManager.attachIsolatedProfile(childWv, currentSite)
                                configureSecureWebViewSettings(
                                    webView = childWv,
                                    site = currentSite,
                                    defaultMobileUa = defaultMobileUserAgent
                                )
                                // Tighten the popup independently of the parent site's settings.
                                childWv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                childWv.settings.allowFileAccess = false
                                childWv.settings.allowContentAccess = false
                                childWv.settings.allowFileAccessFromFileURLs = false
                                childWv.settings.allowUniversalAccessFromFileURLs = false
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    childWv.settings.safeBrowsingEnabled = true
                                }
                                childWv.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        req: WebResourceRequest?
                                    ): Boolean {
                                        val u = req?.url?.toString() ?: return false
                                        val scheme = req.url?.scheme?.lowercase() ?: ""
                                        if (scheme != "https" || !UrlSecurityValidator.isSafeWebUrl(u)) {
                                            return try {
                                                if (scheme in setOf("mailto", "tel")) {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, req.url))
                                                }
                                                true
                                            } catch (_: Exception) {
                                                true
                                            }
                                        }
                                        return false
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: android.webkit.SslErrorHandler?,
                                        error: android.net.http.SslError?
                                    ) {
                                        handler?.cancel()
                                    }
                                }
                                childWv.webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        popupWebView?.destroy()
                                        popupWebView = null
                                    }
                                }
                                popupWebView = childWv
                                childWv.loadUrl(link)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.browserOpenInSecurePopup)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingLinkUrl = null
                            try {
                                BrowserDownloadAndAuthHelper.openCustomTab(context, link)
                            } catch (_: Exception) {
                                onShowMessage(strings.browserSecurityBlockedTitle)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.browserOpenInExternalBrowser)
                    }
                    TextButton(
                        onClick = { pendingLinkUrl = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.browserCancel)
                    }
                }
            }
        )
    }

'''

if "browserOpenInSecurePopup" not in text:
    text = text.replace(popup_dialog_marker, chooser_dialog + "    " + popup_dialog_marker, 1)
    print("OK: link chooser dialog")
else:
    print("OK: chooser already present")

if "import androidx.core.net.toUri" not in text and "toUri()" in text:
    if "androidx.core.net.toUri" not in text:
        text = text.replace(
            "import android.content.Intent\n",
            "import android.content.Intent\nimport androidx.core.net.toUri\n",
            1,
        )
        print("OK: toUri import")

if "material3.AlertDialog" not in text and "AlertDialog(" in text:
    text = text.replace(
        "import androidx.compose.material3.Button\n",
        "import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Button\n",
        1,
    )
    if "material3.AlertDialog" not in text:
        text = text.replace(
            "import androidx.compose.material3.MaterialTheme\n",
            "import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.MaterialTheme\n",
            1,
        )
    print("OK: AlertDialog import check")

if "OutlinedButton" in text and "material3.OutlinedButton" not in text:
    text = text.replace(
        "import androidx.compose.material3.Button\n",
        "import androidx.compose.material3.Button\nimport androidx.compose.material3.OutlinedButton\n",
        1,
    )

if "TextButton" in text and "material3.TextButton" not in text:
    text = text.replace(
        "import androidx.compose.material3.Button\n",
        "import androidx.compose.material3.Button\nimport androidx.compose.material3.TextButton\n",
        1,
    )

path.write_text(text, encoding="utf-8")
print("braces", text.count("{"), text.count("}"))
print("has chooser strings refs", "browserOpenInSecurePopup" in text)
print("uses Custom Tab", "openCustomTab" in text)
