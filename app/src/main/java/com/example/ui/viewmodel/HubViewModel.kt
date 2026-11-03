package com.example.ui.viewmodel

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.browser.IsolatedSessionManager
import com.example.data.local.AppDatabase
import com.example.data.local.DefaultSitesSeed
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.SiteEntity
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.HubRepository
import com.example.ui.localization.AppLanguage
import com.example.ui.theme.ThemeMode
import com.example.util.ExternalLinkMatch
import com.example.util.FaviconResolver
import com.example.util.UrlSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class BottomNavTab {
    HOME, FAVORITES, HISTORY, SETTINGS
}

enum class SettingsSubScreen {
    MAIN, MANAGE_SITES, DOWNLOADS
}

data class ActiveBrowserSession(
    val site: SiteEntity,
    val initialUrl: String,
    val sessionNonce: Long = System.currentTimeMillis()
)

data class PendingUntrustedLink(
    val host: String,
    val fullUrl: String
)

class HubViewModel(application: Application) : AndroidViewModel(application) {

    // Lazy initialization on background threads so ViewModel creation on Main Thread is 0ms!
    private val settingsDataStore by lazy { SettingsDataStore(application) }
    private val database by lazy { AppDatabase.getInstance(application) }
    val repository by lazy {
        HubRepository(
            siteDao = database.siteDao(),
            historyDao = database.historyDao(),
            downloadDao = database.downloadDao(),
            settingsDataStore = settingsDataStore
        )
    }
    val sessionManager by lazy { IsolatedSessionManager(settingsDataStore) }

    private val initialDefaultSites = DefaultSitesSeed.getDefaultSites()

    private val _selectedTab = MutableStateFlow(BottomNavTab.HOME)
    val selectedTab: StateFlow<BottomNavTab> = _selectedTab.asStateFlow()

    private val _settingsSubScreen = MutableStateFlow(SettingsSubScreen.MAIN)
    val settingsSubScreen: StateFlow<SettingsSubScreen> = _settingsSubScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeBrowserSession = MutableStateFlow<ActiveBrowserSession?>(null)
    val activeBrowserSession: StateFlow<ActiveBrowserSession?> = _activeBrowserSession.asStateFlow()

    private val _pendingUntrustedLink = MutableStateFlow<PendingUntrustedLink?>(null)
    val pendingUntrustedLink: StateFlow<PendingUntrustedLink?> = _pendingUntrustedLink.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Pre-seeded initial values ensure the Home screen renders all 7 AI sites on Frame 1 with zero delay
    private val _sitesState = MutableStateFlow(initialDefaultSites)
    val allSites: StateFlow<List<SiteEntity>> = _sitesState.asStateFlow()

