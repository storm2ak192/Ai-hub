#!/usr/bin/env python3
"""Build-time migration from the legacy embedded WebView to browser-owned Custom Tabs."""
from pathlib import Path
import re

ROOT = Path("app/src/main")
JAVA = ROOT / "java/com/example"

def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Required patch point not found: {label}")
    return text.replace(old, new, 1)

# 1) Persist the selected Custom Tabs provider.
p = JAVA / "data/preferences/SettingsDataStore.kt"
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '    val deletedDefaultIds: Set<String> = emptySet()\n)',
    '    val deletedDefaultIds: Set<String> = emptySet(),\n    val selectedBrowserPackage: String? = null\n)',
    "snapshot field"
)
t = replace_once(
    t,
    '        val DELETED_DEFAULT_IDS = stringSetPreferencesKey("deleted_default_ids")\n',
    '        val DELETED_DEFAULT_IDS = stringSetPreferencesKey("deleted_default_ids")\n'
    '        val SELECTED_BROWSER_PACKAGE = stringPreferencesKey("selected_browser_package")\n',
    "browser preference key"
)
t = replace_once(
    t,
    '    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->\n',
    '    val selectedBrowserPackageFlow: Flow<String?> = context.dataStore.data.map { prefs ->\n'
    '        prefs[Keys.SELECTED_BROWSER_PACKAGE]\n'
    '    }\n\n'
    '    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->\n',
    "browser preference flow"
)
t = replace_once(
    t,
    '    suspend fun setLanguage(language: AppLanguage) {\n        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }\n    }\n',
    '    suspend fun setLanguage(language: AppLanguage) {\n        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }\n    }\n\n'
    '    suspend fun setSelectedBrowserPackage(packageName: String) {\n'
    '        context.dataStore.edit { it[Keys.SELECTED_BROWSER_PACKAGE] = packageName }\n'
    '    }\n',
    "browser preference setter"
)
t = replace_once(
    t,
    '            deletedDefaultIds = prefs[Keys.DELETED_DEFAULT_IDS] ?: emptySet()\n',
    '            deletedDefaultIds = prefs[Keys.DELETED_DEFAULT_IDS] ?: emptySet(),\n'
    '            selectedBrowserPackage = prefs[Keys.SELECTED_BROWSER_PACKAGE]\n',
    "snapshot read"
)
t = replace_once(
    t,
    '            prefs[Keys.DELETED_DEFAULT_IDS] = snapshot.deletedDefaultIds\n',
    '            prefs[Keys.DELETED_DEFAULT_IDS] = snapshot.deletedDefaultIds\n'
    '            if (snapshot.selectedBrowserPackage.isNullOrBlank()) {\n'
    '                prefs.remove(Keys.SELECTED_BROWSER_PACKAGE)\n'
    '            } else {\n'
    '                prefs[Keys.SELECTED_BROWSER_PACKAGE] = snapshot.selectedBrowserPackage\n'
    '            }\n',
    "snapshot restore"
)
p.write_text(t, encoding="utf-8")

