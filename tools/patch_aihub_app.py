#!/usr/bin/env python3
"""Wire BrowserScreen routing + Settings reorder callbacks in AIHubApp.kt."""
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

if "onOpenMatchedSite" not in text:
    if old in text:
        text = text.replace(old, new, 1)
        print("OK: AIHubApp routing wired")
    else:
        print("WARN: BrowserScreen call pattern not found")
        raise SystemExit(1)
else:
    print("OK: AIHubApp routing already wired")

# SettingsScreen onReorderSites
old_move = """                                        onMoveSiteOrder = { id, up -> viewModel.moveSiteOrder(id, up) },
                                        onDeleteSite = { viewModel.deleteSiteAndSession(it) },"""
new_move = """                                        onMoveSiteOrder = { id, up -> viewModel.moveSiteOrder(id, up) },
                                        onReorderSites = { from, to -> viewModel.reorderSites(from, to) },
                                        onDeleteSite = { viewModel.deleteSiteAndSession(it) },"""
if "onReorderSites" not in text:
    if old_move in text:
        text = text.replace(old_move, new_move, 1)
        print("OK: Settings onReorderSites wired")
    else:
        # try alternate spacing from restored file
        alt_old = "onMoveSiteOrder = { id, up -> viewModel.moveSiteOrder(id, up) },"
        if alt_old in text:
            text = text.replace(
                alt_old,
                "onMoveSiteOrder = { id, up -> viewModel.moveSiteOrder(id, up) },\n"
                "                                        onReorderSites = { from, to -> viewModel.reorderSites(from, to) },",
                1,
            )
            print("OK: Settings onReorderSites wired (alt)")
        else:
            print("WARN: onMoveSiteOrder pattern not found")
else:
    print("OK: onReorderSites already present")

path.write_text(text, encoding="utf-8")
