#!/usr/bin/env python3
"""Wire BrowserScreen routing callbacks in AIHubApp.kt."""
from pathlib import Path

path = Path("app/src/main/java/com/example/ui/AIHubApp.kt")
text = path.read_text(encoding="utf-8")

old = """                BrowserScreen(
                    site = currentSession.site,
                    initialUrl = currentSession.initialUrl,
                    sessionManager = viewModel.sessionManager,
                    onReturnHome = { viewModel.closeBrowserToHome() },"""

new = """                BrowserScreen(
                    site = currentSession.site,
                    initialUrl = currentSession.initialUrl,
                    sessionManager = viewModel.sessionManager,
                    allSites = allSites,
                    onOpenMatchedSite = { matchedSite, url ->
                        viewModel.openSite(matchedSite, overrideUrl = url)
                    },
                    onUnknownExternalUrl = { url ->
                        viewModel.handleExternalUrl(url)
                    },
                    onReturnHome = { viewModel.closeBrowserToHome() },"""

if "onOpenMatchedSite" in text:
    print("OK: AIHubApp already wired")
elif old in text:
    text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")
    print("OK: AIHubApp routing wired")
else:
    print("WARN: BrowserScreen call pattern not found")
    raise SystemExit(1)