# 2) Expose provider selection to Compose and reject all unregistered external domains.
p = JAVA / "ui/viewmodel/HubViewModel.kt"
t = p.read_text(encoding="utf-8")
t = replace_once(
    t,
    '    private val _languageState = MutableStateFlow(AppLanguage.ARABIC)\n',
    '    private val _selectedBrowserPackage = MutableStateFlow<String?>(null)\n'
    '    val selectedBrowserPackage: StateFlow<String?> = _selectedBrowserPackage.asStateFlow()\n\n'
    '    private val _languageState = MutableStateFlow(AppLanguage.ARABIC)\n',
    "viewmodel browser state"
)
t = replace_once(
    t,
    '            launch {\n                repository.languageFlow.collect { _languageState.value = it }\n            }\n',
    '            launch {\n                repository.languageFlow.collect { _languageState.value = it }\n            }\n'
    '            launch {\n'
    '                settingsDataStore.selectedBrowserPackageFlow.collect { _selectedBrowserPackage.value = it }\n'
    '            }\n',
    "viewmodel browser flow"
)
t = replace_once(
    t,
    '    fun setLanguage(lang: AppLanguage) {\n        _languageState.value = lang\n        viewModelScope.launch(Dispatchers.IO) { repository.setLanguage(lang) }\n    }\n',
    '    fun setLanguage(lang: AppLanguage) {\n        _languageState.value = lang\n        viewModelScope.launch(Dispatchers.IO) { repository.setLanguage(lang) }\n    }\n\n'
    '    fun setSelectedBrowserPackage(packageName: String) {\n'
    '        _selectedBrowserPackage.value = packageName\n'
    '        viewModelScope.launch(Dispatchers.IO) { settingsDataStore.setSelectedBrowserPackage(packageName) }\n'
    '    }\n',
    "viewmodel browser setter"
)
t = t.replace(
    '        val current = _activeBrowserSession.value\n'
    '        _activeBrowserSession.value = null\n'
    '        // Clear any pending browser snackbar message so nothing pops up on the Home screen!\n'
    '        _snackbarMessage.value = null\n'
    '        if (current != null) {\n'
    '            viewModelScope.launch(Dispatchers.IO) {\n'
    '                sessionManager.persistActiveSession(current.site)\n'
    '            }\n'
    '        }',
    '        _activeBrowserSession.value = null\n'
    '        // Custom Tabs owns and persists its browser cookies; AI Hub does not copy or clear them.\n'
    '        _snackbarMessage.value = null'
)
t = t.replace(
    '                is ExternalLinkMatch.UnmatchedValidUrl -> {\n'
    '                    _pendingUntrustedLink.value = PendingUntrustedLink(\n'
    '                        host = match.host,\n'
    '                        fullUrl = match.fullUrl\n'
    '                    )\n'
    '                }',
    '                is ExternalLinkMatch.UnmatchedValidUrl -> {\n'
    '                    _pendingUntrustedLink.value = null\n'
    '                    _snackbarMessage.value = "هذا الموقع غير مضاف إلى AI Hub"\n'
    '                }'
)
p.write_text(t, encoding="utf-8")

# 3) Strict host matching: only an enabled, registered site and its subdomains are accepted.
p = JAVA / "util/UrlSecurityValidator.kt"
t = p.read_text(encoding="utf-8")
start = t.index('        val exactMatch = sites.firstOrNull { site ->')
end = t.index('        return ExternalLinkMatch.UnmatchedValidUrl', start)
replacement = '''        val matchedSite = sites.firstOrNull { site ->
            if (!site.isEnabled) return@firstOrNull false
            val siteHost = extractNormalizedHost(site.url) ?: return@firstOrNull false
            incomingHost == siteHost || incomingHost.endsWith(".$siteHost")
        }
        if (matchedSite != null) {
            return ExternalLinkMatch.MatchedSite(matchedSite, cleaned)
        }

'''
t = t[:start] + replacement + t[end:]
p.write_text(t, encoding="utf-8")

# 4) Settings: show browser selection, remove the in-app downloads entry and WebView-only
# per-site cookie-clearing controls (Custom Tabs data is owned by the selected browser).
p = JAVA / "ui/screens/SettingsScreen.kt"
t = p.read_text(encoding="utf-8")
if 'import com.example.browser.BrowserProviderSettingsCard' not in t:
    t = t.replace(
        'import com.example.browser.BrowserDownloadAndAuthHelper\n',
        'import com.example.browser.BrowserDownloadAndAuthHelper\n'
        'import com.example.browser.BrowserProviderSettingsCard\n',
        1
    )