    val filteredEnabledSites: StateFlow<List<SiteEntity>> = combine(
        _sitesState,
        _searchQuery
    ) { sites, query ->
        val enabled = sites.filter { it.isEnabled }
        if (query.isBlank()) {
            enabled
        } else {
            val q = query.trim().lowercase()
            enabled.filter { site ->
                val arDesc = com.example.ui.localization.ArabicStrings.defaultSiteDescriptions[site.id]?.lowercase() ?: ""
                site.name.lowercase().contains(q) ||
                    site.description.lowercase().contains(q) ||
                    arDesc.contains(q) ||
                    site.url.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialDefaultSites)

    val favoriteSites: StateFlow<List<SiteEntity>> = _sitesState.map { sites ->
        sites.filter { it.isEnabled && it.isFavorite }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        initialDefaultSites.filter { it.isEnabled && it.isFavorite }
    )

    private val _historyState = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val historyList: StateFlow<List<HistoryEntity>> = _historyState.asStateFlow()

    private val _downloadsState = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val downloadsList: StateFlow<List<DownloadEntity>> = _downloadsState.asStateFlow()

    private val _themeModeState = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeModeState.asStateFlow()

    private val _languageState = MutableStateFlow(AppLanguage.ARABIC)
    val language: StateFlow<AppLanguage> = _languageState.asStateFlow()

    init {
        // Initialize Room & DataStore strictly on Dispatchers.IO so Main Thread never blocks
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDefaultSitesSeeded()
            _sitesState.value = repository.getAllSitesSnapshot()

            launch {
                repository.allSitesFlow.collect { dbSites ->
                    _sitesState.value = dbSites
                }
            }
            launch {
                repository.historyFlow.collect { _historyState.value = it }
            }
            launch {
                repository.downloadsFlow.collect { _downloadsState.value = it }
            }
            launch {
                repository.themeModeFlow.collect { _themeModeState.value = it }
            }
            launch {
                repository.languageFlow.collect { _languageState.value = it }
            }
            // Background non-blocking upgrade of default site icons to original high-res HTML icons when reachable
            launch {
                val snapshot = repository.getAllSitesSnapshot()
                for (site in snapshot) {
                    val currentIcon = site.iconUrl
                    if (currentIcon == null || currentIcon.contains("google.com/s2/favicons")) {
                        val discovered = FaviconResolver.discoverOriginalSiteIconUrl(site.url)
                        if (!discovered.isNullOrBlank() && discovered != currentIcon) {
                            val latest = database.siteDao().getSiteById(site.id)
                            if (latest != null && (latest.iconUrl == null || latest.iconUrl.contains("google.com/s2/favicons"))) {
                                repository.updateSite(latest.copy(iconUrl = discovered))
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectTab(tab: BottomNavTab) {
        _selectedTab.value = tab
        if (tab != BottomNavTab.SETTINGS) {
            _settingsSubScreen.value = SettingsSubScreen.MAIN
        }
    }

    fun openSettingsSubScreen(subScreen: SettingsSubScreen) {
        _selectedTab.value = BottomNavTab.SETTINGS
        _settingsSubScreen.value = subScreen
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openSite(site: SiteEntity, overrideUrl: String? = null) {
        val targetUrl = overrideUrl?.takeIf { UrlSecurityValidator.isSafeWebUrl(it) } ?: site.url
        _activeBrowserSession.value = ActiveBrowserSession(
            site = site,
            initialUrl = targetUrl
        )
    }

    fun closeBrowserToHome() {
        val current = _activeBrowserSession.value
        _activeBrowserSession.value = null
        // Clear any pending browser snackbar message so nothing pops up on the Home screen!
        _snackbarMessage.value = null
        if (current != null) {
            viewModelScope.launch(Dispatchers.IO) {
                sessionManager.persistActiveSession(current.site)
            }
        }
    }

    /**
     * Handles incoming external links (ACTION_VIEW or shared URLs from WhatsApp, Telegram, etc.).
     * Preserves the full URL path and query parameters!
     */
    fun handleExternalUrl(rawUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDefaultSitesSeeded()
            val sitesSnapshot = repository.getAllSitesSnapshot().ifEmpty { initialDefaultSites }
            when (val match = UrlSecurityValidator.resolveExternalLink(rawUrl, sitesSnapshot)) {
                is ExternalLinkMatch.MatchedSite -> {
                    openSite(match.site, overrideUrl = match.fullUrl)
                }
                is ExternalLinkMatch.UnmatchedValidUrl -> {
                    _pendingUntrustedLink.value = PendingUntrustedLink(
                        host = match.host,
                        fullUrl = match.fullUrl
                    )
                }
                is ExternalLinkMatch.BlockedInvalidUrl -> {
                    _snackbarMessage.value = match.reason
                }
            }
        }
    }

    fun dismissPendingUntrustedLink() {
        _pendingUntrustedLink.value = null
    }

    fun addUntrustedLinkAsSiteAndOpen(pending: PendingUntrustedLink) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseOrigin = "https://${pending.host}"
            val displayTitle = pending.host.substringBefore(".").replaceFirstChar { it.uppercase() }
            val instantIcon = FaviconResolver.buildInstantFaviconUrl(baseOrigin)
            val created = repository.addCustomSite(
                name = displayTitle,
                url = baseOrigin,
                description = pending.host,
                iconUrl = instantIcon,
                iconPreset = "globe",
                accentColorHex = 0xFF7C3AEDL
            )
            _pendingUntrustedLink.value = null
            openSite(created, overrideUrl = pending.fullUrl)
            // Upgrade to original HTML <link rel="apple-touch-icon"|"icon"> asynchronously
            val originalIcon = FaviconResolver.discoverOriginalSiteIconUrl(baseOrigin)
            if (!originalIcon.isNullOrBlank() && originalIcon != instantIcon) {
                repository.updateSite(created.copy(iconUrl = originalIcon))
            }
        }
    }

    fun openUntrustedLinkEphemeral(pending: PendingUntrustedLink) {
        val tempId = UUID.randomUUID().toString().take(8)
        val ephemeralSite = SiteEntity(
            id = "ephemeral_$tempId",
            name = pending.host,
            url = pending.fullUrl,
            description = pending.host,
            iconUrl = FaviconResolver.buildInstantFaviconUrl(pending.fullUrl),
            iconPreset = "globe",
            accentColorHex = 0xFF3B82F6L,
            profileKey = "profile_ephemeral_$tempId"
        )
        _pendingUntrustedLink.value = null
        openSite(ephemeralSite, overrideUrl = pending.fullUrl)
    }

    fun addOrUpdateSite(
        existing: SiteEntity?,
        name: String,
        url: String,
        description: String,
        iconUrl: String?,
        iconPreset: String,
        accentColorHex: Long,
        javaScriptEnabled: Boolean,
        allowCamera: Boolean,
        allowMicrophone: Boolean,
        allowPopups: Boolean,
        allowDownloads: Boolean,
        desktopMode: Boolean,
        isEnabled: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedUrl = UrlSecurityValidator.normalizeAndValidateInputUrl(url) ?: return@launch
            val userExplicitIcon = iconUrl?.trim()?.takeIf { it.isNotEmpty() }
            val initialIconUrl = userExplicitIcon ?: FaviconResolver.buildInstantFaviconUrl(normalizedUrl)

            if (existing == null) {
                val created = repository.addCustomSite(
                    name = name,
                    url = normalizedUrl,
                    description = description,
                    iconUrl = initialIconUrl,
                    iconPreset = iconPreset,
                    accentColorHex = accentColorHex,
                    javaScriptEnabled = javaScriptEnabled,
                    allowCamera = allowCamera,
                    allowMicrophone = allowMicrophone,
                    allowPopups = allowPopups,
                    allowDownloads = allowDownloads,
                    desktopMode = desktopMode
                )
                // If user didn't hardcode a custom icon URL, resolve original high-res icon from site HTML
                if (userExplicitIcon == null || userExplicitIcon.contains("google.com/s2/favicons")) {
                    val discovered = FaviconResolver.discoverOriginalSiteIconUrl(normalizedUrl)
                    if (!discovered.isNullOrBlank() && discovered != created.iconUrl) {
                        repository.updateSite(created.copy(iconUrl = discovered))
                    }
                }
            } else {
                val updated = existing.copy(
                    name = name.trim(),
                    url = normalizedUrl,
                    description = description.trim(),
                    iconUrl = initialIconUrl,
                    iconPreset = iconPreset,
                    accentColorHex = accentColorHex,
                    javaScriptEnabled = javaScriptEnabled,
                    allowCamera = allowCamera,
                    allowMicrophone = allowMicrophone,
                    allowPopups = allowPopups,
                    allowDownloads = allowDownloads,
                    desktopMode = desktopMode,
                    isEnabled = isEnabled
                )
                repository.updateSite(updated)
                if (_activeBrowserSession.value?.site?.id == updated.id) {
                    _activeBrowserSession.value = _activeBrowserSession.value?.copy(site = updated)
                }
                if (userExplicitIcon == null || userExplicitIcon.contains("google.com/s2/favicons")) {
                    val discovered = FaviconResolver.discoverOriginalSiteIconUrl(normalizedUrl)
                    if (!discovered.isNullOrBlank() && discovered != updated.iconUrl) {
                        val refined = updated.copy(iconUrl = discovered)
                        repository.updateSite(refined)
                        if (_activeBrowserSession.value?.site?.id == refined.id) {
                            _activeBrowserSession.value = _activeBrowserSession.value?.copy(site = refined)
                        }
                    }
                }
            }
        }
    }

    fun toggleFavorite(site: SiteEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.toggleFavorite(site) }
    }

    fun toggleSiteEnabled(site: SiteEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.toggleEnabled(site) }
    }

    fun toggleSiteDesktopMode(site: SiteEntity) {
        val updated = site.copy(desktopMode = !site.desktopMode)
        if (_activeBrowserSession.value?.site?.id == site.id) {
            _activeBrowserSession.value = _activeBrowserSession.value?.copy(site = updated)
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (!site.id.startsWith("ephemeral_")) {
                repository.updateSite(updated)
            }
        }
    }

    fun updateSiteZoomPercent(site: SiteEntity, newZoomPercent: Int) {
        val clampedZoom = newZoomPercent.coerceIn(50, 200)
        if (site.zoomPercent == clampedZoom) return
        val updated = site.copy(zoomPercent = clampedZoom)
        if (_activeBrowserSession.value?.site?.id == site.id) {
            _activeBrowserSession.value = _activeBrowserSession.value?.copy(site = updated)
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (!site.id.startsWith("ephemeral_")) {
                repository.updateSite(updated)
            }
        }
    }

    fun moveSiteOrder(siteId: String, moveUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repository.moveSiteOrder(siteId, moveUp) }
    }

    fun deleteSiteAndSession(site: SiteEntity) {
        if (_activeBrowserSession.value?.site?.id == site.id) {
            _activeBrowserSession.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.clearSiteDataOnly(site)
            repository.deleteHistoryForSite(site.id)
            repository.deleteSite(site)
        }
    }

    fun clearSingleSiteData(site: SiteEntity, activeWebView: WebView? = null, successMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.clearSiteDataOnly(site, activeWebView)
            repository.deleteHistoryForSite(site.id)
            _snackbarMessage.value = successMessage
        }
    }

    fun recordPageVisit(site: SiteEntity, title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.recordHistory(site.id, site.name, title, url)
        }
    }

