#!/usr/bin/env python3
from pathlib import Path

ROOT = Path("app/src/main/java/com/example")

def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Required shortcut patch point missing: {label}")
    return text.replace(old, new, 1)

p = ROOT / "MainActivity.kt"
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '            Intent.ACTION_VIEW -> {',
    '            com.example.browser.PinnedSiteShortcutManager.ACTION_OPEN_PINNED_SITE -> {\n'
    '                val siteId = incoming.getStringExtra(com.example.browser.PinnedSiteShortcutManager.EXTRA_SITE_ID)\n'
    '                if (!siteId.isNullOrBlank()) viewModel.openPinnedSite(siteId)\n'
    '            }\n'
    '            Intent.ACTION_VIEW -> {',
    "MainActivity shortcut action"
)
p.write_text(t, encoding="utf-8")

p = ROOT / "ui/viewmodel/HubViewModel.kt"
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '    fun closeBrowserToHome() {',
    '    fun openPinnedSite(siteId: String) {\n'
    '        viewModelScope.launch(Dispatchers.IO) {\n'
    '            repository.ensureDefaultSitesSeeded()\n'
    '            val site = repository.getAllSitesSnapshot().firstOrNull { it.id == siteId && it.isEnabled }\n'
    '            if (site != null) {\n'
    '                openSite(site)\n'
    '            } else {\n'
    '                _snackbarMessage.value = "هذا الموقع لم يعد متاحاً أو تم تعطيله."\n'
    '            }\n'
    '        }\n'
    '    }\n\n'
    '    fun pinSiteShortcut(site: SiteEntity) {\n'
    '        com.example.browser.PinnedSiteShortcutManager.pin(getApplication(), site)\n'
    '    }\n\n'
    '    fun closeBrowserToHome() {',
    "ViewModel pinned shortcut methods"
)
p.write_text(t, encoding="utf-8")

p = ROOT / "ui/AIHubApp.kt"
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '                                        onEditSite = { site ->\n'
    '                                            editingSite = site\n'
    '                                            showAddEditDialog = true\n'
    '                                        },\n'
    '                                        onAddSiteClick = {',
    '                                        onEditSite = { site ->\n'
    '                                            editingSite = site\n'
    '                                            showAddEditDialog = true\n'
    '                                        },\n'
    '                                        onAddToHomeScreen = { viewModel.pinSiteShortcut(it) },\n'
    '                                        onAddSiteClick = {',
    "HomeScreen shortcut callback"
)
p.write_text(t, encoding="utf-8")
print("Pinned website shortcuts wired.")