t = replace_once(
    t,
    '    language: AppLanguage,\n    onSetThemeMode: (ThemeMode) -> Unit,',
    '    language: AppLanguage,\n'
    '    selectedBrowserPackage: String?,\n'
    '    onSelectBrowser: (String) -> Unit,\n'
    '    onSetThemeMode: (ThemeMode) -> Unit,',
    "settings screen parameters"
)
t = replace_once(
    t,
    '                language = language,\n                onSetThemeMode = onSetThemeMode,',
    '                language = language,\n'
    '                selectedBrowserPackage = selectedBrowserPackage,\n'
    '                onSelectBrowser = onSelectBrowser,\n'
    '                onSetThemeMode = onSetThemeMode,',
    "settings main call"
)
# Patch the SettingsMainContent signature separately from SettingsScreen's signature.
main_idx = t.index("private fun SettingsMainContent(")
main_prefix, main_part = t[:main_idx], t[main_idx:]
old_main_sig = '    language: AppLanguage,\n    onSetThemeMode: (ThemeMode) -> Unit,'
new_main_sig = (
    '    language: AppLanguage,\n'
    '    selectedBrowserPackage: String?,\n'
    '    onSelectBrowser: (String) -> Unit,\n'
    '    onSetThemeMode: (ThemeMode) -> Unit,'
)
if new_main_sig not in main_part:
    if old_main_sig not in main_part:
        raise SystemExit("Required patch point not found: settings main parameters")
    main_part = main_part.replace(old_main_sig, new_main_sig, 1)
t = main_prefix + main_part
# Remove the old in-app downloads card.
t = re.sub(
    r'        // 2\. Downloads Manager Card\n.*?(?=        // 3\. Appearance & Language Card)',
    '',
    t,
    count=1,
    flags=re.S
)
# Add the provider selector card directly below the settings heading.
marker = '        // 1. Manage Websites Card\n'
if 'BrowserProviderSettingsCard(' not in t:
    t = t.replace(
        marker,
        '        item {\n'
        '            BrowserProviderSettingsCard(\n'
        '                selectedPackage = selectedBrowserPackage,\n'
        '                language = language,\n'
        '                onSelectBrowser = onSelectBrowser\n'
        '            )\n'
        '        }\n\n'
        + marker,
        1
    )
# Remove the obsolete WebView cookie/site-data wipe card.
t = re.sub(
    r'        // 4\. Per-Site Session & Data Wipe\n.*?(?=        // 5\. Backup, Restore & Deep Link Verification)',
    '',
    t,
    count=1,
    flags=re.S
)
# Remove the per-site clear-data action from the manage-sites list.
t = re.sub(
    r'                            IconButton\(\n'
    r'                                onClick = \{ onClearSiteSession\(site\) \},\n'
    r'                                modifier = Modifier\.testTag\("manage_clear_data_\$\{site\.id\}"\)\n'
    r'                            \) \{\n'
    r'                                Icon\(Icons\.Filled\.CleaningServices, contentDescription = strings\.browserClearSiteData\)\n'
    r'                            \}\n',
    '',
    t,
    count=1
)
p.write_text(t, encoding="utf-8")

# Remove per-site WebView-only controls from the site editor. The selected browser
# now owns JavaScript, camera/microphone, pop-up, download and desktop-mode controls.
p = JAVA / "ui/components/AddEditSiteDialog.kt"
t = p.read_text(encoding="utf-8")
for tag in [
    "toggle_site_js",
    "toggle_site_camera",
    "toggle_site_mic",
    "toggle_site_popups",
    "toggle_site_downloads",
    "toggle_site_desktop_mode",
]:
    pattern = (
        r'                PermissionToggleRow\\(\\n'
        r'                    label = strings\\.[A-Za-z0-9_]+,\\n'
        r'                    checked = [A-Za-z0-9_]+,\\n'
        r'                    onCheckedChange = \\{ [A-Za-z0-9_]+ = it \\},\\n'
        r'                    tag = "' + re.escape(tag) + r'"\\n'
        r'                \\)\\n'
    )
    t = re.sub(pattern, "", t, count=1)
p.write_text(t, encoding="utf-8")