    fun openHistoryItem(entry: HistoryEntity) {
        val site = allSites.value.firstOrNull { it.id == entry.siteId }
        if (site != null) {
            openSite(site, overrideUrl = entry.url)
        } else {
            handleExternalUrl(entry.url)
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteHistoryItem(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) { repository.clearAllHistory() }
    }

    fun recordDownload(download: DownloadEntity, startedMsg: String) {
        _snackbarMessage.value = "$startedMsg (${download.fileName})"
        viewModelScope.launch(Dispatchers.IO) {
            repository.recordDownload(download)
        }
    }

    fun deleteDownloadEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteDownloadItem(id) }
    }

    fun clearAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) { repository.clearAllDownloads() }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeModeState.value = mode
        viewModelScope.launch(Dispatchers.IO) { repository.setThemeMode(mode) }
    }

    fun setLanguage(lang: AppLanguage) {
        _languageState.value = lang
        viewModelScope.launch(Dispatchers.IO) { repository.setLanguage(lang) }
    }

    fun restoreDefaultSites(successMsg: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreDefaultSites()
            if (!successMsg.isNullOrBlank()) {
                _snackbarMessage.value = successMsg
            }
        }
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    fun importBackupJson(json: String, successMsg: String, errorMsg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.importBackupJson(json)
            _snackbarMessage.value = if (ok) successMsg else errorMsg
        }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun consumeMessage() {
        _snackbarMessage.value = null
    }
}