# 5) Wire the new screen and setting into the restored AIHubApp source.
p = JAVA / "ui/AIHubApp.kt"
t = p.read_text(encoding="utf-8")
t = t.replace(
    'import com.example.ui.screens.BrowserScreen\n',
    'import com.example.browser.CustomTabsBrowserScreen\n'
)
t = replace_once(
    t,
    '    val language by viewModel.language.collectAsStateWithLifecycle()\n',
    '    val language by viewModel.language.collectAsStateWithLifecycle()\n'
    '    val selectedBrowserPackage by viewModel.selectedBrowserPackage.collectAsStateWithLifecycle()\n',
    "AIHubApp browser state"
)
start = t.find('                BrowserScreen(\n')
if start == -1:
    if 'CustomTabsBrowserScreen(' not in t:
        raise SystemExit("BrowserScreen call not found in AIHubApp")
else:
    end = t.find('                SnackbarHost(\n', start)
    if end == -1:
        raise SystemExit("BrowserScreen end marker not found")
    new_call = '''                CustomTabsBrowserScreen(
                    site = currentSession.site,
                    initialUrl = currentSession.initialUrl,
                    selectedBrowserPackage = selectedBrowserPackage,
                    language = language,
                    onSelectBrowser = { viewModel.setSelectedBrowserPackage(it) },
                    onReturnHome = { viewModel.closeBrowserToHome() },
                    onRecordHistory = { title, url ->
                        viewModel.recordPageVisit(currentSession.site, title, url)
                    },
                    onShowMessage = { msg -> viewModel.showMessage(msg) }
                )
'''
    t = t[:start] + new_call + t[end:]
# Add browser selection settings parameters.
t = replace_once(
    t,
    '                                        language = language,\n                                        onSetThemeMode = { viewModel.setThemeMode(it) },',
    '                                        language = language,\n'
    '                                        selectedBrowserPackage = selectedBrowserPackage,\n'
    '                                        onSelectBrowser = { viewModel.setSelectedBrowserPackage(it) },\n'
    '                                        onSetThemeMode = { viewModel.setThemeMode(it) },',
    "AIHubApp settings browser selector"
)
p.write_text(t, encoding="utf-8")

# 6) The legacy BrowserScreen source is restored by older workflows; leave a valid empty
# package file so the WebView implementation is not compiled or used.
legacy = JAVA / "ui/screens/BrowserScreen.kt"
legacy.write_text("package com.example.ui.screens\n", encoding="utf-8")

# 7) Register Custom Tabs providers for Android package visibility and receive HTTP(S)
# links broadly; the ViewModel performs the strict allow-list check before opening.
manifest = ROOT / "AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
if "<queries>" not in m:
    m = m.replace(
        '    <uses-permission android:name="android.permission.INTERNET" />',
        '    <queries>\n'
        '        <intent>\n'
        '            <action android:name="android.support.customtabs.action.CustomTabsService" />\n'
        '        </intent>\n'
        '    </queries>\n\n'
        '    <uses-permission android:name="android.permission.INTERNET" />',
        1
    )
# Replace the previous fixed host allow-list with broad HTTP/HTTPS discovery.
m = re.sub(
    r'            <!-- Explicit Intent Filter for Default AI Sites -->\n'
    r'            <intent-filter android:autoVerify="false">\n'
    r'                <action android:name="android.intent.action.VIEW" />\n'
    r'                <category android:name="android.intent.category.DEFAULT" />\n'
    r'                <category android:name="android.intent.category.BROWSABLE" />\n'
    r'.*?'
    r'            </intent-filter>',
    '            <!-- Accept web links at the Android chooser; AI Hub validates the host against enabled saved sites. -->\n'
    '            <intent-filter android:autoVerify="false">\n'
    '                <action android:name="android.intent.action.VIEW" />\n'
    '                <category android:name="android.intent.category.DEFAULT" />\n'
    '                <category android:name="android.intent.category.BROWSABLE" />\n'
    '                <data android:scheme="http" />\n'
    '                <data android:scheme="https" />\n'
    '            </intent-filter>',
    m,
    count=1,
    flags=re.S
)
manifest.write_text(m, encoding="utf-8")

print("Custom Tabs migration patches applied.")
